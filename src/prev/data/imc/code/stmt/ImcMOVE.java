package prev.data.imc.code.stmt;

import prev.common.logger.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.visitor.*;

/**
 * Move operation.
 * 
 * Evaluates the destination, evaluates the source, and moves the source to the
 * destination. If the root node of the destination is {@link ImcMEM}, then the
 * source is stored to the memory address denoted by the subtree of that
 * {@link ImcMEM} node. If the root node of the destination is {@link ImcTEMP},
 * the source is stored in the temporary variable.
 */
public class ImcMOVE extends ImcStmt {

	/** The destination. */
	public final ImcExpr dst;

	/** The source. */
	public final ImcExpr src;

	/** Constructs a move operation. */
	public ImcMOVE(ImcExpr dst, ImcExpr src) {
		this.dst = dst;
		this.src = src;
	}

	@Override
	public <Result, Arg> Result accept(ImcVisitor<Result, Arg> visitor, Arg accArg) {
		return visitor.visit(this, accArg);
	}

	@Override
	public void log(Logger logger) {
		logger.begElement("imc");
		logger.addAttribute("instruction", "MOVE");
		dst.log(logger);
		src.log(logger);
		logger.endElement();
	}

	@Override
	public String toString() {
		return dst.toString() + " ← " + src.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof ImcMOVE)) {
			return false;
		}
		ImcMOVE imcMOVE = (ImcMOVE) o;
		return dst.equals(imcMOVE.dst) && src.equals(imcMOVE.src);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = result * prime * dst.hashCode();
		result = result * prime * src.hashCode();
		return result;
	}


}
