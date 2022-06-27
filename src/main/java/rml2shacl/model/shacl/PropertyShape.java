package rml2shacl.model.shacl;

import rml2shacl.commons.IRI;
import rml2shacl.commons.Symbols;
import rml2shacl.model.rml.*;

import java.net.URI;
import java.sql.ResultSetMetaData;
import java.util.Optional;

public class PropertyShape extends Shape {
    private boolean isInverse;

    private enum MappingTypes { OBJECT_MAP, REF_OBJECT_MAP, LITERAL_PROPERTY, REFERENCE_PROPERTY}

    private PredicateMap mappedPredicateMap;
    private Optional<ObjectMap> mappedObjectMap = Optional.empty();
    private Optional<RefObjectMap> mappedRefObjectMap = Optional.empty();

    private MappingTypes mappingType;

    private boolean hasQualifiedValueShape;

    private PropertyShape(IRI id) {
        super(id);
    }

    PropertyShape(IRI id, PredicateMap predicateMap, boolean isRepeated, ObjectMap objectMap, Optional<Long> minOccurs, Optional<Long> maxOccurs) {
        this(id);
       // convert(predicateMap, objectMap, minOccurs, maxOccurs);
    }

    PropertyShape(IRI id, PredicateMap predicateMap, boolean isRepeated, IRI referenceIdFromRefObjectMap, Optional<Long> minOccurs, Optional<Long> maxOccurs, boolean inverse) {
        this(id);
        //convert(predicateMap, referenceIdFromRefObjectMap, minOccurs, maxOccurs, inverse);
    }

    PropertyShape(IRI id, PredicateMap predicateMap, IRI referenceIdFromRefObjectMap, Optional<Long> minOccurs, Optional<Long> maxOccurs, boolean inverse) {
        this(id);
        //convert(predicateMap, referenceIdFromRefObjectMap, minOccurs, maxOccurs, inverse);
    }

    PropertyShape(URI id, PredicateMap mappedPredicateMap, ObjectMap mappedObjectMap, boolean hasQualifiedValueShape, SHACLDocModel shaclDocModel) {
        super(id, shaclDocModel);
        this.mappedPredicateMap = mappedPredicateMap;
        this.mappedObjectMap = Optional.of(mappedObjectMap);
        this.hasQualifiedValueShape = hasQualifiedValueShape;

        mappingType = MappingTypes.OBJECT_MAP;
    }

    PropertyShape(URI id, PredicateMap mappedPredicateMap, RefObjectMap mappedRefObjectMap, boolean hasQualifiedValueShape, SHACLDocModel shaclDocModel) {
        super(id, shaclDocModel);
        this.mappedPredicateMap = mappedPredicateMap;
        this.mappedRefObjectMap = Optional.of(mappedRefObjectMap);
        this.hasQualifiedValueShape = hasQualifiedValueShape;

        mappingType = MappingTypes.REF_OBJECT_MAP;
    }

    @Override
    public String toString() {
        String serializedPropertyShape = getSerializedShape();
        if (serializedPropertyShape == null) {
            serializedPropertyShape = buildSerializedPropertyShape();
            setSerializedShape(serializedPropertyShape);
        }

        return serializedPropertyShape;
    }

    private String buildSerializedPropertyShape(RefObjectMap mappedRefObjectMap) {
        StringBuffer buffer = new StringBuffer();

        String o; // to be used as objects of different RDF triples

        // sh:node
        URI parentTriplesMap = mappedRefObjectMap.getParentTriplesMap();
        NodeShape mappedNodeShape = getShaclDocModel().getMappedNodeShape(parentTriplesMap);
        o = getShaclDocModel().getRelativeIRIOr(mappedNodeShape.getId().toString());
        buffer.append(getPO("sh:node", o));
        buffer.append(getSNT());

        return buffer.toString();
    }

    private Optional<NodeKinds> getNodeKind() {
        if (mappedObjectMap.isPresent()) {
            Optional<TermMap.TermTypes> termType = mappedObjectMap.get().getTermType();
            if (termType.isPresent()) {
                switch (termType.get()) {
                    case BLANKNODE: return Optional.of(NodeKinds.BlankNode);
                    case IRI: return Optional.of(NodeKinds.IRI);
                    case LITERAL: return Optional.of(NodeKinds.Literal);
                }
            }
        }

        return Optional.empty();
    }

