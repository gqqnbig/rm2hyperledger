package rm2hyperledger.checkers;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import rm2hyperledger.JavaLexer;
import rm2hyperledger.JavaParser;
import rm2hyperledger.JavaParserBaseVisitor;

import java.io.IOException;
import java.nio.file.Path;

public class EntityChecker extends JavaParserBaseVisitor<Object> {


	public static boolean isEntityClass(Path file) {
		try {
			CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(file)));
			JavaParser parser = new JavaParser(tokens);
			var visitor = new EntityChecker();
			visitor.visitCompilationUnit(parser.compilationUnit());
			return visitor.isEntity;
		}
		catch (IOException exception) {
			return false;
		}
	}

	private EntityChecker() {

	}


	boolean isEntity = false;

	@Override
	public Object visitTypeDeclaration(JavaParser.TypeDeclarationContext ctx) {
		if (ModifierChecker.hasCIModifier(ctx.classOrInterfaceModifier(), JavaParser.PUBLIC) && ctx.classDeclaration() != null) {
			if (checkClassDeclaration(ctx.classDeclaration())) {
				isEntity = true;
				return null;
			}
		}
		return super.visitTypeDeclaration(ctx);
	}

	boolean checkClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
		return ctx.typeList() != null && ctx.typeList().getText().contains("Serializable");
	}
}
