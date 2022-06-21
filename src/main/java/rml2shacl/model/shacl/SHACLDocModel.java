package rml2shacl.model.shacl;

import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class SHACLDocModel {
    private URI baseIRI;
    private String basePrefix;

    private Map<URI, String> prefixMap;

    private Set<Shape> shapes;

    SHACLDocModel(String basePrefix, URI baseIRI) {
        this.baseIRI = baseIRI;
        this.basePrefix = basePrefix;

        prefixMap = new TreeMap<>();
        prefixMap.put(baseIRI, basePrefix); // BASE

        shapes = new HashSet<>();
    }

    public Map<URI, String> getPrefixMap() { return prefixMap; }

    void addPrefixDecl(String prefix, String IRIString) {
        prefixMap.put(URI.create(IRIString), prefix);
    }

    public URI getBaseIRI() {
        return baseIRI;
    }

    public Set<Shape> getShapes() { return shapes; }
}
