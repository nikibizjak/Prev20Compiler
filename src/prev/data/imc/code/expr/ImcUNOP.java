package prev.data.imc.code.expr;

import prev.common.logger.*;
import prev.data.imc.visitor.*;

/**
 * Unary operation (logical, arithmetic).
 * 
 * Evaluates the value of the operand, performs the selected unary operation and
 * return its result.
 */
public class ImcUNOP extends ImcExpr {

	public enum Oper {
		NOT, NEG,
	}

	/** The operator. */
	public final Oper oper;

	/** The operand. */
	public final ImcExpr subExpr;

	/**
	 * Constructs a unary operation.
	 * 
	 * @param oper    The operator.
	 * @param subExpr The operand.
	 */
	public ImcUNOP(Oper oper, ImcExpr subExpr) {
		this.oper = oper;
		this.subExpr = subExpr;
	}

	@Override
	public <Result, Arg> Result accept(ImcVisitor<Result, Arg> visitor, Arg accArg) {
		return visitor.visit(this, accArg);
	}
	
	@Override
	public void log(Logger logger) {
		logger.begElement("imc");
		logger.addAttribute("instruction", "UNOP(" + oper + ")");
		subExpr.log(logger);
		logger.endElement();
	}

	@Override
	public String toString() {
		char operator = oper == Oper.NOT ? '!' : '-';
		return String.format("%c%s", operator, subExpr.toString());
	}


	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof ImcUNOP)) {
			return false;
		}
		ImcUNOP imcUNOP = (ImcUNOP) o;
		return oper == imcUNOP.oper && subExpr.equals(imcUNOP.subExpr);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = result * prime * oper.hashCode();
		result = result * prime * subExpr.hashCode();
		return result;
	}


}
