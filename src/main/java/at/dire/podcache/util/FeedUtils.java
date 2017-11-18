package at.dire.podcache.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.SyndFeedOutput;
import com.rometools.rome.io.XmlReader;

/**
 * Convenience methods for handling feeds using the Rome API.
 * 
 * @author diredev
 */
public final class FeedUtils {
	private FeedUtils() {}

	/**
	 * Parse the given local file as a feed.
	 * 
	 * @param file file to read
	 * @return feed
	 * @throws IOException when reading or parsing fails
	 */
	public static SyndFeed read(Path file) throws IOException {
		try {
			return new SyndFeedInput().build(file.toFile());
		} catch(FeedException e) {
			throw new IOException("Failed to read feed.", e);
		}
	}

	/**
	 * Read the given stream a feed.
	 * 
	 * @param stream stream
	 * @return feed
	 * @throws IOException when reading or parsing fails
	 */
	public static SyndFeed read(InputStream stream) throws IOException {
		try {
			return new SyndFeedInput().build(new XmlReader(stream));
		} catch(FeedException e) {
			throw new IOException("Failed to read feed.", e);
		}
	}

	/**
	 * Write the given feed to the target file.
	 * 
	 * @param feed feed
	 * @param toFile target file
	 * @throws IOException when writing fails
	 */
	public static void write(SyndFeed feed, Path toFile) throws IOException {
		try {
			new SyndFeedOutput().output(feed, toFile.toFile(), false);
		} catch(FeedException e) {
			throw new IOException("Failed to write feed.", e);
		}
	}

	/**
	 * Utility method that will return the {@link SyndEntry#getUri() URIs} or all {@link SyndFeed#getEntries()} as key.
	 * 
	 * @param feed feed
	 * @return entries as map
	 */
	private static Set<String> getExistingEntryURIs(SyndFeed feed) {
		return feed.getEntries().stream().map((entry) -> entry.getUri()).collect(Collectors.toSet());
	}

	/**
	 * Merge the entries of the second feed into the first one if they do not already exist.
	 * 
	 * @param feedData the feed to merge into
	 * @param feedToMerge the source for new entries
	 * @return true if anything was added
	 */
	public static boolean mergeEntries(SyndFeed feedData, SyndFeed feedToMerge) {
		Set<String> existingEntryIds = getExistingEntryURIs(feedData);
		List<SyndEntry> newEntries = feedToMerge.getEntries().stream()
				.filter((newEntry) -> !existingEntryIds.contains(newEntry.getUri())).collect(Collectors.toList());
		return feedData.getEntries().addAll(0, newEntries);
	}
}
