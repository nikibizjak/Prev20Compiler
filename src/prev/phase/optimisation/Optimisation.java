package prev.phase.optimisation;

import prev.Compiler;
import prev.common.report.Report;
import prev.data.lin.*;
import prev.data.imc.code.stmt.*;
import prev.phase.Phase;
import prev.phase.abstr.Abstr;
import prev.phase.imclin.*;
import prev.data.mem.*;
import prev.phase.optimisation.common.control_flow_graph.*;
import prev.phase.optimisation.constant_folding.*;
import prev.phase.optimisation.peephole_optimisation.*;
import prev.phase.optimisation.constant_propagation.*;
import prev.phase.optimisation.copy_propagation.*;
import prev.phase.optimisation.dead_code_elimination.*;
import prev.phase.optimisation.common_subexpression_elimination.*;
import prev.phase.optimisation.loop_hoisting.*;
import prev.phase.optimisation.induction_variable_elimination.*;
import java.util.*;
import prev.common.logger.*;

/**
 * Appel's Tree intermediate representation optimisation.
 */
public class Optimisation extends Phase {

    /** The maximum number of iterations that the optimisation phase can iterate
     * for. */
    public static int DEFAULT_MAX_ITERATIONS = 32;

    /** A list of steps of performed optimisations. */
    private static Vector<LinCodeChunk> optimisations = new Vector<LinCodeChunk>();

    /** Optimisations before and after for each frame. */
    private static Vector<LinCodeChunk> beforeAfterOptimisations = new Vector<LinCodeChunk>();

	public Optimisation() {
		super("optimisation");
    }
    
    public void run() {
        this.run(Optimisation.DEFAULT_MAX_ITERATIONS);
    }

