package rm2hyperledger;

import org.antlr.v4.runtime.TokenStreamRewriter;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.Arrays;

public class AddClassMemberVisitor extends ImportsCollector<Object> {
	private final EditLocation editLocation;
	private final String[] member;

	public AddClassMemberVisitor(TokenStreamRewriter rewriter, EditLocation editLocation, String[] member, String... imports) {
		super(rewriter);
		this.editLocation = editLocation;
		this.member = member;

		newImports.addAll(Arrays.asList(imports));
	}

	@Override
	public Object visitClassBody(JavaParser.ClassBodyContext ctx) {
		var indent = ctx.stop.getCharPositionInLine();
		FormatHelper.increaseIndent(member, indent + 1);

		if (editLocation == EditLocation.Start) {
			rewriter.insertAfter(((TerminalNode) ctx.children.get(0)).getSymbol(), "\n" + String.join("\n", Arrays.asList(member)));
		} else if (editLocation == EditLocation.End) {
			rewriter.insertBefore(((TerminalNode) ctx.children.get(ctx.children.size() - 1)).getSymbol(), String.join("\n", Arrays.asList(member)) + "\n");
		}

		return null;
	}

	public enum EditLocation {
		Start,
		End,
	}
}

