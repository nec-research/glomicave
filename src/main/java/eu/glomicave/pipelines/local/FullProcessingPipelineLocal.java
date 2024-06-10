/* 
* GLOMICAVE-KG 
* 
* file: FullProcessingPipelineLocal.java
* 
* Authors: 	Roman Siarheyeu (raman.siarheyeu@neclab.eu) 
* 			Kiril Gashteovski (kiril.gashteovski@neclab.eu) 
*
* Copyright (c) 2024 NEC Laboratories Europe GmbH All Rights Reserved. 
* 
* NEC Laboratories Europe GmbH DISCLAIMS ALL WARRANTIES, EITHER EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO IMPLIED WARRANTIES OF MERCHANTABILITY 
* AND FITNESS FOR A PARTICULAR PURPOSE AND THE WARRANTY AGAINST LATENT 
* DEFECTS, WITH RESPECT TO THE PROGRAM AND THE ACCOMPANYING 
* DOCUMENTATION. 
* 
* NO LIABILITIES FOR CONSEQUENTIAL DAMAGES:
* IN NO EVENT SHALL NEC Laboratories Europe GmbH or ANY OF ITS SUBSIDIARIES BE
* LIABLE FOR ANY DAMAGES WHATSOEVER (INCLUDING, WITHOUT LIMITATION, DAMAGES
* FOR LOSS OF BUSINESS PROFITS, BUSINESS INTERRUPTION, LOSS OF INFORMATION, OR 
* OTHER PECUNIARY LOSS AND INDIRECT, CONSEQUENTIAL, INCIDENTAL, 
* ECONOMIC OR PUNITIVE DAMAGES) ARISING OUT OF THE USE OF OR INABILITY 
* TO USE THIS PROGRAM, EVEN IF NEC Laboratories Europe GmbH HAS BEEN ADVISED OF
* THE POSSIBILITY OF SUCH DAMAGES. 
* 
* THIS HEADER MAY NOT BE EXTRACTED OR MODIFIED IN ANY WAY. 
*/

package eu.glomicave.pipelines.local;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import eu.glomicave.config.GlobalParamsConfig;
import eu.glomicave.config.GraphDatabaseConfig;
import eu.glomicave.config.SQLDatabaseConfig;

import eu.glomicave.data_import.CitationsAndReferencesRetrieval;
import eu.glomicave.data_import.LoadSQLPublicationsDataIntoGraphDB;
import eu.glomicave.data_import.PublicationGraphDatabase;
import eu.glomicave.data_import.TitleAndAbstractRetrieval;

import eu.glomicave.persistence.CoreGraphDatabase;
import eu.glomicave.persistence.PredefinedCategories;
import eu.glomicave.persistence.SQLDatabaseTableGeneration;

import eu.glomicave.wp3.DOIInitialization;
import eu.glomicave.wp3.IntegrateWP4Traits;
import eu.glomicave.wp3.LoadWikipathwaysIntoGraphDB;
import eu.glomicave.wp3.NamedEntityInitialization;

/**
 * This script runs the full pipeline to populate local instances of SQL and graph databases.
 *
 */
public class FullProcessingPipelineLocal {
	private static final Logger logger = LogManager.getLogger(FullProcessingPipelineLocal.class);
	

