package rm2hyperledger;

import java.util.List;

public class FormatHelper {

	public static void increaseIndent(String[] lines, int tabNumber) {
		String indent = "\t".repeat(tabNumber);
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].strip().length() > 0)
				lines[i] = indent + lines[i];
		}
	}

	public static void increaseIndent(List<String> lines, int tabNumber) {
		String indent = "\t".repeat(tabNumber);
		for (int i = 0; i < lines.size(); i++) {
			lines.set(i, indent + lines.get(i));
		}
	}
}
