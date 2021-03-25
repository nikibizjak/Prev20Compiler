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

    /** Check whether expression is loop invariant. */
    private static boolean isLoopInvariant(LoopNode loop, ControlFlowGraphNode node, ImcExpr expression) {
        // expression is a constant
        if (expression instanceof ImcCONST) {
            return true;
        }
        
        HashSet<ControlFlowGraphNode> reachingDefinitionsIn = node.getReachingDefinitionsIn();
        // All the definitions of expression that reach node are outside the
        // loop
        boolean allDefinitionsOutsideLoop = true;
        HashSet<ControlFlowGraphNode> expressionDefinitions = new HashSet<ControlFlowGraphNode>();
        for (ControlFlowGraphNode definition : reachingDefinitionsIn) {
            if (!definition.getDefines().contains(expression))
                continue;
            expressionDefinitions.add(definition);
            // One definition of a_i that reaches d is inside the loop
            if (loop.containsNode(definition)) {
                allDefinitionsOutsideLoop = false;
                break;
            }
        }
        if (allDefinitionsOutsideLoop) {
            return true;
        }

        // only one definition of a_i reaches d, and that definition is
        // loop-invariant
        if (expressionDefinitions.size() == 1) {
            ControlFlowGraphNode onlyReachingDefinition = expressionDefinitions.iterator().next();
            // The only reaching definition is this statement.
            if (onlyReachingDefinition.statement.equals(node.statement))
                return false;
            
            HashSet<ControlFlowGraphNode> alreadyVisited = new HashSet<ControlFlowGraphNode>();
            alreadyVisited.add(node);
            if (LoopHoisting.isLoopInvariant(loop, onlyReachingDefinition, alreadyVisited))
                return true;
        }
        
        return false;
    }

    static Stack<ControlFlowGraphNode> connectionPath = new Stack<ControlFlowGraphNode>();
    static List<Stack<ControlFlowGraphNode>> connectionPaths = new ArrayList<Stack<ControlFlowGraphNode>>();
    static void findAllPaths(ControlFlowGraphNode node, ControlFlowGraphNode targetNode) {
        for (ControlFlowGraphNode nextNode : node.getPredecessors()) {
            if (nextNode.equals(targetNode)) {
                Stack<ControlFlowGraphNode> temp = new Stack<ControlFlowGraphNode>();
                for (ControlFlowGraphNode node1 : connectionPath)
                    temp.add(node1);
                connectionPaths.add(temp);
            } else if (!connectionPath.contains(nextNode)) {
                connectionPath.push(nextNode);
                findAllPaths(nextNode, targetNode);
                connectionPath.pop();
            }
        }
    }

    private static InductionVariable getDerivedInductionVariable(ImcTEMP temporary, HashMap<ImcTEMP, HashSet<ControlFlowGraphNode>> allDefinitions, ControlFlowGraph graph, LoopNode loop) {
        // The variable k is a *derived induction variable* in loop L if:
        //   1. There is only one definition of k within L, of the form k <- j *
        //      c or k <- j + d, where j is an induction variable and c, d are
        //      loop invariant and
        //   2. if j is a derived induction variable in the family of i, then:
        //       * the only definition of j that reaches k is the one in the
        //         loop and
        //       * there is no definition of i on any path between the
        //         definition of j and the definition of k

        HashSet<ControlFlowGraphNode> definitions = new HashSet<ControlFlowGraphNode>(allDefinitions.get(temporary));
        HashSet<ControlFlowGraphNode> loopNodes = loop.getLoopNodes();
        definitions.retainAll(loopNodes);
        
        // There is only one definition of k within loop
        if (definitions.size() != 1)
            return null;
        
        // Definition is of the form k <- j * c or k <- j + d.
        // There is another possibility: k <- j - d = k <- j + (-d))

        // Get node in which this derived induction variable was defined
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
        
        // The first expression should always be the loop-invariant expression.
        // The second one should be a induction variable. If the order is not
        // correct, swap them.
        ImcExpr firstExpression = binaryOperation.fstExpr;
        ImcExpr secondExpression = binaryOperation.sndExpr;

        if (!isLoopInvariant(loop, definitionNode, firstExpression)) {
            if (!isLoopInvariant(loop, definitionNode, secondExpression)) {
                // The first or the second expression is not loop-invariant.
                // This can't be a derived induction variable.
                return null;
            }
            // First expression is not loop-invariant, but the second one is.
            // Swap them.
            ImcExpr temporaryExpression = firstExpression;
            firstExpression = secondExpression;
            secondExpression = temporaryExpression;
        }

        // The firstExpression is now loop-invariant. Check if second expression
        // is loop-invariant.
        loopInvariantExpression = firstExpression;
        if (!(secondExpression instanceof ImcTEMP))
            return null;
        
        // Check if this temporary is actually induction variable.
        ImcTEMP possibleInductionTemporary = (ImcTEMP) secondExpression;
        // Check if possibleInductionTemporary is a basic induction variable. If
        // yes, then this is a derived induction variable without any other
        // checks.
        HashSet<ControlFlowGraphNode> basicInductionVariableDefinitions = new HashSet<ControlFlowGraphNode>(allDefinitions.get(possibleInductionTemporary));
        basicInductionVariableDefinitions.retainAll(loopNodes);
        inductionVariable = getBasicInductionVariable(possibleInductionTemporary, basicInductionVariableDefinitions, graph, loop);
        if (inductionVariable == null) {
            // Check if possibleInductionTemporary is a derived induction
            // variable. If it is not, then this is not an derived induction
            // variable.
            inductionVariable = getDerivedInductionVariable(possibleInductionTemporary, allDefinitions, graph, loop);
            if (inductionVariable == null)
                return null;
            
            // Induction variable inductionVariable is a derived induction
            // variable in the family of inductionVariable.inductionVariable and
            // is directly derived from possibleInductionTemporary.
            
            // i - inductionVariable.inductionVariable
            // j - possibleInductionTemporary
            // k - temporary
            
            // Check if the only definition of possibleInductionTemporary that
            // reaches temporary is the one in the loop.
            HashSet<ControlFlowGraphNode> reachingDefinitionsIn = definitionNode.getReachingDefinitionsIn();
            // Only leave reaching definitions where possibleInductionTemporary
            // is defined.
            reachingDefinitionsIn.retainAll(allDefinitions.get(possibleInductionTemporary));
            // reachingDefinitionsIn - all definitions of j that reach k

            int numberOfDefinitions = reachingDefinitionsIn.size();
            
            reachingDefinitionsIn.retainAll(loopNodes);
            // reachingDefinitionsIn - all definitions of j that reach k that
            // are in the loop.
            int numberOfDefinitionsOutsideLoop = numberOfDefinitions - reachingDefinitionsIn.size();
            
            // There must be no reaching definitions from outside the loop.
            if (numberOfDefinitionsOutsideLoop > 0)
                return null;

            // There must only be one definition inside the loop.
            if (reachingDefinitionsIn.size() != 1)
                return null;

            // Check if there is no definition of
            // inductionVariable.inductionVariable on any path between the
            // definition of possibleInductionTemporary and the definition of
            // temporary.
            connectionPath.clear();
            connectionPaths.clear();
            findAllPaths(definitionNode, reachingDefinitionsIn.iterator().next());

            for (Stack<ControlFlowGraphNode> path : connectionPaths) {
                while (!path.isEmpty()) {
                    ControlFlowGraphNode pathNode = path.pop();
                    if (pathNode.getDefines().contains(inductionVariable.inductionVariable)) {
                        return null;
                    }
                }
            }
        }

        // Here we can assume that loopInvariantExpression and inductionVariable
        // are non-null. Construct a new derived induction variable in the
        // family of inductionVariable.inductionVariable with modified
        // multiplication and addition terms.

        // Assuming j is characterized by (i, a, b), then k is described by:
        //   * k: (i, a * c, b * c) if k <- j * c
        //   * k: (i, a + d, b) if k <- j + d
        //   * k: (i, a - d, b) if k <- j - d
        DerivedInductionVariable derivedInductionVariable = null;
        if (binaryOperation.oper == ImcBINOP.Oper.ADD) {
            derivedInductionVariable = new DerivedInductionVariable(inductionVariable.inductionVariable, new ImcBINOP(ImcBINOP.Oper.ADD, inductionVariable.additionTerm, loopInvariantExpression), inductionVariable.multiplicationTerm);
        } else if (binaryOperation.oper == ImcBINOP.Oper.SUB) {
            derivedInductionVariable = new DerivedInductionVariable(inductionVariable.inductionVariable, new ImcBINOP(ImcBINOP.Oper.SUB, inductionVariable.additionTerm, loopInvariantExpression), inductionVariable.multiplicationTerm);
        } else if (binaryOperation.oper == ImcBINOP.Oper.MUL) {
            derivedInductionVariable = new DerivedInductionVariable(inductionVariable.inductionVariable, new ImcBINOP(ImcBINOP.Oper.MUL, inductionVariable.additionTerm, loopInvariantExpression), new ImcBINOP(ImcBINOP.Oper.MUL, inductionVariable.multiplicationTerm, loopInvariantExpression));
        }

        if (derivedInductionVariable != null) {
            derivedInductionVariable.addDefinition(definitionNode);
        }

        return derivedInductionVariable; 

    }

    private static ImcExpr getIncrementExpression(ControlFlowGraphNode node, ImcTEMP temporary) {
        ImcStmt statement = node.statement;
        if (!(statement instanceof ImcMOVE))
            return null;
        
        ImcMOVE moveStatement = (ImcMOVE) statement;
        if (!(moveStatement.src instanceof ImcBINOP))
            return null;
        
        ImcBINOP binaryOperation = (ImcBINOP) moveStatement.src;
        if (!(binaryOperation.oper == ImcBINOP.Oper.ADD || binaryOperation.oper == ImcBINOP.Oper.SUB))
            return null;
        
        // Other expression should be loop-invariant expression.
        ImcExpr otherExpression = binaryOperation.fstExpr;
        if (otherExpression.equals(temporary)) {
            otherExpression = binaryOperation.sndExpr;
        }
        if (otherExpression.equals(temporary)) {
            return null;
        }
        return otherExpression;
    }

    private static InductionVariable getBasicInductionVariable(ImcTEMP temporary, HashSet<ControlFlowGraphNode> definitions, ControlFlowGraph graph, LoopNode loop) {
        // The variable i is a basic induction variable in a loop L with header
        // node h if the only definitions of i within L are of the form i <- i +
        // c or i <- i - c, where c is loop invariant.

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
            
            // Other expression should be loop-invariant expression.
            ImcExpr otherExpression = binaryOperation.fstExpr;
            if (otherExpression.equals(temporary)) {
                otherExpression = binaryOperation.sndExpr;
            }
            if (otherExpression.equals(temporary)) {
                return null;
            }

            // If other expression is not loop-invariant, this is not an
            // induction variable.
            if (!isLoopInvariant(loop, definitionNode, otherExpression)) {
                return null;
            }

            // Here, we can assume that the statement is of the form
            // temporary <- temporary oper otherExpression where oper is either
            // ADD or SUB and otherExpression is loop-invariant expression.

            // If every definition passes all conditions, then this temporary is
            // a basic induction variable.
        }

        InductionVariable inductionVariable = new BasicInductionVariable(temporary);
        for (ControlFlowGraphNode definition : definitions)
            inductionVariable.addDefinition(definition);
        return inductionVariable;
    }

    private static HashMap<ImcTEMP, InductionVariable> detectInductionVariables(ControlFlowGraph graph, LoopNode loop) {
        HashMap<ImcTEMP, InductionVariable> inductionVariables = new HashMap<ImcTEMP, InductionVariable>();
        HashSet<ControlFlowGraphNode> loopNodes = loop.getLoopNodes();

        LivenessAnalysis.analysis(graph);
        ReachingDefinitionsAnalysis.run(graph);
        // Find all defined variables and ControlFlowGraphNodes where those
        // variables are defined.
        HashMap<ImcTEMP, HashSet<ControlFlowGraphNode>> definitions = ReachingDefinitionsAnalysis.definitions(graph);

        for (ImcTEMP temporary: definitions.keySet()) {
            HashSet<ControlFlowGraphNode> temporaryDefinedIn = new HashSet<ControlFlowGraphNode>(definitions.get(temporary));
            // Remove all definitions outside the current loop
            temporaryDefinedIn.retainAll(loopNodes);

            if (temporaryDefinedIn.size() <= 0)
                continue;
            
            InductionVariable basicInductionVariable = getBasicInductionVariable(temporary, temporaryDefinedIn, graph, loop);
            if (basicInductionVariable != null) {
                Report.debug(String.format("  * %s is basic induction variable: %s", temporary, basicInductionVariable));
                inductionVariables.put(temporary, basicInductionVariable);
            } else {
                InductionVariable derivedInductionVariable = getDerivedInductionVariable(temporary, definitions, graph, loop);
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

        for (ImcTEMP temporary : inductionVariables.keySet()) {
            InductionVariable variable = inductionVariables.get(temporary);

            if (!(variable instanceof DerivedInductionVariable)) continue;
            
            DerivedInductionVariable inductionVariable = (DerivedInductionVariable) variable;
            InductionVariable derivedFrom = inductionVariables.get(inductionVariable.inductionVariable);
            if (!(derivedFrom instanceof BasicInductionVariable)) continue;
            BasicInductionVariable basicInductionVariable = (BasicInductionVariable) derivedFrom;
            
            // Get derived induction variable definition - there will always be at most one.
            ControlFlowGraphNode definition = inductionVariable.getDefinitions().get(0);

            // Strength reduction should only be performed on derived induction
            // variables with multiplication: T1 <- 8 * T2.
            if (!(definition.statement instanceof ImcMOVE)) continue;
            if (!(((ImcMOVE) definition.statement).dst instanceof ImcBINOP)) continue;

            ImcBINOP binaryOperation = (ImcBINOP) ((ImcMOVE) definition.statement).dst;
            if (binaryOperation.oper != ImcBINOP.Oper.MUL) continue;

            // The inductionVariable is now a DerivedInductionVariable and the
            // only definition of this variable is multiplication operation.
            // Perform strength reduction here.
            ImcTEMP newInductionTemporary = new ImcTEMP(new MemTemp());

            // After each assignment to variable i, make an assignment j' <- j' + c * b
            Vector<ControlFlowGraphNode> inductionVariableAssignments = basicInductionVariable.getDefinitions();
            for (ControlFlowGraphNode inductionVariableAssignment : inductionVariableAssignments) {
                ImcExpr incrementExpression = getIncrementExpression(inductionVariableAssignment, inductionVariable.inductionVariable);
                ImcMOVE move = new ImcMOVE(newInductionTemporary, new ImcBINOP(ImcBINOP.Oper.ADD, newInductionTemporary, new ImcBINOP(ImcBINOP.Oper.MUL, incrementExpression, inductionVariable.multiplicationTerm)));
                ControlFlowGraphNode moveNode = new ControlFlowGraphNode(move);
                graph.insertAfter(inductionVariableAssignment, moveNode);                    
            }

            // Replace assignment j <- ... with j <- j' (there is only one)
            Vector<ControlFlowGraphNode> derivedInductionVariableAssignments = inductionVariable.getDefinitions();
            ControlFlowGraphNode derivedInductionVariableAssignment = derivedInductionVariableAssignments.get(0);
            derivedInductionVariableAssignment.statement = new ImcMOVE(temporary, newInductionTemporary);

            // Initialize j' in loop preheader to j' <- a + i * b
            ImcMOVE newInductionTemporaryInitialization = new ImcMOVE(newInductionTemporary, new ImcBINOP(ImcBINOP.Oper.ADD, inductionVariable.additionTerm, new ImcBINOP(ImcBINOP.Oper.MUL, inductionVariable.inductionVariable, inductionVariable.multiplicationTerm)));
            ControlFlowGraphNode initializationNode = new ControlFlowGraphNode(newInductionTemporaryInitialization);
            loop.preheader.append(initializationNode);
            hasGraphChanged = true;
            
        }

        return hasGraphChanged;
    }
    
}