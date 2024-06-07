package eu.glomicave.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import eu.glomicave.pipelines.aws.FullProcessingPipelineAWS;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;


public class AmazonS3Config {
	private static final Logger logger = LogManager.getLogger(FullProcessingPipelineAWS.class);

	private static AmazonS3Config instance = null;
	
	private S3Client amazonS3Client = null;
	
	// ToDo: Implement option when credentials are read from a file: https://howtodoinjava.com/aws/s3client-read-file/
	private String aws_access_key_id;
	private String aws_secret_access_key;
	private String bucketName;

    private Region region = Region.EU_WEST_1; // default region
    

	private AmazonS3Config(String configFileLocation) {
		try {
			InputStream inputStream = new FileInputStream(new File(configFileLocation));
			Properties properties = new Properties();
			properties.loadFromXML(inputStream);
			inputStream.close();

			region = Region.of(properties.getProperty("region"));
			bucketName = properties.getProperty("bucket_name");
			aws_access_key_id = properties.getProperty("aws_access_key_id");
			aws_secret_access_key = properties.getProperty("aws_secret_access_key");
			
			ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create();
			
			amazonS3Client = S3Client.builder()
			        .region(region)
			        .credentialsProvider(credentialsProvider)
			        .build();

		} catch (Exception e) {
			e.printStackTrace();
			logger.fatal("Failed to configure Amazon S3 access", e);
		}
	}

	public static AmazonS3Config getInstance() {
		return instance;
	}
	
	public static void setupInstance(String configFileLocation) {
		if (instance == null) 
			instance = new AmazonS3Config(configFileLocation);
	}

	public S3Client getS3Client() {
		return amazonS3Client;
	}
	
	public String getBucketName() {
		return bucketName;
	}
	
	public void reconnect() {
		if (amazonS3Client != null) {
			amazonS3Client.close();
		}
		try {
			ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create();
			
			amazonS3Client = S3Client.builder()
			        .region(region)
			        .credentialsProvider(credentialsProvider)
			        .overrideConfiguration(
			                b -> b.apiCallTimeout(Duration.ofSeconds(3600))
			                      .apiCallAttemptTimeout(Duration.ofMillis(36000)))
			        .build();
			
		} catch (Exception e) {
			e.printStackTrace();
			logger.fatal("Failed to configure Amazon S3 access", e);
		}
	}
}
