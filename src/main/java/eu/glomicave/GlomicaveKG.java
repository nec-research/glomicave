/** 
 * This is the main class that provides command-line interface to run Glomicave knowledge graph creation.
 * 
 */

package eu.glomicave;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.Callable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

import eu.glomicave.config.GlobalParamsConfig;
import eu.glomicave.pipelines.aws.AddOntologyAWS;
import eu.glomicave.pipelines.aws.AddPhenotypesAWS;
import eu.glomicave.pipelines.aws.AddPublicationsAWS;
import eu.glomicave.pipelines.aws.FullProcessingPipelineAWS;
import eu.glomicave.pipelines.aws.LoadOIEFactsAWS;
import eu.glomicave.pipelines.local.AddOntologyLocal;
import eu.glomicave.pipelines.local.AddPhenotypesLocal;
import eu.glomicave.pipelines.local.AddPublicationsLocal;
import eu.glomicave.pipelines.local.FullProcessingPipelineLocal;
import eu.glomicave.pipelines.local.LoadOIEFactsLocal;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;


@Command(name = "glomicave-kg", mixinStandardHelpOptions = true, version = "glomicave-kg 1.0",
		 description = "Builds Glomicave knowledge graph from external ontologies and paper abstracts.")


public class GlomicaveKG implements Callable<Integer> {
	
		final String[] piplineNames = { "full", "addPublications", "addOntology", "addTraits", "loadFacts" };

		@Parameters(index = "0", description = "Name of the pipeline to run. "
				+ "\nShould be one of: 'full'| 'addPublications' | 'addOntology' | 'addTraits' | 'loadFacts'.")
		private String pipelineName = null;
		
		@Option(names = {"-a", "--abridge"}, description = "Run shortend version of the pipeline for test purposes.")
		private boolean abridge = false; 
		
		@Option(names = {"-l", "--local"}, description = "Run version of the pipeline on local server. "
				+ "This means that config files for the local instances of Neo4J and SQL databases are expected. "
				+ "All ontologies and a list of paper DOIs are to be uploaded from local file system.")
		private boolean local_version = false; 
		
		@Option(names = {"-i", "--integrate"}, description = "Integrate stored publication data from Athena tables into GraphDB."
				+ "This will prevent writing new publication data into Athena tables."
				+ "The option has effect only with 'addPublications' cloud pipeline.")
		private boolean integrateOnly = false; // when false, run all steps in the pipeline
		
		// continue from step
		
		@Option(names = {"-s", "--step"}, description = "Max number of threads to execute in parallel.")
		private int step_number = 1; // default start step is '1' (whole pipeline) 
		
		// multi-threading
		
		@Option(names = {"-t", "--threads"}, description = "Max number of threads to execute in parallel.")
		private int pool_size = 1; // default pool size is '1' 

		// config files	
		
		@Option(names = {"--cfg_logs"}, description = "Path to file 'log4j2.xml' with the logger settings.")
		private String cfg_logger_file = null;

		@Option(names = {"--cfg_s3"}, description = "Config file for S3. "
				+ "\nHas only effect without option '-l | --local'.")
		private String cfg_s3_file = null;
		
		@Option(names = {"--cfg_sqldb"}, description = "Config file for SQL database."
				+ "\nHas no effect with pipelines 'addOntology', 'addTraits' and 'loadFacts'.")
		private String cfg_sqldb_file = null;
		
		@Option(names = {"--cfg_graphdb"}, description = "Config file for graph database.")
		private String cfg_graphdb_file = null;
		
		// search publications
		
		@Option(names = {"--nrefs"}, description = "Max number of references per publication to explore when adding publications."
				+ "\nHas no effect with pipelines 'full' and 'addPublications'.")
		private int nrefs = -1; // default value '-1' to consider all found references
		
		@Option(names = {"--ncits"}, description = "Max number of citations per publication to explore when adding publications."
				+ "\nHas no effect with pipelines 'full' and 'addPublications'.")
		private int ncits = -1; // default value '-1' to consider all found citations
		
		// locations of ontologies
		
		@Option(names = {"--gene_ontology"}, description = "Filepath to a CSV file with NEC-curated gene ontology."
				+ "\nHas only effect with pipeline 'full'.")
		private String gene_ontology_file = null;
		
		@Option(names = {"--ec_codes"}, description = "Filepath to a CSV file with Enzyme Codes ontology."
				+ "\nHas only effect with pipeline 'full'.")
		private String ec_codes_file = null;
		
		// any extra ontologies that will be uploaded after upload of wikipathways database
		
