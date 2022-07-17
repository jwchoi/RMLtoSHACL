package rml2shacl.model.shacl;

import rml2shacl.commons.IRI;
import rml2shacl.commons.Symbols;
import rml2shacl.model.rml.Template;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

public abstract class Shape implements Comparable<Shape> {
    private Optional<IRI> id;

    private Optional<NodeKinds> nodeKind; // sh:nodeKind

    private Optional<String> pattern; // sh:pattern

    Shape(IRI id) {
        this.id = Optional.ofNullable(id);

        nodeKind = Optional.empty();
        pattern = Optional.empty();
    }

    IRI getId() { return id.orElse(null); }
    void setId(IRI id) { this.id = Optional.ofNullable(id); }

    protected Optional<NodeKinds> getNodeKind() { return nodeKind; }
    protected void setNodeKind(Optional<NodeKinds> nodeKind) { this.nodeKind = nodeKind; }

    protected Optional<String> getPattern() { return pattern; }
    protected void setPattern(Optional<String> pattern) { this.pattern = pattern; }

    protected boolean isEquivalentPattern(Optional<String> pattern) {
        String normalizedThisPattern = this.pattern.get().replaceAll("\\(\\.\\{\\d+\\,\\}\\)", "(.*)");
        String normalizedOtherPattern = pattern.get().replaceAll("\\(\\.\\{\\d+\\,\\}\\)", "(.*)");

        return normalizedThisPattern.equals(normalizedOtherPattern);
    }

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

    abstract boolean isEquivalent(Shape other);

    protected String getPO(String p, String o) { return p + Symbols.SPACE + o; }

    // Unlabeled Blank Node
    protected String getUBN(String p, String o) { return Symbols.OPEN_BRACKET + Symbols.SPACE + p + Symbols.SPACE + o + Symbols.SPACE + Symbols.CLOSE_BRACKET; }
}
