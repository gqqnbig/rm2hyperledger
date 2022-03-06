package rm2hyperledger;

public class StringHelper {
	public static String lowercaseFirstLetter(String name) {
		return Character.toLowerCase(name.charAt(0)) + name.substring(1);
	}

	public static String uppercaseFirstLetter(String name) {
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}
}
