package eu.glomicave.persistence;

import software.amazon.awssdk.services.athena.model.QueryExecutionContext;
import software.amazon.awssdk.services.athena.model.ResultConfiguration;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.AthenaException;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.GetQueryResultsRequest;
import software.amazon.awssdk.services.athena.model.ColumnInfo;
import software.amazon.awssdk.services.athena.model.Row;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.paginators.GetQueryResultsIterable;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.DeleteNamedQueryRequest;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import eu.glomicave.config.AmazonAthenaConfig;


public class AmazonAthena {
	private static Logger logger = LogManager.getLogger(AmazonAthena.class);
	
	private static AthenaClient athenaClient = null;
	public static final long SLEEP_AMOUNT_IN_MS = 1000;
	
	public static String executeQuery(String query) throws InterruptedException {
		try {
			athenaClient = getAthenaClient();
			
	        String queryExecutionId = submitAthenaQuery(athenaClient, query);
	        waitForQueryToComplete(athenaClient, queryExecutionId);
	        
	        return queryExecutionId;
		} catch (Exception e) {
			logger.error("Error executing query: " + query, e);
		}
		return null;
	}
        
	public static GetQueryResultsIterable getQueryResults(String queryExecutionId) throws InterruptedException {        
        GetQueryResultsIterable getQueryResultsResults = processResultRows(athenaClient, queryExecutionId);
        
        return getQueryResultsResults;
	}
	
	public static AthenaClient getAthenaClient() {
		if (athenaClient == null) {
			athenaClient = AmazonAthenaConfig.getInstance().getAthenaClient(); 
		}
		return athenaClient;
	}
	
	// Submits a sample query to Amazon Athena and returns the execution ID of the query.
	public static String submitAthenaQuery(AthenaClient athenaClient, String query) {
	      try {
	          // The QueryExecutionContext allows us to set the database.
	          QueryExecutionContext queryExecutionContext = QueryExecutionContext.builder()
	              .database(AmazonAthenaConfig.getInstance().getDatabase())
	              .build();
	
	          // The result configuration specifies where the results of the query should go.
	          ResultConfiguration resultConfiguration = ResultConfiguration.builder()
	              .outputLocation(AmazonAthenaConfig.getInstance().getOutputBucket())
	              .build();
	
	          StartQueryExecutionRequest startQueryExecutionRequest = StartQueryExecutionRequest.builder()
	              .queryString(query)
	              .queryExecutionContext(queryExecutionContext)
	              .resultConfiguration(resultConfiguration)
	              .build();
	
	          StartQueryExecutionResponse startQueryExecutionResponse = athenaClient.startQueryExecution(startQueryExecutionRequest);
	          return startQueryExecutionResponse.queryExecutionId();
	
	      } catch (AthenaException e) {
	          e.printStackTrace();
	          //System.exit(1);
	      }
	      return "";
    }

	// Wait for an Amazon Athena query to complete, fail or to be cancelled.
	public static void waitForQueryToComplete(AthenaClient athenaClient, String queryExecutionId) throws InterruptedException {
	      GetQueryExecutionRequest getQueryExecutionRequest = GetQueryExecutionRequest.builder()
	          .queryExecutionId(queryExecutionId)
	          .build();
	
	      GetQueryExecutionResponse getQueryExecutionResponse;
	      boolean isQueryStillRunning = true;
	      while (isQueryStillRunning) {
	          getQueryExecutionResponse = athenaClient.getQueryExecution(getQueryExecutionRequest);
	          String queryState = getQueryExecutionResponse.queryExecution().status().state().toString();
	          if (queryState.equals(QueryExecutionState.FAILED.toString())) {
	              throw new RuntimeException("The Amazon Athena query failed to run with error message: " + getQueryExecutionResponse
	                      .queryExecution().status().stateChangeReason());
	          } else if (queryState.equals(QueryExecutionState.CANCELLED.toString())) {
	              throw new RuntimeException("The Amazon Athena query was cancelled.");
	          } else if (queryState.equals(QueryExecutionState.SUCCEEDED.toString())) {
	              isQueryStillRunning = false;
	          } else {
	              // Sleep an amount of time before retrying again.
	              Thread.sleep(SLEEP_AMOUNT_IN_MS);
	          }
	          logger.debug("The current status is: " + queryState);
	          //System.out.println("The current status is: " + queryState);
	      }
	}

	// This code retrieves the results of a query
	public static GetQueryResultsIterable processResultRows(AthenaClient athenaClient, String queryExecutionId) {
	      try {
	
	          // Max Results can be set but if its not set,
	          // it will choose the maximum page size.
	          GetQueryResultsRequest getQueryResultsRequest = GetQueryResultsRequest.builder()
	              .queryExecutionId(queryExecutionId)
	              .build();
	
	          GetQueryResultsIterable getQueryResultsResults = athenaClient.getQueryResultsPaginator(getQueryResultsRequest);
	          // TBD: leave only for debug purposes, change the code to return only rows, use list1.addAll(list2)
//	          for (GetQueryResultsResponse result : getQueryResultsResults) {
//	              List<ColumnInfo> columnInfoList = result.resultSet().resultSetMetadata().columnInfo();
//	              List<Row> results = result.resultSet().rows();
//	              processRow(results, columnInfoList);
//	          }
	          return getQueryResultsResults;
	
	      } catch (AthenaException e) {
	         e.printStackTrace();
	         System.exit(1);
	      }
	      return null;
	}

	private static void processRow(List<Row> row, List<ColumnInfo> columnInfoList) {
	      for (Row myRow : row) {
	          List<Datum> allData = myRow.data();
	          for (Datum data : allData) {
	              System.out.println("The value of the column is "+data.varCharValue());
	          }
	      }
	}
	
	// delete request data after request executed
   public static void deleteQuery(String queryExecutionId) {
       try {
           DeleteNamedQueryRequest deleteNamedQueryRequest = DeleteNamedQueryRequest.builder()
               .namedQueryId(queryExecutionId)
               .build();

            athenaClient.deleteNamedQuery(deleteNamedQueryRequest);

       } catch (AthenaException e) {
           e.printStackTrace();
           System.exit(1);
       }
   }
    
}
