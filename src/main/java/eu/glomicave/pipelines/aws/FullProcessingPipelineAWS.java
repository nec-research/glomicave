/* 
* GLOMICAVE-KG 
* 
* file: FullProcessingPipelineAWS.java
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

/**
 * This script executes full processing pipeline to reset and to populate Glomicave knowledge graph (AWS cloud version).
 *
 */

package eu.glomicave.pipelines.aws;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import eu.glomicave.config.GlobalParamsConfig;
import eu.glomicave.data_import.PublicationGraphDatabase;
import eu.glomicave.config.AmazonAthenaConfig;
import eu.glomicave.config.AmazonS3Config;
import eu.glomicave.config.GraphDatabaseConfig;
import eu.glomicave.data_import.LoadAthenaPublicationsDataIntoGraphDB;
import eu.glomicave.persistence.PublicationDatabase;
import eu.glomicave.persistence.CoreGraphDatabase;
import eu.glomicave.persistence.PredefinedCategories;
import eu.glomicave.wp3.IntegrateWP4Traits;
import eu.glomicave.wp3.LoadWikipathwaysIntoGraphDB;
import eu.glomicave.wp3.NamedEntityInitialization;


public class FullProcessingPipelineAWS {
	private static final Logger logger = LogManager.getLogger(FullProcessingPipelineAWS.class);
	
	public static void run(boolean abridge, String cfg_s3_file, String cfg_sqldb_file, String cfg_graphdb_file, 
							String gene_ontology_file, String ec_codes_file, String[] extra_ontologies_files, 
								String traits_file, String wp_dir, String dois_file, int nrefs, int ncits) throws Exception {
		
		// Assign default parameters
		if (cfg_s3_file == null) {
			cfg_s3_file = GlobalParamsConfig.CFG_S3_FILE;
		}
		if (cfg_sqldb_file == null) {
			cfg_sqldb_file = GlobalParamsConfig.CFG_AWS_SQLDB_FILE;
		}
		if (cfg_graphdb_file == null) {
			cfg_graphdb_file = GlobalParamsConfig.CFG_AWS_GRAPHDB_FILE;
		}
		if (gene_ontology_file == null) {
			gene_ontology_file = GlobalParamsConfig.S3_GENE_ONTOLOGY_FILE;
		}
		if (ec_codes_file == null) {
			ec_codes_file = GlobalParamsConfig.S3_EC_CODES_FILE;
		}
		if (extra_ontologies_files == null) {
			extra_ontologies_files = GlobalParamsConfig.S3_WP_ONTOLOGY_FILE;
		}
		if (traits_file == null) {
			traits_file = GlobalParamsConfig.S3_TRAITS_FILE;
		}
		if (wp_dir == null) {
			wp_dir = GlobalParamsConfig.S3_WIKIPATHWAYS_DIR;
		}
		if (dois_file ==  null) {
			dois_file = GlobalParamsConfig.S3_DOIS_FILE;
		}
		
		if (abridge) {
			logger.info("!!! This is a shortened version of the pipeline (AWS cloud version) for test purposes!!!"
					+ "\nThe pipeline has been started with the following options."
					+ "\n*** Section 1. Configuration options. ***"
					+ "\n -S3 config file: {};"
					+ "\n -SQL config file: {};"
					+ "\n -GraphDB config file: {}; "
					+ "\n*** Section 2. Data ontologies. ***"
					+ "\n -Gene ontology file: {}; "
					+ "\n -EC codes file: {};"
					+ "\n -Extra ontology files: {};"
					+ "\n -WikiPathways directory: {}; "
					+ "\n*** Section 3. Publications data. ***"
					+ "\n -Initial DOIs file: {};"
					+ "\n -Max references to consider: {} ('-1' means all);"
					+ "\n -Max citing papers to consider: {} ('-1' means all).",
					cfg_s3_file, cfg_sqldb_file, cfg_graphdb_file, 
					gene_ontology_file, ec_codes_file, extra_ontologies_files, wp_dir, dois_file, nrefs, ncits);
		}
		else {
			logger.info("Full pipeline (AWS cloud version) has been started with the following options. "
					+ "\n*** Section 1. Configuration options. ***"
					+ "\n -S3 config file - {};"
					+ "\n -SQL config file: {};"
					+ "\n -GraphDB config file: {}; "
					+ "\n*** Section 2. Data ontologies. ***"
					+ "\n -Gene ontology file: {}; "
					+ "\n -EC codes file: {};"
					+ "\n -Extra ontology files: {};"
					+ "\n -WikiPathways directory: {}; "
					+ "\n*** Section 3. Publicaations data. ***"
					+ "\n -Initial DOIs file: {};"
					+ "\n -Max references to consider: {} ('-1' means all);"
					+ "\n -Max citing papers to consider: {} ('-1' means all).",
					cfg_s3_file, cfg_sqldb_file, cfg_graphdb_file, 
					gene_ontology_file, ec_codes_file, extra_ontologies_files, wp_dir, dois_file, nrefs, ncits);
		}
		
		// Start the pipeline
		
		setupAmazonS3Connection(cfg_s3_file);

		setupDatabaseConnections(cfg_sqldb_file, cfg_graphdb_file);

		resetDatabases();

		loadNEAndWP3Data(abridge, gene_ontology_file, ec_codes_file, extra_ontologies_files, traits_file, wp_dir);

		loadPublicationsData(abridge, dois_file, nrefs, ncits);

		CoreGraphDatabase.closeDriver();
		
		logger.info("Pipeline finished!");	
	}

