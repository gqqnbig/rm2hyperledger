package rm2hyperledger.checkers;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import rm2hyperledger.JavaParser;

import java.util.List;

public class ModifierChecker {

	/**
	 * @param ctx
	 * @param token JavaParser tokens, e.g., JavaParser.PUBLIC
	 * @return
	 */
	public static <T> boolean hasCIModifier(List<JavaParser.ClassOrInterfaceModifierContext> ctx, int token) {
		for (JavaParser.ClassOrInterfaceModifierContext modifier : ctx) {
			ParseTree t = modifier.children.get(0);
			if (t instanceof TerminalNodeImpl && (((TerminalNodeImpl) t).symbol).getType() == token)
				return true;
		}
		return false;

	}

	/**
	 * @param ctx
	 * @param token JavaParser tokens, e.g., JavaParser.PUBLIC
	 * @return
	 */
	public static boolean hasModifier(List<JavaParser.ModifierContext> ctx, int token) {
		for (var modifier : ctx) {
			ParseTree t = modifier.children.get(0);
			if (t instanceof TerminalNodeImpl && (((TerminalNodeImpl) t).symbol).getType() == token)
				return true;
			t = t.getChild(0);
			if (t instanceof TerminalNodeImpl && (((TerminalNodeImpl) t).symbol).getType() == token)
				return true;
		}
		return false;

	}
}
