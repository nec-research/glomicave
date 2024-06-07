package eu.glomicave.data_import;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import eu.glomicave.pipelines.aws.FullProcessingPipelineAWS;

public class SemanticScholarAPI {
	private static final Logger logger = LogManager.getLogger(SemanticScholarAPI.class);

	private static final String API_URL = "https://api.semanticscholar.org/graph/v1/paper/";
	private static final String STANDARD_FIELDS = "?fields=title,year,authors,abstract,referenceCount,citationCount,citations.paperId,references.paperId";

	// url string for batch requests, max 200-1000 paper a time
	private static final String API_URL_BATCH = "https://api.semanticscholar.org/graph/v1/paper/";
	
	private static int sleepTime = 100;

	private static void sleep() {
		// Sleep between two queries to not violate the limit of 100 queries per 5 minutes (i.e. 1 query / 3 seconds).
		try {
			Thread.sleep(sleepTime);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public static JSONObject querySemanticScholar(String doi, boolean sleep) throws Exception {
		if (sleep) {
			sleep();
		}

		URL url = new URL(API_URL + doi + STANDARD_FIELDS);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("GET");
		con.setRequestProperty("Content-Type", "application/json");

		int status = con.getResponseCode();

		if (status >= 300) {
			String response = readInputStream(con.getErrorStream());
			con.disconnect();

			//System.err.println(response);
			logger.error("Error executing query to SemanticScholar. Status {}: '{}'", status, response);
			return null;
		}

		else {
			String response = readInputStream(con.getInputStream());
			con.disconnect();
			if (response == null) {
				return null;
			}

			JSONObject jsonObject = new JSONObject(response);
			return jsonObject;
		}
	}
	
	/**
	 * Bath request to SemanticScholarAPI.
	 * 
	 * @param dois
	 * @param sleep
	 * @return
	 * @throws Exception
	 */
	public static JSONArray querySemanticScholarBatch(List<String>  dois, boolean sleep) throws Exception {
		if (sleep) {
			sleep();
		}
	
		// Create a JSON payload with the DOIs
        JSONObject payload = new JSONObject();
        JSONArray ids = new JSONArray(dois);
        payload.put("ids", ids);
        payload.put("format", "json");
        
		try {
	        // Create an HTTP post request with the payload
	        HttpPost post = new HttpPost(API_URL_BATCH);
	        StringEntity entity = new StringEntity(payload.toString());
	        post.setEntity(entity);
	        post.setHeader("Content-type", "application/json");
	        	        
	        // Send the request to the API and retrieve the response
	        
	        // send a JSON data
//	        post.setEntity(new StringEntity(entity.toString()));
//
//	        try (CloseableHttpClient httpClient = HttpClients.createDefault();
//	             CloseableHttpResponse response = httpClient.execute(post)) {
//
//	            result = EntityUtils.toString(response.getEntity());
//	        }
//	        
	        
	        HttpClient client = HttpClients.createDefault();
	        HttpEntity responseEntity = client.execute(post).getEntity();
	        
	        // Parse the response data as JSON
	        if (entity != null) {
		        String responseString = EntityUtils.toString(responseEntity);
		        JSONArray papers = new JSONArray(responseString);
		        
		        return papers;
	        }
	        
        } catch (IOException e) {
            e.printStackTrace();
        }
		
        return null;
	}
	

	/**
	 * Citations are papers that cite this paper (i.e. papers in whose bibliography this paper appears). 
	 * See https://api.semanticscholar.org/graph/v1 for details.
	 */
	public static JSONObject getCitations(String doi, boolean sleep) throws Exception {
		return getCitationsAndReferences(doi, "citations", sleep);
	}

	/**
	 * References are the papers cited by this paper (i.e. appearing in this paper's bibliography). 
	 * See https://api.semanticscholar.org/graph/v1 for details.
	 */
	public static JSONObject getReferences(String doi, boolean sleep) throws Exception {
		return getCitationsAndReferences(doi, "references", sleep);
	}

	private static JSONObject getCitationsAndReferences(String doi, String citationsReferences, boolean sleep) throws Exception {
		if (sleep) {
			sleep();
		}

		URL url = new URL(API_URL + doi + "/" + citationsReferences + "?fields=externalIds");
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("GET");
		con.setRequestProperty("Content-Type", "application/json");

		int status = con.getResponseCode();

		if (status >= 300) {
			String response = readInputStream(con.getErrorStream());
			con.disconnect();

			//System.err.println(response);
			logger.error("Error executing query to SemanticScholar. Status {}: '{}'", status, response);
			return null;
		}

		else {
			String response = readInputStream(con.getInputStream());
			con.disconnect();

			JSONObject jsonObject = new JSONObject(response);
			return jsonObject;
		}
	}

	public static String getDOI(String paperId, boolean sleep) throws Exception {
		if (sleep) {
			sleep();
		}

		URL url = new URL(API_URL + paperId + "?fields=externalIds");
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("GET");
		con.setRequestProperty("Content-Type", "application/json");

		int status = con.getResponseCode();

		if (status >= 300) {
			String response = readInputStream(con.getErrorStream());
			con.disconnect();

			//System.err.println(response);
			logger.error("Error executing query to SemanticScholar. Status {}: '{}'", status, response);
			return null;
		}

		else {
			String response = readInputStream(con.getInputStream());
			con.disconnect();

			JSONObject jsonObject = new JSONObject(response);

			if (!jsonObject.isNull("externalIds")) {
				JSONObject externalIdsObject = (JSONObject) jsonObject.get("externalIds");
				if (!externalIdsObject.isNull("DOI")) {
					String doi = (String) externalIdsObject.get("DOI");
					return doi;
				}
			}

			return null;
		}
	}

	private static String readInputStream(InputStream inputStream) throws IOException {
		return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
	}
}
