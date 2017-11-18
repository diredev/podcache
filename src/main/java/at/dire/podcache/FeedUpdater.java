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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;

import at.dire.podcache.data.Feed;
import at.dire.podcache.service.FeedURLBuilder;
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
	 * Update the given feed's content by downloading new data. Will then also download all content files and Note that
	 * this logic will not update the feed in the database.
	 * 
	 * @param feed feed
	 * @param forceUpdateURLs true to force update of files and URLs
	 * @throws IOException when downloading fail
	 * @return true if the content was updated
	 */
	private boolean update(Feed feed, boolean forceUpdateURLs) throws IOException {
		// Does the feed file exist?
		Path feedFile = this.feedManager.getContentManager().getFeedFile(feed.getName());
		SyndFeed feedData = null;
		boolean newDataAvailable = false;
		boolean updated = false;
		boolean createBackup = true;

		if(Files.notExists(feedFile)) {
			LOG.warn("Failed to find feed file '{}' for feed '{}'. Downloading now.", feedFile, feed);
			this.feedManager.getContentManager().downloadToFile(feed, feedFile);
			newDataAvailable = true;
			createBackup = false;
		} else {
			// Download the newest feed (memory only)
			SyndFeed newFeedData = this.feedManager.getContentManager().downloadFeed(feed);

			if(newFeedData != null) {
				LOG.debug("Got new feed data for feed '{}'. Merging with existing data.", feed);
				newDataAvailable = true;
				updated = true;

				// Got new feed data. Merge with the existing feed.
				feedData = FeedUtils.read(feedFile);
				FeedUtils.mergeEntries(feedData, newFeedData);
			}
		}

		// Download missing files.
		if(newDataAvailable || forceUpdateURLs || !feed.isAllFilesUpdated()) {
			// Load the feed file if not done above already.
			if(feedData == null)
				feedData = FeedUtils.read(feedFile);

			LOG.debug("Downloading missing content files for feed '{}'.", feed);
			
			if(updateContentFiles(feed, feedData)) {
				feed.setAllFilesUpdated(true);

				// Backup the original file.
				if(createBackup) {
					LOG.debug("Backing up original feed file '{}' prior to download.", feedFile);
					Files.copy(feedFile, feedFile.resolveSibling(feedFile.getFileName().toString() + ".backup"),
							StandardCopyOption.REPLACE_EXISTING);
				}

				// Save feed file.
				LOG.debug("Saving updated feed data for feed '{}' to '{}'", feed, feedFile);
				FeedUtils.write(feedData, feedFile);
				updated = true;
			} else
				LOG.debug("Feed content  of '{}' hasn't been updated.", feed);
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
}
