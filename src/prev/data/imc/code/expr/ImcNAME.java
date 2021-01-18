package prev.data.imc.code.expr;

import prev.common.logger.*;
import prev.data.mem.*;
import prev.data.imc.visitor.*;

/**
 * Name.
 * 
 * Returns the address that the label is mapped to.
 */
public class ImcNAME extends ImcExpr {

	/** The label. */
	public final MemLabel label;

	/**
	 * Constructs a new name.
	 * 
	 * @param label The label.
	 */
	public ImcNAME(MemLabel label) {
		this.label = label;
	}

	@Override
	public <Result, Arg> Result accept(ImcVisitor<Result, Arg> visitor, Arg accArg) {
		return visitor.visit(this, accArg);
	}

	@Override
	public void log(Logger logger) {
		logger.begElement("imc");
		logger.addAttribute("instruction", "NAME(" + label.name + ")");
		logger.endElement();
	}

	@Override
	public String toString() {
		return "NAME(" + label.name + ")";
	}


	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof ImcNAME)) {
			return false;
		}
		ImcNAME imcNAME = (ImcNAME) o;
		return label.equals(imcNAME.label);
	}

	@Override
	public int hashCode() {
		return label.hashCode();
	}


}
