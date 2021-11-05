package rm2hyperledger.checkers;

import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import rm2hyperledger.JavaParser;

import java.util.List;

public class ModifierChecker {

	/**
	 * @param ctx
	 * @param token JavaParser tokens, e.g., JavaParser.PUBLIC
	 * @return
	 */
	public static boolean hasModifier(List<JavaParser.ClassOrInterfaceModifierContext> ctx, int token) {
		for (JavaParser.ClassOrInterfaceModifierContext modifier : ctx) {
			if ((((TerminalNodeImpl) modifier.children.get(0)).symbol).getType() == token)
				return true;
		}
		return false;

	}
}
