package rml2shacl.model.shacl;

import rml2shacl.commons.IRI;
import rml2shacl.commons.Symbols;
import rml2shacl.datasource.Column;
import rml2shacl.datasource.DataSource;
import rml2shacl.model.rml.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class PropertyShape extends Shape {
    private boolean inverse;
    private boolean isRepeatedProperty;

    private IRI path;

    //-> ObjectMap
    private Optional<NodeKinds> nodeKind; // sh:nodeKind

    // sh:hasValue -> either literalConstant or iriConstant
    private Optional<String> literalValue; // sh:hasValue
    private Optional<IRI> iriValue; // sh:hasValue

    private Optional<String> languageTag; // sh:languageIn
    private Optional<IRI> datatype; // sh:datatype
    private Optional<String> pattern; // sh:pattern
    private Optional<String> minInclusive; // sh:minInclusive
    private Optional<String> maxInclusive; // sh:maxInclusive
    private Optional<Integer> minLength; // sh:minLength
    private Optional<Integer> maxLength; // sh:maxLength
    //<- ObjectMap

    //-> RefObjectMap
    private IRI node; // sh:node
    //<- RefObjectMap

    private Optional<Long> minCount; // if empty, 0
    private Optional<Long> maxCount; // if empty, -1. && -1 is treated as unbounded

    private enum Type { OBJECT_MAP, REF_OBJECT_MAP }

    private Type type;

    private PropertyShape(IRI id) {
        super(id);

        nodeKind = Optional.empty();
        pattern = Optional.empty();
        minInclusive = Optional.empty();
        maxInclusive = Optional.empty();
        minLength = Optional.empty();
        maxLength = Optional.empty();
    }

    PropertyShape(IRI id, PredicateMap predicateMap, boolean isRepeated, ObjectMap objectMap, Optional<Long> minOccurs, Optional<Long> maxOccurs) {
        this(id);

        type = Type.OBJECT_MAP;

        convert(predicateMap, isRepeated, objectMap, minOccurs, maxOccurs);
    }

    PropertyShape(IRI id, PredicateMap predicateMap, boolean isRepeated, IRI referenceIdFromRefObjectMap, Optional<Long> minOccurs, Optional<Long> maxOccurs, boolean inverse) {
        this(id);

        type = Type.REF_OBJECT_MAP;

        convert(predicateMap, isRepeated, referenceIdFromRefObjectMap, minOccurs, maxOccurs, inverse);
    }

    PropertyShape(IRI id, PredicateMap predicateMap, IRI referenceIdFromRefObjectMap, Optional<Long> minOccurs, Optional<Long> maxOccurs, boolean inverse) {
        this(id);

        type = Type.REF_OBJECT_MAP;

        boolean isRepeated = false;
        convert(predicateMap, isRepeated, referenceIdFromRefObjectMap, minOccurs, maxOccurs, inverse);
    }

    private void convert(PredicateMap predicateMap, boolean isRepeated, IRI referenceIdFromRefObjectMap, Optional<Long> minOccurs, Optional<Long> maxOccurs, boolean inverse) {
        setInverse(inverse);
        setIsRepeatedProperty(isRepeated);

        // for predicate
        setPath(predicateMap);

        // for object
        setNode(referenceIdFromRefObjectMap);

        // for cardinality
        if (minOccurs.isPresent() && minOccurs.get() > 0) setMinCount(1);
        if (maxOccurs.isPresent()) {
            if (maxOccurs.get() == 0) setMaxCount(0);
            else if (maxOccurs.get() == 1) setMaxCount(1);
        }
    }

    private void convert(PredicateMap predicateMap, boolean isRepeated, ObjectMap objectMap, Optional<Long> minOccurs, Optional<Long> maxOccurs) {
        setInverse(false);
        setIsRepeatedProperty(isRepeated);

        // for predicate
        setPath(predicateMap);

        // for object
        convert(objectMap);

        // for cardinality
        setMinCount(objectMap, minOccurs);
        setMaxCount(objectMap, maxOccurs);
    }

    private void setIsRepeatedProperty(boolean isRepeatedProperty) { this.isRepeatedProperty = isRepeatedProperty; }

    private void setInverse(boolean inverse) { this.inverse = inverse; }

    private void setPath(PredicateMap predicateMap) { path = predicateMap.getIRIConstant().get(); }

    private void convert(ObjectMap objectMap) {
        setNodeKind(objectMap); // sh:nodeKind
        setHasValue(objectMap); // sh:hasValue
        setLanguageTag(objectMap); // sh:languageIn
        setDatatype(objectMap); // sh:datatype, must call after setNodeKind(), setHasValue(), setLanguageTag()
        setPattern(objectMap); // sh:pattern
        setValueRange(objectMap); // sh:minInclusive, sh:maxInclusive
        setStringLength(objectMap); // must call after setNodeKind()
    }

    private void setNodeKind(ObjectMap objectMap) {
        Optional<TermMap.TermTypes> termType = objectMap.getTermType();

        if (termType.isPresent()) {
            switch (termType.get()) {
                case IRI -> nodeKind = Optional.of(NodeKinds.IRI);
                case LITERAL -> nodeKind = Optional.of(NodeKinds.Literal);
                case BLANKNODE -> nodeKind = Optional.of(NodeKinds.BlankNode);
            }
        }
    }

    private void setHasValue(ObjectMap objectMap) {
        literalValue = objectMap.getLiteralConstant();
        iriValue = objectMap.getIRIConstant();
    }

    private void setLanguageTag(ObjectMap objectMap) {
        languageTag = objectMap.getLanguageMap().get().getLiteralConstant();
    }

    private void setDatatype(ObjectMap objectMap) {
        datatype = objectMap.getDatatype();

        // When R2RML
        // https://www.w3.org/TR/r2rml/#natural-mapping
        Optional<Column> column = objectMap.getColumn();
        if (column.isPresent()) {
            if (nodeKind.get().equals(NodeKinds.Literal) && literalValue.isEmpty() && languageTag.isEmpty() && datatype.isEmpty()) {
                datatype = column.get().getRdfDatatype();
            }
        }

        // When Database in RML
        Optional<Column> reference = objectMap.getReference();
        if (reference.isPresent() && reference.get().getDataSourceKind().isPresent() && reference.get().getDataSourceKind().get().equals(DataSource.DataSourceKinds.DATABASE)) {
            if (nodeKind.get().equals(NodeKinds.Literal) && literalValue.isEmpty() && languageTag.isEmpty() && datatype.isEmpty()) {
                datatype = reference.get().getRdfDatatype();
            }
        }
    }

    private void setPattern(ObjectMap objectMap) {
        Optional<Template> template = objectMap.getTemplate();
        if (template.isPresent()) {
            String format = template.get().getFormat();

            // logical references
            List<Column> logicalReferences = template.get().getLogicalReferences();
            for (Column logicalReference: logicalReferences) {
                String columnName = logicalReference.getName();
                String quantifier = logicalReference.getMinLength().isPresent() ? "{" + logicalReference.getMinLength().get() + ",}" : "*";
                format = format.replace("{" + columnName + "}", "(." + quantifier + ")");
            }

            // because backslashes need to be escaped by a second backslash in the Turtle syntax,
            // a double backslash is needed to escape each curly brace,
            // and to get one literal backslash in the output one needs to write four backslashes in the template.
            format = format.replace("\\\\", "\\");
            format = format.replace("\\{", "{");
            format = format.replace("\\}", "}");

            pattern = Optional.of(Symbols.DOUBLE_QUOTATION_MARK + Symbols.CARET + format + Symbols.DOLLAR + Symbols.DOUBLE_QUOTATION_MARK);
        }
    }

    private void setValueRange(ObjectMap objectMap) {
        Optional<Column> optionalColumn = objectMap.getColumn();
        Optional<Column> optionalReference = objectMap.getReference();
        if (optionalColumn.isPresent() || optionalReference.isPresent()) {
            Column column = optionalColumn.isPresent() ? optionalColumn.get() : optionalReference.get();

            if (datatype.isPresent() && isNumericRdfDatatype(datatype.get())) {
                minInclusive = column.getMinValue();
                maxInclusive = column.getMaxValue();
            }
        }
    }

    private boolean isNumericRdfDatatype(IRI rdfDatatype) {
        String localPart = rdfDatatype.getLocalPart().toLowerCase();
        List<String> numericTypes = Arrays.asList("byte", "short", "int", "long", "float", "double");
        return numericTypes.stream().filter(localPart::contains).count() > 0 ? true : false;
    }

    private void setStringLength(ObjectMap objectMap) {
        // sh:minLength and sh:maxLength are applied to  any literals and IRIs, but not to blank node in SHACL
        if (nodeKind.isPresent() && nodeKind.get().equals(NodeKinds.BlankNode)) return;

        Optional<Column> optionalColumn = objectMap.getColumn();
        Optional<Column> optionalReference = objectMap.getReference();
        if (optionalColumn.isPresent() || optionalReference.isPresent()) {
            Column column = optionalColumn.isPresent() ? optionalColumn.get() : optionalReference.get();

            if (column.getType().isPresent() && column.isStringType().isPresent() && column.isStringType().get()){ /* only if string type */
                minLength = column.getMinLength();
                maxLength = column.getMaxLength();
            }
        }
    }

    private void setNode(IRI node) { this.node = node; }

    private void setMinCount(ObjectMap objectMap, Optional<Long> minOccurs) {
        // the default cardinality
        setMinCount(0);

        Optional<IRI> iriConstant = objectMap.getIRIConstant();
        if (iriConstant.isPresent()) setMinCount(1);

        Optional<String> literalConstant = objectMap.getLiteralConstant();
        if (literalConstant.isPresent()) setMinCount(1);

        // when column, reference, template
        if (minOccurs.isPresent() && minOccurs.get() == 1) setMinCount(1);
    }

    private void setMaxCount(ObjectMap objectMap, Optional<Long> maxOccurs) {
        // the default cardinality
        setMaxCount(-1); // -1 means unbounded

        Optional<IRI> iriConstant = objectMap.getIRIConstant();
        if (iriConstant.isPresent()) setMaxCount(1);

        Optional<String> literalConstant = objectMap.getLiteralConstant();
        if (literalConstant.isPresent()) setMaxCount(1);

        // when column, reference, template
        if (maxOccurs.isPresent()) {
            if (maxOccurs.get() == 0L) setMaxCount(0);
            else if (maxOccurs.get() == 1L) setMaxCount(1);
        }
    }

    private void setMinCount(long minCount) { this.minCount = (minCount != 0) ? Optional.of(minCount) : Optional.empty(); }
    private void setMaxCount(long maxCount) { this.maxCount = (maxCount != -1) ? Optional.of(maxCount) : Optional.empty(); }

    private String buildSerializedPropertyShapeFromPredicateObjectMap() {
        StringBuilder sb = new StringBuilder();

        String o; // to be used as objects of different RDF triples

        // sh:path
        o = path.getPrefixedNameOrElseAbsoluteIRI();
        sb.append(getPO("sh:path", o));
        sb.append(getSNT());

        // sh:nodeKind
        if (nodeKind.isPresent()) {
            o = switch (nodeKind.get()) {
                case BlankNode -> "sh:BlankNode";
                case IRI -> "sh:IRI";
                case Literal -> "sh:Literal";
                default -> null;
            };

            if (o != null) {
                sb.append(getPO("sh:nodeKind", o));
                sb.append(getSNT());
            }
        }

        // sh:hasValue
        if (literalValue.isPresent() || iriValue.isPresent()) {
            o = "";

            if (literalValue.isPresent())
                o = Symbols.DOUBLE_QUOTATION_MARK + literalValue.get() + Symbols.DOUBLE_QUOTATION_MARK;

            if (iriValue.isPresent())
                o = iriValue.get().getPrefixedNameOrElseAbsoluteIRI();

            if (isRepeatedProperty)
                sb.append(getPO("sh:qualifiedValueShape", getUBN("sh:hasValue", o)));
            else
                sb.append(getPO("sh:in", o));

            sb.append(getSNT());
        }

        // sh:languageIn
        if (languageTag.isPresent()) {
            o = Symbols.OPEN_PARENTHESIS + Symbols.SPACE + Symbols.DOUBLE_QUOTATION_MARK + languageTag.get() + Symbols.DOUBLE_QUOTATION_MARK + Symbols.SPACE + Symbols.CLOSE_PARENTHESIS;
            sb.append(getPO("sh:languageIn", o));
            sb.append(getSNT());
        }

        // sh:datatype
        if (languageTag.isEmpty() && datatype.isPresent()) {
            o = datatype.get().getPrefixedNameOrElseAbsoluteIRI();

            sb.append(getPO("sh:datatype", o));
            sb.append(getSNT());
        }

        // sh:minLength
        if (minLength.isPresent()) {
            o = minLength.get().toString();

            sb.append(getPO("sh:minLength", o));
            sb.append(getSNT());
        }

        // sh:maxLength
        if (maxLength.isPresent()) {
            o = maxLength.get().toString();

            sb.append(getPO("sh:maxLength", o));
            sb.append(getSNT());
        }

        // sh:minInclusive
        if (minInclusive.isPresent()) {
            o = minInclusive.get();

            sb.append(getPO("sh:minInclusive", o));
            sb.append(getSNT());
        }

        // sh:maxInclusive
        if (maxInclusive.isPresent()) {
            o = maxInclusive.get();

            sb.append(getPO("sh:maxInclusive", o));
            sb.append(getSNT());
        }

        // sh:pattern
        if (pattern.isPresent()) {
            o = pattern.get();

            if (isRepeatedProperty)
                sb.append(getPO("sh:qualifiedValueShape", getUBN("sh:pattern", o)));
            else
                sb.append(getPO("sh:pattern", o));

            sb.append(getSNT());
        }

        // sh:minCount
        if (minCount.isPresent()) {
            o = minCount.get().toString();

            if (isRepeatedProperty)
                sb.append(getPO("sh:qualifiedMinCount", o));
            else
                sb.append(getPO("sh:minCount", o));

            sb.append(getSNT());
        }

        // sh:maxCount
        if (maxCount.isPresent()) {
            o = maxCount.get().toString();

            if (isRepeatedProperty)
                sb.append(getPO("sh:qualifiedMaxCount", o));
            else
                sb.append(getPO("sh:maxCount", o));

            sb.append(getSNT());
        }

        // sh:qualifiedValueShapesDisjoint
        if (isRepeatedProperty) {
            sb.append(getPO("sh:qualifiedValueShapesDisjoint", "true"));
            sb.append(getSNT());
        }

        return sb.toString();
    }

    private String buildSerializedPropertyShapeFromPredicateRefObjectMap() {
        StringBuffer buffer = new StringBuffer();

        String o; // to be used as objects of different RDF triples

        // sh:node
        o = node.getPrefixedNameOrElseAbsoluteIRI();
        buffer.append(getPO("sh:node", o));
        buffer.append(getSNT());

        return buffer.toString();
    }

    @Override
    public String getSerializedShape() {
        StringBuffer sb = new StringBuffer(super.getSerializedShape());
        sb.append(getNT());

        // rdf:type sh:PropertyShape
        sb.append(getPO(Symbols.A, "sh:PropertyShape"));
        sb.append(getSNT());

        switch (type) {
            case OBJECT_MAP -> sb.append(buildSerializedPropertyShapeFromPredicateObjectMap());
            case REF_OBJECT_MAP -> sb.append(buildSerializedPropertyShapeFromPredicateRefObjectMap());
        }

        sb.setLength(sb.lastIndexOf(Symbols.SEMICOLON));
        sb.append(getDNT());

        return sb.toString();
    }
}