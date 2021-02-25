package prev.data.mem;

import prev.common.logger.*;

/**
 * An access to a variable in temporary MemTemp.
 * 
 * (Also used for string constants.)
 */
public class TemporaryAccess extends MemAccess {

    public final MemTemp temporary;

	public TemporaryAccess() {
		super(8);
        this.temporary = new MemTemp();
	}

	@Override
	public void log(Logger logger) {
		if (logger == null)
			return;
		logger.begElement("access");
		logger.addAttribute("size", Long.toString(size));
        logger.addAttribute("temporary", temporary.toString());
		logger.endElement();
	}

}
