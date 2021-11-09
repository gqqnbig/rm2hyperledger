import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import rm2hyperledger.FieldTypeFinder;
import rm2hyperledger.JavaLexer;
import rm2hyperledger.JavaParser;

public class FieldTypeFinderTest {

	@Test
	void TestFindField() {
		String input = "public class CashDesk {" +
				"private int id;" +
				"}";

		var tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromString(input)));
		var parser = new JavaParser(tokens);
		var p = parser.compilationUnit();

		Assertions.assertEquals("int", FieldTypeFinder.findField(p, "id"));
	}
}
