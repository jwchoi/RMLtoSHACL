package rml2shacl.model.shacl;

import rml2shacl.commons.IRI;
import rml2shacl.commons.Symbols;
import rml2shacl.model.rml.Template;

import java.net.URI;
import java.util.Optional;

public abstract class Shape implements Comparable<Shape> {
    private Optional<IRI> id;

    Shape(IRI id) { this.id = Optional.ofNullable(id); }

    IRI getId() { return id.orElse(null); }

    public String getSerializedShape() {  return id.isPresent() ? id.get().getPrefixedName() : Symbols.EMPTY; }

    @Override
    public int compareTo(Shape o) {
        if (id.isPresent() && o.id.isPresent()) return id.get().compareTo(o.id.get());

        if (id.isEmpty() && o.id.isEmpty()) return 0;

        return id.isPresent() ? 1 : -1;
    }

    protected String getPO(String p, String o) { return p + Symbols.SPACE + o; }

    // Unlabeled Blank Node
    protected String getUBN(String p, String o) { return Symbols.OPEN_BRACKET + Symbols.SPACE + p + Symbols.SPACE + o + Symbols.SPACE + Symbols.CLOSE_BRACKET; }
}
