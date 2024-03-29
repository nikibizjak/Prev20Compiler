package prev.data.imc.code.expr;

import prev.common.logger.*;
import java.util.*;
import prev.data.mem.*;
import prev.data.imc.visitor.*;

/**
 * Function call.
 * 
 * Evaluates arguments (the static link must be included) from left to right,
 * calls the function denoted by the label provided and returns the function's
 * result.
 */
public class ImcCALL extends ImcExpr {

	/** The label of the function. */
	public final MemLabel label;

	/** The offsets of arguments. */
	private final Vector<Long> offs;

	/** The values of arguments. */
	private final Vector<ImcExpr> args;

	/**
	 * Constructs a function call.
	 * 
	 * @param label The label of the function.
	 * @param offs  The offsets of arguments.
	 * @param args  The values of arguments.
	 */
	public ImcCALL(MemLabel label, Vector<Long> offs, Vector<ImcExpr> args) {
		this.label = label;
		this.offs = new Vector<Long>(offs);
		this.args = new Vector<ImcExpr>(args);
	}

	/**
	 * Returns the offsets of arguments.
	 * 
	 * @return The offsets of arguments.
	 */
	public Vector<Long> offs() {
		return new Vector<Long>(offs);
	}

	/**
	 * Returns the label of the function.
	 * 
	 * @return The label of the function.
	 */
	public Vector<ImcExpr> args() {
		return new Vector<ImcExpr>(args);
	}

	@Override
	public <Result, Arg> Result accept(ImcVisitor<Result, Arg> visitor, Arg accArg) {
		return visitor.visit(this, accArg);
	}

	@Override
	public void log(Logger logger) {
		logger.begElement("imc");
		logger.addAttribute("instruction", "CALL(" + label.name + ")");
		for (int a = 0; a < args.size(); a++)
			args.get(a).log(logger);
		logger.endElement();
	}

	@Override
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append(label.name);
		buffer.append("(");
		for (int a = 0; a < args.size(); a++) {
			if (a > 0)
				buffer.append(", ");
			buffer.append(args.get(a).toString());
		}
		buffer.append(")");
		return buffer.toString();
	}


	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof ImcCALL)) {
			return false;
		}
		ImcCALL imcCALL = (ImcCALL) o;

		if (!label.equals(imcCALL.label))
			return false;
		
		if (args.size() != imcCALL.args.size())
			return false;
		
		for (int i = 0; i < args.size(); i++) {
			boolean equals = args.get(i).equals(imcCALL.args.get(i));
			if (!equals)
				return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = result * prime * label.hashCode();
		for (ImcExpr argument : args) {
			result = result * prime * argument.hashCode();
		}
		return result;
	}

}
