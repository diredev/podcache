package at.dire.podcache.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Date;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * This component will download resources. HTTP headers are used to ensure that files are only downloaded if necessary.
 * 
 * @author diredev
 * @see HttpClient
 */
@Component
public class Downloader implements Closeable {
	/** Logger */
	private static final Logger LOG = LoggerFactory.getLogger(Downloader.class);

	/** The actual HTTP Client. Thread-safe according to documentation. */
	private final CloseableHttpClient httpClient = HttpClients.createDefault();

	/**
	 * Request the given resource if it was changed.
	 * 
	 * @param url the URL to download
	 * @param lastModified date of last modification or null
	 * @return the downloaded resource
	 * @throws IOException if the request fails or returns an error code
	 */
	public DownloadResponse request(URL url, @Nullable Date lastModified) throws IOException {
		HttpGet httpGet = new HttpGet(url.toString());

		if(lastModified != null)
			httpGet.setHeader(HttpHeaders.IF_MODIFIED_SINCE, DateUtils.formatDate(lastModified));

		// Request
		CloseableHttpResponse response = httpClient.execute(httpGet);

		// Handle common error (404, 500, etc.)
		if(response.getStatusLine().getStatusCode() >= HttpStatus.SC_BAD_REQUEST)
			throw new IOException(String.format("Request to '%s' has returned '%s'", url, response.getStatusLine()));

		return new DownloadResponse(response);
	}

	/**
	 * Downloads the given URL to the target file. If <code>lastModified</code> is specified then no file will be
	 * downloaded when not needed and null will be returned.
	 * 
	 * @param url the URL to download
	 * @param targetFile the target file
	 * @param lastModified date of last modification or null
	 * @return new downloaded resource or null
	 * @throws IOException if the request or download fails
	 */
	public @Nullable ResourceInfo download(URL url, Path targetFile, @Nullable Date lastModified) throws IOException {
		// Does the local file exist?
		if(lastModified != null && Files.notExists(targetFile)) {
			LOG.debug("I was given a last modification date but my file '{}' does not exist. Will force download.");
			lastModified = null;
		}

		// Request download.
		try(DownloadResponse resource = request(url, lastModified)) {
			// Unchanged?
			if(resource.isUnchanged()) {
				LOG.debug("Resource at '{}' is unchanged. Not downloading.", url);
				return null;
			}

			// Download
			try(InputStream content = resource.getContent()) {
				LOG.debug("Downloading content to '{}'.", targetFile);
				download(content, targetFile);
			}

			return resource;
		}
	}

	/**
	 * Simple download using NIO. Will use a temporary file.
	 * 
	 * @param stream stream to download
	 * @param targetFile target path
	 * @throws IOException when the download fails
	 */
	public static void download(InputStream stream, Path targetFile) throws IOException {
		// Download to a temporary file first.
		Path tempFile = targetFile.resolveSibling(targetFile.getFileName() + ".partial");

		try(ReadableByteChannel inChannel = Channels.newChannel(stream);
				FileChannel outChannel = FileChannel.open(tempFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
						StandardOpenOption.TRUNCATE_EXISTING)) {
			outChannel.transferFrom(inChannel, 0, Long.MAX_VALUE);
		} catch(IOException e) {
			LOG.warn("Failed to download file '{}'. Will remove temporary file.", tempFile);
			Files.deleteIfExists(tempFile);

			throw e;
		}

		// Have downloaded successfully. Move over original file.
		Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
	}

	@Override
	public void close() throws IOException {
		this.httpClient.close();
	}
}
