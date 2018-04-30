package at.dire.podcache.data;

import java.net.URL;
import java.util.Date;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PostPersist;

import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single named feed in the database.
 * 
 * @author diredev
 */
@Entity
public class Feed implements Persistable<String> {
	/** Unique name for this feed. */
	@Id
	private String name;

	/** URL for this feed. */
	@Column(nullable = false)
	private URL url;

	/** The content type of the feed's file. */
	@Column(nullable = false)
	private String contentType;

	/** The last modification date of the resource. */
	@Column
	@Nullable
	private Date lastModified;

	/** True if we have downloaded all known content files for this feed. */
	@Column(nullable = false)
	private boolean allFilesUpdated = false;

	/** Set to true when the given feed needs to be deleted. */
	@Column(nullable = false)
	private boolean markedForDeletion = false;

	/** True if this is a {@link #isNew() new} item. */
	@Transient
	private boolean isNew;

	/**
	 * For serialization.
	 */
	@SuppressWarnings("initialization.fields.uninitialized")
	protected Feed() {}

	/**
	 * Creates a new feed.
	 * 
	 * @param name name
	 * @param url URL
	 * @param contentType content type of this feed
	 * @param lastModified last modification date
	 */
	public Feed(String name, URL url, String contentType, @Nullable Date lastModified) {
		this.name = Objects.requireNonNull(name);
		this.url = Objects.requireNonNull(url);
		this.contentType = Objects.requireNonNull(contentType);
		this.lastModified = Objects.requireNonNull(lastModified);
		this.isNew = true;

		if(name.isEmpty())
			throw new IllegalArgumentException("Feed name must not be empty.");
	}

	/**
	 * Returns the unique name for this feed.
	 * 
	 * @return name
	 */
	@JsonProperty(required = true)
	public String getName() {
		return name;
	}

	/**
	 * Change the feed's internal name.
	 * 
	 * <p>
	 * This is intended for controller use only. Using this may cause database entities to not work properly.
	 * </p>
	 * 
	 * @param name new name
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the URL for this feed.
	 * 
	 * @return URL
	 */
	@JsonProperty(required = true)
	public URL getUrl() {
		return url;
	}

	/**
	 * Set the URL for this feed.
	 * 
	 * <p>
	 * Note that the new file will only be picked up on next update.
	 * </p>
	 * 
	 * @param url URL
	 */
	public void setUrl(URL url) {
		this.url = url;
	}

	/**
	 * Returns the content type of this feed.
	 * 
	 * @return content type
	 */
	@JsonProperty(required = true, defaultValue = MediaType.APPLICATION_XML_VALUE)
	public String getContentType() {
		return contentType;
	}

	/**
	 * Set the content type.
	 * 
	 * @param contentType content type
	 */
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	/**
	 * Returns the last modification date of this feed's file.
	 * 
	 * @return last modification date
	 */
	@JsonProperty
	public @Nullable Date getLastModified() {
		return lastModified;
	}

	/**
	 * Sets the last modification date of this feed's file.
	 * 
	 * @param lastModified last modification date
	 */
	public void setLastModified(@Nullable Date lastModified) {
		this.lastModified = lastModified;
	}

	/**
	 * Returns true if we have downloaded all of the content files of this feed.
	 * 
	 * @return true if updated
	 */
	@JsonProperty
	public boolean isAllFilesUpdated() {
		return allFilesUpdated;
	}

	/**
	 * Set this to true when we have downloaded all of the content files for this feed.
	 * 
	 * @param allFilesUpdated true if updated
	 */
	public void setAllFilesUpdated(boolean allFilesUpdated) {
		this.allFilesUpdated = allFilesUpdated;
	}

	/**
	 * Returns true if this feed needs to be deleted on next purge.
	 * 
	 * @return true if marked for deletion
	 */
	@JsonProperty
	public boolean isMarkedForDeletion() {
		return markedForDeletion;
	}

	/**
	 * Set to true to mark the feed for deletion.
	 * 
	 * @param markedForDeletion true for delete
	 */
	public void setMarkedForDeletion(boolean markedForDeletion) {
		this.markedForDeletion = markedForDeletion;
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if(obj instanceof Feed)
			return equals((Feed)obj);
		else
			return false;
	}

	/**
	 * Compare the two feeds based on name.
	 * 
	 * @param other other feed
	 * @return true if identical
	 */
	public boolean equals(Feed other) {
		return this.name.equals(other.getName());
	}

	@Override
	public int hashCode() {
		return this.name.hashCode();
	}

	@Override
	public String toString() {
		return this.name;
	}

	@Override
	@Transient
	public String getId() {
		return this.name;
	}

	@Override
	@Transient
	public boolean isNew() {
		return isNew;
	}

	/**
	 * Sets the {@link #isNew} attribute.
	 * 
	 * Intended for use by the controller only. May cause transaction issues.
	 * 
	 * @param isNew true if new
	 */
	public void setNew(boolean isNew) {
		this.isNew = isNew;
	}

	@PostPersist
	private void OnPersist() {
		this.isNew = false;
	}
}
