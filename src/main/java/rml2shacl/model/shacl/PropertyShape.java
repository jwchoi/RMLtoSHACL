package rml2shacl.model.shacl;

import rml2shacl.commons.IRI;
import rml2shacl.commons.Symbols;
import rml2shacl.datasource.Column;
import rml2shacl.datasource.DataSource;
import rml2shacl.model.rml.*;

import java.util.*;
import java.util.stream.Collectors;

public class PropertyShape extends Shape implements Cloneable {
    private boolean isRepeatedProperty;

    private IRI path;

    //-> ObjectMap
    // sh:hasValue -> either literalConstant or iriConstant
    private Optional<String> literalValue; // sh:hasValue
    private Optional<IRI> iriValue; // sh:hasValue

    private Optional<String> languageTag; // sh:languageIn
    private Optional<IRI> datatype; // sh:datatype
    private Optional<String> minInclusive; // sh:minInclusive
    private Optional<String> maxInclusive; // sh:maxInclusive
    private Optional<Integer> minLength; // sh:minLength
    private Optional<Integer> maxLength; // sh:maxLength
    //<- ObjectMap

    //-> RefObjectMap
    private Optional<IRI> node; // sh:node
    //<- RefObjectMap

    private Optional<Long> minCount; // if empty, 0
    private Optional<Long> maxCount; // if empty, -1. && -1 is treated as unbounded

    private enum Type {OBJECT_MAP, REF_OBJECT_MAP}

    private Type type;

