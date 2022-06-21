package rml2shacl.model.shex;

import rml2shacl.commons.IRI;
import rml2shacl.commons.Symbols;

public class ShapeExprRef extends ShapeExpr {
    private IRI shapeExprLabel;

    ShapeExprRef(IRI shapeExprLabel) {
        super(Kinds.shapeExprRef);
        this.shapeExprLabel = shapeExprLabel;
    }

    @Override
    public String getSerializedShapeExpr() { return Symbols.AT + shapeExprLabel.getPrefixedName(); }
}