    private Optional<URI> getDatatype() {
        if (mappedObjectMap.isPresent()) {
            Optional<URI> datatype = mappedObjectMap.get().getDatatype();

            if (datatype.isPresent()) return Optional.of(datatype.get());

            boolean isBlankNodeOrIRI = false;
            Optional<NodeKinds> nodeKind = getNodeKind();
            if (nodeKind.isPresent()) {
                switch (nodeKind.get()) {
                    case BlankNode:
                    case IRI:
                        isBlankNodeOrIRI = true;
                }
            }

            // only if it's a Literal
            if (!isBlankNodeOrIRI) {
                // Natural Mapping of SQL Values
                Optional<SQLSelectField> sqlSelectField = mappedObjectMap.get().getColumn();
                if (sqlSelectField.isPresent())
                    return Optional.of(SqlXsdMap.getMappedXSD(sqlSelectField.get().getSqlType()).getURI());
            }
        }

        return Optional.empty();
    }

    private String buildSerializedPropertyShape(ObjectMap mappedObjectMap, boolean hasQualifiedValueShape) {
        StringBuffer buffer = new StringBuffer();

        String o; // to be used as objects of different RDF triples

        // sh:nodeKind
        Optional<NodeKinds> nodeKind = getNodeKind();
        if (nodeKind.isPresent()) {
            switch (nodeKind.get()) {
                case BlankNode: o = "sh:BlankNode"; break;
                case IRI: o = "sh:IRI"; break;
                case Literal: o = "sh:Literal"; break;
                default: o = null;
            }

            if (o != null) {
                buffer.append(getPO("sh:nodeKind", o));
                buffer.append(getSNT());
            }
        }

        // sh:in
        Optional<String> constant = mappedObjectMap.getConstant();
        if (constant.isPresent()) {
            o = constant.get();

            if (nodeKind.isPresent()) {
                switch (nodeKind.get()) {
                    case IRI: o = getShaclDocModel().getRelativeIRIOr(o); break;
                    case Literal: o = Symbols.DOUBLE_QUOTATION_MARK + o + Symbols.DOUBLE_QUOTATION_MARK; break;
                }
            }

            o = Symbols.OPEN_PARENTHESIS + Symbols.SPACE + o + Symbols.SPACE + Symbols.CLOSE_PARENTHESIS;

            if (hasQualifiedValueShape)
                buffer.append(getPO("sh:qualifiedValueShape", getUBN("sh:in", o)));
            else
                buffer.append(getPO("sh:in", o));

            buffer.append(getSNT());
        }

        // sh:languageIn
        Optional<String> language = mappedObjectMap.getLanguage();
        if (language.isPresent()) {
            o = Symbols.OPEN_PARENTHESIS + Symbols.SPACE + Symbols.DOUBLE_QUOTATION_MARK + language.get() + Symbols.DOUBLE_QUOTATION_MARK + Symbols.SPACE + Symbols.CLOSE_PARENTHESIS;
            buffer.append(getPO("sh:languageIn", o));
            buffer.append(getSNT());
        }

        // sh:datatype
        o = null;

        Optional<URI> datatype = getDatatype();
        if (language.isEmpty() && datatype.isPresent()) {
            o = getShaclDocModel().getRelativeIRIOr(datatype.get());

            if (o != null) {
                buffer.append(getPO("sh:datatype", o));
                buffer.append(getSNT());
            }
        }

        // sh:maxLength
        Optional<SQLSelectField> sqlSelectField = mappedObjectMap.getColumn();
        if (sqlSelectField.isPresent()) {
            if (nodeKind.isPresent() && nodeKind.get().equals(NodeKinds.Literal)) {
                if (language.isPresent() ||
                        (datatype.isPresent() && datatype.get().equals(XSDs.XSD_STRING.getURI()))) {
                    o = Integer.toString(sqlSelectField.get().getDisplaySize());

                    buffer.append(getPO("sh:maxLength", o));
                    buffer.append(getSNT());
                }
            }
        }

        // sh:pattern
        Optional<String> regex = getRegex(mappedObjectMap);
        if (regex.isPresent()) {
            o = regex.get();

            if (hasQualifiedValueShape)
                buffer.append(getPO("sh:qualifiedValueShape", getUBN("sh:pattern", o)));
            else
                buffer.append(getPO("sh:pattern", o));

            buffer.append(getSNT());
        }

        // cardinality
        o = "1";
        // cardinality: rr:column
        if (sqlSelectField.isPresent()) {
            switch (sqlSelectField.get().getNullable()) {
                case ResultSetMetaData.columnNoNulls:
                    // "at least one"
                    if (hasQualifiedValueShape)
                        buffer.append(getPO("sh:qualifiedMinCount", o));
                    else
                        buffer.append(getPO("sh:minCount", o));
                    buffer.append(getSNT());
            }
        }
        // cardinality: rr:template
        Optional<Template> template = mappedObjectMap.getTemplate();
        if (template.isPresent()) {
            List<SQLSelectField> columnNames = template.get().getColumnNames();
            boolean isEveryColumnNoNulls = true;
            for (SQLSelectField columnName: columnNames) {
                // Is a nullable column?
                if (columnName.getNullable() != ResultSetMetaData.columnNoNulls) {
                    isEveryColumnNoNulls = false;
                    break;
                }
            }
            // "at least one"
            if (isEveryColumnNoNulls) {
                if (hasQualifiedValueShape)
                    buffer.append(getPO("sh:qualifiedMinCount", o));
                else
                    buffer.append(getPO("sh:minCount", o));
                buffer.append(getSNT());
            }
        }
        // cardinality: rr:constant or rr:object
        if (constant.isPresent()) {
            // "exactly one"
            if (hasQualifiedValueShape)
                buffer.append(getPO("sh:qualifiedMinCount", o));
            else
                buffer.append(getPO("sh:minCount", o));
            buffer.append(getSNT());
            if (hasQualifiedValueShape)
                buffer.append(getPO("sh:qualifiedMaxCount", o));
            else
                buffer.append(getPO("sh:maxCount", o));
            buffer.append(getSNT());
        }

        // sh:qualifiedValueShapesDisjoint
        if (hasQualifiedValueShape) {
            buffer.append(getPO("sh:qualifiedValueShapesDisjoint", "true"));
            buffer.append(getSNT());
        }

        return buffer.toString();
    }

