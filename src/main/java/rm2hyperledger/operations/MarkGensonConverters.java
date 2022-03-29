package rm2hyperledger.operations;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;
import rm2hyperledger.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class MarkGensonConverters extends GitCommit {
	private final Set<String> entityNames;

	public MarkGensonConverters(String targetFolder, Set<String> entityNames) {
		super("Add converters for certain Java types because they are otherwise not serializable by genson", targetFolder);
		this.entityNames = entityNames;
	}

	@Override
	protected ArrayList<Path> editCommitCore() throws IOException {
		Path folder = Path.of(targetFolder, "src\\main\\java\\entities");
		assert Files.exists(folder);

		ArrayList<Path> changedFiles = new ArrayList<>();
		for (var entity : entityNames) {
			Path file = Path.of(targetFolder, "src\\main\\java\\entities", entity + ".java");
			try {
				CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(file)));


				JavaParser parser = new JavaParser(tokens);
				TokenStreamRewriter2 rewriter = new TokenStreamRewriter2(tokens);
				var converter = new GetterSetterVisitor(rewriter);

				converter.visit(parser.compilationUnit());
				if (rewriter.hasChanges()) {
					try (PrintWriter out = new PrintWriter(file.toFile())) {
						out.print(rewriter.getText());
					}
					changedFiles.add(file);
				}
			}
			catch (IOException exception) {
				logger.severe(exception.toString());
			}
		}


		return changedFiles;
	}


	static class GetterSetterVisitor extends ImportsCollector<Object> {
		//com.owlike.genson.JsonBindingException: No constructor has been found for type class java.time.LocalDate
		static Set<String> nonSerializableTypes = Set.of("LocalDate");


		public GetterSetterVisitor(TokenStreamRewriter rewriter) {
			super(rewriter);
		}

		@Override
		public Object visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
			if (ctx.memberDeclaration() != null) {
				var methodDeclaration = ctx.memberDeclaration().methodDeclaration();
				if (methodDeclaration != null) {
					String converterClass = addConverterForField(methodDeclaration);
					if (converterClass != null) {
						rewriter.insertBefore(ctx.start, String.format("@JsonConverter(%s.class)\n\t", converterClass));
						newImports.add("converters.*");
						newImports.add("com.owlike.genson.annotation.*");
					}
				}
			}
			return null;
		}

		private static String addConverterForField(JavaParser.MethodDeclarationContext ctx) {
			var methodName = ctx.IDENTIFIER().getText();
			if (methodName.startsWith("get")) {
				if (nonSerializableTypes.contains(ctx.typeTypeOrVoid().getText()))
					return ctx.typeTypeOrVoid().getText() + "Converter";

			} else if (methodName.startsWith("set")) {
				var formalParameters = ctx.formalParameters().formalParameterList().formalParameter();
				if (formalParameters.size() == 1 && nonSerializableTypes.contains(formalParameters.get(0).typeType().getText()))
					return formalParameters.get(0).typeType().getText() + "Converter";
			}

			return null;
		}
	}
}
