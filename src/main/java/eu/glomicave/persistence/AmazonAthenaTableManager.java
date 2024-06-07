package eu.glomicave.persistence;

import java.lang.InterruptedException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import eu.glomicave.config.AmazonAthenaConfig;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.Row;
import software.amazon.awssdk.services.athena.paginators.GetQueryResultsIterable;
import software.amazon.awssdk.services.athena.model.AthenaException;
import software.amazon.awssdk.services.athena.model.Datum;


public class AmazonAthenaTableManager {
	private static final Logger logger = LogManager.getLogger(AmazonAthenaTableManager.class);
	
	// tables not to drop from the database
	public static final List<String> TABLES_TO_LEAVE = List.of("sampleiceberg");
	// indicate iceberg tables
	public static boolean icebergTable = false;
	
	
	/** Execute query. */
	public static void executeQuery(String query) {
		try {
			AmazonAthena.executeQuery(query);
		} catch (InterruptedException e) {
			e.printStackTrace();
			logger.error("Error executing query: " + query, e);
		}
	}
	
	/** Execute query and return results. */
	public static GetQueryResultsIterable executeQueryWithResults(String query) {
		try {
			String queryExecutionId = AmazonAthena.executeQuery(query);
			GetQueryResultsIterable resultsIterable = AmazonAthena.getQueryResults(queryExecutionId);
			return resultsIterable;
		} catch (InterruptedException e) {
			e.printStackTrace();
			logger.error("Error executing query: " + query, e);
		}
		return null;
	}
	
	/** Return only list of rows with query results */
	public static List<List<Datum>> executeQueryWithResultRows(String query) {
		try {
			GetQueryResultsIterable qResults = executeQueryWithResults(query);
			List<List<Datum>> resultRows = new ArrayList<>();
			boolean first = true; // first row is header
	        for (GetQueryResultsResponse result : qResults) {
			    for (Row row : result.resultSet().rows()) {
			    	if (first) {
			    		first = false;
			    	} else {
			    		resultRows.add(row.data());
		    		}
		    	}
	        }
	        return resultRows;
	        
	    } catch (AthenaException e) {
		    e.printStackTrace();
		    logger.error("Error executing query: " + query, e);
		}
		return null;
	}	
	
	/** Return values from a columns with a specified index. */
	public static List<String> executeQueryWithResultColumn(String query, int columnIdx) {
		try {
			GetQueryResultsIterable qResults = executeQueryWithResults(query);
			List<String> resultColumn = new ArrayList<>();
			boolean first = true; // first raw is header
	        for (GetQueryResultsResponse result : qResults) {
			    for (Row row : result.resultSet().rows()) {
			    	if (row.data().size() < columnIdx + 1) {
			    		System.out.println("Column index " + columnIdx + " in the query is out of range.");
			    		return null;
			    	} else if (first) {
			    		first = false;
			    	} else {
			    		resultColumn.add(row.data().get(columnIdx).varCharValue());
		    		}
		    	}
	        }
	        return resultColumn;
	        
	    } catch (AthenaException e) {
		    e.printStackTrace();
		    logger.error("Error executing query: " + query, e);
		}
		return null;
	}	

	/** Drop publication and sentence tables from Athena and clear related S3 data. 
	 * This assumes that all the data are stored in s3 via path common_s3_prefix_for_tables/table_name. */
	public static void dropTable(String tableName, boolean clearS3Data) {
		// delete tables from Athena
		logger.info("Table '" + tableName + "' will be dropped:");
		
		executeQuery(String.format("DROP TABLE IF EXISTS `%s`;", tableName));
		// clear related data files from S3 storage
		if (clearS3Data) {
			logger.info("Data from "+AmazonAthenaConfig.getInstance().getAthenaTablesS3Prefix()+tableName+" will be deleted");
			AmazonS3.deleteObjectsInDir(AmazonAthenaConfig.getInstance().getAthenaTablesS3Prefix()+tableName);	
		}
	}
	
	/** Drop all tables in the database, except listed in TABLES_TO_LEAVE. */
	public static void dropDatabase() {
		// delete all tables
		logger.info("Tables from "+AmazonAthenaConfig.getInstance().getDatabase()+"will be dropped.");
		
		GetQueryResultsIterable qResults = executeQueryWithResults(
				String.format("SHOW TABLES IN %s;", AmazonAthenaConfig.getInstance().getDatabase()));
		
		List<String> tablesToDrop = new ArrayList<>();
		boolean first = true; // first row is header
        for (GetQueryResultsResponse result : qResults) {
		    for (Row row : result.resultSet().rows()) {
		    	if (first) {
		    		first = false;
		    	} else {
					String objectKey = row.data().get(0).varCharValue();
					if (!tablesToDrop.contains(objectKey)) {
						tablesToDrop.add(objectKey);
					}
	    		}
	    	}
        }
        for (String table : tablesToDrop) {
        	executeQuery(String.format("DROP TABLE IF EXISTS `%s`;", table));
        }
        // clear data all files from S3 table-related storage
		//AmazonS3.deleteObjectsInDir(
		//		AmazonAthenaDatabaseConfig.getInstance().getAthenaS3Bucket()+"/"+AmazonAthenaDatabaseConfig.getInstance().getLayer()+"/");
	}
	
	/** Check if table is in Athena database. */
	public static boolean isTableInDatabase(String table, String database) {
		logger.info("Check if table '{}' exists in database '{}'", table, database);
		
		List<String> resultList = executeQueryWithResultColumn(
				String.format("SELECT COUNT(table_name) "
						+ "FROM information_schema.columns "
						+ "WHERE table_schema = '%s' AND table_name = '%s';", 
						database, table), 0);
		
		if (resultList.size() > 0 && Integer.valueOf(resultList.get(0)) > 0) {
			return true;
		} else {
			return false;
		}
	}
	
	/** Remove related table data from S3 bucket. */
	public static void dropRelatedS3Objects(String table) {
		Pattern pattern = Pattern.compile("^s3://([^/]+)/(.+)$");
		try {
			GetQueryResultsIterable qResults = executeQueryWithResults(String.format("SELECT \"$path\" FROM `%s`;", table));
			List<String> objectKeys = new ArrayList<>();
        	boolean first = true; // first row is header
	        for (GetQueryResultsResponse result : qResults) {
			    for (Row row : result.resultSet().rows()) {
			    	if (first) {
			    		first = false;
			    	} else {
			    		String objectURI = row.data().get(0).varCharValue();
			    		Matcher matcher = pattern.matcher(objectURI);
			    		if (matcher.matches()) {
			    			String objectKey = matcher.group(2);
			    			if (!objectKeys.contains(objectKey)) {
			    				objectKeys.add(objectKey);
			    			}	    	 
			    		}	    		      
			    	}
			    }
		    }
	        if (objectKeys.size() > 0) {
	        	AmazonS3.deleteBucketObjects(objectKeys);
	        }

		} catch (Exception e) {
			e.printStackTrace();
			logger.error("Error while dropping Amazon S3 data", e);
		}
	}

}
