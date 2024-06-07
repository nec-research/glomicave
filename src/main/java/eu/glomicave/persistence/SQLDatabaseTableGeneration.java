package eu.glomicave.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import eu.glomicave.pipelines.aws.AddPublicationsAWS;

public class SQLDatabaseTableGeneration {
	
	private static final Logger logger = LogManager.getLogger(SQLDatabaseTableGeneration.class);

	public static final String PUBLICATIONS = "publications";
	public static final String SENTENCES = "sentences";

	public static void createTables() {
		logger.info("Generate table '{}' in SQL database", PUBLICATIONS);
		createPublicationsTable();
		logger.info("Generate table '{}' in SQL database", SENTENCES);
		createSentencesTable();
		logger.info("SQL tables were generated.");
	}

	public static void dropTables() {
		logger.info("Drop table '{}' from SQL database", PUBLICATIONS);
		executeQuery("DROP TABLE IF EXISTS " + PUBLICATIONS + ";");
		logger.info("Drop table '{}' from SQL database", SENTENCES);
		executeQuery("DROP TABLE IF EXISTS " + SENTENCES + ";");
		logger.info("SQL database was dropped.");
	}

	private static void createPublicationsTable() {
		executeQuery(String.format("""
				CREATE TABLE IF NOT EXISTS `%s` (
				`id` int(11) unsigned NOT NULL AUTO_INCREMENT,
				`doi` VARCHAR(255) NOT NULL DEFAULT '' COLLATE 'utf8_general_ci',
				`source` INT(11) NULL DEFAULT NULL,
				`title` TEXT(65535) NULL DEFAULT NULL COLLATE 'utf8_general_ci',
				`abstract` TEXT(65535) NULL DEFAULT NULL COLLATE 'utf8_general_ci',
				PRIMARY KEY (`id`),
				UNIQUE KEY `doi` (`doi`) USING HASH
				) ENGINE=InnoDB DEFAULT CHARSET=utf8;""", PUBLICATIONS));
	}

	private static void createSentencesTable() {
		executeQuery(String.format("""
				CREATE TABLE IF NOT EXISTS `%s` (
				`id` int(11) unsigned NOT NULL AUTO_INCREMENT,
				`publication_id` int(11) DEFAULT NULL,
				`idx` int(11) NOT NULL,
				`text` text NOT NULL,
				PRIMARY KEY (`id`)
				) ENGINE=InnoDB DEFAULT CHARSET=utf8;""", SENTENCES));
	}

	private static void executeQuery(String query) {
		try {
			Connection connection = SQLDatabase.getConnection();
			Statement statement = connection.createStatement();
			statement.execute(query);
			statement.close();
		}

		catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
