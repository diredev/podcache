package at.dire.podcache;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;

import at.dire.podcache.data.Feed;
import at.dire.podcache.service.FeedURLBuilder;
import at.dire.podcache.util.DownloadResponse;
import at.dire.podcache.util.FeedUtils;

/**
 * Component used to update a {@link Feed}'s content. Will also download all of the attachments of the feed
 * automatically.
 * 
 * @author diredev
 */
@Component
public class FeedUpdater {
	/** Logger */
	private static final Logger LOG = LoggerFactory.getLogger(FeedUpdater.class);

	/** Feed manager */
	private final FeedManager feedManager;

	/** Component used to build URLs */
	private final FeedURLBuilder urlBuilder;

	/**
	 * Creates a new instance
	 * 
	 * @param feedManager the feed manager
	 * @param urlBuilder URL builder
	 */
	@Autowired
	public FeedUpdater(FeedManager feedManager, FeedURLBuilder urlBuilder) {
		this.feedManager = Objects.requireNonNull(feedManager);
		this.urlBuilder = Objects.requireNonNull(urlBuilder);
	}

	/**
	 * The same as {@link #updateAll(boolean)} but called asynchronously through Spring.
	 * 
	 * @param forceUpdateURLs true to force update of files and URLs
	 * @throws IOException when the update fails
	 */
	@Async
	@Transactional(rollbackFor = IOException.class)
	public void updateAllAsync(boolean forceUpdateURLs) throws IOException {
		try {
			updateAll(forceUpdateURLs);
		} catch(IOException e) {
			LOG.error("Failed to update all feeds", e);
			throw e;
		}
	}

	/**
	 * Update all feeds and download all attachments. Will also delete all feeds that have been
	 * {@link Feed#isMarkedForDeletion() marked} for deletion.
	 * 
	 * @param forceUpdateURLs true to force update of files and URLs
	 * @throws IOException when the update fails
	 */
	@Transactional(rollbackFor = IOException.class)
	public void updateAll(boolean forceUpdateURLs) throws IOException {
		List<Feed> feedsToUpdate = new ArrayList<>();

		LOG.info("Updating all known feeds.");

		// Iterate over all feeds while also locking all feed entries.
		for(Feed feed : this.feedManager.getFeedsAndLock()) {
			// Check if we have to delete this feed
			if(feed.isMarkedForDeletion()) {
				LOG.info("Removing feed entry and content for '{}'.", feed);

				// Feed is marked for deletion. Remove content and delete.
				this.feedManager.getContentManager().delete(feed.getName());

				// Remove from database
				this.feedManager.delete(feed);
			} else {
				// Feed isn't going to be deleted. Update the feed's files.
				if(update(feed, forceUpdateURLs)) {
					LOG.info("Updating feed '{}' from URL '{}'.", feed, feed.getUrl());
					feedsToUpdate.add(feed);
				}
			}
		}

		// Update the database.
		this.feedManager.update(feedsToUpdate);

		LOG.info("All feeds have been updated.");
	}

	/**
	 * Update the {@link ContentManager#ORIGINAL_FEED_FILE feed file} for the given feed.
	 * 
	 * <p>
	 * This method takes last update date and file existence into account. When the method is done, the (merged)
	 * original feed file will exist on the file system.
	 * </p>
	 * 
	 * @param feed the feed
	 * @return the new feed's data
	 * @throws IOException when the download fails
	 */
	private OriginalFeedData updateOriginalFeed(Feed feed) throws IOException {
		SyndFeed feedData;

		// Does a local (original) file exist?
		Path originalFile = this.feedManager.getContentManager().getFile(feed.getName(),
				ContentManager.ORIGINAL_FEED_FILE);
		boolean originalFileExists = Files.exists(originalFile);

		// Download the feed
		try(DownloadResponse response = this.feedManager.getContentManager().download(feed, !originalFileExists)) {
			if(response.isUnchanged()) {
				// Feed is unchanged and original file exists (cannot normally happen otherwise). Load feed and return.
				LOG.debug("Feed not updated and original file exists. Loading original file at '{}'.", originalFile);
				return new OriginalFeedData(FeedUtils.read(originalFile), false);
			}

			// Load to memory using Rome API
			LOG.debug("Loading new original feed from download response.");
			feedData = FeedUtils.read(response.getContent());
		}

		// Create a backup of the original file (which will exist at
		if(originalFileExists) {
			Path backupFile = originalFile.resolveSibling(originalFile.getFileName().toString() + ".save");
			LOG.debug("Creating backup of original stream at '{}'.", backupFile);
			Files.copy(originalFile, backupFile, StandardCopyOption.REPLACE_EXISTING);

			// Original file exists. Load, merge entries and save.
			LOG.debug("Loading ");
			SyndFeed originalData = FeedUtils.read(originalFile);
			FeedUtils.mergeEntries(originalData, feedData);

			// Also set new data variable to the merged feed.
			feedData = originalData;
		}

		// Save the original feed data (may be merged data now)
		FeedUtils.write(feedData, originalFile);

		return new OriginalFeedData(feedData, true);
	}

