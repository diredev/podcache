package at.dire.podcache.util;

import java.util.Date;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A downloaded resource.
 * 
 * @author diredev
 */
public interface ResourceInfo {
	/**
	 * The file's content type.
	 * 
	 * @return content type
	 */
	public @Nullable String getContentType();

	/**
	 * The last modification date.
	 * 
	 * @return last modification date
	 */
	public @Nullable Date getLastModified();
}
