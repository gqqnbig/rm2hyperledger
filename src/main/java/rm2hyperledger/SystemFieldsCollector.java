package rm2hyperledger;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;

/**
 * Each REModel requires a service whose name is the file name appended with the word "System".
 * <p>
 * All fields in the system service will be shared to all other services.
 */
public class SystemFieldsCollector extends JavaParserBaseVisitor<Object> {

	public static HashSet<String> collect(Path systemFile) throws IOException {
		CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(systemFile)));
		JavaParser parser = new JavaParser(tokens);
		var collector = new SystemFieldsCollector();

		collector.visit(parser.compilationUnit());
		collector.getters.retainAll(collector.setters);

		return collector.getters;
	}


	HashSet<String> getters = new HashSet<>();
	HashSet<String> setters = new HashSet<>();

	private SystemFieldsCollector() {

	}


	@Override
	public Object visitInterfaceMethodDeclaration(JavaParser.InterfaceMethodDeclarationContext ctx) {
		var identifier = ctx.IDENTIFIER().getText().trim();
		if (identifier.startsWith("get") && ctx.typeTypeOrVoid().getText().equals("void") == false && ctx.formalParameters().children.size() == 2) {
			getters.add(lowercaseFirstLetter(identifier.substring(3)));
		} else if (identifier.startsWith("set") && ctx.typeTypeOrVoid().getText().equals("void") && ctx.formalParameters().children.size() == 3) {
			setters.add(lowercaseFirstLetter(identifier.substring(3)));
		}

		return null;
	}

	private static String lowercaseFirstLetter(String str) {
		if (Character.isUpperCase(str.charAt(0)))
			return Character.toLowerCase(str.charAt(0)) + str.substring(1);
		else
			return str;
	}
}
