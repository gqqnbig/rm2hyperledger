package rm2hyperledger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;


public class PrimaryKeyCollector {
	private static final Logger logger = Logger.getLogger(PrimaryKeyCollector.class.getName());

	private final Path reModelFilePath;

	public PrimaryKeyCollector(String reModelFilePath) {
		this(Path.of(reModelFilePath));
	}

	public PrimaryKeyCollector(Path reModelFilePath) {
		this.reModelFilePath = reModelFilePath;
	}

	/**
	 *
	 * @return class name and its primary key. But type is unknown.
	 * @throws IOException
	 */
	public HashMap<String, String> collect() throws IOException {

		HashMap<String, String> map = new HashMap<>();
		String content = Files.readString(reModelFilePath);

		Pattern uniquePattern = Pattern.compile("\\.allInstance\\(\\)->isUnique\\(([_\\w\\d]+):([_\\w\\d]+)\\s*\\|\\s*\\1.([_\\w\\d]+)\\)");
		var matcher = uniquePattern.matcher(content);
		while (matcher.find()) {
			String type = matcher.group(2);
			String identifier = matcher.group(3);

			var i = map.getOrDefault(type, null);
			if (i == null)
				map.put(type, identifier);
			else if (identifier.equals(i) == false)
				logger.warning(String.format("%1$s has primary key %2$s. Another primary key %3$s will be ignored.", type, i, identifier));
		}

		Pattern anyPattern = Pattern.compile("\\.allInstance\\(\\)->any\\(([_\\w\\d]+):([_\\w\\d]+)\\s*\\|\\s*\\1.([_\\w\\d]+)\\s*=");
		matcher = anyPattern.matcher(content);
		while (matcher.find()) {
			String type = matcher.group(2);
			String identifier = matcher.group(3);

			var i = map.getOrDefault(type, null);
			if (i == null)
				map.put(type, identifier);
			else if (identifier.equals(i) == false)
				logger.warning(String.format("%1$s has primary key %2$s. Another primary key %3$s will be ignored.", type, i, identifier));
		}

		return map;

	}
}
