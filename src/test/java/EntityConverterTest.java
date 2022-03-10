import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;
import org.junit.jupiter.api.Test;
import rm2hyperledger.JavaLexer;
import rm2hyperledger.JavaParser;
import rm2hyperledger.operations.ConvertEntities;

import java.io.IOException;

public class EntityConverterTest {

	@Test
	void test() throws IOException {
		var tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromFileName("D:\\rm2pt\\cocome-hyperledger\\src\\main\\java\\entities\\Store.java")));
		var parser = new JavaParser(tokens);
		TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);

		ConvertEntities.EntityConverter converter = new ConvertEntities.EntityConverter(tokens, rewriter);

		converter.visit(parser.compilationUnit());

		System.out.println(rewriter.getText());

	}
}
