package eu.glomicave.pipelines.local;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import eu.glomicave.config.GlobalParamsConfig;
import eu.glomicave.config.GraphDatabaseConfig;
import eu.glomicave.data_import.PublicationGraphDatabase;
import eu.glomicave.persistence.CoreGraphDatabase;
import eu.glomicave.wp3.IntegrateWP4Traits;

public class AddPhenotypesLocal {

	private static final Logger logger = LogManager.getLogger(AddPhenotypesLocal.class);

	public static void run(boolean abridge, String cfg_graphdb_file, String traits_file) throws Exception {
		
		// assign parameters
		if (cfg_graphdb_file == null) {
			cfg_graphdb_file = GlobalParamsConfig.CFG_GRAPHDB_FILE;
		}
		if (traits_file ==  null) {
			traits_file = GlobalParamsConfig.TRAITS_FILE;
		}
		
		if (abridge) {
			logger.info("!!! This is a shortened version of the 'Add traits' pipeline for test purposes!!!"
					+ "\nThe pipeline has been started with the following options: "
					+ "\n*** Section 1. Configuration options. ***"
					+ "\n -GraphDB config file: {}; "
					+ "\n*** Section 2. File with phenotype traits. ***"
					+ "\n -Traits file: {}.",
					cfg_graphdb_file, traits_file);
		}
		else {
			logger.info("'Add traits' pipeline has been started with the following options: "
					+ "\n*** Section 1. Configuration options. ***"
					+ "\n -GraphDB config file: {}; "
					+ "\n*** Section 2. File with phenotype traits. ***"
					+ "\n -Traits file: {}.",
					cfg_graphdb_file, traits_file);
		}
		
		// start the pipeline
		setupDatabaseConnections(cfg_graphdb_file);
		
		loadPhenotypeData(abridge, traits_file);

		createLinksToPublications(abridge);

		CoreGraphDatabase.closeDriver();
		
		logger.info("Pipeline finished!");
	}
	
	public static void setupDatabaseConnections(String cfg_graphdb_file) {
		// connection to Graph database
		GraphDatabaseConfig.setupInstance(cfg_graphdb_file);
		// Test connection
		CoreGraphDatabase.testConnection();
	}	
	
	public static void loadPhenotypeData(boolean abridge, String traits_file) throws Exception {
		// Insert trait data from file:
		IntegrateWP4Traits.addTraitsFromCSV(abridge, traits_file);
	}
	
	public static void createLinksToPublications(boolean abridge) throws Exception {
		// Initialize lexical form node maps
		PublicationGraphDatabase.initializeNewLexicalFormNodeMaps();
		
		// Create sentence nodes
		PublicationGraphDatabase.connectNewLexicalFormsWithExistingSentenceNodes(abridge);
		
		// Re-create COOCCUR relation
		PublicationGraphDatabase.createCooccursWithRelations();
		
		// Re-create SYNONYM_WITH relation
		PublicationGraphDatabase.createSynonymWithRelations();
	}
}
