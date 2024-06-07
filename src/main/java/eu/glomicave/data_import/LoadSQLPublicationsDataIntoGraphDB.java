package eu.glomicave.data_import;

import java.util.List;

import eu.glomicave.persistence.CoreGraphDatabase;
import eu.glomicave.persistence.PredefinedCategories;
import eu.glomicave.persistence.PredefinedRelations;
import eu.glomicave.persistence.SQLDatabase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class LoadSQLPublicationsDataIntoGraphDB {
	private static final Logger logger = LogManager.getLogger(LoadSQLPublicationsDataIntoGraphDB.class);
	
	private static final int ABRIDGE_MAX_NUM = 10;

	public static void integrateSQLPublicationsIntoGraphDB(boolean abridge) throws Exception {
		List<String> dois = SQLDatabase.getAllDOIs();
		
		logger.info("Integrating publication data into graph database. ");
		logger.info("{} publications retrieved from SQL database.", dois.size());

		if (abridge) {
			dois = dois.subList(0, Math.min(ABRIDGE_MAX_NUM, dois.size()));
			logger.info("! Shortened pipeline. {} publications will be processed !", dois.size());
		}

		int processed = 0;
		for (String doi : dois) {
			integrateSQLPublicationIntoGraphDB(doi);
			logger.info(++processed + " / " + dois.size() + " publications processed.");
		}
	}

	private static Publication integrateSQLPublicationIntoGraphDB(String doi) throws Exception {
		Publication publication = new Publication();
		publication.sqlId = SQLDatabase.getPublicationId(doi);
		publication.doi = doi;
		publication.paperAbstract = SQLDatabase.getAbstract(publication.sqlId);
		if (publication.paperAbstract != null && publication.paperAbstract.length() != 0) {
			PublicationGraphDatabase.createPublicationNodeWithProperties(publication);
		} else {
			logger.info("Publication '{}' containt no abstract text, skipped.", doi);
		}

		return publication;
	}

	public static void createCooccursWithRelations() {
		logger.info("Creating COUCCUR_WITH relations in graph database.");
		
		CoreGraphDatabase.runCypherQuery("MATCH (lf1:" + PredefinedCategories.LEXICAL_FORM.toString() + ")-[r1:" + PredefinedRelations.APPEARS_IN.toString() + "]->(s:" + PredefinedCategories.SENTENCE.toString() + ")<-[r2:" + PredefinedRelations.APPEARS_IN.toString() + "]-(lf2:" + PredefinedCategories.LEXICAL_FORM.toString() + ") WHERE lf1<>lf2 MERGE (lf1)-[c:" + PredefinedRelations.COOCCURS_WITH.toString() + "]->(lf2)");
	}
}