	public static void setupAmazonS3Connection(String cfg_s3_file) {
		// Configure connection to Amazon S3 buckets
		AmazonS3Config.setupInstance(cfg_s3_file);
	}
	
	public static void setupDatabaseConnections(String cfg_sqldb_file, String cfg_graphdb_file) throws Exception {
		// Amazon Athena connection
		AmazonAthenaConfig.setupInstance(cfg_sqldb_file);
		
		// Graph database connection
		GraphDatabaseConfig.setupInstance(cfg_graphdb_file);
		
		// Test connection
		CoreGraphDatabase.testConnection();
	}

	public static void resetDatabases() {
		// Drop Athena publications table (true = clear also S3 data)
		PublicationDatabase.dropPublicationsTable(true);
		
		// Recreate Athena publications table (false = normal, not iceberg tables)
		PublicationDatabase.createPublicationsTable(false);
		
		// Clear graph database
		CoreGraphDatabase.clearDatabase();
	}

	public static void loadNEAndWP3Data(boolean abridge, String gene_ontology_file, 
								String ec_codes_file, String[] extra_ontologies_files, 
										String traits_file, String wp_dir) throws Exception {
		// Add NEs to graph database
		addNamedEntitiesFromGeneOntologyS3CSV(abridge, gene_ontology_file);
		
		// Add EC enzyme nomenclature
		addNamedEntitiesFromECEnzymesS3CSV(abridge, ec_codes_file);
		
		// Insert trait data from WP4	
		IntegrateWP4Traits.addTraitsFromS3CSV(abridge, traits_file);
		
		// Insert Wikipathways data			
		LoadWikipathwaysIntoGraphDB.loadWikipathwaysS3IntoGraphDB(abridge, wp_dir);
		
		// Add manually curated NEs and lexical forms for uploaded wikipathway entities
		addExtraNamedEntitiesS3CSV(abridge, extra_ontologies_files);
	}

	
	public static void loadPublicationsData(boolean abridge, String dois_file, int nrefs, int ncits) throws Exception {
		// Initialize publication processing
		PublicationDatabase.initPublicationDatabase(abridge, GlobalParamsConfig.TMP_DATA_DIR);
		
		// Populate DOIs
		PublicationDatabase.getInstance().addInitDOIsFromS3Object(dois_file);
		
		// Find more publications based on citations and references
		PublicationDatabase.getInstance().retrieveCitationsAndReferences(nrefs, ncits);
		
		// Retrieve titles and abstracts of all publications
		PublicationDatabase.getInstance().addPublicationData();
		
		// Move filled file with publication info onto AWS Athena table via S3 bucket
		PublicationDatabase.getInstance().appendDataToAthena();
		
		// Copy publication data from Athena database to graph database
		LoadAthenaPublicationsDataIntoGraphDB.integrateAthenaPublicationsIntoGraphDB(abridge);
		
		// Initialize lexical form node maps
		PublicationGraphDatabase.initializeLexicalFormNodeMaps();
		
		// Create sentence nodes
		PublicationGraphDatabase.createSentenceNodes();
		
		// Create COOCCUR relation
		PublicationGraphDatabase.createCooccursWithRelations();
		
		// Create SYNONYM_WITH relation
		PublicationGraphDatabase.createSynonymWithRelations();
	}
	
	
	// Load NLE-curated gene ontology NLE-BIO-KG from Amazon S3 object. 
	// Source: https://repos.ant-net/bai/bai-closed/biomedical-knowledge-graph/-/tree/master/processed-data/gene-info.csv  
	public static void addNamedEntitiesFromGeneOntologyS3CSV(boolean abridge, String s3filekey) 
																throws IOException, FileNotFoundException {
		int uidColIdx = 1;
		int[] lfColIds = {1,4};
		String ontologyName = "NLE-BIO-KG";
		
		NamedEntityInitialization.addNamedEntitiesFromS3CSV(abridge, s3filekey, uidColIdx, lfColIds, ontologyName);
	}
		
	// Add EC enzyme codes dictionary from Amazon S3 object. 
	public static void addNamedEntitiesFromECEnzymesS3CSV(boolean abridge, String s3filekey) 
																throws IOException, FileNotFoundException {
		int uidColIdx = 0;
		int[] lfColIds = {0,1,2};
		String ontologyName = "EC-CODES";
		String entityCategory = PredefinedCategories.PROTEIN.toString();
		
		NamedEntityInitialization.addNamedEntitiesFromS3CSV(abridge, s3filekey, uidColIdx, lfColIds, ontologyName, entityCategory);
	}
	
	// Add data from extra ontologies listed as Amazon S3 objects. 
	public static void addExtraNamedEntitiesS3CSV(boolean abridge, String[] s3filekeys) 
															throws IOException, FileNotFoundException {
		for (String s3filekey: s3filekeys) {
			logger.info("Loading ontologies from file: '{}'.", s3filekey);
			// Assumed input file column ordering: source, category, uid, name, syn1, syn2, ..., synN
			// In case there is a node with such uid, this will only adds new lexical forms
			int sourceOntologyColIdx = 0;
			int entityCategoryColIdx = 1;
			int uidColIdx = 2;
			int lfColStartIdx = 3;

			NamedEntityInitialization.addNamedEntitiesFromS3CSV(abridge, s3filekey, 
							uidColIdx, lfColStartIdx, entityCategoryColIdx, sourceOntologyColIdx);
		}
	}
	
}
