package eu.glomicave.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import eu.glomicave.pipelines.aws.FullProcessingPipelineAWS;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.AthenaClientBuilder;

public class AmazonAthenaConfig {
	private static final Logger logger = LogManager.getLogger(FullProcessingPipelineAWS.class);

	private static AmazonAthenaConfig instance = null;

	private AthenaClient athenaClient = null;
	
	private String athena_s3_bucket;
	private String athena_tables_s3_prefix;
	private String athena_output_s3_prefix;
	private String layer;
	private String workgroup;
	private String database;
	
    private Region region = Region.EU_WEST_1; // default region
    

	private AmazonAthenaConfig(String configFileLocation) {
		try {
			InputStream inputStream = new FileInputStream(new File(configFileLocation));
			Properties properties = new Properties();
			properties.loadFromXML(inputStream);
			inputStream.close();
			
			athena_s3_bucket = properties.getProperty("athena_s3_bucket");	// s3 bucket for Athene data
			athena_tables_s3_prefix = properties.getProperty("athena_tables_s3_prefix");	// where table data is stored
			athena_output_s3_prefix = properties.getProperty("athena_output_s3_prefix");	// where query logs are stored
			layer = properties.getProperty("layer");	// bronze, silver or gold
			workgroup = properties.getProperty("workgroup");
			database = workgroup + "_" + layer;  // "Schema" name in Athena properties

			ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create();
			
		    AthenaClientBuilder builder = AthenaClient.builder()
		            .region(region)
		            .credentialsProvider(credentialsProvider);
		    
		    athenaClient =  builder.build();
			
		} catch (Exception e) {
			e.printStackTrace();
			logger.fatal("Failed to configure Amazon Athena connection.", e);
		}
	}	
	
	public static AmazonAthenaConfig getInstance() {
		return instance;
	}

	public static void setupInstance(String configFileLocation) {
		if (instance == null) 
			instance = new AmazonAthenaConfig(configFileLocation);
	}
	
	public AthenaClient getAthenaClient() {
		return athenaClient;
	}

	public String getAthenaS3Bucket() {
		return athena_s3_bucket;
	}
	
	public String getAthenaTablesS3Prefix() {
		return athena_tables_s3_prefix;
	}
	
	public String getTablesBucket() {
		return athena_s3_bucket+"/"+athena_tables_s3_prefix;
	}

	public String getAthenaOutputS3Prefix() {
		return athena_output_s3_prefix;
	}
	
	public String getOutputBucket() {
		return athena_s3_bucket+"/"+athena_output_s3_prefix;
	}

	public String getLayer() {
		return layer;
	}
	
	public String getDatabase() {
		return database;
	}
}
