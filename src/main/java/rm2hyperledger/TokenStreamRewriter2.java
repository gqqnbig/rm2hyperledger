package rm2hyperledger;

import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;

import java.util.List;

public class TokenStreamRewriter2 extends TokenStreamRewriter {
	public TokenStreamRewriter2(TokenStream tokens) {
		super(tokens);
	}

	public boolean hasChanges() {
		return super.programs.values().stream().flatMap(List::stream).findAny().isPresent();
	}
}
