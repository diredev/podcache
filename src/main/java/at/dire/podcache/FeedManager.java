package at.dire.podcache;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import at.dire.podcache.data.Feed;
import at.dire.podcache.data.FeedRepository;

/**
 * Component used to manage {@link Feed feeds}.
 * 
 * @author diredev
 */
@Component
public class FeedManager {
	/** Logger */
	private static final Logger LOG = LoggerFactory.getLogger(FeedManager.class);

	/** The DB repository for managed feeds */
	private final FeedRepository feedRepo;

	/** Used to manage feed files */
	private final ContentManager contentManager;

	/**
	 * Returns the content manager.
	 * 
	 * @return content manager
	 */
	public ContentManager getContentManager() {
		return this.contentManager;
	}

	/**
	 * Creates a new instance.
	 * 
	 * @param feedRepo DB repository
	 * @param contentManager content manager
	 */
	@Autowired
	public FeedManager(FeedRepository feedRepo, ContentManager contentManager) {
		this.feedRepo = feedRepo;
		this.contentManager = contentManager;
	}

	/**
	 * Returns all feeds.
	 * 
	 * @return feeds
	 */
	@Transactional(readOnly = true)
	public Iterable<Feed> getFeeds() {
		return feedRepo.findAll();
	}

	/**
	 * Returns all feeds and lock all the returned records. Call as part of a transaction.
	 * 
	 * @return feeds
	 */
	public Iterable<Feed> getFeedsAndLock() {
		return feedRepo.findAllPessimistic();
	}

	/**
	 * Find the feed of the given name.
	 * 
	 * @param name name
	 * @return feed or null
	 */
	@Transactional(readOnly = true)
	public @Nullable Feed getFeed(String name) {
		return feedRepo.findOneByName(name);
	}

	/**
	 * Return a feed's file.
	 * 
	 * @param name name of the feed
	 * @return feed file
	 */
	public Path getFeedFile(String name) {
		return this.contentManager.getFeedFile(name);
	}

	/**
	 * Return one of a feed's attachments.
	 * 
	 * @param feedName name of the feed
	 * @param fileName name of the file
	 * @return file
	 */
	public Path getAttachment(String feedName, String fileName) {
		return this.contentManager.getFile(feedName, fileName);
	}

	/**
	 * Add the given feed to the database and download content.
	 * 
	 * @param name name for the feed
	 * @param url URL of the feed
	 * @return the new feed
	 * @throws IOException when download of the feed fails
	 */
	@Transactional(rollbackFor = IOException.class)
	public Feed add(String name, URL url) throws IOException {
		Path tempFile = Files.createTempFile("feed", ".xml");

		try {
			// We first download the feed file to a temporary location.
			Feed feed = this.contentManager.downloadToFile(name, url, tempFile);

			// Then we add the feed to the database.
			this.feedRepo.save(feed);

			// If that was OK, we add the file to the content manager.
			Path actualFeedFile = this.contentManager.getFeedFile(name);

			// Do not copy if the feed file exists already.
			if(Files.notExists(actualFeedFile)) {
				// Create the directory if needed.
				Files.createDirectories(actualFeedFile.getParent());

				Files.move(tempFile, actualFeedFile);
			} else
				LOG.warn("Feed file '{}' already exists. Will merge on next update.", actualFeedFile);

			return feed;
		} finally {
			// Remove the temporary file.
			Files.deleteIfExists(tempFile);
		}
	}

	/**
	 * Update the list of feeds in the database.
	 * 
	 * @param feeds feeds
	 */
	@Transactional
	public void update(Iterable<Feed> feeds) {
		this.feedRepo.save(feeds);
	}

	/**
	 * Update the given feed in the database.
	 * 
	 * @param feed feed
	 */
	@Transactional
	public void update(Feed feed) {
		this.feedRepo.save(feed);
	}

	/**
	 * Mark the given feed for deletion. Content will be removed on the next {@link FeedUpdater update}.
	 * 
	 * @param name name
	 */
	@Transactional
	public void markForDeletion(String name) {
		LOG.info("Marking feed '{}' for deletion.", name);

		if(feedRepo.markForDeletion(name) == 0)
			LOG.debug("Feed '{}' not found. Not deleting anything.", name);
	}

	/**
	 * Mark the given feed for deletion. Content will be removed on the next {@link FeedUpdater update}.
	 * 
	 * @param feed Feed
	 */
	@Transactional
	public void markForDeletion(Feed feed) {
		// Remove the entry from the database.
		LOG.info("Marking feed '{}' for deletion.", feed);
		feed.setMarkedForDeletion(true);
		this.feedRepo.save(feed);
	}

	/**
	 * Remove the given feed from the database completelly.
	 * 
	 * @param feed feed
	 */
	@Transactional
	public void delete(Feed feed) {
		LOG.info("Removing feed '{}' from database.", feed);
		this.feedRepo.delete(feed);
	}
}
