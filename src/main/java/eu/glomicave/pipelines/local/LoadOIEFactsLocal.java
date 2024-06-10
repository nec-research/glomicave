/* 
* GLOMICAVE-KG 
* 
* file: LoadOIEFactsLocal.java
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import eu.glomicave.config.GlobalParamsConfig;
import eu.glomicave.config.GraphDatabaseConfig;
import eu.glomicave.persistence.CoreGraphDatabase;
import eu.glomicave.wp2.ore.IntegrateOpenRelations;


public class LoadOIEFactsLocal {

	private static final Logger logger = LogManager.getLogger(LoadOIEFactsLocal.class);

	public static void run(boolean abridge, String cfg_graphdb_file, String oie_triples_file) throws Exception {
		// assign parameters
		if (cfg_graphdb_file == null) {
			cfg_graphdb_file = GlobalParamsConfig.CFG_GRAPHDB_FILE;
		}
		if (oie_triples_file ==  null) {
			oie_triples_file = GlobalParamsConfig.OIE_TRIPLES_FILE;
		}
		

		if (abridge) {
			logger.info("!!! This is a shortened version of the 'Load text-mined facts' pipeline for test purposes!!!"
					+ "\nThe pipeline has been started with the following options: "
					+ "\n*** Section 1. Configuration options. ***"
					+ "\n -GraphDB config file: '{}'. "
					+ "\n*** Section 2. File with phenotype traits. ***"
					+ "\n -OpenIE triples file: '{}'.",
					cfg_graphdb_file, oie_triples_file);
		}
		else {
			logger.info("'Load text-mined facts' pipeline has been started with the following options: "
					+ "\n*** Section 1. Configuration options. ***"
					+ "\n -GraphDB config file: '{}'. "
					+ "\n*** Section 2. File with phenotype traits. ***"
					+ "\n -OpenIE triples file: '{}'.",
					cfg_graphdb_file, oie_triples_file);
		}
		
		// Start the pipeline
		// connection to Graph database
		GraphDatabaseConfig.setupInstance(cfg_graphdb_file);
		// test connection
		CoreGraphDatabase.testConnection();
		// add text extracted extracted facts into graph database data from file
		addOpenRelations(abridge, oie_triples_file);
		// close connection
		CoreGraphDatabase.closeDriver();
		
		logger.info("Pipeline finished!");
	}
	
	public static void addOpenRelations(boolean abridge, String oie_triples_file) throws Exception {
		// assumed input file column ordering:  subject, relation, object, polarity, modality, attribution, sentence_uid
		IntegrateOpenRelations.addOpenRelationsFromCSV(abridge, oie_triples_file);
	}

}
