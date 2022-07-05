package rml2shacl;

import rml2shacl.datasource.Database;
import rml2shacl.processor.Rml2ShaclConverter;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

public class Rml2Shacl {
    private static final String PROPERTIES_FILE = "rml2shacl.properties";

    public static void main(String[] args) {
        Properties properties = loadPropertiesFile(PROPERTIES_FILE);

        if (properties == null) return;

        generateShaclFile(properties);
    }

    private static void generateShaclFile(Properties properties) {
        String rmlPathname = properties.getProperty("rml.pathname");
        String shaclPathname = properties.getProperty("shacl.pathname");
        String shaclBasePrefix = properties.getProperty("shacl.base.prefix");
        String shaclBaseIRI = properties.getProperty("shacl.base.iri");

        Rml2ShaclConverter converter;

        boolean useDataSource = Boolean.parseBoolean(properties.getProperty("useDataSource"));
        if (useDataSource) {
            String dataSourceFileDir = properties.getProperty("dataSource.file.dir");

            String dataSourceJdbcUrl = properties.getProperty("dataSource.jdbc.url");
            String dataSourceJdbcDriver = properties.getProperty("dataSource.jdbc.driver");
            String dataSourceJdbcUser = properties.getProperty("dataSource.jdbc.user");
            String dataSourceJdbcPassword = properties.getProperty("dataSource.jdbc.password");

            if (dataSourceJdbcUrl == null || dataSourceJdbcDriver == null || dataSourceJdbcUser == null || dataSourceJdbcPassword == null) {
                if (dataSourceFileDir != null)
                    converter = new Rml2ShaclConverter(dataSourceFileDir, rmlPathname, shaclPathname, shaclBasePrefix, shaclBaseIRI);
                else {
                    System.err.println("dataSource.file or some sub-properties of dataSource.jdbc are not specified.");
                    converter = new Rml2ShaclConverter(rmlPathname, shaclPathname, shaclBasePrefix, shaclBaseIRI);
                }
            } else {
                Database database = new Database(dataSourceJdbcUrl, dataSourceJdbcDriver, dataSourceJdbcUser, dataSourceJdbcPassword);
                if (dataSourceFileDir != null)
                    converter = new Rml2ShaclConverter(dataSourceFileDir, database, rmlPathname, shaclPathname, shaclBasePrefix, shaclBaseIRI);
                else
                    converter = new Rml2ShaclConverter(database, rmlPathname, shaclPathname, shaclBasePrefix, shaclBaseIRI);
            }
        }
        else {
            converter = new Rml2ShaclConverter(rmlPathname, shaclPathname, shaclBasePrefix, shaclBaseIRI);
        }

        try {
            File file = converter.generateShaclFile();
            System.out.println("SUCCESS: The SHACL file \"" + file.getCanonicalPath() + "\" is generated.");
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            System.err.println("ERROR: To Generate the SHACL file.");
        }
    }

    private static Properties loadPropertiesFile(String propertiesFile) {
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(propertiesFile));
            System.out.println("The properties file is loaded.");
        } catch (Exception ex) {
            System.err.println("ERROR: To Load the properties file (" + propertiesFile + ").");
            properties = null;
        }

        return properties;
    }
}