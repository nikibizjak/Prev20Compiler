package prev.data.imc.code.expr;

import prev.common.logger.*;
import prev.data.mem.*;
import prev.data.imc.visitor.*;
import java.util.Objects;

/**
 * Temporary variable.
 * 
 * Returns the value of a temporary variable.
 */
public class ImcTEMP extends ImcExpr {

	/** The temporary variable. */
	public final MemTemp temp;

	/**
	 * Constructs a temporary variable.
	 * 
	 * @param temp The temporary variable.
	 */
	public ImcTEMP(MemTemp temp) {
		this.temp = temp;
	}

	@Override
	public <Result, Arg> Result accept(ImcVisitor<Result, Arg> visitor, Arg accArg) {
		return visitor.visit(this, accArg);
	}

	@Override
	public void log(Logger logger) {
		logger.begElement("imc");
		logger.addAttribute("instruction", "TEMP(" + temp.temp + ")");
		logger.endElement();
	}

	@Override
	public String toString() {
		return "TEMP(" + temp.temp + ")";
	}


	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof ImcTEMP)) {
			return false;
		}
		ImcTEMP imcTEMP = (ImcTEMP) o;
		return Objects.equals(temp, imcTEMP.temp);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(temp);
	}


}
