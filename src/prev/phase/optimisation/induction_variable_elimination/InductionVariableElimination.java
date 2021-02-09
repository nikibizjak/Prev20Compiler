package prev.phase.optimisation.induction_variable_elimination;

import prev.phase.optimisation.common.control_flow_graph.*;
import prev.phase.optimisation.common.reaching_definitions.*;
import prev.phase.optimisation.common.dominators.*;
import prev.phase.optimisation.common.liveness_analysis.*;
import prev.phase.optimisation.loop_hoisting.*;
import prev.common.report.*;
import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.mem.*;
import java.util.*;

public class InductionVariableElimination {

    public static boolean run(ControlFlowGraph graph) {

        boolean hasGraphChanged = false;

        // The nesting tree contains all loops in the current program. The
        // first level of nestingTree is a full program.
        LoopNode nestingTree = LoopFinder.findAllLoops(graph);

        // Only add preheaders to all loops ONCE. Exclude the first level of
        // nesting (the whole program).
        for (LoopNode loop : nestingTree.subLoops) {
            LoopHoisting.addPreheader(graph, loop);
        }

        // Don't optimize the first level of nesting tree.
        for (LoopNode loop : nestingTree.subLoops) {
            // Detect induction variables in the current loop.
            HashMap<ImcTEMP, InductionVariable> inductionVariables = detectInductionVariables(graph, loop);
            // Perform strength reduction on derived induction variables inside
            // the current loop.
            hasGraphChanged = hasGraphChanged || runStrengthReduction(graph, loop, inductionVariables);
        }


        return hasGraphChanged;
    }

    private static boolean isLoopInvariant(LoopNode loop, ImcExpr expression) {
        // TODO: Actually check for loop invariance here
        return expression instanceof ImcCONST;
    }

    private static InductionVariable getDerivedInductionVariable(ImcTEMP temporary, HashMap<ImcTEMP, HashSet<ControlFlowGraphNode>> allDefinitions, HashSet<ControlFlowGraphNode> definitions, ControlFlowGraph graph, LoopNode loop) {
        // There is only one definition of k within loop
        if (definitions.size() != 1)
            return null;
        
        // Definition is of the form k <- j * c or k <- j + d.
        // There is another possibility: k <- j - d = k <- j + (-d))
        ControlFlowGraphNode definitionNode = definitions.iterator().next();
        ImcStmt statement = definitionNode.statement;
        if (!(statement instanceof ImcMOVE))
            return null;
        ImcMOVE moveStatement = (ImcMOVE) statement;
        if (!(moveStatement.src instanceof ImcBINOP))
            return null;
        
        ImcBINOP binaryOperation = (ImcBINOP) moveStatement.src;
        if (!(binaryOperation.oper == ImcBINOP.Oper.ADD || binaryOperation.oper == ImcBINOP.Oper.SUB || binaryOperation.oper == ImcBINOP.Oper.MUL))
            return null;
        
        // One of variables must be induction variable (either basic induction
        // variable or derived induction variable). The other must be a loop
        // invariant expression.
        InductionVariable inductionVariable = null;
        ImcExpr loopInvariantExpression = null;

        if (isLoopInvariant(loop, binaryOperation.fstExpr)) {
            // Second expression must be an induction variable
            if (!(binaryOperation.sndExpr instanceof ImcTEMP))
                return null;
            
            ImcTEMP possibleInductionTemporary = (ImcTEMP) binaryOperation.sndExpr;
            inductionVariable = getBasicInductionVariable(possibleInductionTemporary, allDefinitions.get(possibleInductionTemporary), graph, loop);
            if (inductionVariable == null) {
                // temporary could also be a derived induction variable
                // if (!...)
                //     return null;
            }
            loopInvariantExpression = binaryOperation.fstExpr;
            if (loopInvariantExpression == null || inductionVariable == null)
                return null;
            
            if (binaryOperation.oper == ImcBINOP.Oper.ADD) {
                return new DerivedInductionVariable(inductionVariable.inductionVariable, new ImcBINOP(ImcBINOP.Oper.ADD, inductionVariable.additionTerm, loopInvariantExpression), inductionVariable.multiplicationTerm);
            } else if (binaryOperation.oper == ImcBINOP.Oper.SUB) {
                return new DerivedInductionVariable(inductionVariable.inductionVariable, new ImcBINOP(ImcBINOP.Oper.SUB, inductionVariable.additionTerm, loopInvariantExpression), inductionVariable.multiplicationTerm);
            } else if (binaryOperation.oper == ImcBINOP.Oper.MUL) {
                return new DerivedInductionVariable(inductionVariable.inductionVariable, new ImcBINOP(ImcBINOP.Oper.MUL, inductionVariable.additionTerm, loopInvariantExpression), new ImcBINOP(ImcBINOP.Oper.MUL, inductionVariable.multiplicationTerm, loopInvariantExpression));
            }
            return null;            
        } else if (isLoopInvariant(loop, binaryOperation.sndExpr)) {
            // First expression must be an induction variable
            if (!(binaryOperation.fstExpr instanceof ImcTEMP))
                return null;

            inductionVariable = getBasicInductionVariable(temporary, definitions, graph, loop);
            if (inductionVariable == null) {
                // temporary could also be a derived induction variable
            }
            return null;
        } else {
            // There is no loop invariant expression so this isn't a derived
            // induction variable.
            return null;
        }

        // Check if first variable is induction variable
        // Check if second variable is induction variable
        
        /*if (binaryOperation.oper == ImcBINOP.Oper.ADD) {

        } else if (binaryOperation.oper == ImcBINOP.Oper.SUB) {

        } else if (binaryOperation.oper == ImcBINOP.Oper.MUL) {

        }
        
        return null;*/
    }

