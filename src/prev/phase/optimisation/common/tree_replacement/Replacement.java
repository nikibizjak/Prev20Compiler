package prev.phase.optimisation.common.tree_replacement;

import prev.data.imc.code.expr.ImcExpr;

public class Replacement {

    ImcExpr replace;
    ImcExpr replaceWith;
    boolean replaceInMoveDestination;

    public Replacement(ImcExpr replace, ImcExpr replaceWith, boolean replaceInMoveDestination) {
        this.replace = replace;
        this.replaceWith = replaceWith;
        this.replaceInMoveDestination = replaceInMoveDestination;
    }

    public Replacement(ImcExpr replace, ImcExpr replaceWith) {
        this(replace, replaceWith, false);
    }

}