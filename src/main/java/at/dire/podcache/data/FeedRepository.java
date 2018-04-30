package at.dire.podcache.data;

import javax.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.Nullable;

/**
 * Database repository to access {@link Feed feeds}.
 * 
 * @author diredev
 */
public interface FeedRepository extends CrudRepository<Feed, String> {
	/**
	 * Find a given feed by name.
	 * 
	 * @param name name of the feed
	 * @return feed or null
	 */
	@Nullable
	Feed findOneByName(String name);

	/**
	 * The same {@link #findAll()}, but will lock all current feeds.
	 * 
	 * @return feeds
	 */
	@Query("select f from Feed f")
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Iterable<Feed> findAllPessimistic();

	/**
	 * Marks the given feed for {@link Feed#isMarkedForDeletion() deletion}.
	 * 
	 * @param name name of the feed
	 * @return number of updated rows (max 1)
	 */
	@Modifying
	@Query("update Feed f set f.markedForDeletion=true where f.name=?1")
	int markForDeletion(String name);
}
