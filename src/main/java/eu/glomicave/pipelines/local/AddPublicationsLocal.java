/* 
* GLOMICAVE-KG 
* 
* file: AddPublicationsLocal.java
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

import eu.glomicave.config.GlobalParamsConfig;
import eu.glomicave.config.GraphDatabaseConfig;
import eu.glomicave.config.SQLDatabaseConfig;
import eu.glomicave.data_import.CitationsAndReferencesRetrieval;
import eu.glomicave.data_import.LoadSQLPublicationsDataIntoGraphDB;
import eu.glomicave.data_import.PublicationGraphDatabase;
import eu.glomicave.data_import.TitleAndAbstractRetrieval;
import eu.glomicave.persistence.CoreGraphDatabase;
import eu.glomicave.wp3.DOIInitialization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class AddPublicationsLocal {
	private static final Logger logger = LogManager.getLogger(FullProcessingPipelineLocal.class);

	public static void run(boolean abridge, String cfg_sqldb_file, String cfg_graphdb_file, 
										String dois_file, int nrefs, int ncits) throws Exception {
		// assign parameters
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
			logger.info("!!! This is a shortened version of the pipeline (local version) for test purposes!!!"
					+ "\nThe pipeline (local version) has been started with the following options."
					+ "\n*** Section 1. Configuration options. ***"
					+ "\n -SQL config file: {};"
					+ "\n -GraphDB config file: {}; "
					+ "\n*** Section 2. Publicaations data. ***"
					+ "\n -DOIs file: {}.",
					cfg_sqldb_file, cfg_graphdb_file, dois_file);
		}
		else {
			logger.info("Full pipeline (local version) has been started with the following options. "
					+ "\n*** Section 1. Configuration options. ***"
					+ "\n -SQL config file: {};"
					+ "\n -GraphDB config file: {}; "
					+ "\n*** Section 2. Publicaations data. ***"
					+ "\n -DOIs file: {}.",
					cfg_sqldb_file, cfg_graphdb_file, dois_file);
		}
		
		// start the pipeline

		setupDatabaseConnections(cfg_sqldb_file, cfg_graphdb_file);

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

}
