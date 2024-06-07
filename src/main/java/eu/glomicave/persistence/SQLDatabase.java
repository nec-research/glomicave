package eu.glomicave.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
//import java.util.Properties;

import eu.glomicave.config.SQLDatabaseConfig;

public class SQLDatabase {

	private static Connection connection;

	public static Connection getConnection() throws SQLException {
		//Properties props = new Properties();
		//setProperties(props);
		
		
		if (connection == null || connection.isClosed()) {
			connection = DriverManager.getConnection("jdbc:mysql://" + SQLDatabaseConfig.getInstance().getServerAddress() + ":" + SQLDatabaseConfig.getInstance().getServerPort() + "/" + SQLDatabaseConfig.getInstance().getDatabase() + "?" + "user=" + SQLDatabaseConfig.getInstance().getUsername() + "&password=" + SQLDatabaseConfig.getInstance().getPassword());
			//connection = DriverManager.getConnection(connectionUrl, props);
		}

		return connection;
	}

	public static void insertDOI(String doi, int source) {
		try {
			Connection connection = getConnection();
			PreparedStatement statement = connection.prepareStatement("INSERT IGNORE INTO publications (doi, source) VALUES (?, ?)");
			
			//PreparedStatement statement = connection.prepareStatement("SELECT * FROM nec_bronze.publications;");
				
			statement.setString(1, doi);
			statement.setInt(2, source);

			statement.execute();
			statement.close();
			
			
		}

		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static LinkedList<String> getNewDOIs() {
		try {
			Connection connection = getConnection();
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("SELECT doi FROM publications WHERE title IS NULL AND abstract IS NULL;");

			LinkedList<String> dois = new LinkedList<>();
			while (resultSet.next()) {
				dois.add(resultSet.getString("doi"));
			}

			statement.close();
			return dois;
		}

		catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static String getAbstract(int documentId) {
		try {
			Connection connection = getConnection();
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("SELECT abstract FROM " + SQLDatabaseTableGeneration.PUBLICATIONS + " WHERE id = " + documentId + ";");

			resultSet.next();

			String abstractString = resultSet.getString("abstract");
			statement.close();
			return abstractString;
		}

		catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static LinkedList<String> getAllDOIs() {
		try {
			Connection connection = getConnection();
			Statement statement = connection.createStatement();
			ResultSet resultSet = statement.executeQuery("SELECT doi FROM publications;");

			LinkedList<String> dois = new LinkedList<>();
			while (resultSet.next()) {
				dois.add(resultSet.getString("doi"));
			}

			statement.close();
			return dois;
		}

		catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static void updateTitleAndAbstract(String doi, String title, String abs) {
		try {
			Connection connection = getConnection();
			PreparedStatement statement = connection.prepareStatement("UPDATE publications SET title=?, abstract=? WHERE doi=?;");
			statement.setString(1, title);
			statement.setString(2, abs);
			statement.setString(3, doi);

			statement.execute();
			statement.close();
		}

		catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static int getGenerationSystemId(String generationSystemName, boolean insertIfAbsent) throws Exception {
		return getConstantStringId(generationSystemName, "generation_systems", insertIfAbsent);
	}

	private static int getConstantStringId(String constantStringName, String table, boolean insertIfAbsent) throws Exception {
		Connection connection = getConnection();
		PreparedStatement statement = connection.prepareStatement("SELECT id FROM " + table + " WHERE name=?;");
		statement.setString(1, constantStringName);

		ResultSet resultSet = statement.executeQuery();
		if (!resultSet.next()) {
			if (insertIfAbsent) {
				insertConstantString(constantStringName, table);
				return getConstantStringId(constantStringName, table, false);
			}

			else {
				throw new Exception("Single result query returned no result.");
			}
		}

		else {
			int id = resultSet.getInt("id");

			if (resultSet.next()) {
				throw new Exception("Single result query returned more than one result.");
			}

			statement.close();
			return id;
		}
	}

	private static void insertConstantString(String constantStringName, String table) {
		try {
			Connection connection = getConnection();
			PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table + " (name) VALUES (?)");
			statement.setString(1, constantStringName);

			statement.execute();
			statement.close();
		}

		catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static int getPublicationId(String doi) throws Exception {
		Connection connection = getConnection();
		PreparedStatement statement = connection.prepareStatement("SELECT id FROM publications WHERE doi=?;");
		statement.setString(1, doi);

		ResultSet resultSet = statement.executeQuery();
		resultSet.next();
		int id = resultSet.getInt("id");

		if (resultSet.next()) {
			throw new Exception("Single result query returned more than one result.");
		}
		statement.close();

		return id;
	}

}
