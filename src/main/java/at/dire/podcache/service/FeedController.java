package at.dire.podcache.service;

import java.io.IOException;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import at.dire.podcache.FeedManager;
import at.dire.podcache.data.Feed;

/**
 * REST CRUD-style controller that gives access to managed {@link Feed feeds} and their content.
 * 
 * @author diredev
 */
@RestController
@RequestMapping("/feed")
public class FeedController {
	/** The database repository */
	private final FeedManager manager;

	/**
	 * Creates a new instance.
	 * 
	 * @param manager the feed manager
	 */
	@Autowired
	public FeedController(FeedManager manager) {
		this.manager = Objects.requireNonNull(manager);
	}

	/**
	 * Returns all currently managed feeds.
	 * 
	 * @return Feeds
	 */
	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public Iterable<Feed> getFeeds() {
		return manager.getFeeds();
	}

	/**
	 * Add a new feed.
	 * 
	 * @param feed feed
	 * @return the added feed
	 * @throws IOException when downloading the feed fails
	 */
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public Feed addFeed(@RequestBody Feed feed) throws IOException {
		return manager.add(Objects.requireNonNull(feed.getName()), Objects.requireNonNull(feed.getUrl()));
	}

	/**
	 * Update a feed in the database.
	 * 
	 * @param name name of the existing feed
	 * @param feed updated feed information
	 */
	@PutMapping(path = "/{name}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public void updateFeed(@PathVariable String name, @RequestBody Feed feed) {
		// Make sure that the feed is not considered new (do not update on PUT)
		feed.setNew(false);
		feed.setName(name);
		
		manager.update(feed);
	}

	/**
	 * Add or update feeds in the database. Can also be used to {@link #deleteFeed(String) undelete} feeds.
	 *
	 * @param feeds feeds
	 */
	@PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public void updateFeeds(@RequestBody Iterable<Feed> feeds) {
		// Make sure that the feeds aren't considered new
		feeds.forEach((feed) -> feed.setNew(false));

		manager.update(Objects.requireNonNull(feeds));
	}

	/**
	 * Mark the given feed as {@link Feed#isMarkedForDeletion() deleted}.
	 * 
	 * @param name feed name
	 */
	@DeleteMapping(path = "/{name}")
	public void deleteFeed(@PathVariable String name) {
		if(StringUtils.isEmpty(name))
			throw new IllegalArgumentException("Name must no be null or empty.");

		manager.markForDeletion(name);
	}
}
