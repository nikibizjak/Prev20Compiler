package prev.data.mem;

/**
 * A label.
 */
public class MemLabel {

	/** The name of a label. */
	public final String name;

	/** Counter of anonymous labels. */
	private static long count = 0;

	/** Creates a new anonymous label. */
	public MemLabel() {
		this.name = "L" + count;
		count++;
	}

	/**
	 * Creates a new named label.
	 * 
	 * @param name The name of a label.
	 */
	public MemLabel(String name) {
		this.name = "_" + name;
	}

	/**
	 * Creates a new unique named label <NAME><UNIQUE_NUMBER>.
	 * 
	 * @param name The name of a label.
	 */
	public static MemLabel uniqueFromName(String name) {
		String uniqueName = String.format("%s%d", name, count);
		count++;
		return new MemLabel(uniqueName);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof MemLabel)) {
			return false;
		}
		MemLabel memLabel = (MemLabel) o;
		return name.equals(memLabel.name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

}