    private static InductionVariable getBasicInductionVariable(ImcTEMP temporary, HashSet<ControlFlowGraphNode> definitions, ControlFlowGraph graph, LoopNode loop) {
        for (ControlFlowGraphNode definitionNode : definitions) {
            ImcStmt statement = definitionNode.statement;
            if (!(statement instanceof ImcMOVE))
                return null;
            
            ImcMOVE moveStatement = (ImcMOVE) statement;
            if (!(moveStatement.src instanceof ImcBINOP))
                return null;
            
            ImcBINOP binaryOperation = (ImcBINOP) moveStatement.src;
            if (!(binaryOperation.oper == ImcBINOP.Oper.ADD || binaryOperation.oper == ImcBINOP.Oper.SUB))
                return null;
            
            if (binaryOperation.fstExpr.equals(temporary)) {
                // Check if second expression is loop-invariant
                if (isLoopInvariant(loop, binaryOperation.sndExpr)) {
                    ImcExpr expression = binaryOperation.sndExpr;
                    if (binaryOperation.oper == ImcBINOP.Oper.SUB)
                        expression = new ImcUNOP(ImcUNOP.Oper.NEG, expression);
                    return new BasicInductionVariable(temporary, expression);
                }
                return null;
            } else if (binaryOperation.sndExpr.equals(temporary)) {
                // Check if first expression is loop-invariant
                if (isLoopInvariant(loop, binaryOperation.fstExpr)) {
                    ImcExpr expression = binaryOperation.fstExpr;
                    if (binaryOperation.oper == ImcBINOP.Oper.SUB)
                        expression = new ImcUNOP(ImcUNOP.Oper.NEG, expression);
                    return new BasicInductionVariable(temporary, expression);   
                }
                return null;
            }
            return null;
        }
        return null;
    }

