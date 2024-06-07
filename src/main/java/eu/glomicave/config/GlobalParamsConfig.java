package eu.glomicave.config;

/**
 *  Global cross-package configurations.
 */
public class GlobalParamsConfig {
	// Pool size for multi-thread processing
	public static int MAX_POOL_SIZE = 1;
	// Local paths for temporarily stored data
	public static final String TMP_DATA_DIR = "./data/tmp_data";
	// Path to loggers configuration file
	public static final String CFG_LOGGER_FILE = "./config/log4j2.xml";
	
	// DEFAULT input parameters if not given in the pipeline calls
	
	// Default file paths for AWS cloud pipelines
	
	// Config files
	public static final String CFG_S3_FILE = "./config/s3/aws_s3_config.xml";
	public static final String CFG_AWS_SQLDB_FILE = "./config/sqldb/aws_athena_config.xml";
	public static final String CFG_AWS_GRAPHDB_FILE = "./config/graphdb/aws_neptune_int_config.xml";
	// Basic ontologies
	public static final String S3_GENE_ONTOLOGY_FILE = "raw/data/ontologies/nle-bio-kg/gene-info.csv";
	public static final String S3_EC_CODES_FILE = "raw/data/ontologies/ec-codes/ec-enzymes.csv";
	public static final String[] S3_WP_ONTOLOGY_FILE = {"raw/data/ontologies/wp-annotations/wp-manually-annotated.csv"};
	// Extra ontologies
	public static final String[] S3_EXTRA_ONTOLOGY_FILES = {
			"raw/data/ontologies/uniprot/uniprotkb_reviewed_true_2024_02_23-file_to_upload.csv",
			"raw/data/ontologies/chebi/chebi_compounds-file_to_upload.csv"
			};
	// Traits
	public static final String S3_TRAITS_FILE = "raw/data/phenotypes/wp4_traits.csv";
	// Wikipathways
	public static final String S3_WIKIPATHWAYS_DIR = "raw/data/wikipathways/wikipathways-20220110-rdf-wp/wp/";
	// Publication dois
	public static final String S3_DOIS_FILE = "raw/data/publications/publication-dois.txt";
	// OIE fact triplets
	public static final String S3_OIE_TRIPLES_FILE = "raw/data/openie/triples_final.csv";
	
	// Default file paths for local pipelines
	
	// Config filepaths
	public static final String CFG_SQLDB_FILE = "./config/sqldb/sqldb_config.xml";
	public static final String CFG_GRAPHDB_FILE = "./config/graphdb/graphdb_config.xml";
	// Basic ontologies
	public static final String GENE_ONTOLOGY_FILE = "../data/ontologies/nle-bio-kg/gene-info.csv";
	public static final String EC_CODES_FILE = "../data/ontologies/ec-codes/ec-enzymes.csv";
	public static final String[] WP_ONTOLOGY_FILE = {"../data/ontologies/wp-annotations/wp-manually-annotated.csv"};
	// Extra ontologies
	public static final String[] EXTRA_ONTOLOGY_FILES = {

			};
	// Traits
	public static final String TRAITS_FILE = "../data/phenotypes/wp4_traits.csv";
	// Wikipathways
	public static final String WIKIPATHWAYS_DIR = "../data/ontologies/wikipathways/wikipathways-20220110-rdf-wp/wp/";
	// Publication dois
	public static final String DOIS_FILE = "../data/publications/seed_publications.txt";
	// OIE fact triplets
	public static final String OIE_TRIPLES_FILE = "";
}