	public static void run(boolean abridge, String cfg_sqldb_file, String cfg_graphdb_file, String gene_ontology_file, 
								String ec_codes_file, String[] extra_ontologies_files, String traits_file, String wp_dir, 
																String dois_file, int nrefs, int ncits) throws Exception {
		// assign parameters
		if (cfg_sqldb_file == null) {
			cfg_sqldb_file = GlobalParamsConfig.CFG_SQLDB_FILE;
		}
		if (cfg_graphdb_file == null) {
			cfg_graphdb_file = GlobalParamsConfig.CFG_GRAPHDB_FILE;
		}
		if (gene_ontology_file == null) {
			gene_ontology_file = GlobalParamsConfig.GENE_ONTOLOGY_FILE;
		}
		if (ec_codes_file == null) {
			ec_codes_file = GlobalParamsConfig.EC_CODES_FILE;
		}
		if (extra_ontologies_files == null) {
			extra_ontologies_files = GlobalParamsConfig.EXTRA_ONTOLOGY_FILES;
		}
		if (traits_file == null) {
			traits_file = GlobalParamsConfig.TRAITS_FILE;
		}
		if (wp_dir == null) {
			wp_dir = GlobalParamsConfig.WIKIPATHWAYS_DIR;
		}
		if (dois_file ==  null) {
			dois_file = GlobalParamsConfig.DOIS_FILE;
		}
		
		if (abridge) {
			logger.info("!!! This is a shortened version of the pipeline (local version) for test purposes!!!"
					+ "\nThe pipeline (local version) has been started with the following options."
					+ "\n*** Section 1. Configuration options. ***"
					+ "\n -SQL config file: {};"
					+ "\n -GraphDB config file: {}; "
					+ "\n*** Section 2. Data ontologies. ***"
					+ "\n -Gene ontology file: {};"
					+ "\n -EC numbers file: {};"
					+ "\n -Extra ontology files: {};"
					+ "\n -WikiPathways directory: {}; "
					+ "\n*** Section 3. Phenotypes. ***"
					+ "\n -Traits file: {};"
					+ "\n*** Section 4. Publications data. ***"
					+ "\n -DOIs file: {}."
					+ "\n -Max references to consider: {} ('-1' means all);"
					+ "\n -Max citing papers to consider: {} ('-1' means all).",
					cfg_sqldb_file, cfg_graphdb_file, 
					gene_ontology_file, ec_codes_file, extra_ontologies_files, wp_dir, 
					traits_file, dois_file, nrefs, ncits);
		}
		else {
			logger.info("Full pipeline (local version) has been started with the following options. "
					+ "\n*** Section 1. Configuration options. ***"
					+ "\n -SQL config file: {};\n -GraphDB config file: {}; "
					+ "\n*** Section 2. Data ontologies. ***"
					+ "\n -Gene ontology file: {};"
					+ "\n -EC numbers file: {};"
					+ "\n -Extra ontology files: {};"
					+ "\n -WikiPathways directory: {}; "
					+ "\n*** Section 3. Phenotypes. ***"
					+ "\n -Traits file: {};"
					+ "\n*** Section 4. Publications data. ***"
					+ "\n -DOIs file: {}."
					+ "\n -Max references to consider: {} ('-1' means all);"
					+ "\n -Max citing papers to consider: {} ('-1' means all).",
					cfg_sqldb_file, cfg_graphdb_file, 
					gene_ontology_file, ec_codes_file, extra_ontologies_files, wp_dir, 
					traits_file, dois_file, nrefs, ncits);
		}
		
		// start the pipeline

		setupDatabaseConnections(cfg_sqldb_file, cfg_graphdb_file);

		resetDatabases();

		loadNEAndWP3Data(abridge, gene_ontology_file, ec_codes_file, extra_ontologies_files, wp_dir, traits_file);

		loadPublicationsData(abridge, dois_file, nrefs, ncits);

		CoreGraphDatabase.closeDriver();
		
		logger.info("Pipeline finished!");	
	}
		
	public static void setupDatabaseConnections(String cfg_sqldb_file, String cfg_graphdb_file) {
		// connection to SQL database
		SQLDatabaseConfig.setupInstance(cfg_sqldb_file);
		// connection to Graph database
		GraphDatabaseConfig.setupInstance(cfg_graphdb_file);
		// Test connection
		CoreGraphDatabase.testConnection();
	}

	public static void resetDatabases() {
		// Drop SQL database tables:
		SQLDatabaseTableGeneration.dropTables();
		// Create SQL database tables:
		SQLDatabaseTableGeneration.createTables();
		// Clear graph database:
		CoreGraphDatabase.clearDatabase();
	}

