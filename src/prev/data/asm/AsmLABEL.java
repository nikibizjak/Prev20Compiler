package prev.data.asm;

import java.util.*;
import prev.data.mem.*;

/**
 * An assembly label.
 */
public class AsmLABEL extends AsmOPER {

	/** The label. */
	// private final MemLabel label;
	public final MemLabel label;

	public AsmLABEL(MemLabel label) {
		super("", null, null, null);
		this.label = label;
	}

	@Override
	public String toString() {
		return label.name;
	}

	@Override
	public String toString(HashMap<MemTemp, Integer> regs) {
		return label.name;
	}

}