    private static HashMap<ImcTEMP, InductionVariable> detectInductionVariables(ControlFlowGraph graph, LoopNode loop) {
        HashMap<ImcTEMP, InductionVariable> inductionVariables = new HashMap<ImcTEMP, InductionVariable>();
        HashSet<ControlFlowGraphNode> loopNodes = loop.getLoopNodes();

        // Find all defined variables and ControlFlowGraphNodes where those
        // variables are defined.
        HashMap<ImcTEMP, HashSet<ControlFlowGraphNode>> definitions = ReachingDefinitionsAnalysis.definitions(graph);
        for (ImcTEMP temporary: definitions.keySet()) {
            HashSet<ControlFlowGraphNode> temporaryDefinedIn = definitions.get(temporary);
            // Remove all definitions outside the current loop
            temporaryDefinedIn.retainAll(loopNodes);

            if (temporaryDefinedIn.size() <= 0)
                continue;
            
            InductionVariable basicInductionVariable = getBasicInductionVariable(temporary, temporaryDefinedIn, graph, loop);
            if (basicInductionVariable != null) {
                Report.debug(String.format("  * %s is basic induction variable: %s", temporary, basicInductionVariable));
                inductionVariables.put(temporary, basicInductionVariable);
            } else {
                InductionVariable derivedInductionVariable = getDerivedInductionVariable(temporary, definitions, temporaryDefinedIn, graph, loop);
                if (derivedInductionVariable != null) {
                    Report.debug(String.format("  * %s is derived induction variable: %s", temporary, derivedInductionVariable));
                    inductionVariables.put(temporary, derivedInductionVariable);
                }
            }
        }

        return inductionVariables;
    }

    private static boolean runStrengthReduction(ControlFlowGraph graph, LoopNode loop, HashMap<ImcTEMP, InductionVariable> inductionVariables) {
        boolean hasGraphChanged = false;

        HashMap<ImcTEMP, HashSet<ControlFlowGraphNode>> definitions = ReachingDefinitionsAnalysis.definitions(graph);
        HashSet<ControlFlowGraphNode> loopNodes = loop.getLoopNodes();

        for (ImcTEMP temporary : inductionVariables.keySet()) {
            InductionVariable inductionVariable = inductionVariables.get(temporary);
            if (inductionVariable instanceof DerivedInductionVariable && inductionVariables.get(inductionVariable.inductionVariable) instanceof BasicInductionVariable) {
                // Create new temporary j'
                ImcTEMP newInductionTemporary = new ImcTEMP(new MemTemp());

                // After each assignment to variable i, make an assignment j' <- j' + c * b
                HashSet<ControlFlowGraphNode> inductionVariableAssignments = definitions.get(inductionVariable.inductionVariable);
                inductionVariableAssignments.retainAll(loopNodes);
                for (ControlFlowGraphNode inductionVariableAssignment : inductionVariableAssignments) {
                    ImcExpr additionTerm = ((BasicInductionVariable) inductionVariables.get(inductionVariable.inductionVariable)).incrementExpression;
                    ImcMOVE move = new ImcMOVE(newInductionTemporary, new ImcBINOP(ImcBINOP.Oper.ADD, newInductionTemporary, new ImcBINOP(ImcBINOP.Oper.MUL, additionTerm, inductionVariable.multiplicationTerm)));
                    ControlFlowGraphNode moveNode = new ControlFlowGraphNode(move);
                    graph.insertAfter(inductionVariableAssignment, moveNode);                    
                }

                // Replace assignment j <- ... with j <- j' (there is only one)
                HashSet<ControlFlowGraphNode> derivedInductionVariableAssignments = definitions.get(temporary);
                derivedInductionVariableAssignments.retainAll(loopNodes);
                ControlFlowGraphNode derivedInductionVariableAssignment = derivedInductionVariableAssignments.iterator().next();
                derivedInductionVariableAssignment.statement = new ImcMOVE(temporary, newInductionTemporary);

                // Initialize j' in loop preheader to j' <- a + i * b
                ImcMOVE newInductionTemporaryInitialization = new ImcMOVE(newInductionTemporary, new ImcBINOP(ImcBINOP.Oper.ADD, inductionVariable.additionTerm, new ImcBINOP(ImcBINOP.Oper.MUL, inductionVariable.inductionVariable, inductionVariable.multiplicationTerm)));
                ControlFlowGraphNode initializationNode = new ControlFlowGraphNode(newInductionTemporaryInitialization);
                loop.preheader.append(initializationNode);
                hasGraphChanged = true;
            }
        }

        return hasGraphChanged;
    }
    
}