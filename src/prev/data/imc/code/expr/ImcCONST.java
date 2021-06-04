package prev.data.imc.code.expr;

import prev.common.logger.*;
import prev.data.imc.visitor.*;

/**
 * Constant.
 * 
 * Returns the value of a constant.
 */
public class ImcCONST extends ImcExpr {

	/** The value. */
	public final long value;

	/**
	 * Constructs a new constant.
	 * 
	 * @param value The value.
	 */
	public ImcCONST(long value) {
		this.value = value;
	}

	@Override
	public <Result, Arg> Result accept(ImcVisitor<Result, Arg> visitor, Arg accArg) {
		return visitor.visit(this, accArg);
	}

	@Override
	public void log(Logger logger) {
		logger.begElement("imc");
		logger.addAttribute("instruction", toString());
		logger.endElement();
	}

	@Override
	public String toString() {
		return Long.toString(value);
	}


	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof ImcCONST)) {
			return false;
		}
		ImcCONST imcCONST = (ImcCONST) o;
		return value == imcCONST.value;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(value);
	}

}
