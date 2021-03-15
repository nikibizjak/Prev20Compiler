package prev.data.imc.code.stmt;

import prev.common.logger.*;
import prev.data.mem.*;
import prev.data.imc.visitor.*;

/**
 * Label.
 * 
 * Does nothing.
 */
public class ImcLABEL extends ImcStmt {

	/** The label. */
	public MemLabel label;

	/**
	 * Constructs a label.
	 * 
	 * @param label The label.
	 */
	public ImcLABEL(MemLabel label) {
		this.label = label;
	}

	@Override
	public <Result, Arg> Result accept(ImcVisitor<Result, Arg> visitor, Arg accArg) {
		return visitor.visit(this, accArg);
	}

	@Override
	public void log(Logger logger) {
		logger.begElement("imc");
		logger.addAttribute("instruction", "LABEL(" + label.name + ")");
		logger.endElement();
	}

	@Override
	public String toString() {
		return label.name;
	}


	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof ImcLABEL)) {
			return false;
		}
		ImcLABEL imcLABEL = (ImcLABEL) o;
		return label.equals(imcLABEL.label);
	}

	@Override
	public int hashCode() {
		return label.hashCode();
	}

}
