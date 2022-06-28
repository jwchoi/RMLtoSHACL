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
                setNodeKind(NodeKinds.BlankNode);
                return;
            }
        }

        setNodeKind(NodeKinds.IRI);
    }

    private void setNodeKind(NodeKinds nodeKind) {
        if (nodeKind != null) this.nodeKind = Optional.of(nodeKind);
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

    private String buildSerializedNodeShape(SubjectMap subjectMap) {
        StringBuffer buffer = new StringBuffer();

        String o; // to be used as objects of different RDF triples

        // sh:nodeKind
        NodeKinds nodeKind = getNodeKind();
        if (nodeKind != null) {
            o = nodeKind.equals(NodeKinds.BlankNode) ? "sh:BlankNode" : "sh:IRI";

            buffer.append(getPO("sh:nodeKind", o));
            buffer.append(getSNT());
        }

        // sh:class
        Set<URI> classIRIs = new TreeSet(subjectMap.getClassIRIs());
        for (URI classIRI: classIRIs) {
            o = getShaclDocModel().getRelativeIRIOr(classIRI.toString());

            buffer.append(getPO("sh:class", o));
            buffer.append(getSNT());
        }

        // sh:hasValue
        Optional<String> constant = subjectMap.getConstant();
        if (constant.isPresent()) {
            o = constant.get();
            if (nodeKind.equals(NodeKinds.IRI))
                o = getShaclDocModel().getRelativeIRIOr(o);


            buffer.append(getPO("sh:hasValue", o));
            buffer.append(getSNT());
        }

        // sh:pattern
        // only if rr:termType is rr:IRI
        if (nodeKind.equals(NodeKinds.IRI)) {
            Optional<String> regex = getRegexOnlyForPrint(subjectMap);
            if (regex.isPresent()) {
                o = regex.get();
                buffer.append(getPO("sh:pattern", o));
                buffer.append(getSNT());
            }
        }

        return buffer.toString();
    }

    private String buildSerializedNodeShape(Set<URI> nodeShapesOfSameSubject) {
        StringBuffer buffer = new StringBuffer();

        List<String> qualifiedValueShapes = new ArrayList<>();

        for (URI nodeShapeOfSameSubject: nodeShapesOfSameSubject) {
            String o = getShaclDocModel().getRelativeIRIOr(nodeShapeOfSameSubject.toString());
            qualifiedValueShapes.add(getUBN("sh:qualifiedValueShape", o));
        }

        if (qualifiedValueShapes.size() > 0) {
            buffer.append("sh:and" + Symbols.SPACE + Symbols.OPEN_PARENTHESIS + Symbols.NEWLINE);
            for (String qualifiedValueShape: qualifiedValueShapes)
                buffer.append(Symbols.TAB + Symbols.TAB + qualifiedValueShape + Symbols.NEWLINE);
            buffer.append(Symbols.TAB + Symbols.CLOSE_PARENTHESIS + Symbols.SPACE + Symbols.DOT + Symbols.NEWLINE);
        }

        return buffer.toString();
    }

    @Override
    public String toString() {
        String serializedNodeShape = getSerializedShape();
        if (serializedNodeShape != null) return serializedNodeShape;

        StringBuffer buffer = new StringBuffer();

        String o; // to be used as objects of different RDF triples

        String id = getShaclDocModel().getRelativeIRIOr(getId().toString());
        buffer.append(id);
        buffer.append(getNT());

        // rdf:type
        buffer.append(getPO(Symbols.A, "sh:NodeShape"));
        buffer.append(getSNT());

        switch (type) {
            case MAPPED:
            case INFERRED_AND:
                buffer.append(buildSerializedNodeShapeForR2RML());
                break;
            case INFERRED_OR:
                buffer.append(buildSerializedNodeShapeForDirectMapping());
        }

        serializedNodeShape = buffer.toString();
        setSerializedShape(serializedNodeShape);
        return serializedNodeShape;
    }

    private String buildSerializedNodeShapeForR2RML() {
        StringBuffer buffer = new StringBuffer();

        String o; // to be used as objects of different RDF triples

        switch (type) {
            case MAPPED:
                // if SubjectMap
                if (subjectMapOfMappedTriplesMap.isPresent())
                    buffer.append(buildSerializedNodeShape(subjectMapOfMappedTriplesMap.get()));

                break;

            case INFERRED_AND:
                if (nodeShapeIRIs.isPresent())
                    buffer.append(buildSerializedNodeShape(nodeShapeIRIs.get()));
        }

        if (type.equals(Type.MAPPED)) {
            // sh:property
            for (URI propertyShapeIRI : propertyShapes) {
                o = getShaclDocModel().getRelativeIRIOr(propertyShapeIRI.toString());
                buffer.append(getPO("sh:property", o));
                buffer.append(getSNT());
            }

            buffer.setLength(buffer.lastIndexOf(Symbols.SEMICOLON));
            buffer.append(getDNT());
        }

        return buffer.toString();
    }
}
