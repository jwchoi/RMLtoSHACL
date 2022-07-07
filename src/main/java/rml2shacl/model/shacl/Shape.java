package rml2shacl.model.shacl;

import rml2shacl.commons.IRI;
import rml2shacl.commons.Symbols;
import rml2shacl.model.rml.Template;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

public abstract class Shape implements Comparable<Shape> {
    private Optional<IRI> id;

    Shape(IRI id) { this.id = Optional.ofNullable(id); }

    IRI getId() { return id.orElse(null); }
    void setId(IRI id) { this.id = Optional.ofNullable(id); }

    public String getSerializedShape() {  return id.isPresent() ? id.get().getPrefixedName() : Symbols.EMPTY; }

    @Override
    public int compareTo(Shape o) {
        if (id.isPresent() && o.id.isPresent()) return id.get().compareTo(o.id.get());

        if (id.isEmpty() && o.id.isEmpty()) return 0;

        return id.isPresent() ? 1 : -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Shape)) return false;
        Shape shape = (Shape) o;
        return getId().equals(shape.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    protected String getPO(String p, String o) { return p + Symbols.SPACE + o; }

    // Unlabeled Blank Node
    protected String getUBN(String p, String o) { return Symbols.OPEN_BRACKET + Symbols.SPACE + p + Symbols.SPACE + o + Symbols.SPACE + Symbols.CLOSE_BRACKET; }
}
