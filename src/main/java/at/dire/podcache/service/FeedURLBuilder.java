package at.dire.podcache.service;

import java.net.MalformedURLException;
import java.net.URL;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A small utility component used to build URLs to my local files.
 * 
 * @author diredev
 * @see ContentController
 */
@Component
public class FeedURLBuilder {
	/** Address for service via properties. */
	private final URL baseURL;

	/**
	 * Creates a new instance.
	 * 
	 * @param baseUrl base URL for file access
	 */
	@Autowired
	public FeedURLBuilder(@Value("${podcache.content.url}") String baseUrl) {
		try {
			this.baseURL = new URL(baseUrl);
		} catch(MalformedURLException e) {
			throw new IllegalArgumentException(String.format(
					"Invalid base URL %s.", baseUrl), e);
		}
	}

	/**
	 * Returns the url used to access the given file.
	 * 
	 * @param feedName name of the feed
	 * @param fileName name of the file
	 * @return URL
	 */
	public URL getURL(String feedName, String fileName) {
		try {
			return new URL(baseURL, feedName + "/" + fileName);
		} catch(MalformedURLException e) {
			throw new IllegalArgumentException("Invalid url", e);
		}
	}

	@Override
	public String toString() {
		return this.baseURL.toString();
	}
}
