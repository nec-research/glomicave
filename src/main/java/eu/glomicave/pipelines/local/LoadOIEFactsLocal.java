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
