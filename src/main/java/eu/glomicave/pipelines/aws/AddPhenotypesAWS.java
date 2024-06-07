package eu.glomicave.pipelines.aws;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import eu.glomicave.config.GlobalParamsConfig;
import eu.glomicave.config.AmazonS3Config;
import eu.glomicave.config.GraphDatabaseConfig;
import eu.glomicave.data_import.PublicationGraphDatabase;
import eu.glomicave.persistence.CoreGraphDatabase;
import eu.glomicave.wp3.IntegrateWP4Traits;

public class AddPhenotypesAWS {
	private static final Logger logger = LogManager.getLogger(AddPhenotypesAWS.class);

	public static void run(boolean abridge, String cfg_s3_file, String cfg_graphdb_file, String traits_s3_file) throws Exception {
		
		// assign parameters
		if (cfg_s3_file == null) {
			cfg_s3_file = GlobalParamsConfig.CFG_S3_FILE;
		}
		if (cfg_graphdb_file == null) {
			cfg_graphdb_file = GlobalParamsConfig.CFG_AWS_GRAPHDB_FILE;
		}
		if (traits_s3_file ==  null) {
			traits_s3_file = GlobalParamsConfig.S3_TRAITS_FILE;
		}
						
		if (abridge) {
			logger.info("!!! This is a shortened version of the 'Add traits' pipeline for test purposes!!!"
					+ "\nThe pipeline has been started with the following options: "
					+ "\n*** Section 1. Configuration options. ***"
					+ "\n -S3 storage config file: {};"
					+ "\n -GraphDB config file: {}. "
					+ "\n*** Section 2. Data ontologies. ***"
					+ "\n -Traits file: {}.",
					cfg_s3_file, cfg_graphdb_file, traits_s3_file);
		}
		else {
			logger.info("'Add traits' pipeline has been started with the following options: "
					+ "\n*** Section 1. Configuration options. ***"
					+ "\n -S3 storage config file: {};"
					+ "\n -GraphDB config file: {}. "
					+ "\n*** Section 2. Data ontologies. ***"
					+ "\n -Traits file: {}.",
					cfg_s3_file, cfg_graphdb_file, traits_s3_file);
		}
		
		// start the pipeline
		setupAmazonS3Connection(cfg_s3_file);
		
		setupDatabaseConnections(cfg_graphdb_file);
		
		loadPhenotypeData(abridge, traits_s3_file);

		createLinksToPublications(abridge);

		CoreGraphDatabase.closeDriver();
		
		logger.info("Pipeline finished!");
	}
	

	public static void setupAmazonS3Connection(String cfg_s3_file) {
		AmazonS3Config.setupInstance(cfg_s3_file);
	}
	
	public static void setupDatabaseConnections(String cfg_graphdb_file) {
		// Graph database connection:
		GraphDatabaseConfig.setupInstance(cfg_graphdb_file);
		// Test connection
		CoreGraphDatabase.testConnection();
	}
	
	public static void loadPhenotypeData(boolean abridge, String traits_s3_file) throws Exception {
		// Insert trait data from file:
		IntegrateWP4Traits.addTraitsFromS3CSV(abridge, traits_s3_file);
	}
	
	public static void createLinksToPublications(boolean abridge) throws Exception {
		// Initialize lexical form node maps
		PublicationGraphDatabase.initializeNewLexicalFormNodeMaps();
		
		// Link new lexical forms to sentence nodes
		PublicationGraphDatabase.connectNewLexicalFormsWithExistingSentenceNodes(abridge);
		
		// Re-create COOCCUR relation
		PublicationGraphDatabase.createCooccursWithRelations();
		
		// Re-create SYNONYM_WITH relation
		PublicationGraphDatabase.createSynonymWithRelations();
	}
}
