package rml2shacl.model.shacl;

import com.google.common.collect.Sets;
import rml2shacl.commons.IRI;
import rml2shacl.commons.Symbols;
import rml2shacl.model.rml.*;

import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class SHACLDocModelFactory {
    public static SHACLDocModel getSHACLDocModel(RMLModel rmlModel, String shaclBasePrefix, URI shaclBaseIRI) {
        SHACLDocModel shaclDocModel = new SHACLDocModel(shaclBasePrefix, shaclBaseIRI);

        addPrefixes(rmlModel, shaclDocModel);

        Map<TriplesMap, ConversionResult> tmcrMap = new HashMap<>();

        Set<TriplesMap> triplesMaps = rmlModel.getTriplesMaps();

        for (TriplesMap triplesMap : triplesMaps)
            tmcrMap.put(triplesMap, new ConversionResult());

        for (TriplesMap triplesMap : triplesMaps)
            convertSubjectMap2NodeShape(shaclBasePrefix, shaclBaseIRI, triplesMap, tmcrMap.get(triplesMap)); // subject map -> node shape

        for (TriplesMap triplesMap : triplesMaps)
            convertPredicateObjectMaps2PropertyShapes(shaclBasePrefix, shaclBaseIRI, triplesMap, tmcrMap.get(triplesMap)); // predicate-object map -> property shapes

        Set<Set<TriplesMap>> triplesMapGroup = groupTriplesMapWithSameSubject(tmcrMap);
        assignReferenceId(shaclBasePrefix, shaclBaseIRI, triplesMapGroup, tmcrMap);

        convertPredicateRefObjectMaps2PropertyShapes(shaclBasePrefix, shaclBaseIRI, tmcrMap); // predicate-referencing-object map -> property shapes

        assignPropertyShapesToNodeShape(tmcrMap.values());

        Set<Shape> inferredShapes = getInferredShapes(shaclBasePrefix, shaclBaseIRI, triplesMapGroup, tmcrMap);

        inferredShapes.stream().forEach(shaclDocModel::addShape);

        return shaclDocModel;
    }

    // register namespaces in rmlModel to SHACLDocModel
    private static void addPrefixes(RMLModel rmlModel, SHACLDocModel shaclDocModel) {
        Set<Map.Entry<String, String>> entrySet = rmlModel.getPrefixMap().entrySet();
        for (Map.Entry<String, String> entry : entrySet)
            shaclDocModel.addPrefixDecl(entry.getKey(), entry.getValue());

        shaclDocModel.addPrefixDecl("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        shaclDocModel.addPrefixDecl("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        shaclDocModel.addPrefixDecl("sh", "http://www.w3.org/ns/shacl#");
        shaclDocModel.addPrefixDecl("xsd", "http://www.w3.org/2001/XMLSchema#");
    }

    // subject map -> node shape
    private static void convertSubjectMap2NodeShape(String shaclBasePrefix, URI shaclBaseIRI, TriplesMap triplesMap, ConversionResult conversionResult) {
        String localPartPostfix = "Shape";
        IRI id = createNodeShapeID(shaclBasePrefix, shaclBaseIRI, localPartPostfix, Set.of(triplesMap));
        SubjectMap subjectMap = triplesMap.getSubjectMap();
        NodeShape sm2ns = new NodeShape(id, subjectMap);
        conversionResult.nodeShape = sm2ns;
    }

    private static Set<Set<TriplesMap>> groupTriplesMapWithSameSubject(Map<TriplesMap, ConversionResult> tcMap) {
        Map<NodeShape, TriplesMap> ntMap = new HashMap<>();

        Set<TriplesMap> triplesMaps = tcMap.keySet();
        for (TriplesMap triplesMap : triplesMaps) {
            ntMap.put(tcMap.get(triplesMap).nodeShape, triplesMap);
        }

        Set<NodeShape> nodeShapes = ntMap.keySet();
        
        Set<Set<NodeShape>> nsGroup = new HashSet<>();
        // build group
        for (NodeShape ns1 : nodeShapes) {
            Set<NodeShape> nsSubgroup = new HashSet<>();
            for (NodeShape ns2 : nodeShapes) {
                if (ns1.isEquivalent(ns2)) nsSubgroup.add(ns2);
            }
            nsGroup.add(nsSubgroup);
        }

        Set<Set<TriplesMap>> tmGroup = new HashSet<>();
        for (Set<NodeShape> nsSubgroup : nsGroup) {
            Set<TriplesMap> tmSubgroup = new HashSet<>();
            for (NodeShape ns : nsSubgroup) {
                tmSubgroup.add(ntMap.get(ns));
            }
            tmGroup.add(tmSubgroup);
        }

        return tmGroup;
    }

    private static void assignReferenceId(String shaclBasePrefix, URI shaclBaseIRI, Set<Set<TriplesMap>> tmGroup, Map<TriplesMap, ConversionResult> tmcrMap) {
        for (Set<TriplesMap> subgroup : tmGroup) {

            for (TriplesMap triplesMap : subgroup) {
                ConversionResult conversionResult = tmcrMap.get(triplesMap);

                if (subgroup.size() > 1) {
                    String localPartPostfix = "ShapeOr";
                    IRI referenceId = createNodeShapeID(shaclBasePrefix, shaclBaseIRI, localPartPostfix, Set.of(triplesMap));
                    conversionResult.referenceId4NodeShape = referenceId;
                } else
                    conversionResult.referenceId4NodeShape = conversionResult.nodeShape.getId();
            }
        }
    }

    private static IRI createNodeShapeID(String shaclBasePrefix, URI shaclBaseIRI, String localPartPostfix, Set<TriplesMap> triplesMaps) {
        String localPart = triplesMaps.stream()
                .map(TriplesMap::getUri)
                .map(SHACLDocModelFactory::extractFragmentOrLastPathSegmentOf)
                .sorted()
                .collect(Collectors.joining(Symbols.DASH, "", localPartPostfix));

        return new IRI(shaclBasePrefix, shaclBaseIRI, localPart);
    }

    private static String extractFragmentOrLastPathSegmentOf(URI uri) {
        String fragmentOrLastPathSegment = uri.getFragment();
        if (fragmentOrLastPathSegment == null) {
            Path path = Path.of(uri.getPath());
            fragmentOrLastPathSegment = path.getName(path.getNameCount()-1).toString();
        }

        return fragmentOrLastPathSegment;
    }

    // predicate object map -> property shape
    private static void convertPredicateObjectMaps2PropertyShapes(String shaclBasePrefix, URI shaclBaseIRI, TriplesMap triplesMap, ConversionResult conversionResult) {
        List<PredicateObjectMap> predicateObjectMaps = triplesMap.getPredicateObjectMaps();
        for (PredicateObjectMap predicateObjectMap : predicateObjectMaps) {
            List<PredicateObjectMap.PredicateObjectPair> predicateObjectPairs = predicateObjectMap.getPredicateObjectPairs();

            for (PredicateObjectMap.PredicateObjectPair predicateObjectPair : predicateObjectPairs) {
                PredicateMap predicateMap = predicateObjectPair.getPredicateMap();

                boolean isRepeatedPredicate = isRepeatedPredicate(Set.of(triplesMap), predicateMap);
                IRI propertyShapeID = createPropertyShapeID(shaclBasePrefix, shaclBaseIRI, predicateMap, isRepeatedPredicate, conversionResult, false);

                // when object map
                if (predicateObjectPair.getObjectMap().isPresent()) {
                    ObjectMap objectMap = predicateObjectPair.getObjectMap().get();

                    Optional<Long> minOccurs = predicateObjectPair.getMinOccurs();
                    Optional<Long> maxOccurs = predicateObjectPair.getMaxOccurs();

                    PropertyShape po2ps = new PropertyShape(propertyShapeID, predicateMap, isRepeatedPredicate, objectMap, minOccurs, maxOccurs);

                    conversionResult.propertyShapes.add(po2ps);
                    conversionResult.propertyShapePredicateObjectPairMap.put(po2ps, predicateObjectPair);
                }
            }
        }
    }

    private static boolean isRepeatedPredicate(Set<TriplesMap> triplesMaps, PredicateMap predicateMap) {
        int multiplicity = 0;

        for (TriplesMap triplesMap: triplesMaps) {
            List<PredicateObjectMap> predicateObjectMaps = triplesMap.getPredicateObjectMaps();
            for (PredicateObjectMap predicateObjectMap : predicateObjectMaps) {

                List<PredicateObjectMap.PredicateObjectPair> predicateObjectPairs = predicateObjectMap.getPredicateObjectPairs();
                for (PredicateObjectMap.PredicateObjectPair predicateObjectPair : predicateObjectPairs) {

                    PredicateMap other = predicateObjectPair.getPredicateMap();
                    if (predicateMap.getIRIConstant().equals(other.getIRIConstant())) {
                        multiplicity++;
                        if (multiplicity > 1) return true;
                    }
                }
            }
        }

        return false;
    }

    private static IRI createPropertyShapeID(String shaclBasePrefix, URI shaclBaseIRI, PredicateMap predicateMap, boolean isRepeated, ConversionResult conversionResult, boolean isInverse) {
        IRI predicateIRI = predicateMap.getIRIConstant().get();

        String localPart = predicateIRI.getLocalPart();
        if (localPart == null)
            localPart = extractFragmentOrLastPathSegmentOf(URI.create(predicateIRI.toString()));

        String prefixLabelOfPredicateIRI = predicateIRI.getPrefixLabel();
        if (prefixLabelOfPredicateIRI != null)
            localPart = prefixLabelOfPredicateIRI + Symbols.DASH + localPart;

        localPart = conversionResult.nodeShape.getId().getLocalPart() + Symbols.DASH + localPart;

        if (isRepeated) {
            String tempLocalPart;

            Set<String> existingLocalParts = conversionResult.propertyShapes.stream().map(PropertyShape::getId).map(IRI::getLocalPart).collect(Collectors.toSet());

            int index = 0;
            do {
                index++;
                tempLocalPart = localPart + Symbols.DASH + "q" + index;
            } while (existingLocalParts.contains(tempLocalPart));

            localPart = tempLocalPart;
        }

        localPart = isInverse ? localPart + Symbols.DASH + "inverse" : localPart;

        return new IRI(shaclBasePrefix, shaclBaseIRI, localPart);
    }

    // predicate referencing object map -> property shapes
    private static void convertPredicateRefObjectMaps2PropertyShapes(String shaclBasePrefix, URI shaclBaseIRI, Map<TriplesMap, ConversionResult> tmcrMap) {
        Set<TriplesMap> triplesMaps = tmcrMap.keySet();

        for (TriplesMap triplesMap: triplesMaps) {
            ConversionResult conversionResult = tmcrMap.get(triplesMap);

            List<PredicateObjectMap> predicateObjectMaps = triplesMap.getPredicateObjectMaps();
            for (PredicateObjectMap predicateObjectMap : predicateObjectMaps) {
                List<PredicateObjectMap.PredicateObjectPair> predicateObjectPairs = predicateObjectMap.getPredicateObjectPairs();

                for (PredicateObjectMap.PredicateObjectPair predicateObjectPair : predicateObjectPairs) {
                    PredicateMap predicateMap = predicateObjectPair.getPredicateMap();

                    boolean isRepeatedPredicate = isRepeatedPredicate(Set.of(triplesMap), predicateMap);

                    // when referencing object map
                    if (predicateObjectPair.getRefObjectMap().isPresent()) {
                        RefObjectMap refObjectMap = predicateObjectPair.getRefObjectMap().get();
                        URI uriOfParentTriplesMap = refObjectMap.getParentTriplesMap();
                        IRI referenceIdFromParentTriplesMap = getReferenceIdFromTriplesMap(uriOfParentTriplesMap, tmcrMap);

                        Optional<Long> minOccurs = predicateObjectPair.getMinOccurs();
                        Optional<Long> maxOccurs = predicateObjectPair.getMaxOccurs();

                        IRI propertyShapeID = createPropertyShapeID(shaclBasePrefix, shaclBaseIRI, predicateMap, isRepeatedPredicate, conversionResult, false);

                        PropertyShape pr2ps = new PropertyShape(propertyShapeID, predicateMap, isRepeatedPredicate, referenceIdFromParentTriplesMap, minOccurs, maxOccurs, false);

                        conversionResult.propertyShapes.add(pr2ps);
                        conversionResult.propertyShapePredicateObjectPairMap.put(pr2ps, predicateObjectPair);

                        // for inverse
                        URI uriOfChildTriplesMap = triplesMap.getUri();
                        IRI referenceIdFromChildTriplesMap = getReferenceIdFromTriplesMap(uriOfChildTriplesMap, tmcrMap);

                        Optional<Long> inverseMinOccurs = predicateObjectPair.getInverseMinOccurs();
                        Optional<Long> inverseMaxOccurs = predicateObjectPair.getInverseMaxOccurs();

                        IRI inversePropertyShapeID = createPropertyShapeID(shaclBasePrefix, shaclBaseIRI, predicateMap, isRepeatedPredicate, conversionResult, true);

                        PropertyShape pr2InversePs = new PropertyShape(inversePropertyShapeID, predicateMap, referenceIdFromChildTriplesMap, inverseMinOccurs, inverseMaxOccurs, true);

                        TriplesMap parentTriplesMap = triplesMaps.stream().filter(tm -> tm.getUri().equals(uriOfParentTriplesMap)).findAny().get();
                        ConversionResult conversionResultCorrespondingToParentTriplesMap = tmcrMap.get(parentTriplesMap);
                        conversionResultCorrespondingToParentTriplesMap.propertyShapes.add(pr2InversePs);
                    }
                }
            }

        }
    }

    private static IRI getReferenceIdFromTriplesMap(URI uriOfTriplesMap, Map<TriplesMap, ConversionResult> tmcrMap) {
        Set<TriplesMap> triplesMaps = tmcrMap.keySet();

        TriplesMap foundTriplesMap = triplesMaps.stream()
                .filter(triplesMap -> triplesMap.getUri().equals(uriOfTriplesMap))
                .findFirst()
                .get();

        ConversionResult conversionResult = tmcrMap.get(foundTriplesMap);

        return conversionResult.referenceId4NodeShape;
    }

    private static void assignPropertyShapesToNodeShape(Collection<ConversionResult> conversionResults) {
        for (ConversionResult conversionResult: conversionResults) {
            conversionResult.propertyShapes.stream()
                    .map(PropertyShape::getId)
                    .forEach(conversionResult.nodeShape::addPropertyShape);
        }
    }

    private static Set<Shape> getInferredShapes(String shaclBasePrefix, URI shaclBaseIRI, Set<Set<TriplesMap>> triplesMapGroup, Map<TriplesMap, ConversionResult> tmcrMap) {
        Set<Shape> inferredShapes = new TreeSet<>();

        for (Set<TriplesMap> triplesMapSubgroup: triplesMapGroup) {

            int n = triplesMapSubgroup.size();
            
            if (n == 1) {
                TriplesMap triplesMap = triplesMapSubgroup.stream().findAny().get();
                ConversionResult conversionResult = tmcrMap.get(triplesMap);

                inferredShapes.addAll(conversionResult.propertyShapes); // property shapes
                inferredShapes.add(conversionResult.nodeShape); // node shape
                continue;
            }

            Map<TriplesMap, Set<IRI>> nodeShapeIdsInferredFromTriplesMap = new HashMap<>(); // nodeShape + shapeAnds Per TriplesMap
            triplesMapSubgroup.stream().forEach(triplesMap -> nodeShapeIdsInferredFromTriplesMap.put(triplesMap, new TreeSet<>()));

            for (int r = 1; r <= n; r++) {
                Set<Set<TriplesMap>> combinations = Sets.combinations(triplesMapSubgroup, r);

                for (Set<TriplesMap> combination: combinations) {
                    if (r == 1) {
                        TriplesMap triplesMap = combination.stream().findAny().get();
                        ConversionResult conversionResult = tmcrMap.get(triplesMap);

                        nodeShapeIdsInferredFromTriplesMap.get(triplesMap).add(conversionResult.nodeShape.getId());

                        inferredShapes.addAll(conversionResult.propertyShapes); // property shapes
                        inferredShapes.add(conversionResult.nodeShape); // node shape
                    } else {
                        IRI id = createNodeShapeID(shaclBasePrefix, shaclBaseIRI, "ShapeAnd", combination);

                        NodeShape shapeAnd = new NodeShape(id, combination.stream().map(TriplesMap::getSubjectMap).toArray(SubjectMap[]::new)); // create ShapeAnd
                        Set<PropertyShape> propertyShapes = getPropertyShapesForShapeAnd(shaclBasePrefix, shaclBaseIRI, combination, tmcrMap); // create PropertyShapes for ShapeAnd
                        propertyShapes.stream().map(PropertyShape::getId).forEach(shapeAnd::addPropertyShape); // assign PropertyShapes to ShapeAnd

                        inferredShapes.add(shapeAnd); // add ShapeAnd to inferredShapes
                        propertyShapes.stream().forEach(inferredShapes::add); // add PropertyShapes to inferredShapes

                        combination.stream().forEach(triplesMap -> nodeShapeIdsInferredFromTriplesMap.get(triplesMap).add(id)); // add ShapeAnd's id for ShapeOr
                    }
                }
            }

            for (TriplesMap triplesMap: triplesMapSubgroup) {
                ConversionResult conversionResult = tmcrMap.get(triplesMap);
                IRI referenceId = conversionResult.referenceId4NodeShape;
                Set<IRI> ids = nodeShapeIdsInferredFromTriplesMap.get(triplesMap).stream().collect(Collectors.toSet());
                NodeShape shapeOr = new NodeShape(referenceId, ids);

                inferredShapes.add(shapeOr);
            }
        }

        return inferredShapes;
    }

    private static Set<PropertyShape> getPropertyShapesForShapeAnd(String shaclBasePrefix, URI shaclBaseIRI, Set<TriplesMap> triplesMaps, Map<TriplesMap, ConversionResult> tmcrMap) {
        Set<PropertyShape> propertyShapes = new TreeSet<>();

        for (TriplesMap triplesMap: triplesMaps) {
            ConversionResult conversionResult = tmcrMap.get(triplesMap);
            for (PropertyShape existingPropertyShape: conversionResult.propertyShapes) {
                if (existingPropertyShape.getInverse() || existingPropertyShape.isRepeatedProperty()) {
                    propertyShapes.add(existingPropertyShape);
                    continue;
                }

                PredicateMap predicateMap = conversionResult.propertyShapePredicateObjectPairMap.get(existingPropertyShape).getPredicateMap();
                boolean isRepeatedPredicate = isRepeatedPredicate(triplesMaps, predicateMap);
                if (isRepeatedPredicate) {
                    IRI propertyShapeID = createPropertyShapeID(shaclBasePrefix, shaclBaseIRI, predicateMap, true, conversionResult, false);

                    try {
                        PropertyShape newPropertyShape = existingPropertyShape.clone();
                        newPropertyShape.setId(propertyShapeID);
                        newPropertyShape.setIsRepeatedProperty(true);

                        propertyShapes.add(newPropertyShape);
                    } catch (CloneNotSupportedException e) {
                        System.err.println("Failed clone of PropertyShape");
                    }
                }
            }
        }

        return propertyShapes;
    }

    private static class ConversionResult {
        private NodeShape nodeShape; // from the subject map
        private Set<PropertyShape> propertyShapes = new TreeSet<>(); // from predicate object maps
        private Map<PropertyShape, PredicateObjectMap.PredicateObjectPair> propertyShapePredicateObjectPairMap = new HashMap<>();
        private IRI referenceId4NodeShape; // if (groupSize > 1) id of inferredNodeShapeId(sh:or with sh:and) if (groupSize == 1) convertedNodeShapeId
    }
}
