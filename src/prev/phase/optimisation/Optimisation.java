package prev.phase.optimisation;

import prev.Compiler;
import prev.common.report.Report;
import prev.phase.Phase;
import prev.phase.abstr.Abstr;
import prev.phase.imclin.ChunkGenerator;
import prev.phase.imclin.ImcLin;
import prev.phase.optimisation.constant_folding.ConstantFolder;

/**
 * Appel's Tree intermediate representation optimisation.
 */
public class Optimisation extends Phase {

	public Optimisation() {
		super("optimisation");
    }
    
    public void optimise() {
        // Some of the optimisation can be executed prior to code linearization
        // (ie. constant folding).
        this.constantFolding();

        // Linearize intermediate code
        this.linearizeIntermediateCode();
        // If target phase is imclin, no optimisations should be performed on
        // linearized code.
        if (Compiler.cmdLineArgValue("--target-phase").equals("imclin"))
            return;

        // Perform optimisations on linearized intermediate code
    }

    public void linearizeIntermediateCode() {
        // Linearization of intermediate code.
        try (ImcLin imclin = new ImcLin()) {
            Abstr.tree.accept(new ChunkGenerator(), null);
            imclin.log();
        }
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