    private String buildSerializedPropertyShape() {
        StringBuffer buffer = new StringBuffer();

        String o; // to be used as objects of different RDF triples

        // rdf:type sh:PropertyShape
        String id = getShaclDocModel().getRelativeIRIOr(getId().toString());
        buffer.append(id);
        buffer.append(getNT());

        buffer.append(getPO(Symbols.A, "sh:PropertyShape"));
        buffer.append(getSNT());

        switch (mappingType) {
            case OBJECT_MAP:
            case REF_OBJECT_MAP:
                buffer.append(buildSerializedPropertyShapeForR2RML());
                break;
            case LITERAL_PROPERTY:
            case REFERENCE_PROPERTY:
                buffer.append(buildSerializedPropertyShapeForDirectMapping());
        }

        return buffer.toString();
    }

    private String buildSerializedPropertyShapeForR2RML() {
        StringBuffer buffer = new StringBuffer();

        String o; // to be used as objects of different RDF triples

        // sh:path
        o = getShaclDocModel().getRelativeIRIOr(mappedPredicateMap.getConstant().get());
        buffer.append(getPO("sh:path", o));
        buffer.append(getSNT());

        // if RefObjectMap
        if (mappingType.equals(MappingTypes.REF_OBJECT_MAP))
            buffer.append(buildSerializedPropertyShape(mappedRefObjectMap.get()));

        // if ObjectMap
        if (mappingType.equals(MappingTypes.OBJECT_MAP))
            buffer.append(buildSerializedPropertyShape(mappedObjectMap.get(), hasQualifiedValueShape));

        buffer.setLength(buffer.lastIndexOf(Symbols.SEMICOLON));
        buffer.append(getDNT());

        return buffer.toString();
    }

    private Optional<String> getRegex(ObjectMap objectMap) {
        Optional<Template> template = objectMap.getTemplate();

        if (!isPossibleToHavePattern(template)) return Optional.empty();

        String regex = template.get().getFormat();

        // column names
        List<SQLSelectField> columnNames = template.get().getColumnNames();
        for (SQLSelectField columnName: columnNames) {
            String replacement = "(.*)";

            Optional<NodeKinds> nodeKind = getNodeKind();
            if (nodeKind.isPresent() && nodeKind.get().equals(NodeKinds.Literal)) {
                int displaySize = columnName.getDisplaySize();
                replacement = "(.{0," + displaySize + "})";
            }

            regex = regex.replace("{" + columnName.getColumnNameOrAlias() + "}", replacement);
        }

        // because backslashes need to be escaped by a second backslash in the Turtle syntax,
        // a double backslash is needed to escape each curly brace,
        // and to get one literal backslash in the output one needs to write four backslashes in the template.
        regex = regex.replace("\\\\", "\\");

        regex = regex.replace("\\{", "{");
        regex = regex.replace("\\}", "}");

        return Optional.of(Symbols.DOUBLE_QUOTATION_MARK + Symbols.CARET + regex + Symbols.DOLLAR + Symbols.DOUBLE_QUOTATION_MARK);
    }
}