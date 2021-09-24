import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStreamRewriter;

import java.util.*;

public abstract class ImportsCollector<T> extends JavaParserBaseVisitor<T> {

	private final ArrayList<java.util.Map.Entry<String, Token>> imported = new ArrayList<>();
	protected final SortedSet<String> newImports = new TreeSet<>();
	protected final TokenStreamRewriter rewriter;

	protected ImportsCollector(TokenStreamRewriter rewriter) {
		this.rewriter = rewriter;
	}

	@Override
	public T visitImportDeclaration(JavaParser.ImportDeclarationContext ctx) {
		imported.add(new AbstractMap.SimpleEntry<>(ctx.qualifiedName().getText() + (ctx.MUL() == null ? "" : ".*"), ctx.stop));
		return null;
	}

	@Override
	public T visitCompilationUnit(JavaParser.CompilationUnitContext ctx) {
		var returnValue = super.visitCompilationUnit(ctx);


		Token importPoint;
		if (imported.size() > 0)
			importPoint = imported.get(imported.size() - 1).getValue();
		else if (ctx.packageDeclaration() != null)
			importPoint = ctx.packageDeclaration().stop;
		else
			importPoint = AntlrHelper.findPreviousToken(rewriter.getTokenStream(), ctx.typeDeclaration(0).start);

		String[] arr = new String[newImports.size()];
		newImports.toArray(arr);
		for (int i = arr.length - 1; i >= 0; i--) {
			String newImport = arr[i];
			if (newImport.endsWith(".*")) {
				if (imported.stream().anyMatch(p -> p.getKey().equals(newImport)) == false)
					rewriter.insertAfter(importPoint, "\nimport " + newImport + ";");
				continue;
			}

			String starImport = newImport.substring(0, newImport.lastIndexOf('.')) + ".*";
			if (imported.stream().anyMatch(p -> p.getKey().equals(starImport)) == false)
				rewriter.insertAfter(importPoint, "\nimport " + newImport + ";");
		}

		return returnValue;
	}

}
