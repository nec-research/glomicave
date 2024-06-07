package eu.glomicave.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class SQLDatabaseConfig {

	private static SQLDatabaseConfig instance = null;

	private String serverAddress;
	private String serverPort;
	private String database;
	private String username;
	private String password;

	private SQLDatabaseConfig(String configFileLocation) {
		try {
			InputStream inputStream = new FileInputStream(new File(configFileLocation));
			Properties properties = new Properties();
			properties.loadFromXML(inputStream);
			inputStream.close();

			serverAddress = properties.getProperty("server_address");
			serverPort = properties.getProperty("server_port");
			database = properties.getProperty("database");
			username = properties.getProperty("username");
			password = properties.getProperty("password");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static SQLDatabaseConfig getInstance() {
		return instance;
	}

	public static void setupInstance(String configFileLocation) {
		instance = new SQLDatabaseConfig(configFileLocation);
	}

	public String getServerAddress() {
		return serverAddress;
	}

	public String getServerPort() {
		return serverPort;
	}

	public String getDatabase() {
		return database;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}
}
