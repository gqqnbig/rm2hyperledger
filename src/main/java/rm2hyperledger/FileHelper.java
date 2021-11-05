package rm2hyperledger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileHelper {
	public static String getFileNameWithoutExtension(String fileName) {
		int pos = fileName.lastIndexOf(".");
		if (pos > 0) {
			fileName = fileName.substring(0, pos);
		}
		return fileName;
	}

	static String getFileLineEnding(Path fileName) throws IOException {
		String content = Files.readString(fileName);
		if (content.contains("\r\n"))
			return "\r\n";
		else if (content.contains("\n"))
			return "\n";
		else if (content.contains("\r"))
			return "\r";
		else
			return System.getProperty("line.separator");
	}
}
