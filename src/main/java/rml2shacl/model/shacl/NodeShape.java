package rml2shacl.model.shacl;

import rml2shacl.commons.IRI;
import rml2shacl.commons.Symbols;
import rml2shacl.model.rml.SubjectMap;
import rml2shacl.model.rml.Template;
import rml2shacl.model.rml.TermMap;

import java.net.URI;
import java.util.*;

public class NodeShape extends Shape {

    private Optional<NodeKinds> nodeKind; // sh:nodeKind
    private Optional<String> pattern; // sh:pattern
    private Set<IRI> classes; // sh:class

    private enum MappingTypes { TRIPLES_MAP, NODE_SHAPES_OF_SAME_SUBJECTS, TABLE_IRI }

    private MappingTypes mappingType;

    private Optional<URI> mappedTriplesMap = Optional.empty(); // mapped rr:TriplesMap
    private Optional<SubjectMap> subjectMapOfMappedTriplesMap = Optional.empty();

    private Set<IRI> propertyShapes = new TreeSet<>();

    private Optional<Set<URI>> nodeShapesOfSameSubject = Optional.empty();

    private NodeShape(IRI id) {
        super(id);
    }

    NodeShape(IRI id, SubjectMap subjectMap) {
        this(id);
    }

    NodeShape(IRI id, Set<IRI> nodeShapesOfSameSubject) {
        this(id);
    }

    boolean isEquivalent(NodeShape other) {
        if (!nodeKind.equals(other.nodeKind)) return false;

        if (!pattern.equals(other.pattern)) return false;

        return true;
    }

    NodeShape(URI id, URI mappedTriplesMap, SubjectMap subjectMapOfMappedTriplesMap, SHACLDocModel shaclDocModel) {
        super(id, shaclDocModel);
        this.mappedTriplesMap = Optional.of(mappedTriplesMap);
        this.subjectMapOfMappedTriplesMap = Optional.of(subjectMapOfMappedTriplesMap);

        mappingType = MappingTypes.TRIPLES_MAP;
    }

    NodeShape(URI id, Set<URI> nodeShapesOfSameSubject, SHACLDocModel shaclDocModel) {
        super(id, shaclDocModel);
        this.nodeShapesOfSameSubject = Optional.of(nodeShapesOfSameSubject);

        mappingType = MappingTypes.NODE_SHAPES_OF_SAME_SUBJECTS;
    }

    public Set<IRI> getPropertyShapeIDs() { return propertyShapes; }

    Optional<URI> getMappedTriplesMap() { return mappedTriplesMap; }

    void addPropertyShape(IRI propertyShape) { propertyShapes.add(propertyShape); }

    NodeKinds getNodeKind() {
        if (subjectMapOfMappedTriplesMap.isPresent()) {
            Optional<TermMap.TermTypes> termType = subjectMapOfMappedTriplesMap.get().getTermType();
            if (termType.isPresent())
                return termType.get().equals(TermMap.TermTypes.BLANKNODE) ? NodeKinds.BlankNode : NodeKinds.IRI;
        }

        return null;
    }

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

        switch (mappingType) {
            case TRIPLES_MAP:
            case NODE_SHAPES_OF_SAME_SUBJECTS:
                buffer.append(buildSerializedNodeShapeForR2RML());
                break;
            case TABLE_IRI:
                buffer.append(buildSerializedNodeShapeForDirectMapping());
        }

        serializedNodeShape = buffer.toString();
        setSerializedShape(serializedNodeShape);
        return serializedNodeShape;
    }

    private String buildSerializedNodeShapeForR2RML() {
        StringBuffer buffer = new StringBuffer();

        String o; // to be used as objects of different RDF triples

        switch (mappingType) {
            case TRIPLES_MAP:
                // if SubjectMap
                if (subjectMapOfMappedTriplesMap.isPresent())
                    buffer.append(buildSerializedNodeShape(subjectMapOfMappedTriplesMap.get()));

                break;

            case NODE_SHAPES_OF_SAME_SUBJECTS:
                if (nodeShapesOfSameSubject.isPresent())
                    buffer.append(buildSerializedNodeShape(nodeShapesOfSameSubject.get()));
        }

        if (mappingType.equals(MappingTypes.TRIPLES_MAP)) {
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

    Optional<String> getRegex() {
        if (subjectMapOfMappedTriplesMap.isEmpty()) return Optional.empty();

        Optional<Template> template = subjectMapOfMappedTriplesMap.get().getTemplate();

        if (template.isEmpty()) return Optional.empty();

        String regex = template.get().getFormat();

        // column names
        List<SQLSelectField> columnNames = template.get().getColumnNames();
        for (SQLSelectField columnName: columnNames)
            regex = regex.replace("{" + columnName.getColumnNameOrAlias() + "}", "(.*)");

        return Optional.of(Symbols.DOUBLE_QUOTATION_MARK + Symbols.CARET + regex + Symbols.DOLLAR + Symbols.DOUBLE_QUOTATION_MARK);
    }

    private Optional<String> getRegexOnlyForPrint(SubjectMap subjectMap) {
        Optional<Template> template = subjectMap.getTemplate();

        if (!isPossibleToHavePattern(template)) return Optional.empty();

        return getRegex();
    }
}
