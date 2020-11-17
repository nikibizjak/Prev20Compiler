package prev.phase.optimisation;

import prev.Compiler;
import prev.phase.*;
import prev.common.report.*;
import prev.phase.optimisation.constant_folding.*;

import prev.phase.abstr.*;

import java.util.*;

/**
 * Machine code generator.
 */
public class Optimisation extends Phase {

	public Optimisation() {
		super("optimisation");
    }
    
    public void optimise() {
        Report.info("OPTIMISATION:");
        this.constantFolding();
    }

    public void constantFolding() {
        String constantFolding = Compiler.cmdLineArgValue("--constant-folding");

        if (constantFolding == null || constantFolding.length() <= 0)
            return;
        
        boolean constantFoldingEnabled = Boolean.parseBoolean(constantFolding);

        if (constantFoldingEnabled) {
            Report.info("\t* constant folding");
            ConstantFolder constantFolder = new ConstantFolder();
            Abstr.tree.accept(constantFolder, null);
        }
    }

	public void log() {
        return;
	}

}
