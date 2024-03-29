package rml2shacl.datasource;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import rml2shacl.model.rml.LogicalSource;
import rml2shacl.model.rml.LogicalTable;
import rml2shacl.model.rml.Source;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

class DataSourceFactory {
    static DataSource createDataSource(Session session, LogicalSource logicalSource, Optional<String> dataSourceDir) throws Exception {
        DataSource.DataSourceKinds dataSourceKind = detectDataSourceKind(logicalSource);

        switch(dataSourceKind) {
            case CSV: {
                String fileName = logicalSource.getSource().getSource().toString();
                Dataset<Row> df = session.loadCSV(dataSourceDir.orElseThrow(), fileName);
                if (df != null) return new DataSource(dataSourceKind, session, df);
                break;
            }
            case JSON: {
                String fileName = logicalSource.getSource().getSource().toString();
                String jsonPathExpression = logicalSource.getIterator();
                Dataset<Row> df = session.loadJSON(dataSourceDir.orElseThrow(), fileName, jsonPathExpression);
                if (df != null) return new HierarchicalDataSource(dataSourceKind, session, df, ".");
                break;
            }
            case XML: {
                String fileName = logicalSource.getSource().getSource().toString();
                String xPathExpression = logicalSource.getIterator();
                Dataset<Row> df = session.loadXML(dataSourceDir.orElseThrow(), fileName, xPathExpression);
                if (df != null) return new HierarchicalDataSource(dataSourceKind, session, df, "/");
                break;
            }
            case DATABASE: {
                Optional<Database> database = logicalSource.getSource().getDatabase();
                String tableName = logicalSource.getTableName();
                String query = logicalSource.getQuery();
                RelationalDataSource.Metadata metadata = new RelationalDataSource.Metadata(database.orElseThrow(), tableName, query);
                Dataset<Row> df = session.loadDatabase(database.orElseThrow(), metadata.getQuery());
                if (df != null) return new RelationalDataSource(dataSourceKind, session, df, metadata);
                break;
            }
        }

        return null;
    }

    private static DataSource.DataSourceKinds detectDataSourceKind(LogicalSource logicalSource) {
        Source source = logicalSource.getSource();
        URI referenceFormulation = logicalSource.getReferenceFormulation();
        String query = logicalSource.getQuery();
        Set<URI> sqlVersions = logicalSource.getSqlVersions();
        String tableName = logicalSource.getTableName();

        String sourceAsString = source.getSource().toString();

        if (source.getDatabase().isPresent() || sqlVersions.size() > 0 || query != null || tableName != null)
            return DataSource.DataSourceKinds.DATABASE;

        List<String> CSVExtensions = Arrays.asList(".csv", ".tsv", ".tab");
        if ((referenceFormulation != null && referenceFormulation.equals(URI.create("http://semweb.mmlab.be/ns/ql#CSV")))
                || CSVExtensions.contains(sourceAsString.substring(sourceAsString.lastIndexOf(".")).toLowerCase()))
            return DataSource.DataSourceKinds.CSV;

        if ((referenceFormulation != null && referenceFormulation.equals(URI.create("http://semweb.mmlab.be/ns/ql#JSONPath")))
                || sourceAsString.toLowerCase().endsWith(".json"))
            return DataSource.DataSourceKinds.JSON;

        if ((referenceFormulation != null && referenceFormulation.equals(URI.create("http://semweb.mmlab.be/ns/ql#XPath")))
                || sourceAsString.toLowerCase().endsWith(".xml"))
            return DataSource.DataSourceKinds.XML;

        return null;
    }

    static DataSource createDataSource(Session session, LogicalTable logicalTable, Optional<Database> database) throws Exception {
        String tableName = logicalTable.getTableName();
        String sqlQuery = logicalTable.getSqlQuery();
        RelationalDataSource.Metadata metadata = new RelationalDataSource.Metadata(database.orElseThrow(), tableName, sqlQuery);
        Dataset<Row> df = session.loadDatabase(database.orElseThrow(), metadata.getQuery());

        return df != null ? new RelationalDataSource(DataSource.DataSourceKinds.DATABASE, session, df, metadata) : null;
    }
}