    private PropertyShape(IRI id) {
        super(id);

        languageTag = Optional.empty();
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

    PropertyShape(IRI id, PredicateMap predicateMap, boolean isRepeated, IRI referenceIdFromRefObjectMap, Optional<Long> minOccurs, Optional<Long> maxOccurs) {
        this(id);

        type = Type.REF_OBJECT_MAP;

        convert(predicateMap, isRepeated, referenceIdFromRefObjectMap, minOccurs, maxOccurs);
    }

    private void convert(PredicateMap predicateMap, boolean isRepeated, IRI referenceIdFromRefObjectMap, Optional<Long> minOccurs, Optional<Long> maxOccurs) {
        setIsRepeatedProperty(isRepeated);

        // for predicate
        setPath(predicateMap);

        // for object
        setNode(referenceIdFromRefObjectMap);

        // for cardinality
        setMinCount(0); // by default
        setMaxCount(-1); // by default

        if (minOccurs.isPresent() && minOccurs.get() > 0) setMinCount(1);
        if (maxOccurs.isPresent()) {
            if (maxOccurs.get() == 0) setMaxCount(0);
            else if (maxOccurs.get() == 1) setMaxCount(1);
        }
    }

    private void convert(PredicateMap predicateMap, boolean isRepeated, ObjectMap objectMap, Optional<Long> minOccurs, Optional<Long> maxOccurs) {
        setIsRepeatedProperty(isRepeated);

        // for predicate
        setPath(predicateMap);

        // for object
        convert(objectMap);

        // for cardinality
        setMinCount(objectMap, minOccurs);
        setMaxCount(objectMap, maxOccurs);
    }

    boolean isRepeatedProperty() { return isRepeatedProperty; }

    void setIsRepeatedProperty(boolean isRepeatedProperty) {
        this.isRepeatedProperty = isRepeatedProperty;
    }

    private void setPath(PredicateMap predicateMap) {
        path = predicateMap.getIRIConstant().get();
    }

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
                case IRI -> setNodeKind(Optional.of(NodeKinds.IRI));
                case LITERAL -> setNodeKind(Optional.of(NodeKinds.Literal));
                case BLANKNODE -> setNodeKind(Optional.of(NodeKinds.BlankNode));
            }
        }
    }

    private void setHasValue(ObjectMap objectMap) {
        literalValue = objectMap.getLiteralConstant();
        iriValue = objectMap.getIRIConstant();
    }

    private void setLanguageTag(ObjectMap objectMap) {
        if (objectMap.getLanguageMap().isPresent())
            languageTag = objectMap.getLanguageMap().get().getLiteralConstant();
    }

    private void setDatatype(ObjectMap objectMap) {
        datatype = objectMap.getDatatype();

        // When R2RML
        // https://www.w3.org/TR/r2rml/#natural-mapping
        Optional<Column> column = objectMap.getColumn();
        if (column.isPresent()) {
            if (getNodeKind().get().equals(NodeKinds.Literal) && literalValue.isEmpty() && languageTag.isEmpty() && datatype.isEmpty()) {
                datatype = column.get().getRdfDatatype();
            }
        }

        // When Database in RML
        Optional<Column> reference = objectMap.getReference();
        if (reference.isPresent() && reference.get().getDataSourceKind().isPresent() && reference.get().getDataSourceKind().get().equals(DataSource.DataSourceKinds.DATABASE)) {
            if (getNodeKind().get().equals(NodeKinds.Literal) && literalValue.isEmpty() && languageTag.isEmpty() && datatype.isEmpty()) {
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
            for (Column logicalReference : logicalReferences) {
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

            setPattern(Optional.of(Symbols.DOUBLE_QUOTATION_MARK + Symbols.CARET + format + Symbols.DOLLAR + Symbols.DOUBLE_QUOTATION_MARK));
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
        Optional<NodeKinds> nodeKind = getNodeKind();
        if (nodeKind.isPresent() && nodeKind.get().equals(NodeKinds.BlankNode)) return;

        Optional<Column> optionalColumn = objectMap.getColumn();
        Optional<Column> optionalReference = objectMap.getReference();
        if (optionalColumn.isPresent() || optionalReference.isPresent()) {
            Column column = optionalColumn.isPresent() ? optionalColumn.get() : optionalReference.get();

            if (column.getType().isPresent() && column.isStringType().isPresent() && column.isStringType().get()) { /* only if string type */
                minLength = column.getMinLength();
                maxLength = column.getMaxLength();
            }
        }
    }

    private void setNode(IRI node) {
        this.node = Optional.ofNullable(node);
    }

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

    private void setMinCount(long minCount) {
        this.minCount = (minCount != 0) ? Optional.of(minCount) : Optional.empty();
    }

    private void setMaxCount(long maxCount) {
        this.maxCount = (maxCount != -1) ? Optional.of(maxCount) : Optional.empty();
    }

    private List<String> buildSerializedPropertyShapeFromPredicateObjectMap() {
        List<String> pos = new ArrayList<>();

        String o; // to be used as objects of different RDF triples

        // sh:nodeKind
        Optional<NodeKinds> nodeKind = getNodeKind();
        if (nodeKind.isPresent()) {
            o = switch (nodeKind.get()) {
                case BlankNode -> "sh:BlankNode";
                case IRI -> "sh:IRI";
                case Literal -> "sh:Literal";
                default -> null;
            };

            if (o != null) pos.add(getPO("sh:nodeKind", o));
        }

        // sh:hasValue
        if (literalValue.isPresent() || iriValue.isPresent()) {
            o = "";

            if (literalValue.isPresent())
                o = literalValue.get();

            if (iriValue.isPresent())
                o = iriValue.get().getPrefixedNameOrElseAbsoluteIRI();

            pos.add(getPO("sh:hasValue", o));
        }

        // sh:languageIn
        if (languageTag.isPresent()) {
            o = Symbols.OPEN_PARENTHESIS + Symbols.SPACE + Symbols.DOUBLE_QUOTATION_MARK + languageTag.get() + Symbols.DOUBLE_QUOTATION_MARK + Symbols.SPACE + Symbols.CLOSE_PARENTHESIS;
            pos.add(getPO("sh:languageIn", o));
        }

        // sh:datatype
        if (languageTag.isEmpty() && datatype.isPresent()) {
            o = datatype.get().getPrefixedNameOrElseAbsoluteIRI();

            pos.add(getPO("sh:datatype", o));
        }

        // sh:minLength
        if (minLength.isPresent()) {
            o = minLength.get().toString();

            pos.add(getPO("sh:minLength", o));
        }

        // sh:maxLength
        if (maxLength.isPresent()) {
            o = maxLength.get().toString();

            pos.add(getPO("sh:maxLength", o));
        }

        // sh:minInclusive
        if (minInclusive.isPresent()) {
            o = minInclusive.get();

            pos.add(getPO("sh:minInclusive", o));
        }

        // sh:maxInclusive
        if (maxInclusive.isPresent()) {
            o = maxInclusive.get();

            pos.add(getPO("sh:maxInclusive", o));
        }

        // sh:pattern
        Optional<String> pattern = getPattern();
        if (pattern.isPresent()) {
            o = pattern.get();

            pos.add(getPO("sh:pattern", o));
        }

        if (isRepeatedProperty) {
            String delimiter = Symbols.SPACE + Symbols.SEMICOLON + Symbols.NEWLINE;
            o = pos.stream().collect(Collectors.joining(delimiter)).indent(4);
            o = Symbols.OPEN_BRACKET + Symbols.NEWLINE + o + Symbols.CLOSE_BRACKET;

            return Arrays.asList(getPO("sh:qualifiedValueShape", o));
        } else return pos;
    }

    private List<String> buildSerializedPropertyShapeFromPredicateRefObjectMap() {
        List<String> pos = new ArrayList<>();

        String o; // to be used as objects of different RDF triples

        // sh:node
        if (node.isPresent()) {
            o = node.get().getPrefixedNameOrElseAbsoluteIRI();

            if (isRepeatedProperty)
                pos.add(getPO("sh:qualifiedValueShape", getUBN("sh:node", o)));
            else
                pos.add(getPO("sh:node", o));
        }

        return pos;
    }

    @Override
    public String getSerializedShape() {
        StringBuffer sb = new StringBuffer(super.getSerializedShape());

        List<String> pos = new ArrayList<>();

        String o; // to be used as objects of different RDF triples

        // rdf:type sh:PropertyShape
        o = "sh:PropertyShape";
        pos.add(getPO(Symbols.A, o));

        // sh:path
        o = path.getPrefixedNameOrElseAbsoluteIRI();
        pos.add(getPO("sh:path", o));

        switch (type) {
            case OBJECT_MAP -> pos.addAll(buildSerializedPropertyShapeFromPredicateObjectMap());
            case REF_OBJECT_MAP -> pos.addAll(buildSerializedPropertyShapeFromPredicateRefObjectMap());
        }

        // sh:minCount
        if (minCount.isPresent()) {
            o = minCount.get().toString();

            if (isRepeatedProperty)
                pos.add(getPO("sh:qualifiedMinCount", o));
            else
                pos.add(getPO("sh:minCount", o));
        }

        // sh:maxCount
        if (maxCount.isPresent()) {
            o = maxCount.get().toString();

            if (isRepeatedProperty)
                pos.add(getPO("sh:qualifiedMaxCount", o));
            else
                pos.add(getPO("sh:maxCount", o));
        }

        String delimiter = Symbols.SPACE + Symbols.SEMICOLON + Symbols.NEWLINE;
        String prefix = Symbols.NEWLINE;
        String suffix = Symbols.SPACE + Symbols.DOT;
        String constraints = pos.stream().collect(Collectors.joining(delimiter, prefix, suffix)).indent(4);

        sb.append(constraints);

        return sb.toString();
    }

    @Override
    protected PropertyShape clone() throws CloneNotSupportedException {
        return (PropertyShape) super.clone();
    }

    @Override
    boolean isEquivalent(Shape other) {
        if(!(other instanceof PropertyShape)) return false;

        PropertyShape that = (PropertyShape) other;

        boolean isEquivalent = Objects.equals(path, that.path)
                && Objects.equals(getNodeKind(), that.getNodeKind())
                && Objects.equals(literalValue, that.literalValue)
                && Objects.equals(iriValue, that.iriValue)
                && Objects.equals(languageTag, that.languageTag)
                && Objects.equals(datatype, that.datatype)
                && Objects.equals(minInclusive, that.minInclusive)
                && Objects.equals(maxInclusive, that.maxInclusive)
                && Objects.equals(minLength, that.minLength)
                && Objects.equals(maxLength, that.maxLength)
                && Objects.equals(node, that.node);

        if (!isEquivalent) return false;

        Optional<String> thisPattern = getPattern();
        Optional<String> thatPattern = that.getPattern();
        if (thisPattern.isEmpty() && thatPattern.isEmpty()) return true;
        if (thisPattern.isPresent() && thatPattern.isPresent()) return isEquivalentPattern(thatPattern);

        return false;
    }
}