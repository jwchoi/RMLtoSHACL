package rml2shacl.processor;

import rml2shacl.datasource.DataSourceMetadataExtractor;
import rml2shacl.datasource.Database;
import rml2shacl.model.shacl.SHACLDocModel;
import rml2shacl.model.shacl.SHACLDocModelFactory;
import rml2shacl.model.shacl.Shape;
import rml2shacl.commons.Symbols;
import rml2shacl.model.rml.*;

import java.io.File;
import java.io.PrintWriter;
import java.net.URI;
import java.util.*;

public class Rml2ShaclConverter {
    private Optional<String> dataSourceDir;
    private Optional<Database> database;

    private String rmlPathname;
    private String shaclPathname;

    private String shaclBasePrefix;
    private URI shaclBaseIRI;

    private SHACLDocModel shaclDocModel;

    private File output;
    private PrintWriter writer;

    public Rml2ShaclConverter(String rmlPathname, String shaclPathname, String shaclBasePrefix, String shaclBaseIRI) {
        this.dataSourceDir = Optional.empty();
        this.database = Optional.empty();

        this.rmlPathname = rmlPathname;
        this.shaclPathname = shaclPathname;
        this.shaclBasePrefix = shaclBasePrefix;
        this.shaclBaseIRI = URI.create(shaclBaseIRI);
    }

    public Rml2ShaclConverter(String dataSourceDir, String rmlPathname, String shaclPathname, String shaclBasePrefix, String shaclBaseIRI) {
        this(rmlPathname, shaclPathname, shaclBasePrefix, shaclBaseIRI);
        this.dataSourceDir = Optional.of(dataSourceDir);
    }

    public Rml2ShaclConverter(Database database, String rmlPathname, String shaclPathname, String shaclBasePrefix, String shaclBaseIRI) {
        this(rmlPathname, shaclPathname, shaclBasePrefix, shaclBaseIRI);
        this.database = Optional.of(database);
    }

    public Rml2ShaclConverter(String dataSourceDir, Database database, String rmlPathname, String shaclPathname, String shaclBasePrefix, String shaclBaseIRI) {
        this(rmlPathname, shaclPathname, shaclBasePrefix, shaclBaseIRI);
        this.dataSourceDir = Optional.of(dataSourceDir);
        this.database = Optional.of(database);
    }

    private RMLParser getRMLParser() { return new RMLParser(rmlPathname, RMLParser.Lang.TTL); }

    private void writeDirectives() {
        // base
        writer.println(Symbols.AT + Symbols.base + Symbols.SPACE + Symbols.LT + shaclDocModel.getBaseIRI() + Symbols.GT + Symbols.SPACE + Symbols.DOT);

        // prefixes
        Set<Map.Entry<URI, String>> entrySet = shaclDocModel.getPrefixMap().entrySet();
        for (Map.Entry<URI, String> entry: entrySet)
            writer.println(Symbols.AT + Symbols.prefix + Symbols.SPACE + entry.getValue() + Symbols.COLON + Symbols.SPACE + Symbols.LT + entry.getKey() + Symbols.GT + Symbols.SPACE + Symbols.DOT);

        writer.println();
    }

    private void writeShacl() {
        Set<Shape> shapes = shaclDocModel.getShapes();

        shapes.stream().map(Shape::getSerializedShape).sorted().forEach(writer::println);
    }

    private void preProcess() {
        output = new File(shaclPathname);

        try {
            writer = new PrintWriter(output);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void postProcess() {
        try {
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public File generateShaclFile() throws Exception {
        RMLModel rmlModel = RMLModelFactory.getRMLModel(getRMLParser());
        if (dataSourceDir.isPresent() || database.isPresent()) DataSourceMetadataExtractor.acquireMetadataFor(rmlModel, dataSourceDir, database);
        shaclDocModel = SHACLDocModelFactory.getSHACLDocModel(rmlModel, shaclBasePrefix, shaclBaseIRI);

        preProcess();
        writeDirectives();
        writeShacl();
        postProcess();

        return output;
    }
}
