package eu.glomicave.pipelines.local;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import eu.glomicave.config.GraphDatabaseConfig;
import eu.glomicave.data_import.PublicationGraphDatabase;
import eu.glomicave.persistence.CoreGraphDatabase;
import eu.glomicave.wp3.NamedEntityInitialization;


public class AddOntologyLocal {

	private static final Logger logger = LogManager.getLogger(AddOntologyLocal.class);

	// DEFAULT input parameters if not given in the pipeline call
	// Config files
	private static final String CFG_GRAPHDB_FILE = "./src/main/resources/eu/glomicave/graphdb_config.xml";
	
	// Ontologies
	private static final String[] EXTRA_ONTOLOGY_FILES = {
				"../data/ontologies/extra_ontologies/uniprot/uniprotkb_reviewed_true_2024_02_23-file_to_upload.csv",
				"../data/ontologies/extra_ontologies/chebi/chebi_compounds-file_to_upload.csv"
		};


	public static void run(boolean abridge, String cfg_graphdb_file, String[] extra_ontology_files) throws Exception {
		
		// assign parameters
		if (cfg_graphdb_file == null) {
			cfg_graphdb_file = AddOntologyLocal.CFG_GRAPHDB_FILE;
		}
		if (extra_ontology_files ==  null) {
			extra_ontology_files = AddOntologyLocal.EXTRA_ONTOLOGY_FILES;
		}
		
		// init loggers
//		initLoggers();
				
		if (abridge) {
			logger.info("!!! This is a shortened version of the 'Add ontology' pipeline for test purposes!!!"
					+ "\nThe pipeline has been started with the following options: "
					+ "\n*** Section 1. Configuration options. ***"
					+ "\n -GraphDB config file: {}. "
					+ "\n*** Section 2. Data ontologies. ***"
					+ "\n -Extra ontology files: {}.",
					cfg_graphdb_file, extra_ontology_files);
		}
		else {
			logger.info("'Add ontology' pipeline has been started with the following options: "
					+ "\n*** Section 1. Configuration options. ***"
					+ "\n -GraphDB config file: {}."
					+ "\n*** Section 2. Data ontologies. ***"
					+ "\n -Extra ontology files: {}.",
					cfg_graphdb_file, extra_ontology_files);
		}
		
		// start the pipeline
		setupDatabaseConnections(cfg_graphdb_file);
		
		loadOntologyData(abridge, extra_ontology_files);

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
	
	public static void loadOntologyData(boolean abridge, String[] extra_ontologies_files) throws Exception {
		// Add manually data from new ontologies
		addExtraNamedEntitiesCSV(abridge, extra_ontologies_files);
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
	
	public static void addExtraNamedEntitiesCSV(boolean abridge, String[] filepaths) throws IOException, FileNotFoundException {
		for (String filepath: filepaths) {
			logger.info("Loading ontologies from file: '{}'.", filepath);
			// Assumed input file column ordering: source, category, uid, name, syn1, syn2, ..., synN
			int uidColIdx = 2;
			int lfColStartIdx = 3;
			int entityCategoryColIdx = 1;
			int sourceOntologyColIdx = 0;

			NamedEntityInitialization.addNamedEntitiesFromCSV(abridge, filepath, 
					uidColIdx, lfColStartIdx, entityCategoryColIdx, sourceOntologyColIdx);
		}
	}

}