		@Option(names = {"--extra_ontology"}, description = "List of filepaths to any other ontologies stores in CSV format."
				+ "In case of 'full' pipeline they will be uploaded after processing data from WikiPathways.")
		private String[] extra_ontology_files = null;
		
		// location of file with phenotypic traits
		
		@Option(names = {"--traits"}, description = "CSV file with a list of phenotypic traits."
				+ "\nHas only effect with pipelines 'full' and 'addTraits'.")
		private String traits_file = null;
		
		// location of pathways database

		@Option(names = {"--wp"}, description = "Path to directory with stored WikiPathways dataset."
				+ "\nHas only effect with pipeline 'full'.")
		private String wp_dir = null;
		
		// location to store list of dois
		
		@Option(names = {"--dois"}, description = "File with a list of DOIs to analyse."
				+ "\nHas only effect with pipelines 'full' and 'addPublications'.")
		private String dois_file = null;
		
		// location for a file with extracted facts
		
		@Option(names = {"--facts"}, description = "File with OIE facts."
				+ "\nHas only effect with pipeline 'loadFacts'.")
		private String oie_triples_file = null;
		
		
		public Integer call() throws Exception {
			
			if (!Arrays.asList(piplineNames).contains(pipelineName)) {
				System.out.println("Wrong pipline name. "
						+ "\nPlease start again and select one of the pipelines from list: "
						+ "\n\t'full'| 'addPublications' | 'addOntology' | 'addTraits' | 'loadFacts'.");
				return 1;
			}
			
			if (pool_size > 0) {
				GlobalParamsConfig.MAX_POOL_SIZE = pool_size;
			}
			
			initLoggers();
			
			if (pipelineName != null && !local_version) {
				// AWS version of the pipeline
				switch (pipelineName) {
					case "full":
						FullProcessingPipelineAWS.run(
								abridge,
								cfg_s3_file,
								cfg_sqldb_file,
								cfg_graphdb_file,
								gene_ontology_file,
								ec_codes_file,
								extra_ontology_files,
								traits_file,
								wp_dir,
								dois_file,
								nrefs,
								ncits,
								step_number);
						break;
					case "addPublications":
						AddPublicationsAWS.run(
								abridge,
								integrateOnly,
								cfg_s3_file,
								cfg_sqldb_file,
								cfg_graphdb_file,
								dois_file,
								nrefs,
								ncits);
						break;
					case "loadFacts":
						LoadOIEFactsAWS.run(
								abridge,
								cfg_s3_file,
								cfg_graphdb_file,
								oie_triples_file);
						break;
					case "addOntology":
						AddOntologyAWS.run(
								abridge,
								cfg_s3_file,
								cfg_graphdb_file,
								extra_ontology_files);
						break;
					case "addTraits":
						AddPhenotypesAWS.run(
								abridge,
								cfg_s3_file,
								cfg_graphdb_file, 
								traits_file);						
						break;
				}
			}
			else if (pipelineName != null && local_version) {
				// local version of the pipeline
				switch (pipelineName) {
					case "full":
						FullProcessingPipelineLocal.run(
								abridge,
								cfg_sqldb_file,
								cfg_graphdb_file,
								gene_ontology_file,
								ec_codes_file,
								extra_ontology_files,
								traits_file,
								wp_dir,
								dois_file,
								nrefs,
								ncits);
						break;
					case "addPublications":
						AddPublicationsLocal.run(
								abridge,
								cfg_sqldb_file,
								cfg_graphdb_file,
								dois_file,
								nrefs,
								ncits);
						break;
					case "loadFacts":
						LoadOIEFactsLocal.run(
								abridge, 
								cfg_graphdb_file,
								oie_triples_file);
						break;
					case "addOntology":
						AddOntologyLocal.run(
								abridge,
								cfg_graphdb_file,
								extra_ontology_files);
						break;
					case "addTraits":
						AddPhenotypesLocal.run(
								abridge, 
								cfg_graphdb_file, 
								traits_file);
						break;
				}				
			}

			return 0;
		}
		
		public void initLoggers() {
			LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
			if (cfg_logger_file == null) {
				cfg_logger_file = GlobalParamsConfig.CFG_LOGGER_FILE;
			}
			File file = new File(cfg_logger_file);
			// this will force a reconfiguration
			context.setConfigLocation(file.toURI());
		}
		
		
		public static void main(String... args) throws Exception {			
	        int exitCode = new CommandLine(new GlomicaveKG()).execute(args);
	        System.exit(exitCode);
		}

}
