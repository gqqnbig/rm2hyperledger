package rm2hyperledger.operations;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStreamRewriter;
import rm2hyperledger.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class EntityManagerGetObjectByPK extends GitCommit {
	private final Set<String> entities;

	public EntityManagerGetObjectByPK(String targetFolder, Set<String> entities) {
		super("For all entity types, add getXxxByPK to EntityManager", targetFolder);
		this.entities = entities;
	}

	@Override
	protected ArrayList<Path> editCommitCore() throws IOException {
		Path entityManagerFileName = Paths.get(targetFolder, "src\\main\\java\\entities\\EntityManager.java");
		CommonTokenStream tokens = new CommonTokenStream(new JavaLexer(CharStreams.fromPath(entityManagerFileName)));

		JavaParser parser = new JavaParser(tokens);
		TokenStreamRewriter rewriter = new TokenStreamRewriter(tokens);

		GetObjectByPKAdder converter = new GetObjectByPKAdder(rewriter, entities);
		converter.visit(parser.compilationUnit());
		try (PrintWriter out = new PrintWriter(entityManagerFileName.toFile())) {
			out.print(rewriter.getText());
		}

		ArrayList<Path> paths = new ArrayList<>();
		paths.add(entityManagerFileName);
		return paths;
	}

	static class GetObjectByPKAdder extends ImportsCollector<Object> {

		private final TreeSet<String> entities;

		protected GetObjectByPKAdder(TokenStreamRewriter rewriter, Set<String> entities) {
			super(rewriter);
			this.entities = new TreeSet<>(entities);
		}

		private static String getTemplate() {
			var s = new String[]{"public static %1$s get%1$sByPK(Object pk) {",
					"\tif (pk == null)",
					"\t\treturn null;",
					"\tfor (var i : EntityManager.getAllInstancesOf(%1$s.class)) {",
					"\t\tif (Objects.equals(i.getPK(), pk))",
					"\t\t\treturn i;",
					"\t}",
					"\treturn null;",
					"}"};
			FormatHelper.increaseIndent(s, 1);

			return String.join("\n", s);
		}

		@Override
		public Object visitClassBody(JavaParser.ClassBodyContext ctx) {
			var str = entities.stream().map(e -> String.format(getTemplate(), e)).collect(Collectors.joining("\n\n"));
			rewriter.insertBefore(ctx.stop, "\n" + str + "\n");

			newImports.add("java.util.*");
			return null;
		}
	}
}