	/**
	 * Update the given feed's content by downloading new data. Will then also download all content files and Note that
	 * this logic will not update the feed in the database.
	 * 
	 * @param feed feed
	 * @param forceUpdateURLs true to force update of files and URLs
	 * @throws IOException when downloading fail
	 * @return true if the content was updated
	 */
	private boolean update(Feed feed, boolean forceUpdateURLs) throws IOException {
		// Download the feed and load original feed.
		OriginalFeedData originalFeedData = updateOriginalFeed(feed);
		boolean updated = originalFeedData.isUpdated();
		
		// Update the URLs found in the feed (if any new data was downloaded)
		Path feedFile = this.feedManager.getFeedFile(feed.getName());

		if(updated || forceUpdateURLs || !feed.isAllFilesUpdated() || Files.notExists(feedFile)) {
			LOG.debug("Downloading missing content files for feed '{}'.", feed);

			if(updateContentFiles(feed, originalFeedData.getData())) {
				feed.setAllFilesUpdated(true);

				// Save feed file.
				LOG.debug("Saving updated feed data for feed '{}' to '{}'", feed, feedFile);
				FeedUtils.write(originalFeedData.getData(), feedFile);
				return true;
			} else {
				LOG.debug("Feed content  of '{}' hasn't been updated.", feed);
				return false;
			}
		}

		return updated;
	}

	/**
	 * Download and update attachment files for the given feed.
	 * 
	 * @param feed feed
	 * @param feedData RSS feed
	 * @throws IOException when a download fails
	 * @return true if any entry was updated
	 */
	private boolean updateContentFiles(Feed feed, SyndFeed feedData) throws IOException {
		boolean anyUpdated = false;

		for(SyndEntry entry : feedData.getEntries()) {
			String entryLink = entry.getLink();

			// Next we download all the attachments.
			for(SyndEnclosure enclosure : entry.getEnclosures()) {
				Path newContentFile;

				try {
					newContentFile = this.feedManager.getContentManager().download(feed.getName(),
							new URL(enclosure.getUrl()), false);
				} catch(IOException e) {
					LOG.error(
							String.format("Failed to download entry '%s' of feed '%s'. Will continue with next entry.",
									enclosure.getUrl(), feed.getName()),
							e);
					continue;
				}

				String localURL = this.urlBuilder.getURL(feed.getName(), newContentFile.getFileName().toString())
						.toString();

				// If the URL matches the entry's, update that URL as well.
				if(entryLink.equals(enclosure.getUrl()) && !localURL.equals(entryLink)) {
					LOG.debug("Updating entry URL to '{}'.", localURL);
					entry.setLink(localURL);
					anyUpdated = true;
				}

				// Update the attachment's URL
				if(!localURL.equals(enclosure.getUrl())) {
					LOG.debug("Updating enclosure with local URL '{}'.", localURL);
					enclosure.setUrl(localURL);
					anyUpdated = true;
				}
			}
		}

		return anyUpdated;
	}

	/**
	 * Utility structure holding feed data and update information.
	 * 
	 * @author diredev
	 */
	private static class OriginalFeedData {
		/** Content of the feed */
		private final SyndFeed data;

		/** True if new data was downloaded */
		private final boolean updated;

		/**
		 * Returns the feed' data.
		 * 
		 * @return data
		 */
		public SyndFeed getData() {
			return data;
		}

		/**
		 * Returns true if new data was downloaded.
		 * 
		 * @return boolean
		 */
		public boolean isUpdated() {
			return updated;
		}

		/**
		 * Creates a new instance.
		 * 
		 * @param data feed data
		 * @param updated true if new data was downloaded
		 */
		public OriginalFeedData(SyndFeed data, boolean updated) {
			this.data = data;
			this.updated = updated;
		}
	}
}
