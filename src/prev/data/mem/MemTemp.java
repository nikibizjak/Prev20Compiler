package prev.data.mem;

/**
 * A temporary variable.
 */
public class MemTemp {

	/** The name of a temporary variable. */
	public final long temp;

	/** Counter of temporary variables. */
	private static long count = 0;

	/** Creates a new temporary variable. */
	public MemTemp() {
		this.temp = count;
		count++;
	}

	@Override
	public String toString() {
		return "T" + temp;
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof MemTemp)) {
			return false;
		}
		MemTemp memTemp = (MemTemp) o;
		return temp == memTemp.temp;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(temp);
	}

}
