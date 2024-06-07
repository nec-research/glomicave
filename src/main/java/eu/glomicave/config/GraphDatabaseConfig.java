package eu.glomicave.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.neo4j.driver.Config.TrustStrategy;

import eu.glomicave.pipelines.aws.FullProcessingPipelineAWS;

public class GraphDatabaseConfig {
	private static final Logger logger = LogManager.getLogger(FullProcessingPipelineAWS.class);

	private static GraphDatabaseConfig instance = null;

	private String db_type;
	private String server_address;
	private String server_port;
	private String username="";
	private String password="";
	private String aws_region="";

	private GraphDatabaseConfig(String configFileLocation) {
		try {
			InputStream inputStream = new FileInputStream(new File(configFileLocation));
			Properties properties = new Properties();
			properties.loadFromXML(inputStream);
			inputStream.close();
			
			db_type = properties.getProperty("graphdb"); // neptune or neo4j
			
			// put ENV variable, e.g. GRAPH_INT_DATABASE_ENDPOINT name into file
			// If files exists and non-empty - get information from ENV variable, else - from the field
			// String sysEnvStr = System.getenv("JAVA_HOME");
			
			server_address = properties.getProperty("server_address");
			server_port = properties.getProperty("server_port");
			
			if (db_type.toLowerCase().contains("neo4j")) {
				username = properties.getProperty("username");
				password = properties.getProperty("password");
			} else if (db_type.toLowerCase().contains("neptune")) {
				aws_region = properties.getProperty("aws_region");
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			logger.fatal("Failed to configure graph DB connection.", e);
		}
	}

	public static GraphDatabaseConfig getInstance() {
		return instance;
	}

	public static void setupInstance(String configFileLocation) {
		instance = new GraphDatabaseConfig(configFileLocation);
	}

	public String getGraphDB_type() {
		return db_type;
	}
	
	public String getServer_address() {
		return server_address;
	}

	public String getServer_port() {
		return server_port;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}
	
	public String getURI() {
		return "bolt://" + server_address + ":" + server_port;
	}
}
