package at.dire.podcache;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.rometools.rome.feed.synd.SyndFeed;

import at.dire.podcache.data.Feed;
import at.dire.podcache.util.DownloadResponse;
import at.dire.podcache.util.Downloader;
import at.dire.podcache.util.FeedUtils;
import at.dire.podcache.util.FileUtils;
import at.dire.podcache.util.ResourceInfo;

/**
 * Component responsible for holding {@link Feed feed's} file and content files.
 * 
 * @author diredev
 */
@Component
public class ContentManager {
	/** Logger */
	private static final Logger LOG = LoggerFactory.getLogger(ContentManager.class);

	/** Used to hold the feed's RSS feed. */
	private static final String FEED_FILE = "_feed.xml";

	/** Component for downloading files */
	private final Downloader downloader;

	/** The root directory to hold all files. Use Spring configuration. */
	private final Path dataDir;

	// private final ReentrantReadWriteLock fileAccessLock = new reeent

	/**
	 * Creates a new instance.
	 * 
	 * @param downloader used to download files
	 * @param dataDir root data directory
	 * @throws IOException when I cannot connect to my work directory
	 */
	@Autowired
	public ContentManager(Downloader downloader,
			@Value("${podcache.content.directory}") Path dataDir) throws IOException {
		this.dataDir = Objects.requireNonNull(dataDir).toAbsolutePath();
		this.downloader = Objects.requireNonNull(downloader);

		LOG.info("Initializing content manager on directory '{}'.", this.dataDir);
		Files.createDirectories(this.dataDir);
	}

	/**
	 * Delete the feed directory and all content.
	 * 
	 * @param feedName name of the feed
	 * @throws IOException when deleting fails
	 */
	public void delete(String feedName) throws IOException {

		Path feedDir = getFeedDir(feedName);

		if(Files.notExists(feedDir))
			return;

		LOG.debug("Deleting feed directory '{}' and all it's content.", feedDir);
		FileUtils.deleteDirectoryAndContent(getFeedDir(feedName));
	}

	/**
	 * Delete a file for the given feed.
	 * 
	 * @param feedName name of the feed
	 * @param fileName name of the file
	 * @throws IOException when deleting fails
	 */
	public void deleteFile(String feedName, String fileName) throws IOException {
		Path file = getFeedDir(feedName).resolve(fileName);

		if(Files.notExists(file))
			return;

		LOG.debug("Removing feed file '{}'.", file);
		Files.delete(file);
	}

	/**
	 * Download the given feed and return a response. Will also update the feed's modification date if needed.
	 * 
	 * @param feed the feed
	 * @return response
	 * @throws IOException when the download fails
	 */
	public DownloadResponse download(Feed feed) throws IOException {
		DownloadResponse response = this.downloader.request(feed.getUrl(), feed.getLastModified());

		if(!response.isUnchanged()) {
			// Set feed properties.
			feed.setLastModified(response.getLastModified());
			feed.setAllFilesUpdated(false);
		}

		return response;
	}

	/**
	 * Download the newest version of the given feed, parse the result and return it. Will return null if the feed is
	 * {@link Feed#getLastModified() up-to-date}. This will also update this modification date automatically.
	 * 
	 * @param feed the feed
	 * @return new feed or null
	 * @throws IOException when the download fails
	 */
	public @Nullable SyndFeed downloadFeed(Feed feed) throws IOException {
		try(DownloadResponse response = download(feed)) {
			// Unchanged?
			if(response.isUnchanged())
				return null;

			return FeedUtils.read(response.getContent());
		}
	}

	/**
	 * Downloads the feed file for the given feed. Will also update the feed's modification date if needed.
	 * 
	 * @param feed the feed
	 * @return path to the feed's file
	 * @throws IOException when the download fails
	 */
	public Path downloadToFile(Feed feed) throws IOException {
		Path feedDir = createFeedDir(feed.getName());
		Path targetFile = feedDir.resolve(FEED_FILE);

		downloadToFile(feed, targetFile);
		return targetFile;
	}

	/**
	 * Downloads the feed file for the given feed. Will also update the feed's modification date if necessary.
	 * 
	 * @param feed the feed
	 * @param targetFile the target file
	 * @return true if a new file was downloaded
	 * @throws IOException when the download fails
	 */
	public boolean downloadToFile(Feed feed, Path targetFile) throws IOException {
		// Download the file.
		ResourceInfo downloaded = this.downloader.download(feed.getUrl(), targetFile, feed.getLastModified());

		if(downloaded != null) {
			// Set feed properties.
			feed.setLastModified(downloaded.getLastModified());
			feed.setAllFilesUpdated(false);

			return true;
		} else
			return false;
	}

	/**
	 * Downloads and stores the feed file for the given URL and returns a new feed with matching data.
	 * 
	 * @param feedName name of the feed
	 * @param url URL of the feed
	 * @param targetFile file to download to
	 * @return new feed
	 * @throws IOException when the download fails
	 */
	public Feed downloadToFile(String feedName, URL url, Path targetFile) throws IOException {
		// Note: We always pass the stream through Rome here.
		// This is inefficient and slower, but will ensure that we have a useable
		// feed for later use.
		try(DownloadResponse response = this.downloader.request(url, null)) {
			// Download the feed and save it.
			SyndFeed feed = FeedUtils.read(response.getContent());
			FeedUtils.write(feed, targetFile);

			// Return a new feed object.
			String contentType = response.getContentType();

			if(contentType == null)
				contentType = MediaType.APPLICATION_XML_VALUE;

			return new Feed(feedName, url, contentType, response.getLastModified());
		}
	}

	/**
	 * Download the given URL into the feed's directory.
	 *
	 * @param feedName name of the feed
	 * @param url url of the file
	 * @param overwrite overwrite existing files
	 * @return the path of the downloaded file
	 * @throws IOException when downloading fails
	 */
	public Path download(String feedName, URL url, boolean overwrite) throws IOException {
		Path feedDir = createFeedDir(feedName);
		Path targetFile = feedDir.resolve(Paths.get(url.getFile()).getFileName());
		// TODO: Problematic. Need to make sure that names contain no invalid characters. Unlikely in URL.

		if(overwrite || Files.notExists(targetFile)) {
			this.downloader.download(url, targetFile, null);
		} else
			LOG.debug("Not overwriting existing '{}'.", targetFile);

		return targetFile;
	}

	/**
	 * Returns the path to the feed file, i.e. the one containing the RSS/ATOM content.
	 * 
	 * Note that the path returned may not point to an existing file.
	 * 
	 * @param feedName name of the feed
	 * @return path to the file
	 */
	public Path getFeedFile(String feedName) {
		return getFeedDir(feedName).resolve(FEED_FILE);
	}

	/**
	 * Return the content file for the given feed. May not exist.
	 * 
	 * @param feedName name of the feed
	 * @param fileName file name
	 * @return path to the content file
	 */
	public Path getFile(String feedName, String fileName) {
		return getFeedDir(feedName).resolve(fileName);
	}

	/**
	 * Returns the directory for the given feed.
	 * 
	 * @param feedName name of the feed
	 * @return data directory
	 */
	private Path getFeedDir(String feedName) {
		// TODO: have to sanitize this name!!
		return dataDir.resolve(feedName);
	}

	/**
	 * Returns the directory for the given feed and also creates it if necessary.
	 * 
	 * @param feedName name of the feed
	 * @return feed directory
	 * @throws IOException when creating the directory fails
	 */
	private Path createFeedDir(String feedName) throws IOException {
		Path feedDir = getFeedDir(feedName);
		Files.createDirectories(feedDir);
		return feedDir;
	}
}
