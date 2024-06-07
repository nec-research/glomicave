package eu.glomicave.data_import;

import java.util.List;

import org.json.JSONObject;
import eu.glomicave.persistence.SQLDatabase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class TitleAndAbstractRetrieval {
	private static final Logger logger = LogManager.getLogger(TitleAndAbstractRetrieval.class);
	
	public static void run() {
		List<String> dois = SQLDatabase.getNewDOIs();

		logger.info("Found " + dois.size() + " new DOIs to retrieve title and abstract.");

		for (int doiIndex = 0; doiIndex < dois.size(); doiIndex++) {
			String doi = dois.get(doiIndex);
			try {
				JSONObject jsonObject = SemanticScholarAPI.querySemanticScholar(doi, true);

				if (jsonObject != null) {
					String title = null;
					String abs = null;

					if (!jsonObject.isNull("title")) {
						title = (String) jsonObject.get("title");
					}

					if (!jsonObject.isNull("abstract")) {
						abs = (String) jsonObject.get("abstract");
					}

					SQLDatabase.updateTitleAndAbstract(doi, title, abs);
					logger.info((doiIndex + 1) + " / " + dois.size() + " titles and abstracts retrieved via SemanticScholar API.");
				}

				else {
					logger.error("Could not retrieve title and abstract for publication '" + doi + "'.");
				}

			} catch (Exception e) {
				e.printStackTrace();
				logger.error("Error while retrieving publication data", e);
			}
		}
	}
}