    public void run(int maxIterations) {

        logOptimisationsBefore();

        // Execute optimisation on all code chunks
        Vector<LinCodeChunk> optimizedCodeChunks = new Vector<LinCodeChunk>();
        for (LinCodeChunk codeChunk : ImcLin.codeChunks()) {

            // Construct a control-flow graph on which all optimisations will run
            ControlFlowGraph graph = ControlFlowGraphBuilder.build(codeChunk);
            addOptimisationLog(optimisations, "original", graph);

            addOptimisationLog(beforeAfterOptimisations, "original", graph);
            
            // Repeat optimisations until graph is fully optimized
            Optimisation.runOptimisations(graph, maxIterations);
            
            addOptimisationLog(beforeAfterOptimisations, "optimised", graph);

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
        boolean loopHoisting = getFlagValue("--loop-hoisting");
        boolean inductionVariableElimination = getFlagValue("--induction-variable-elimination");

        Report.info(String.format("Optimising frame %s", graph.codeChunk.frame.label.name));

        // Execute optimisations one by one until ALL optimisations stop
        // modifying control-flow graph or number of iterations exceeds maximum
        // number of iterations maxIter. 
        int currentIteration = 0;
        boolean repeatOptimisations = false;

        long startTime = System.currentTimeMillis();
        do {
            repeatOptimisations = false;

            if (constantFolding) {
                Report.debug("Constant folding started");
                boolean graphChanged = ConstantFolding.run(graph);
                if (graphChanged) {
                    addOptimisationLog(optimisations, "constant folding", graph);
                }
                repeatOptimisations = repeatOptimisations || graphChanged;
                Report.debug("Constant folding ended");
            }

            if (symbolicConstantFolding) {
                Report.debug("Symbolic constant folding started");
                boolean graphChanged = SymbolicConstantFolding.run(graph);
                if (graphChanged) {
                    addOptimisationLog(optimisations, "symbolic constant folding", graph);
                }
                repeatOptimisations = repeatOptimisations || graphChanged;
                Report.debug("Symbolic constant folding ended");
            }

            if (peepholeOptimisation) {
                Report.debug("Peephole optimisations started");
                boolean graphChanged = PeepholeOptimisation.run(graph);
                if (graphChanged) {
                    addOptimisationLog(optimisations, "peephole optimisation", graph);
                }
                repeatOptimisations = repeatOptimisations || graphChanged;
                Report.debug("Peephole optimisations ended");
            }

            if (commonSubexpressionElimination) {
                Report.debug("Common subexpression elimination started");
                boolean graphChanged = CommonSubexpressionElimination.run(graph);
                if (graphChanged) {
                    addOptimisationLog(optimisations, "common subexpression elimination", graph);
                }
                repeatOptimisations = repeatOptimisations || graphChanged;
                Report.debug("Common subexpression elimination ended");
            }

            if (constantPropagation) {
                Report.debug("Constant propagation started");
                boolean graphChanged = ConstantPropagation.run(graph);
                if (graphChanged) {
                    addOptimisationLog(optimisations, "constant propagation", graph);
                }
                repeatOptimisations = repeatOptimisations || graphChanged;
                Report.debug("Constant propagation ended");
            }

            if (copyPropagation) {
                Report.debug("Copy propagation started");
                boolean graphChanged = CopyPropagation.run(graph);
                if (graphChanged) {
                    addOptimisationLog(optimisations, "copy propagation", graph);
                }
                repeatOptimisations = repeatOptimisations || graphChanged;
                Report.debug("Copy propagation ended");
            }

            if (deadCodeElimination) {
                Report.debug("Dead code elimination started");
                boolean graphChanged = DeadCodeElimination.run(graph);
                if (graphChanged) {
                    addOptimisationLog(optimisations, "dead code elimination", graph);
                }
                repeatOptimisations = repeatOptimisations || graphChanged;
                Report.debug("Dead code elimination ended");
            }

            if (loopHoisting) {
                Report.debug("Loop invariant code motion started");
                boolean graphChanged = LoopHoisting.run(graph);
                if (graphChanged) {
                    addOptimisationLog(optimisations, "loop invariant code motion", graph);
                }
                repeatOptimisations = repeatOptimisations || graphChanged;
                Report.debug("Loop invariant code motion ended");
            }

            if (inductionVariableElimination) {
                Report.debug("Induction variable elimination started");
                boolean graphChanged = InductionVariableElimination.run(graph);
                if (graphChanged) {
                    addOptimisationLog(optimisations, "induction variable elimination", graph);
                }
                repeatOptimisations = repeatOptimisations || graphChanged;
                Report.debug("Induction variable elimination ended");
            }

            currentIteration += 1;

        } while (repeatOptimisations && currentIteration < maxIterations);

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        Report.info(String.format("  * optimisation completed in %d iterations [ %d ms ]", currentIteration, totalTime));

    }

    private static void addOptimisationLog(Vector<LinCodeChunk> optimisations, String title, ControlFlowGraph graph) {
        LinCodeChunk codeChunk = graph.codeChunk;
        Vector<ImcStmt> newStatements = ControlFlowGraphBuilder.toStatements(graph);
        MemFrame newFrame = codeChunk.frame.copyWithLabel(new MemLabel(codeChunk.frame.label.name + " " + title));
        LinCodeChunk newCodeChunk = new LinCodeChunk(newFrame, newStatements, codeChunk.entryLabel, codeChunk.exitLabel);
        optimisations.add(newCodeChunk);
    }

    private static boolean getFlagValue(String flag) {
        String flagArgument = Compiler.cmdLineArgValue(flag);
        if (flagArgument == null || flagArgument.length() <= 0)
            return false;
        return Boolean.parseBoolean(flagArgument);
    }

	public void log() {
        OptimisationLogger optimisationLogger = new OptimisationLogger(logger);
		for (LinDataChunk dataChunk : ImcLin.dataChunks())
            optimisationLogger.log(dataChunk);
        for (LinCodeChunk codeChunk : optimisations)
            optimisationLogger.log(codeChunk);
        
        logOptimisationsAfter();
        logOptimisationsBeforeAfter();
	}

    public void logOptimisationsBeforeAfter() {
        Logger logger = getLogger("optimisation-before-after");

        OptimisationLogger optimisationLogger = new OptimisationLogger(logger);
		for (LinDataChunk dataChunk : ImcLin.dataChunks())
            optimisationLogger.log(dataChunk);
        for (LinCodeChunk codeChunk : beforeAfterOptimisations)
            optimisationLogger.log(codeChunk);
        
        logger.close();
    }

    public void logOptimisationsBefore() {
        Logger logger = getLogger("optimisation-before");

        OptimisationLogger optimisationLogger = new OptimisationLogger(logger);
		for (LinDataChunk dataChunk : ImcLin.dataChunks())
            optimisationLogger.log(dataChunk);
        for (LinCodeChunk codeChunk : ImcLin.codeChunks())
            optimisationLogger.log(codeChunk);
        
        logger.close();
    }

    public void logOptimisationsAfter() {
        Logger logger = getLogger("optimisation-after");

        OptimisationLogger optimisationLogger = new OptimisationLogger(logger);
		for (LinDataChunk dataChunk : ImcLin.dataChunks())
            optimisationLogger.log(dataChunk);
        for (LinCodeChunk codeChunk : ImcLin.codeChunks())
            optimisationLogger.log(codeChunk);
        
        logger.close();
    }

    private static Logger getLogger(String phaseName) {
        // Prepare the name of the xml file.
        String xmlFileName = prev.Compiler.cmdLineArgValue("--xml");
        if (xmlFileName == null) {
            xmlFileName = Compiler.cmdLineArgValue("--src-file-name").replaceFirst("\\.[^./]*$", "") + "."
                    + phaseName + ".xml";
        }
        // Prepare the name of the supporting xsl file.
        String xslDirName = Compiler.cmdLineArgValue("--xsl");
        if (xslDirName == null) {
            xslDirName = "";
        }
        return new Logger(phaseName, xmlFileName, xslDirName + phaseName + ".xsl");
    }

}
