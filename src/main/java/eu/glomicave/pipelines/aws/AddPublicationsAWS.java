/**
/* This pipeline adds new publication data into the current version of the publication database, 
/* writes new data into a new CSV file and puts it into the Athena folder with a new suffix '_part_x'
 */

package eu.glomicave.pipelines.aws;


import eu.glomicave.config.GlobalParamsConfig;
import eu.glomicave.config.AmazonAthenaConfig;
import eu.glomicave.config.AmazonS3Config;
import eu.glomicave.config.GraphDatabaseConfig;
import eu.glomicave.data_import.LoadAthenaPublicationsDataIntoGraphDB;
import eu.glomicave.data_import.PublicationGraphDatabase;
import eu.glomicave.persistence.CoreGraphDatabase;
import eu.glomicave.persistence.PublicationDatabase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

 
public class AddPublicationsAWS {
	private static final Logger logger = LogManager.getLogger(AddPublicationsAWS.class);
	
	public static void run(boolean abridge, boolean integrateOnly, String cfg_s3_file, String cfg_sqldb_file, String cfg_graphdb_file, 
										String dois_file, int nrefs, int ncits) throws Exception {
		
		// assign parameters
		if (cfg_s3_file == null) {
			cfg_s3_file = GlobalParamsConfig.CFG_S3_FILE;
		}
		if (cfg_sqldb_file == null) {
			cfg_sqldb_file = GlobalParamsConfig.CFG_SQLDB_FILE;
		}
		if (cfg_graphdb_file == null) {
			cfg_graphdb_file = GlobalParamsConfig.CFG_GRAPHDB_FILE;
		}
		if (dois_file ==  null) {
			dois_file = GlobalParamsConfig.DOIS_FILE;
		}
				
		if (abridge) {
			logger.info("!!! This is a shortened version of the 'Add publications' pipeline for test purposes!!!"
					+ "\nThe pipeline has been started with the following options."
					+ "\n*** Section 1. Configuration options. ***"
					+ "\n -Amazon S3 config file: {};"
					+ "\n -Amazon Athena config file: {};"
					+ "\n -GraphDB config file: {}; "
					+ "\n*** Section 2. Publicaations data. ***"
					+ "\n -Initial DOIs file: {};"
					+ "\n -Max references to consider: {} ('-1' means all);"
					+ "\n -Max citing papers to consider: {} ('-1' means all).",
					cfg_s3_file, cfg_sqldb_file, cfg_graphdb_file, dois_file, nrefs, ncits);
		}
		else {
			logger.info("'Add publications' pipeline has been started with the following options: "
					+ "\n*** Section 1. Configuration options. ***"
					+ "\n -Amazon S3 config file: {};"
					+ "\n -Amazon Athena file: {};"
					+ "\n -GraphDB config file: {}; "
					+ "\n*** Section 2. Publicaations data. ***"
					+ "\n -Initial DOIs file: {};"
					+ "\n -Max references to consider: {} ('-1' means all);"
					+ "\n -Max citing papers to consider: {} ('-1' means all).",
					cfg_s3_file, cfg_sqldb_file, cfg_graphdb_file, dois_file, nrefs, ncits);
		}
		
		// start the pipeline
		setupAmazonS3Connection(cfg_s3_file);
		
		setupDatabaseConnections(cfg_sqldb_file, cfg_graphdb_file);

		loadPublicationsData(abridge, integrateOnly, dois_file, nrefs, ncits);

		CoreGraphDatabase.closeDriver();
		
		logger.info("Pipeline finished!");
	}

	public static void setupAmazonS3Connection(String cfg_s3_file) {
		AmazonS3Config.setupInstance(cfg_s3_file);
	}
	
	public static void setupDatabaseConnections(String cfg_sqldb_file, String cfg_graphdb_file) {
		// Amazon Athena connection
		AmazonAthenaConfig.setupInstance(cfg_sqldb_file);
		// Graph database connection
		GraphDatabaseConfig.setupInstance(cfg_graphdb_file);
		// Test connection
		CoreGraphDatabase.testConnection();
	}
	
	public static void loadPublicationsData(boolean abridge, boolean integrateOnly, String dois_file, int nrefs, int ncits) throws Exception {
		
		if (integrateOnly) {
			// Only integrate into GraphDB existing publications data from Athena tables
			
			// Copy publication data from SQL database to graph database
			LoadAthenaPublicationsDataIntoGraphDB.integrateAthenaPublicationsIntoGraphDB(abridge);
			
			// Initialize lexical form node maps
			PublicationGraphDatabase.initializeLexicalFormNodeMaps();
			
			// Create sentence nodes
			PublicationGraphDatabase.createSentenceNodes();
			
			// Create COOCCUR relation
			PublicationGraphDatabase.createCooccursWithRelations();
			
			// Create SYNONYM_WITH relation
			PublicationGraphDatabase.createSynonymWithRelations();
			
		} else {
			// Execute all steps
			
			// Initialize publication processing
			PublicationDatabase.initPublicationDatabase(abridge, GlobalParamsConfig.TMP_DATA_DIR);
			
			// Populate DOIs
			PublicationDatabase.getInstance().addInitDOIsFromS3Object(dois_file);
			
			// Find more publications based on citations and references:
			PublicationDatabase.getInstance().retrieveCitationsAndReferences(nrefs, ncits);
			
			// Retrieve titles and abstracts of all publications
			PublicationDatabase.getInstance().addPublicationData();
			
			// Move filled file with publication info onto AWS Athena table via S3 bucket
			int movedTablePart = PublicationDatabase.getInstance().appendDataToAthena();
			
			// Continue if new publications found
			if (movedTablePart > -1) {
				// Copy publication data from SQL database to graph database
				LoadAthenaPublicationsDataIntoGraphDB.integrateAthenaPublicationsPartIntoGraphDB(abridge, movedTablePart);
				
				// Initialize lexical form node maps
				PublicationGraphDatabase.initializeLexicalFormNodeMaps();
				
				// Create sentence nodes
				PublicationGraphDatabase.createSentenceNodes();
				
				// Create COOCCUR relation
				PublicationGraphDatabase.createCooccursWithRelations();
				
				// Create SYNONYM_WITH relation
				PublicationGraphDatabase.createSynonymWithRelations();
			}
		}
	}
}
