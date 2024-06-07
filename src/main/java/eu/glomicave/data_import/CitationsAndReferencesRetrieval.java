package eu.glomicave.data_import;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import eu.glomicave.persistence.SQLDatabase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class CitationsAndReferencesRetrieval {
	private static final Logger logger = LogManager.getLogger(CitationsAndReferencesRetrieval.class);

	private static int ABRIDGE_MAX_NUM = 3;
	
	private static int REFERENCE_BASED = 3;
	private static int CITATION_BASED = 4;
	
	public static void run(boolean abridge, int nrefs, int ncits) throws Exception {
		logger.info("Retrieve citations and references.");
		
		Connection connection = SQLDatabase.getConnection();
		Statement statement = connection.createStatement();
		ResultSet resultSet = statement.executeQuery("SELECT doi FROM publications;");

		List<String> dois = new LinkedList<>();
		while (resultSet.next()) {
			dois.add(resultSet.getString("doi"));
		}

		statement.close();

		logger.info("Found " + dois.size() + " DOIs for citation and reference retrieval.");

		boolean sleep = true;

		if (abridge) {
			dois = dois.subList(0, Math.min(ABRIDGE_MAX_NUM, dois.size()));
			logger.info("! Shortened pipeline. Only {} DOIs will be considered for reference and citation retrieval !", dois.size());
		}

		dois.forEach(doi -> {
			logger.info("Analysing '" + doi + "'...");
			try {

				JSONObject jsonObject = SemanticScholarAPI.getReferences(doi, sleep);

				if (jsonObject != null) {
					JSONArray jsonArray = jsonObject.getJSONArray("data");

					if (jsonArray != null) {
						logger.info("Found " + jsonArray.length() + " references.");

						if (abridge) {
							while (jsonArray.length() >= ABRIDGE_MAX_NUM) {
								jsonArray.remove(0);
							}
							logger.info("! Shortened pipeline. Only {} references will be added !", jsonArray.length());
						}
						
						if (nrefs >= 0) {
							while (jsonArray.length() >= nrefs) {
								jsonArray.remove(0);
							}
							logger.info("nrefs = {}. {} references will be added.", nrefs, jsonArray.length());
						}

						jsonArray.forEach(entry -> {
							JSONObject citedPaper = ((JSONObject) entry).getJSONObject("citedPaper");
							Object externalIds = citedPaper.get("externalIds");
							if (externalIds instanceof JSONObject && ((JSONObject) externalIds).has("DOI")) {
								String newDOI = ((JSONObject) externalIds).getString("DOI");
								SQLDatabase.insertDOI(newDOI, REFERENCE_BASED);
							}
						});
					}
				}

				else {
					logger.error("Could not retrieve publication with DOI: '" + doi + "'.");
				}

				jsonObject = SemanticScholarAPI.getCitations(doi, sleep);

				if (jsonObject != null) {
					JSONArray jsonArray = jsonObject.getJSONArray("data");

					if (jsonArray != null) {
						logger.info(" found " + jsonArray.length() + " citing papers");

						if (abridge) {
							while (jsonArray.length() >= 3) {
								jsonArray.remove(0);
							}
							logger.info("! Shortened pipeline. Only {} citing papers will be added !", jsonArray.length());
						}
						
						if (ncits >= 0) {
							while (jsonArray.length() >= ncits) {
								jsonArray.remove(0);
							}
							logger.info("ncits = {}. {} citing papers will be added.", ncits, jsonArray.length());
						}

						jsonArray.forEach(entry -> {
							JSONObject citingPaper = ((JSONObject) entry).getJSONObject("citingPaper");
							Object externalIds = citingPaper.get("externalIds");
							if (externalIds instanceof JSONObject && ((JSONObject) externalIds).has("DOI")) {
								String newDOI = ((JSONObject) externalIds).getString("DOI");
								SQLDatabase.insertDOI(newDOI, CITATION_BASED);
							}
						});
					}
				}

				else {
					logger.error("Could not retrieve publication with DOI: '" + doi + "'.");
				}

			} catch (Exception e) {
				e.printStackTrace();
				logger.error("Error while retrieving citations and references.", e);
			}
		});
	}
	
}

