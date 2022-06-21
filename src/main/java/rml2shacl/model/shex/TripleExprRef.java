package rml2shacl.model.shex;

import rml2shacl.commons.IRI;
import rml2shacl.commons.Symbols;

public class TripleExprRef extends TripleExpr {
    private IRI tripleExprLabel;

    TripleExprRef(IRI tripleExprLabel) {
        super(Kinds.tripleExprRef);
        this.tripleExprLabel = tripleExprLabel;
    }

    @Override
    public String getSerializedTripleExpr() { return Symbols.AMPERSAND + tripleExprLabel.getPrefixedName(); }
}
