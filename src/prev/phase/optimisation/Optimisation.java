package prev.phase.optimisation;

import prev.Compiler;
import prev.common.report.Report;
import prev.data.lin.*;
import prev.data.imc.code.stmt.*;
import prev.phase.Phase;
import prev.phase.abstr.Abstr;
import prev.phase.imclin.*;
import prev.phase.optimisation.common.control_flow_graph.*;
import prev.phase.optimisation.constant_folding.*;
import prev.phase.optimisation.peephole_optimisation.*;
import prev.phase.optimisation.constant_propagation.*;
import prev.phase.optimisation.copy_propagation.*;
import prev.phase.optimisation.dead_code_elimination.*;
import prev.phase.optimisation.common_subexpression_elimination.*;
import prev.phase.optimisation.loop_hoisting.*;
import java.util.*;

/**
 * Appel's Tree intermediate representation optimisation.
 */
public class Optimisation extends Phase {

    public static int DEFAULT_MAX_ITERATIONS = 32;

	public Optimisation() {
		super("optimisation");
    }
    
    public void run() {
        this.run(Optimisation.DEFAULT_MAX_ITERATIONS);
    }

    public void run(int maxIterations) {

        // Some of the optimisation can be executed prior to code linearization
        // (ie. constant folding and symbolic constant folding).
        /*boolean constantFolding = getFlagValue("--constant-folding");
        if (constantFolding)
            this.expressionTreeConstantFolding();
        
        boolean symbolicConstantFolding = getFlagValue("--symbolic-constant-folding");
        if (symbolicConstantFolding)
            this.expressionTreeSymbolicConstantFolding();*/

        // Linearize intermediate code
        this.intermediateCodeLinearization();
        // If target phase is imclin, no optimisations should be performed on
        // linearized code.
        if (Compiler.cmdLineArgValue("--target-phase").equals("imclin"))
            return;
        
        // Execute optimisation on all code chunks
        Vector<LinCodeChunk> optimizedCodeChunks = new Vector<LinCodeChunk>();
        for (LinCodeChunk codeChunk : ImcLin.codeChunks()) {

            // Construct a control-flow graph on which all optimisations will run
            ControlFlowGraph graph = ControlFlowGraphBuilder.build(codeChunk);

            // Repeat optimisations until graph is fully optimized
            Optimisation.runOptimisations(graph, maxIterations);

            // Convert control-flow graph back to list of statements and create
            // a new code chunk with modified statements.
            Vector<ImcStmt> newStatements = ControlFlowGraphBuilder.toStatements(graph);
            LinCodeChunk newCodeChunk = new LinCodeChunk(codeChunk.frame, newStatements, codeChunk.entryLabel, codeChunk.exitLabel);
            optimizedCodeChunks.add(newCodeChunk);
        }
        ImcLin.setCodeChunks(optimizedCodeChunks);
    }

    public static void runOptimisations(ControlFlowGraph graph, int maxIterations) {
        // Check which types of optimisation should be performed
        boolean constantFolding = getFlagValue("--constant-folding");
        boolean symbolicConstantFolding = getFlagValue("--symbolic-constant-folding");
        boolean peepholeOptimisation = getFlagValue("--peephole-optimisation");
        boolean constantPropagation = getFlagValue("--constant-propagation");
        boolean copyPropagation = getFlagValue("--copy-propagation");
        boolean commonSubexpressionElimination = getFlagValue("--common-subexpression-elimination");
        boolean deadCodeElimination = getFlagValue("--dead-code-elimination");

        System.out.printf("Optimising frame %s%n", graph.codeChunk.frame.label.name);

        // Execute optimisations one by one until ALL optimisations stop
        // modifying control-flow graph or number of iterations exceeds maximum
        // number of iterations maxIter. 
        int currentIteration = 0;
        boolean repeatOptimisations = false;
        do {
            repeatOptimisations = false;

            if (peepholeOptimisation) {
                boolean graphChanged = PeepholeOptimisation.run(graph);
                repeatOptimisations = repeatOptimisations || graphChanged;
            }

            if (commonSubexpressionElimination) {
                boolean graphChanged = CommonSubexpressionElimination.run(graph);
                repeatOptimisations = repeatOptimisations || graphChanged;
            }

            if (constantFolding) {
                boolean graphChanged = ConstantFolding.run(graph);
                repeatOptimisations = repeatOptimisations || graphChanged;
            }

            if (symbolicConstantFolding) {
                boolean graphChanged = SymbolicConstantFolding.run(graph);
                repeatOptimisations = repeatOptimisations || graphChanged;
            }

            if (constantPropagation) {
                boolean graphChanged = ConstantPropagation.run(graph);
                repeatOptimisations = repeatOptimisations || graphChanged;
            }

            if (copyPropagation) {
                boolean graphChanged = CopyPropagation.run(graph);
                repeatOptimisations = repeatOptimisations || graphChanged;
            }

            if (deadCodeElimination) {
                boolean graphChanged = DeadCodeElimination.run(graph);
                repeatOptimisations = repeatOptimisations || graphChanged;
            }

            currentIteration += 1;

        } while (repeatOptimisations && currentIteration < maxIterations);

        System.out.printf("  * optimisation completed in %d iterations%n", currentIteration);

    }

    private static boolean getFlagValue(String flag) {
        String flagArgument = Compiler.cmdLineArgValue(flag);
        if (flagArgument == null || flagArgument.length() <= 0)
            return false;
        return Boolean.parseBoolean(flagArgument);
    }

    public void intermediateCodeLinearization() {
        // Linearization of intermediate code.
        try (ImcLin imclin = new ImcLin()) {
            Abstr.tree.accept(new ChunkGenerator(), null);
            imclin.log();
        }
    }

    public void expressionTreeConstantFolding() {
        Report.info("\t* expression tree constant folding");
        ConstantFolding constantFolder = new ConstantFolding();
        Abstr.tree.accept(constantFolder, null);
    }

    public void expressionTreeSymbolicConstantFolding() {
        Report.info("\t* expression tree symbolic constant folding");
        SymbolicConstantFolding constantFolder = new SymbolicConstantFolding();
        Abstr.tree.accept(constantFolder, null);
    }

	public void log() {
        OptimisationLogger optimisationLogger = new OptimisationLogger(logger);
		for (LinDataChunk dataChunk : ImcLin.dataChunks())
            optimisationLogger.log(dataChunk);
		for (LinCodeChunk codeChunk : ImcLin.codeChunks())
            optimisationLogger.log(codeChunk);
	}

}
