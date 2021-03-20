package prev;

import java.util.HashMap;

import org.antlr.v4.runtime.Token;

import prev.common.report.Report;
import prev.data.ast.tree.AstNode;
import prev.data.lin.LinCodeChunk;
import prev.phase.abstr.AbsLogger;
import prev.phase.abstr.Abstr;
import prev.phase.all.FinalPhase;
import prev.phase.asmgen.AsmGen;
import prev.phase.imcgen.CodeGenerator;
import prev.phase.imcgen.ImcGen;
import prev.phase.imcgen.ImcLogger;
import prev.phase.imclin.ChunkGenerator;
import prev.phase.imclin.ImcLin;
import prev.phase.imclin.Interpreter;
import prev.phase.lexan.LexAn;
import prev.phase.livean.LiveAn;
import prev.phase.memory.VariableMemoryAnalysis;
import prev.phase.memory.VariableMemoryAnalysisStaticLink;
import prev.phase.memory.MemEvaluator;
import prev.phase.memory.MemLogger;
import prev.phase.memory.Memory;
import prev.phase.optimisation.Optimisation;
import prev.phase.optimisation.common.control_flow_graph.ControlFlowGraph;
import prev.phase.optimisation.common.control_flow_graph.ControlFlowGraphBuilder;
import prev.phase.regall.RegAll;
import prev.phase.seman.AddrResolver;
import prev.phase.seman.NameResolver;
import prev.phase.seman.SemAn;
import prev.phase.seman.SemLogger;
import prev.phase.seman.TypeResolver;
import prev.phase.synan.SynAn;

/**
 * The compiler.
 */
public class Compiler {

	// COMMAND LINE ARGUMENTS

	/** All valid phases of the compiler. */
	private static final String phases = "none|lexan|synan|abstr|seman|memory|imcgen|optimisation|interpreter|imclin|asmgen|livean|regall|all";

	/** Values of command line arguments. */
	private static HashMap<String, String> cmdLine = new HashMap<String, String>();

	/* Total number of registers (not including FP, SP and HP) */
	public static int numberOfRegisters = 64;

	public static boolean printInterpreterStatistics = false;

	/** Logging level of the compiler. */
	public static Report.LoggingLevel loggingLevel = Report.DEFAULT_LOGGING_LEVEL;

	/**
	 * Returns the value of a command line argument.
	 * 
	 * @param cmdLineArgName The name of the command line argument.
	 * @return The value of the specified command line argument or {@code null} if
	 *         the specified command line argument has not been used.
	 */
	public static String cmdLineArgValue(String cmdLineArgName) {
		return cmdLine.get(cmdLineArgName);
	}

	// THE COMPILER'S STARTUP METHOD

