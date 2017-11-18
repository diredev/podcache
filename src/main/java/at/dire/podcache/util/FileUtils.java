package at.dire.podcache.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Utility methods for handling files, downloading, etc.
 * 
 * @author diredev
 */
public final class FileUtils {
	private FileUtils() {}

	/**
	 * Delete the given directory and all content.
	 * 
	 * @param directory directory
	 * @throws IOException when deleting fails
	 */
	public static void deleteDirectoryAndContent(Path directory) throws IOException {
		Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/**
	 * Delete all files in the directory matching the given glob.
	 * 
	 * @param directory the directory
	 * @param glob glob
	 * @throws IOException when deleting fails
	 */
	public static void deleteFiles(Path directory, String glob) throws IOException {
		Files.newDirectoryStream(directory, glob).forEach((path) -> {
			try {
				Files.delete(path);
			} catch(IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}
}
