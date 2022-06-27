package rml2shacl.model.shacl;

import rml2shacl.commons.IRI;
import rml2shacl.commons.Symbols;
import rml2shacl.model.rml.Template;

import java.net.URI;
import java.util.Optional;

public abstract class Shape implements Comparable<Shape> {
    private Optional<IRI> id;

    private String serializedShape;

    Shape(IRI id) { this.id = Optional.ofNullable(id); }

    IRI getId() { return id.orElse(null); }

    protected String getSerializedShape() { return serializedShape; }
    protected void setSerializedShape(String serializedShape) { this.serializedShape = serializedShape; }

    @Override
    public int compareTo(Shape o) {
        if (id.isPresent() && o.id.isPresent()) return id.get().compareTo(o.id.get());

        if (id.isEmpty() && o.id.isEmpty()) return 0;

        return id.isPresent() ? 1 : -1;
    }

    protected boolean isPossibleToHavePattern(Optional<Template> template) {
        if (template.isPresent()) {
            if (template.get().getLengthExceptColumnName() > 0)
                return true;
        }

        return false;
    }

    // \n\t
    protected String getNT() { return Symbols.NEWLINE + Symbols.TAB; }
    // ;\n\t
    protected String getSNT() { return Symbols.SPACE + Symbols.SEMICOLON + Symbols.NEWLINE + Symbols.TAB; }
    // .\n\t
    protected String getDNT() { return Symbols.SPACE + Symbols.DOT + Symbols.NEWLINE + Symbols.TAB; }

    protected String getPO(String p, String o) { return p + Symbols.SPACE + o; }

    // Unlabeled Blank Node
    protected String getUBN(String p, String o) { return Symbols.OPEN_BRACKET + Symbols.SPACE + p + Symbols.SPACE + o + Symbols.SPACE + Symbols.CLOSE_BRACKET; }
}
