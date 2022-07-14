package rml2shacl.model.shacl;

import rml2shacl.commons.IRI;
import rml2shacl.commons.Symbols;
import rml2shacl.datasource.Column;
import rml2shacl.model.rml.SubjectMap;
import rml2shacl.model.rml.Template;
import rml2shacl.model.rml.TermMap;

import java.util.*;
import java.util.stream.Collectors;

public class NodeShape extends Shape {
    enum Type {SINGLE_MAPPED, MULTIPLE_MAPPED, INFERRED_OR}

    private Type type;

    //->MAPPED & MULTIPLE_MAPPED
    private Optional<NodeKinds> nodeKind; // sh:nodeKind
    private Set<IRI> classes; // sh:class
    private Optional<IRI> hasValue; //sh:hasValue
    private Optional<String> pattern; // sh:pattern
    private Set<IRI> propertyShapes;
    //<-MAPPED & MULTIPLE_MAPPED

    //->INFERRED_OR
    private Set<IRI> nodeShapeIRIs;
    //<-INFERRED_OR

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

        type = Type.SINGLE_MAPPED;
        convert(subjectMap);
    }

    NodeShape(IRI id, SubjectMap... subjectMaps) {
        this(id);

        type = Type.MULTIPLE_MAPPED;
        convert(subjectMaps);
    }

    NodeShape(IRI id, Set<IRI> nodeShapeIRIs) {
        this(id);

        type = Type.INFERRED_OR;
        this.nodeShapeIRIs.addAll(nodeShapeIRIs);
    }

    boolean isEquivalent(NodeShape other) {
        // nodeKind
        if (!nodeKind.equals(other.nodeKind)) return false;

        // pattern
        if (pattern.isPresent() && other.pattern.isPresent()) {
            if (!isEquivalentPattern(other.pattern)) return false;
        }

        // hasValue
        if (!hasValue.equals(other.hasValue)) return false;

        // hasValue ⊂ pattern
        if (hasValue.isPresent() && other.pattern.isPresent()) {
            if (!hasValue.get().toString().matches(other.pattern.get())) return false;
        }

        if (other.hasValue.isPresent() && pattern.isPresent()) {
            if (!other.hasValue.get().toString().matches(pattern.get())) return false;
        }

        return true;
    }

    private boolean isEquivalentPattern(Optional<String> pattern) {
        String normalizedThisPattern = this.pattern.get().replaceAll("\\(\\.\\{\\d+\\,\\}\\)", "(.*)");
        String normalizedOtherPattern = pattern.get().replaceAll("\\(\\.\\{\\d+\\,\\}\\)", "(.*)");

        return normalizedThisPattern.equals(normalizedOtherPattern);
    }

    private void convert(SubjectMap subjectMap) {
        setNodeKind(subjectMap); // sh:nodeKind
        setClasses(subjectMap); // sh:class
        setHasValue(subjectMap); // sh:hasValue
        setPattern(subjectMap); // sh:pattern
    }

    private void convert(SubjectMap... subjectMaps) {
        setNodeKind(Arrays.stream(subjectMaps).findAny().get()); // all nodeKinds are the same.
        Arrays.stream(subjectMaps).forEach(this::setClasses); // classes are all added.
        setPattern(subjectMaps);
        if (pattern.isEmpty() ) setHasValue(Arrays.stream(subjectMaps).findAny().get()); // if exists, all IRI constants are the same.
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

    private void setPattern(SubjectMap... subjectMaps) {
        // only if rr:termType is rr:IRI
        if (!nodeKind.get().equals(NodeKinds.IRI)) return;

        // filter non-null templates
        Set<Template> templates = Arrays.stream(subjectMaps)
                .map(SubjectMap::getTemplate)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());

        // find minLengths that mean maximum values of minLengths
        int[] minLengths = new int[templates.stream().findAny().get().getLogicalReferences().size()];

        for (Template template: templates) {
            List<Column> logicalReferences = template.getLogicalReferences();
            for (int i = 0; i < logicalReferences.size(); i++) {
                Column column = logicalReferences.get(i);
                int minLength = column.getMinLength().orElse(0);
                if (minLength > minLengths[i]) minLengths[i] = minLength;
            }
        }

        // build pattern string
        Template template = templates.stream().findAny().get();
        String format = template.getFormat();

        // logical references
        List<Column> logicalReferences = template.getLogicalReferences();
        for (int i = 0; i < logicalReferences.size(); i++) {
            Column column = logicalReferences.get(i);
            String columnName = column.getName();
            String quantifier = minLengths[i] > 0 ? "{" + minLengths[i] + ",}" : "*";
            format = format.replace("{" + columnName + "}", "(." + quantifier + ")");
        }

        pattern = Optional.of(Symbols.DOUBLE_QUOTATION_MARK + Symbols.CARET + format + Symbols.DOLLAR + Symbols.DOUBLE_QUOTATION_MARK);
    }

    private void setPattern(SubjectMap subjectMap) {
        // only if rr:termType is rr:IRI
        if (!nodeKind.get().equals(NodeKinds.IRI)) return;

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

    void addPropertyShape(IRI propertyShape) { propertyShapes.add(propertyShape); }

    private List<String> buildSerializedMappedNodeShape() {
        List<String> pos = new ArrayList<>();

        String o; // to be used as objects of different RDF triples

        // sh:closed
        o = "true";
        pos.add(getPO("sh:closed", o));

        // sh:nodeKind
        if (nodeKind.isPresent()) {
            o = nodeKind.get().equals(NodeKinds.BlankNode) ? "sh:BlankNode" : "sh:IRI";

            pos.add(getPO("sh:nodeKind", o));
        }

        // sh:class
        for (IRI cls: classes) {
            o = cls.getPrefixedNameOrElseAbsoluteIRI();

            pos.add(getPO("sh:class", o));
        }

        // sh:hasValue
        if (nodeKind.get().equals(NodeKinds.IRI) && hasValue.isPresent()) {
            o = hasValue.get().getPrefixedNameOrElseAbsoluteIRI();

            pos.add(getPO("sh:hasValue", o));
        }

        // sh:pattern
        // only if rr:termType is rr:IRI
        if (nodeKind.get().equals(NodeKinds.IRI) && pattern.isPresent()) {
            o = pattern.get();

            pos.add(getPO("sh:pattern", o));
        }

        // sh:property
        for (IRI propertyShape : propertyShapes) {
            o = propertyShape.getPrefixedNameOrElseAbsoluteIRI();
            pos.add(getPO("sh:property", o));
        }

        return pos;
    }

    private String buildSerializedInferredNodeShape() {
        String constraint = "sh:or";

        String delimiter = Symbols.NEWLINE;
        String shaclList = nodeShapeIRIs.stream()
                .map(IRI::getPrefixedNameOrElseAbsoluteIRI)
                .sorted()
                .collect(Collectors.joining(delimiter)).indent(4);

        return constraint + Symbols.SPACE + Symbols.OPEN_PARENTHESIS + Symbols.NEWLINE + shaclList + Symbols.CLOSE_PARENTHESIS;
    }

    @Override
    public String getSerializedShape() {
        StringBuffer sb = new StringBuffer(super.getSerializedShape());

        List<String> pos = new ArrayList<>();

        // rdf:type
        pos.add(getPO(Symbols.A, "sh:NodeShape"));

        switch (type) {
            case SINGLE_MAPPED, MULTIPLE_MAPPED -> pos.addAll(buildSerializedMappedNodeShape());
            case INFERRED_OR -> pos.add(buildSerializedInferredNodeShape());
        }

        String delimiter = Symbols.SPACE + Symbols.SEMICOLON + Symbols.NEWLINE;
        String prefix = Symbols.NEWLINE;
        String suffix = Symbols.SPACE + Symbols.DOT;
        String constraints = pos.stream().collect(Collectors.joining(delimiter, prefix, suffix)).indent(4);

        sb.append(constraints);

        return sb.toString();
    }
}
