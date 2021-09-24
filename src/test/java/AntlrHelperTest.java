import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class AntlrHelperTest {

	@Test
	void testFindPreviousToken() {
		String input = "public abstract class ImportsCollector<T> { }";

		var tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromString(input)));
		var parser = new JavaParser(tokens);
		var p = parser.compilationUnit();

		var t = AntlrHelper.findPreviousToken(tokens, p.typeDeclaration(0).start);

		Assertions.assertNull(t);
	}

	@Test
	void testFindPreviousTokenWithPackage() {
		String input = "package hello;\npublic abstract class ImportsCollector<T> { }";

		var tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromString(input)));
		var parser = new JavaParser(tokens);
		var p = parser.compilationUnit();

		var t = AntlrHelper.findPreviousToken(tokens, p.typeDeclaration(0).start);
		Assertions.assertNotNull(t);
		Assertions.assertEquals(";", t.getText());
	}
}
