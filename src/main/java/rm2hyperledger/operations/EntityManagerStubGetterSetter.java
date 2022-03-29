package rm2hyperledger.operations;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;
import rm2hyperledger.*;
import rm2hyperledger.checkers.ModifierChecker;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EntityManagerStubGetterSetter extends GitCommit {
	public EntityManagerStubGetterSetter(String targetFolder) {
		super("Add getter and setter of stub to EntityManager", targetFolder);
	}

	@Override
	protected ArrayList<Path> editCommitCore() throws IOException {
		Path entityManagerFileName = Paths.get(targetFolder, "src\\main\\java\\entities\\EntityManager.java");
		CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(entityManagerFileName)));

		JavaParser parser = new JavaParser(tokens);
		TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);

		var setterCollector = new SetterCollector();
		setterCollector.visit(parser.compilationUnit());

		var part1 = setterCollector.lists.entrySet().stream().map(s -> String.format("\t%s = new LinkedList<>();", s.getKey()));

		var part2 = setterCollector.lists.entrySet().stream().map(s -> String.format("\tAllInstance.put(\"%s\", %s);", s.getValue(), s.getKey()));

		var member = new ArrayList<>(Arrays.asList("",
				"public static ChaincodeStub getStub() {",
				"\treturn stub;",
				"}",
				"",
				"public static void setStub(ChaincodeStub stub) {",
				"\tEntityManager.stub = stub;",
				"\trandom = null;",
				""));
		member.addAll(part1.collect(Collectors.toList()));
		member.add("");
		member.addAll(part2.collect(Collectors.toList()));
		member.add("}");

		parser.reset();
		new AddClassMemberVisitor(rewriter, AddClassMemberVisitor.EditLocation.End, member.toArray(String[]::new)).visit(parser.compilationUnit());
		try (PrintWriter out = new PrintWriter(entityManagerFileName.toFile())) {
			out.print(rewriter.getText());
		}

		ArrayList<Path> paths = new ArrayList<>();
		paths.add(entityManagerFileName);
		return paths;
	}

	static class SetterCollector extends JavaParserBaseVisitor<Object> {
		Map<String, String> lists = new TreeMap<>();


		@Override
		public Object visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
			if (ModifierChecker.hasModifier(ctx.modifier(), JavaParser.PRIVATE) && ctx.memberDeclaration() != null && ctx.memberDeclaration().fieldDeclaration() != null) {
				checkFieldDeclaration(ctx.memberDeclaration().fieldDeclaration());
				return null;
			}

			return null;
		}

		private Pattern pattern = Pattern.compile("List<([\\w\\d_]+)>");

		void checkFieldDeclaration(JavaParser.FieldDeclarationContext ctx) {
			String listType = ctx.typeType().getText();
			var m = pattern.matcher(listType);
			if (m.matches()) {
				var variableDeclarator = ctx.variableDeclarators().variableDeclarator(0);
				lists.put(variableDeclarator.variableDeclaratorId().IDENTIFIER().getText(), m.group(1));
			}
		}


	}
}
