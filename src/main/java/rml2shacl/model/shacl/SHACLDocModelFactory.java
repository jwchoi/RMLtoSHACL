package rml2shacl.model.shacl;

import rml2shacl.model.rml.RMLModel;

import java.net.URI;
import java.util.Map;
import java.util.Set;

public class SHACLDocModelFactory {
    public static SHACLDocModel getSHACLDocModel(RMLModel rmlModel, String shaclBasePrefix, URI shaclBaseIRI) {
        SHACLDocModel shaclDocModel = new SHACLDocModel(shaclBasePrefix, shaclBaseIRI);

        addPrefixes(rmlModel, shaclDocModel);

        return shaclDocModel;
    }

    private static void addPrefixes(RMLModel rmlModel, SHACLDocModel shaclDocModel) {
        Set<Map.Entry<String, String>> entrySet = rmlModel.getPrefixMap().entrySet();
        for (Map.Entry<String, String> entry : entrySet)
            shaclDocModel.addPrefixDecl(entry.getKey(), entry.getValue());

        shaclDocModel.addPrefixDecl("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        shaclDocModel.addPrefixDecl("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        shaclDocModel.addPrefixDecl("sh", "http://www.w3.org/ns/shacl#");
        shaclDocModel.addPrefixDecl("xsd", "http://www.w3.org/2001/XMLSchema#");
    }
}
