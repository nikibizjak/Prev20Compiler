
package prev.data.mem;

import prev.common.logger.*;
import prev.data.semtype.*;

public abstract class MemoryFrame {

    /** The function's entry label. */
    public final MemLabel label;
    
    /** A list of booleans that represent whether a parameter is escaped (ie. it
     * should be passed in memory instead of in register) or not */
    public final Vector<Boolean> formals;

    public abstract MemoryFrame newFrame(MemLabel label, Vector<Boolean> formals);
    // public abstract Access allocateLocal(boolean escape);

    abstract class Access {}
}

public class InFrameAccess extends MemoryFrame.Access {
    long offset;
    InFrameAccess(long offset) {
        this.offset = offset;
    }
}

public class InRegisterAccess extends MemoryFrame.Access {
    MemTemp temporary;
    InRegisterAccess(MemTemp temporary) {
        this.temporary = temporary;
    }
}