	public static void loadNEAndWP3Data(boolean abridge, String gene_ontology_file, 
			String ec_codes_file, String[] extra_ontologies_files, String wp_dir, String traits_file) throws Exception {
		// Add NEs to graph database:
		addNamedEntitiesFromGeneOntologyCSV(abridge, gene_ontology_file);

		// Add EC enzyme nomenclature
		addNamedEntitiesFromECEnzymesCSV(abridge, ec_codes_file);
		
		// Insert trait data from WP4:
		IntegrateWP4Traits.addTraitsFromCSV(abridge, traits_file);

		// Insert Wikipathways data:
		LoadWikipathwaysIntoGraphDB.loadWikipathwaysIntoGraphDB(abridge, wp_dir);
		
		// Add manually curated NEs and lexical forms for uploaded wikipathway entities
		addExtraNamedEntitiesCSV(abridge, extra_ontologies_files);
	}
	
	public static void loadPublicationsData(boolean abridge, String dois_file, int nrefs, int ncits) throws Exception {
		// Populate DOIs
		DOIInitialization.readManuallyProvidedRelevantDOIsToDatabase(abridge, dois_file);
		
		// Find more publications based on citations and references:
		CitationsAndReferencesRetrieval.run(abridge, nrefs, ncits);
		
		// Retrieve titles and abstracts of all publications:
		TitleAndAbstractRetrieval.run();
		
		// Copy publication data from SQL database to graph database:
		LoadSQLPublicationsDataIntoGraphDB.integrateSQLPublicationsIntoGraphDB(abridge);
		
		// Initialize lexical form node maps
		PublicationGraphDatabase.initializeLexicalFormNodeMaps();
		
		// Create sentence nodes
		PublicationGraphDatabase.createSentenceNodes();
		
		// Create COOCCUR relation:
		PublicationGraphDatabase.createCooccursWithRelations();
		
		// Create SYNONYM_WITH relation
		PublicationGraphDatabase.createSynonymWithRelations();
	}
	
	// Load NLE-curated gene ontology NLE-BIO-KG from local CSV file. 
	// Source: https://repos.ant-net/bai/bai-closed/biomedical-knowledge-graph/-/tree/master/processed-data/gene-info.csv  
	public static void addNamedEntitiesFromGeneOntologyCSV(boolean abridge, String filepath) 
																throws IOException, FileNotFoundException {
		int uidColIdx = 1;
		int[] lfColIds = {1,4};
		String ontologyName = "NLE-BIO-KG";
		
		NamedEntityInitialization.addNamedEntitiesFromCSV(abridge, filepath, uidColIdx, lfColIds, ontologyName);
	}
		
	// Add EC enzyme codes dictionary from local CSV file. 
	public static void addNamedEntitiesFromECEnzymesCSV(boolean abridge, String filepath) 
																throws IOException, FileNotFoundException {
		int uidColIdx = 0;	// column with UID
		int[] lfColIds = {0,1,2};	// columns with lexical forms
		String ontologyName = "EC-CODES";	// prefix
		String entityCategory = PredefinedCategories.PROTEIN.toString();	// nodes label
		
		NamedEntityInitialization.addNamedEntitiesFromCSV(abridge, filepath, uidColIdx, lfColIds, ontologyName, entityCategory);
	}
	
	// Add data from extra ontologies listed as local files. 
	public static void addExtraNamedEntitiesCSV(boolean abridge, String[] filepaths) throws IOException, FileNotFoundException {
		for (String filepath: filepaths) {
			logger.info("Loading ontologies from file: '{}'.", filepath);
			// Assumed input file column ordering: source, category, uid, name, syn1, syn2, ..., synN
			int sourceOntologyColIdx = 0;	// prefix
			int entityCategoryColIdx = 1;	// node labels
			int uidColIdx = 2;	// UID
			int lfColStartIdx = 3;	// lexical forms

			NamedEntityInitialization.addNamedEntitiesFromCSV(abridge, filepath, 
					uidColIdx, lfColStartIdx, entityCategoryColIdx, sourceOntologyColIdx);
		}
	}
	
}
