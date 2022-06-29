package rml2shacl.model.shacl;

import rml2shacl.commons.IRI;
import rml2shacl.commons.Symbols;
import rml2shacl.datasource.Column;
import rml2shacl.model.rml.SubjectMap;
import rml2shacl.model.rml.Template;
import rml2shacl.model.rml.TermMap;

import java.net.URI;
import java.util.*;

public class NodeShape extends Shape {
    enum Type {MAPPED, INFERRED_AND, INFERRED_OR}

    private Type type;

    //->MAPPED
    private Optional<NodeKinds> nodeKind; // sh:nodeKind
    private Set<IRI> classes; // sh:class
    private Optional<IRI> hasValue; //sh:hasValue
    private Optional<String> pattern; // sh:pattern
    private Set<IRI> propertyShapes;
    //<-MAPPED

    //->INFERRED_OR & INFERRED_AND
    private Set<IRI> nodeShapeIRIs;
    //<-INFERRED_OR & INFERRED_AND

    private NodeShape(IRI id) {
        super(id);

        nodeKind = Optional.empty();
        classes = new TreeSet<>();
        hasValue = Optional.empty();
        pattern = Optional.empty();
        propertyShapes = new TreeSet<>();

        nodeShapeIRIs = new TreeSet<>();
    }

    NodeShape(IRI id, SubjectMap subjectMap) {
        this(id);

        type = Type.MAPPED;
        convert(subjectMap);
    }

    NodeShape(IRI id, Set<IRI> nodeShapeIRIs, Type type) {
        this(id);

        this.type = type;
        this.nodeShapeIRIs.addAll(nodeShapeIRIs);
    }

    boolean isEquivalent(NodeShape other) {
        if (!nodeKind.equals(other.nodeKind)) return false;

        if (!pattern.equals(other.pattern)) return false;

        return true;
    }

    private void convert(SubjectMap subjectMap) {
        setNodeKind(subjectMap); // sh:nodeKind
        setClasses(subjectMap); // sh:class
        setHasValue(subjectMap); // sh:hasValue
        setPattern(subjectMap); // sh:pattern
    }

    private void setNodeKind(SubjectMap subjectMap) {
        Optional<TermMap.TermTypes> termType = subjectMap.getTermType();

        if (termType.isPresent()) {
            if (termType.get().equals(TermMap.TermTypes.BLANKNODE)) {
                nodeKind = Optional.of(NodeKinds.BlankNode);
                return;
            }
        }

        nodeKind = Optional.of(NodeKinds.IRI);
    }

    private void setClasses(SubjectMap subjectMap) { classes.addAll(subjectMap.getClasses()); }

    private void setHasValue(SubjectMap subjectMap) { hasValue = subjectMap.getIRIConstant(); }

    private void setPattern(SubjectMap subjectMap) {
        // only if rr:termType is rr:IRI
        if (nodeKind.equals(NodeKinds.IRI)) {
            Optional<Template> template = subjectMap.getTemplate();
            if (template.isPresent()) {
                String format = template.get().getFormat();

                // logical references
                List<Column> logicalReferences = template.get().getLogicalReferences();
                for (Column logicalReference: logicalReferences) {
                    String columnName = logicalReference.getName();
                    String quantifier = logicalReference.getMinLength().isPresent() ? "{" + logicalReference.getMinLength().get() + ",}" : "*";
                    format = format.replace("{" + columnName + "}", "(." + quantifier + ")");
                }

                pattern = Optional.of(Symbols.DOUBLE_QUOTATION_MARK + Symbols.CARET + format + Symbols.DOLLAR + Symbols.DOUBLE_QUOTATION_MARK);
            }
        }
    }

    void addPropertyShape(IRI propertyShape) { propertyShapes.add(propertyShape); }

    private String buildSerializedMappedNodeShape() {
        StringBuffer sb = new StringBuffer();

        String o; // to be used as objects of different RDF triples

        // sh:nodeKind
        if (nodeKind.isPresent()) {
            o = nodeKind.get().equals(NodeKinds.BlankNode) ? "sh:BlankNode" : "sh:IRI";

            sb.append(getPO("sh:nodeKind", o));
            sb.append(getSNT());
        }

        // sh:class
        for (IRI cls: classes) {
            o = cls.getPrefixedNameOrElseAbsoluteIRI();

            sb.append(getPO("sh:class", o));
            sb.append(getSNT());
        }

        // sh:hasValue
        if (nodeKind.equals(NodeKinds.IRI) && hasValue.isPresent()) {
            o = hasValue.get().getPrefixedNameOrElseAbsoluteIRI();

            sb.append(getPO("sh:hasValue", o));
            sb.append(getSNT());
        }

        // sh:pattern
        // only if rr:termType is rr:IRI
        if (nodeKind.equals(NodeKinds.IRI) && pattern.isPresent()) {
            o = pattern.get();

            sb.append(getPO("sh:pattern", o));
            sb.append(getSNT());
        }

        // sh:property
        for (IRI propertyShape : propertyShapes) {
            o = propertyShape.getPrefixedNameOrElseAbsoluteIRI();
            sb.append(getPO("sh:property", o));
            sb.append(getSNT());
        }

        sb.setLength(sb.lastIndexOf(Symbols.SEMICOLON));
        sb.append(getDNT());

        return sb.toString();
    }

    private String buildSerializedInferredNodeShape(Type type) {
        StringBuffer sb = new StringBuffer();

        String condition = type.equals(Type.INFERRED_AND) ? "sh:and" : "sh:or";

        sb.append(condition + Symbols.SPACE + Symbols.OPEN_PARENTHESIS + Symbols.NEWLINE);
        for (IRI nodeShapeIRI: nodeShapeIRIs) {
            String o = nodeShapeIRI.getPrefixedNameOrElseAbsoluteIRI();
            sb.append(Symbols.TAB + Symbols.TAB + getUBN("sh:qualifiedValueShape", o) + Symbols.NEWLINE);
        }
        sb.append(Symbols.TAB + Symbols.CLOSE_PARENTHESIS + Symbols.SPACE + Symbols.DOT + Symbols.NEWLINE);

        return sb.toString();
    }

    @Override
    public String getSerializedShape() {
        StringBuffer sb = new StringBuffer(super.getSerializedShape());
        sb.append(getNT());

        // rdf:type
        sb.append(getPO(Symbols.A, "sh:NodeShape"));
        sb.append(getSNT());

        switch (type) {
            case MAPPED:
                sb.append(buildSerializedMappedNodeShape());
                break;
            case INFERRED_AND:
                sb.append(buildSerializedInferredNodeShape(Type.INFERRED_AND));
                break;
            case INFERRED_OR:
                sb.append(buildSerializedInferredNodeShape(Type.INFERRED_OR));
                break;
        }

        return sb.toString();
    }
}
