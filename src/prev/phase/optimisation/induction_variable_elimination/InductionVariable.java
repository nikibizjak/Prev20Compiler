package prev.phase.optimisation.induction_variable_elimination;

import prev.data.imc.code.expr.*;
import prev.data.imc.code.stmt.*;
import prev.data.mem.*;

/**
 * A representation of linear induction variable.
 *
 * Each linear induction variable j can be represented by a tuple j: (i, a, b),
 * where i is an induction variable. Variable j can be written as j = a + i * b.
 * InductionVariable class is used to represent this tuple.
*/
public abstract class InductionVariable {
    
    /** An induction variable i that this induction variable depends on. */
    public ImcTEMP inductionVariable;

    /** Addition part a in induction variable tuple. */
    public ImcExpr additionTerm;

    /** Multiplication part b in induction variable tuple. */
    public ImcExpr multiplicationTerm;

    public InductionVariable(ImcTEMP inductionVariable, ImcExpr additionTerm, ImcExpr multiplicationTerm) {
        this.inductionVariable = inductionVariable;
        this.additionTerm = additionTerm;
        this.multiplicationTerm = multiplicationTerm;
    }

    @Override
    public String toString() {
        // return String.format("(%s, %s, %s)", this.inductionVariable, this.additionTerm, this.multiplicationTerm);
        return String.format("%s + %s * %s", this.additionTerm, this.inductionVariable, this.multiplicationTerm);
    }

}

/**
 * A representation of basic induction variable.
 *
 * Basic induction variables are variables that are either incremented or
 * decremented by a loop-invariant expression incrementExpression. So basic
 * induction variable is always either i <- i + c or i <- i - c where c is
 * loop-invariant expression. We can characterize the basic induction variable i
 * by a triple (i, 0, 1), meaning that i = 0 + i * 1.
 */
class BasicInductionVariable extends InductionVariable {

    /** Loop-invariant expression used to update value of basic induction
     * variable each loop iteration. */
    public ImcExpr incrementExpression;

    public BasicInductionVariable(ImcTEMP inductionVariable, ImcExpr incrementExpression) {
        super(inductionVariable, new ImcCONST(0), new ImcCONST(1));
        this.incrementExpression = incrementExpression;
    }
}

/**
 * A representation of derived induction variable.
 */
class DerivedInductionVariable extends InductionVariable {
    public DerivedInductionVariable(ImcTEMP inductionVariable, ImcExpr additionTerm, ImcExpr multiplicationTerm) {
        super(inductionVariable, additionTerm, multiplicationTerm);
    }
}