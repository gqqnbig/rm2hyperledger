import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

public class AntlrHelper {

	/**
	 * @param tokens
	 * @param token
	 * @return null, if the given token is the first token.
	 */
	public static Token findPreviousToken(TokenStream tokens, Token token) {
		int index = tokens.index();
		try {
			for (int i = token.getTokenIndex() - 1; i >= 0; i--) {
				tokens.seek(i);

				if (tokens.LT(1) != token)
					return tokens.LT(1);
			}
			return null;
		} finally {
			tokens.seek(index);
		}
	}
}
