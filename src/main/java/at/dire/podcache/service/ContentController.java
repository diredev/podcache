package at.dire.podcache.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import at.dire.podcache.FeedManager;
import at.dire.podcache.data.Feed;

/**
 * REST controller that gives access to a {@link Feed}'s content and attachment files.
 * 
 * @author diredev
 */
@RestController
@RequestMapping("/content")
public class ContentController {
	/** Logger */
	private static final Logger LOG = LoggerFactory.getLogger(ContentController.class);

	/** The database repository */
	private final FeedManager manager;

	/**
	 * Creates a new instance.
	 * 
	 * @param manager the feed manager
	 */
	@Autowired
	public ContentController(FeedManager manager) {
		this.manager = Objects.requireNonNull(manager);
	}

	/**
	 * Returns the feed's content. Will check the last modification date and
	 *
	 * @param request request
	 * @param feedName name of the feed
	 * @return feed file
	 */
	@GetMapping(path = "/{name}")
	public ResponseEntity<Resource> getContent(WebRequest request, @PathVariable("name") String feedName) {
		Feed feed = this.manager.getFeed(feedName);

		if(feed == null) {
			LOG.debug("Feed '{}' not found.", feedName);
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		// Check last modified, may return NOT_MODIFIED.
		Date lastModified = feed.getLastModified();

		if(lastModified != null && request.checkNotModified(lastModified.getTime())) {
			LOG.debug("Feed '{}' hasn't been changed ({}). Returning.", feedName, lastModified);
			return new ResponseEntity<>(HttpStatus.NOT_MODIFIED);
		}

		// Check if the file exists.
		Path file = this.manager.getFeedFile(feedName);

		if(Files.notExists(file)) {
			LOG.debug("Requested feed content file '{}' not found.", file);
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		PathResource resource = new PathResource(file);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(feed.getContentType())).body(resource);
	}

	/**
	 * Returns one of a feed's attachments.
	 * 
	 * @param feedName name of the feed
	 * @param fileName file name to get
	 * @return attachment file
	 */
	@GetMapping(path = "/{name}/{fileName:.+}")
	public ResponseEntity<Resource> getAttachment(@PathVariable("name") String feedName,
			@PathVariable("fileName") String fileName) {
		Path file = this.manager.getAttachment(feedName, fileName);

		if(Files.notExists(file)) {
			LOG.debug("Requested feed attachment file '{}' not found.", file);
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		PathResource resource = new PathResource(file);
		return ResponseEntity.ok(resource);
	}
}
