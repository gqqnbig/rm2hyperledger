public class FileHelper {
	public static String getFileNameWithoutExtension(String fileName) {
		int pos = fileName.lastIndexOf(".");
		if (pos > 0) {
			fileName = fileName.substring(0, pos);
		}
		return fileName;
	}
}
