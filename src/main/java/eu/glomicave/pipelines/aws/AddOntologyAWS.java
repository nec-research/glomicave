package eu.glomicave.pipelines.aws;


import java.io.FileNotFoundException;
import java.io.IOException;

import eu.glomicave.config.GlobalParamsConfig;
import eu.glomicave.config.AmazonS3Config;
import eu.glomicave.config.GraphDatabaseConfig;
import eu.glomicave.data_import.PublicationGraphDatabase;
import eu.glomicave.persistence.CoreGraphDatabase;
import eu.glomicave.wp3.NamedEntityInitialization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class AddOntologyAWS {

	private static final Logger logger = LogManager.getLogger(AddOntologyAWS.class);

	public static void run(boolean abridge, String cfg_s3_file, String cfg_graphdb_file, String[] extra_ontology_files) throws Exception {
		
		// assign parameters
		if (cfg_s3_file == null) {
			cfg_s3_file = GlobalParamsConfig.CFG_S3_FILE;
		}
		if (cfg_graphdb_file == null) {
			cfg_graphdb_file = GlobalParamsConfig.CFG_AWS_GRAPHDB_FILE;
		}
		if (extra_ontology_files ==  null) {
			extra_ontology_files = GlobalParamsConfig.S3_EXTRA_ONTOLOGY_FILES;
		}
				
		if (abridge) {
			logger.info("!!! This is a shortened version of the 'Add ontology' pipeline for test purposes!!!"
					+ "\nThe pipeline has been started with the following options: "
					+ "\n*** Section 1. Configuration options. ***"
					+ "\n -S3 storage config file: {};"
					+ "\n -GraphDB config file: {}; "
					+ "\n*** Section 2. Data ontologies. ***"
					+ "\n -Extra ontology files: {};",
					cfg_s3_file, cfg_graphdb_file, extra_ontology_files);
		}
		else {
			logger.info("'Add ontology' pipeline has been started with the following options: "
					+ "\n*** Section 1. Configuration options. ***"
					+ "\n -S3 storage config file: {};"
					+ "\n -GraphDB config file: {}; "
					+ "\n*** Section 2. Data ontologies. ***"
					+ "\n -Extra ontology files: {};",
					cfg_s3_file, cfg_graphdb_file, extra_ontology_files);
		}
		
		// start the pipeline
		setupAmazonS3Connection(cfg_s3_file);
		
		setupDatabaseConnections(cfg_graphdb_file);
		
		loadOntologyData(abridge, extra_ontology_files);

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
	
	public static void loadOntologyData(boolean abridge, String[] extra_ontologies_files) throws Exception {
		// Add manually data from new ontologies
		addExtraNamedEntitiesS3CSV(abridge, extra_ontologies_files);
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
	
	public static void addExtraNamedEntitiesS3CSV(boolean abridge, String[] s3filekeys) throws IOException, FileNotFoundException {
		for (String s3filekey: s3filekeys) {
			logger.info("Loading ontologies from file: '{}'.", s3filekey);
			// Assumed input file column ordering: source, category, uid, name, syn1, syn2, ..., synN
			int uidColIdx = 2;
			int lfColStartIdx = 3;
			int entityCategoryColIdx = 1;
			int sourceOntologyColIdx = 0;

			NamedEntityInitialization.addNamedEntitiesFromS3CSV(abridge, s3filekey, 
					uidColIdx, lfColStartIdx, entityCategoryColIdx, sourceOntologyColIdx);
		}
	}

}