package at.dire.podcache.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Objects;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.utils.DateUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Response of a {@link Downloader#request(java.net.URL, Date) request}. Gives access to the content, if any was
 * downloaded.
 * 
 * @author diredev
 */
public class DownloadResponse implements ResourceInfo, AutoCloseable {
	/** The response */
	private final CloseableHttpResponse response;

	/**
	 * Creates a new instance for the given response.
	 * 
	 * @param response response
	 */
	public DownloadResponse(CloseableHttpResponse response) {
		this.response = Objects.requireNonNull(response);
	}

	/**
	 * Returns true if the resource is unchanged, i.e. no need to download the entire file. Do not use
	 * {@link #getContent()} in this state.
	 * 
	 * @return True if unchanged
	 */
	public boolean isUnchanged() {
		return(response.getStatusLine().getStatusCode() == 304);
	}

	/**
	 * Returns the last modification date.
	 * 
	 * @return last modification date
	 */
	@Override
	public @Nullable Date getLastModified() {
		Header header = response.getFirstHeader(HttpHeaders.LAST_MODIFIED);

		if(header == null)
			return null;

		return DateUtils.parseDate(header.getValue());
	}

	/**
	 * Returns the content type of the resource.
	 * 
	 * @return content type
	 */
	@Override
	public @Nullable String getContentType() {
		Header header = response.getEntity().getContentType();

		if(header == null)
			return null;

		return header.getValue();
	}

	/**
	 * Returns the resource content.
	 * 
	 * @return content stream
	 * @throws IOException when opening fails
	 * @see #isUnchanged()
	 */
	public InputStream getContent() throws IOException {
		return this.response.getEntity().getContent();
	}

	@Override
	public void close() throws IOException {
		this.response.close();
	}
}