	/**
	 * The compiler's startup method.
	 * 
	 * @param args Command line arguments (see {@link prev.Compiler}).
	 */
	public static void main(String[] args) {
		try {
			Report.info("This is PREV'20 compiler:");

			// Scan the command line.
			for (int argc = 0; argc < args.length; argc++) {
				if (args[argc].startsWith("--")) {
					// Command-line switch.
					if (args[argc].matches("--src-file-name=.*")) {
						if (cmdLine.get("--src-file-name") == null) {
							cmdLine.put("--src-file-name", args[argc]);
							continue;
						}
					}
					if (args[argc].matches("--dst-file-name=.*")) {
						if (cmdLine.get("--dst-file-name") == null) {
							cmdLine.put("--dst-file-name", args[argc]);
							continue;
						}
					}
					if (args[argc].matches("--target-phase=(" + phases + "|all)")) {
						if (cmdLine.get("--target-phase") == null) {
							cmdLine.put("--target-phase", args[argc].replaceFirst("^[^=]*=", ""));
							continue;
						}
					}
					if (args[argc].matches("--logged-phase=(" + phases + "|all)")) {
						if (cmdLine.get("--logged-phase") == null) {
							cmdLine.put("--logged-phase", args[argc].replaceFirst("^[^=]*=", ""));
							continue;
						}
					}
					if (args[argc].matches("--xml=.*")) {
						if (cmdLine.get("--xml") == null) {
							cmdLine.put("--xml", args[argc].replaceFirst("^[^=]*=", ""));
							continue;
						}
					}
					if (args[argc].matches("--xsl=.*")) {
						if (cmdLine.get("--xsl") == null) {
							cmdLine.put("--xsl", args[argc].replaceFirst("^[^=]*=", ""));
							continue;
						}
					}
					if (args[argc].matches("--logging-level=.*")) {
						if (cmdLine.get("--logging-level") == null) {
							cmdLine.put("--logging-level", args[argc].replaceFirst("^[^=]*=", ""));
							continue;
						}
					}
					if (args[argc].matches("--num-regs=\\d+")) {
						if (cmdLine.get("--num-regs") == null) {
							cmdLine.put("--num-regs", args[argc].replaceFirst("^[^=]*=", ""));
							continue;
						}
					}
					if (args[argc].matches("--constant-folding=.*")) {
						if (cmdLine.get("--constant-folding") == null) {
							cmdLine.put("--constant-folding", args[argc].replaceFirst("^[^=]*=", ""));
							continue;
						}
					}
					if (args[argc].matches("--symbolic-constant-folding=.*")) {
						if (cmdLine.get("--symbolic-constant-folding") == null) {
							cmdLine.put("--symbolic-constant-folding", args[argc].replaceFirst("^[^=]*=", ""));
							continue;
						}
					}
					if (args[argc].matches("--peephole-optimisation=.*")) {
						if (cmdLine.get("--peephole-optimisation") == null) {
							cmdLine.put("--peephole-optimisation", args[argc].replaceFirst("^[^=]*=", ""));
							continue;
						}
					}
					if (args[argc].matches("--constant-propagation=.*")) {
						if (cmdLine.get("--constant-propagation") == null) {
							cmdLine.put("--constant-propagation", args[argc].replaceFirst("^[^=]*=", ""));
							continue;
						}
					}
					if (args[argc].matches("--copy-propagation=.*")) {
						if (cmdLine.get("--copy-propagation") == null) {
							cmdLine.put("--copy-propagation", args[argc].replaceFirst("^[^=]*=", ""));
							continue;
						}
					}
					if (args[argc].matches("--common-subexpression-elimination=.*")) {
						if (cmdLine.get("--common-subexpression-elimination") == null) {
							cmdLine.put("--common-subexpression-elimination", args[argc].replaceFirst("^[^=]*=", ""));
							continue;
						}
					}
					if (args[argc].matches("--dead-code-elimination=.*")) {
						if (cmdLine.get("--dead-code-elimination") == null) {
							cmdLine.put("--dead-code-elimination", args[argc].replaceFirst("^[^=]*=", ""));
							continue;
						}
					}
					if (args[argc].matches("--loop-hoisting=.*")) {
						if (cmdLine.get("--loop-hoisting") == null) {
							cmdLine.put("--loop-hoisting", args[argc].replaceFirst("^[^=]*=", ""));
							continue;
						}
					}
					if (args[argc].matches("--induction-variable-elimination=.*")) {
						if (cmdLine.get("--induction-variable-elimination") == null) {
							cmdLine.put("--induction-variable-elimination", args[argc].replaceFirst("^[^=]*=", ""));
							continue;
						}
					}
					if (args[argc].matches("--interpreter-statistics")) {
						if (cmdLine.get("--interpreter-statistics") == null) {
							printInterpreterStatistics = true;
							continue;
						}
					}
					Report.warning("Command line argument '" + args[argc] + "' ignored.");
				} else {
					// Source file name.
					if (cmdLine.get("--src-file-name") == null) {
						cmdLine.put("--src-file-name", args[argc]);
					} else {
						Report.warning("Source file '" + args[argc] + "' ignored.");
					}
				}
			}
			if (cmdLine.get("--src-file-name") == null) {
				throw new Report.Error("Source file not specified.");
			}
			if (cmdLine.get("--dst-file-name") == null) {
				cmdLine.put("--dst-file-name", cmdLine.get("--src-file-name").replaceFirst("\\.[^./]*$", "") + ".mmix");
			}
			if (cmdLine.get("--target-phase") == null) {
				cmdLine.put("--target-phase", phases.replaceFirst("^.*\\|", ""));
			}
			if (cmdLine.get("--logging-level") != null) {
				try {
					loggingLevel = Report.LoggingLevel.valueOf(cmdLine.get("--logging-level"));
				} catch (IllegalArgumentException exception) {
					Report.warning("Invalid logging level, using " + loggingLevel);
				}
			}

			String numberOfRegistersData = Compiler.cmdLineArgValue("--num-regs");
			if (numberOfRegistersData != null) {
				numberOfRegisters = Integer.parseInt(numberOfRegistersData);
				if (numberOfRegisters < 1)
					throw new Report.Error("Number of registers must be at least 1.");
			} else {
				Report.info("No number of registers set, using " + numberOfRegisters);
			}

			// Compilation process carried out phase by phase.
			while (true) {

				// Lexical analysis.
				if (Compiler.cmdLineArgValue("--target-phase").equals("lexan"))
					try (LexAn lexan = new LexAn()) {
						while (lexan.lexer.nextToken().getType() != Token.EOF) {
						}
						break;
					}

				// Syntax analysis.
				try (LexAn lexan = new LexAn(); SynAn synan = new SynAn(lexan)) {
					SynAn.tree = synan.parser.source();
					synan.log(SynAn.tree);
				}
				if (Compiler.cmdLineArgValue("--target-phase").equals("synan"))
					break;

				// Abstract syntax tree construction.
				try (Abstr abstr = new Abstr()) {
					Abstr.tree = SynAn.tree.ast;
					AstNode.lock();
					AbsLogger logger = new AbsLogger(abstr.logger);
					Abstr.tree.accept(logger, "Decls");
				}
				if (Compiler.cmdLineArgValue("--target-phase").equals("abstr"))
					break;

				// Semantic analysis.
				try (SemAn seman = new SemAn()) {
					Abstr.tree.accept(new NameResolver(), null);
					Abstr.tree.accept(new TypeResolver(), null);
					Abstr.tree.accept(new AddrResolver(), null);
					SemAn.declaredAt.lock();
					SemAn.declaresType.lock();
					SemAn.isType.lock();
					SemAn.ofType.lock();
					SemAn.isAddr.lock();
					AbsLogger logger = new AbsLogger(seman.logger);
					logger.addSubvisitor(new SemLogger(seman.logger));
					Abstr.tree.accept(logger, "Decls");
				}
				if (Compiler.cmdLineArgValue("--target-phase").equals("seman"))
					break;

				// Memory layout.
				try (Memory memory = new Memory()) {
					Abstr.tree.accept(new VariableMemoryAnalysis(), null);
					Abstr.tree.accept(new VariableMemoryAnalysisStaticLink(), VariableMemoryAnalysisStaticLink.first());
					Abstr.tree.accept(new VariableMemoryAnalysisStaticLink(), VariableMemoryAnalysisStaticLink.second());
					Abstr.tree.accept(new MemEvaluator(), null);
					Memory.frames.lock();
					Memory.accesses.lock();
					Memory.strings.lock();
					AbsLogger logger = new AbsLogger(memory.logger);
					logger.addSubvisitor(new SemLogger(memory.logger));
					logger.addSubvisitor(new MemLogger(memory.logger));
					Abstr.tree.accept(logger, "Decls");
				}
				if (Compiler.cmdLineArgValue("--target-phase").equals("memory"))
					break;

				// Intermediate code generation.
				try (ImcGen imcgen = new ImcGen()) {
					Abstr.tree.accept(new CodeGenerator(), null);
					ImcGen.stmtImc.lock();
					AbsLogger logger = new AbsLogger(imcgen.logger);
					logger.addSubvisitor(new SemLogger(imcgen.logger));
					logger.addSubvisitor(new MemLogger(imcgen.logger));
					logger.addSubvisitor(new ImcLogger(imcgen.logger));
					Abstr.tree.accept(logger, "Decls");
				}
				if (Compiler.cmdLineArgValue("--target-phase").equals("imcgen"))
					break;
				
				// Linearization of intermediate code.
				try (ImcLin imclin = new ImcLin()) {
					Abstr.tree.accept(new ChunkGenerator(), null);
					imclin.log();
				}
				if (Compiler.cmdLineArgValue("--target-phase").equals("imclin"))
					break;

				// Optimize generated intermediate code. The optimizer will
				// modify code chunks in ImcLin.codeChunks Vector.
				try (Optimisation optimisation = new Optimisation()) {
					optimisation.run();
					optimisation.log();
				}
				if (Compiler.cmdLineArgValue("--target-phase").equals("optimisation"))
					break;
				
				// Additional phase that can only be executed using
				// --target-phase=interpreter flag. It runs the code using
				// intermediate representation interpreter. 
				if (Compiler.cmdLineArgValue("--target-phase").equals("interpreter")) {
					try {
						Interpreter interpreter = new Interpreter(ImcLin.dataChunks(), ImcLin.codeChunks());
						long exitCode = interpreter.run("_main", printInterpreterStatistics);
						System.out.printf("Exit code: %d%n", exitCode);
					} finally {
						break;
					}
				}
								
				// Machine code generation.
				try (AsmGen asmgen = new AsmGen()) {
					asmgen.genAsmCodes();
					asmgen.log();
				}
				if (Compiler.cmdLineArgValue("--target-phase").equals("acmgen"))
					break;

				// Liveness analysis.
				try (LiveAn livean = new LiveAn()) {
					livean.analysis();
					livean.log();
				}
				if (Compiler.cmdLineArgValue("--target-phase").equals("livean"))
					break;

				// Register allocation
				RegAll registerAllocation = null;
				try (RegAll regall = new RegAll()) {
					regall.allocate();
					regall.log();
					registerAllocation = regall;
				}
				if (Compiler.cmdLineArgValue("--target-phase").equals("regall"))
					break;
				
				// The last phase - FINAL PHASE
				try (FinalPhase finalPhase = new FinalPhase(registerAllocation.tempToReg)) {
					finalPhase.run();
					finalPhase.log();
				}

				break;
			}

			Report.info("Done.");
		} catch (Report.Error __) {
			System.exit(1);
		}
	}

}
