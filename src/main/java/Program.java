import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;

import java.io.IOException;

public class Program {
	public static void main(String[] args) throws IOException {

		CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromFileName("B:\\EntityManager.java")));

		JavaParser parser = new JavaParser(tokens);
		TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);

		AddObjectConverter converter = new AddObjectConverter(rewriter);
		converter.visit(parser.compilationUnit());

		System.out.print(rewriter.getText());
	}
}