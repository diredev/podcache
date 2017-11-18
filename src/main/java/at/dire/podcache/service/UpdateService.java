package at.dire.podcache.service;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import at.dire.podcache.Arguments;
import at.dire.podcache.FeedUpdater;
import at.dire.podcache.data.Feed;

/**
 * A scheduled service used to update feeds regularly and possibly on startup. This will also download and replace the
 * feed's files with local versions.
 * 
 * <p>
 * The logic for this is mostly implemented in {@link FeedUpdater}. This component is needed to allow transaction
 * handling.
 * </p>
 * 
 * @author diredev
 */
@Component
public class UpdateService implements ApplicationRunner {
	/** Logger */
	private static final Logger LOG = LoggerFactory.getLogger(UpdateService.class);

	/** Default mode when using {@link Arguments#UPDATE}. */
	private static final String UPDATE_MODE_DEFAULT = "default";

	/** Force update of file URLs when using {@link Arguments#UPDATE}. */
	private static final String UPDATE_MODE_FORCE = "force";

	/** The actual updater. */
	private final FeedUpdater updater;

	/**
	 * Creates a new instance.
	 * 
	 * @param updater updater component
	 */
	@Autowired
	public UpdateService(FeedUpdater updater) {
		this.updater = Objects.requireNonNull(updater);
	}

	/**
	 * Run daily as a scheduled task to update feeds. Will also remove any {@link Feed#isMarkedForDeletion() marked}
	 * feed.
	 * 
	 * @throws IOException when the update fails
	 */
	@Scheduled(cron = "${podcache.update.interval}")
	public synchronized void update() throws IOException {
		try {
			this.updater.updateAll(false);
		} catch(IOException e) {
			LOG.error("Failed to update feeds.", e);
			throw e;
		}
	}

	@Override
	public synchronized void run(ApplicationArguments args) throws Exception {
		List<String> updateArgs = args.getOptionValues(Arguments.UPDATE);

		if(updateArgs == null)
			return;

		String updateMode;
		if(updateArgs.isEmpty())
			updateMode = UPDATE_MODE_DEFAULT;
		else
			updateMode = updateArgs.get(0);

		// Interpret mode
		boolean forceUpdate = UPDATE_MODE_FORCE.equalsIgnoreCase(updateMode);

		try {
			this.updater.updateAll(forceUpdate);
		} catch(IOException e) {
			LOG.error("Failed to update feeds on startup.", e);
		}
	}
}
