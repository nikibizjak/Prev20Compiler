package prev.phase.optimisation.common.tree_replacement;

import prev.data.imc.code.expr.ImcExpr;

public class Replacement {
    ImcExpr replace;
    ImcExpr replaceWith;
    public Replacement(ImcExpr replace, ImcExpr replaceWith) {
        this.replace = replace;
        this.replaceWith = replaceWith;
    }
}