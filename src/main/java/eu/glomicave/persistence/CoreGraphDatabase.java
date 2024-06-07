package eu.glomicave.persistence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.neo4j.driver.Config;
import org.neo4j.driver.Config.TrustStrategy;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.neo4j.driver.GraphDatabase;

import eu.glomicave.config.GraphDatabaseConfig;
import eu.glomicave.persistence.PredefinedCategories;


/**
 * Contains generic database interface methods not related to any specific node or relation type
 */
public class CoreGraphDatabase {
	private static final Logger logger = LogManager.getLogger(CoreGraphDatabase.class);
	
	private static Driver driver = null;
	private static boolean driverIsClosed = false;
	
	public String toString()
	{
	  return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}

	private static Driver getDriver() {
		GraphDatabaseConfig instance = GraphDatabaseConfig.getInstance();
		
		if (instance == null) {
			return null;
		}
		
		if (instance.getGraphDB_type().toLowerCase().contains("neo4j")) {
			return getNeo4jDriver();
		} else if (instance.getGraphDB_type().toLowerCase().contains("neptune")) {
			return getNeptuneDriver();
		} else {
			return null;
		}
	}
	
	private static Driver getNeo4jDriver() {
		if (driver == null || driverIsClosed) {
			try {
				driver = GraphDatabase.driver(
						GraphDatabaseConfig.getInstance().getURI(),
						AuthTokens.basic(
								GraphDatabaseConfig.getInstance().getUsername(), 
								GraphDatabaseConfig.getInstance().getPassword())
				);
				driverIsClosed = false;
				
			} catch(Exception e) {
				logger.fatal("Error getting Neo4j driver", e);
			}
		}
		return driver;
	}
	
	private static Driver getNeptuneDriver() {
		if (driver == null || driverIsClosed) {
			try {
				driver = GraphDatabase.driver(
						GraphDatabaseConfig.getInstance().getURI(),
					    Config.builder().withConnectionTimeout(30, TimeUnit.SECONDS)
					    				.withEncryption()
					                    .withTrustStrategy(TrustStrategy.trustAllCertificates().withoutHostnameVerification())
					                    .build()
				);
				driverIsClosed = false;
				
			} catch(Exception e) {
				logger.fatal("Error getting Neptune driver", e);
			}
		}
		return driver;
	}

	public static void testConnection() throws ServiceUnavailableException {
		try {
			logger.info("Test connection to the graph database...");
			Session session = getDriver().session();
			Result result = session.run("MATCH (n) RETURN COUNT(*);");
			List<org.neo4j.driver.Record> records = result.list();
			if (records.size() > 0) {
				logger.info("Graph database of {} nodes.", records.get(0).get(0).asInt());
			}
			session.close();
			logger.info("Connection ok!");
		} catch (ServiceUnavailableException e) {
			logger.error("Unable to connect graph database! Check configuration or make sure that the database is running.", e);
			throw new ServiceUnavailableException("Can't reach graph database."); 
		}
	}
	
	/**
	 * Counts relations in the graph database of a specified type.
	 * 
	 * @param label
	 * @return
	 */
	public static int countRelationType(String label) {
		try {
			Session session = getDriver().session();
			Result result = session.run("MATCH ()-[r:" + label + "]->() RETURN count(r);");
			List<org.neo4j.driver.Record> records = result.list();
			if (records.size() > 0) {
				return records.get(0).get(0).asInt();
			}
			session.close();
		} catch (Exception e) {
			logger.error("Error running count query in Graph DB", e);
		}
		return -1;
	}
	
	/**
	 * Counts relations in the graph database of a specified type.
	 * 
	 * @param label
	 * @return
	 */
	public static int countNodeType(String label) {
		try {
			Session session = getDriver().session();
			Result result = session.run("MATCH (n:" + label + ") RETURN count(n);");
			List<org.neo4j.driver.Record> records = result.list();
			if (records.size() > 0) {
				return records.get(0).get(0).asInt();
			}
			session.close();
		} catch (Exception e) {
			logger.error("Error running count query in Graph DB", e);
		}
		return -1;
	}
	
	
	public static void closeDriver() {
		if (driver != null && !driverIsClosed) {
			driver.close();
			driverIsClosed = true;
		}
	}

	public static List<Record> runCypherQuery(String query) {
		try {
			Session session = getDriver().session();
			Result result = session.run(query);
			List<org.neo4j.driver.Record> records = result.list();
			session.close();
			return records;
		} catch (Exception e) {
			logger.error("Error running query in Graph DB: " + query, e);
		}
		return null;
	}

	private static List<Record> runParametrizedCypherQuery(String query, Map<String, Object> params) {
		List<org.neo4j.driver.Record> records = null;
		try (Session session = getDriver().session()) {
			Result result = session.run(query, params);
			records = result.list();
			
//			// debug
//			//System.out.println(query);
//			logger.debug("Query: {}", query);
//			for (Entry<String, Object> pair : params.entrySet()) {
//			    //System.out.println(String.format("Key is: %s, Value is : %s", pair.getKey(), pair.getValue()));  
//			    logger.debug("Key is: {}, Value is : {}", pair.getKey(), pair.getValue());
//			}
//			//System.out.println(records.size());
//			if (records.size() >= 1) {
//				//System.out.println(records.get(0).get(0).asNode());
//				logger.debug("Node object: {}",records.get(0).get(0).asNode());
//				//ReflectionToStringBuilder.toString(records.get(0).get(0).asNode(), ToStringStyle.SHORT_PREFIX_STYLE);
//				
//				Node o = records.get(0).get(0).asNode();
//				
//			    //System.out.println("An object: " + ReflectionToStringBuilder.toString(o));
//				logger.debug("An object: {}", ReflectionToStringBuilder.toString(o));
//			}
//			//// end of debug
			
			session.close();
		} catch (Exception e) {
			logger.error("Error running query in Graph DB: {}, params: {}, returned records: {}", query, params, records, e);
		}
		return records;
	}

	public static void clearDatabase() {
		//runCypherQuery("MATCH (n) DETACH DELETE n");
		//runCypherQuery("MATCH (n) RETURN distinct labels(n), count(*)");
		logger.info("Drop graph database.");
		
		for(PredefinedCategories category: PredefinedCategories.values()) {
			runCypherQuery("MATCH (n:" + category.toString() + ") DETACH DELETE n");
		}
	}

	public static Node addLabel(Node node, String label) {
		//return runCypherQuery("MATCH (n) WHERE ID(n) = " + node.id() + " SET n :" + label + " RETURN n").get(0).get(0).asNode();
		return runCypherQuery("MATCH (n) WHERE n.uid = " + node.get(UID_FIELD) + " SET n :" + label + " RETURN n").get(0).get(0).asNode();
	}

//	public static Node getNodeById(long id) {
//		return runCypherQuery("MATCH (n) WHERE id(n)=" + id + " RETURN n").get(0).get(0).asNode();
//	}
	
	public static Node getNodeById(String id) {
		return runCypherQuery("MATCH (n) WHERE n.uid=\"" + id + "\" RETURN n").get(0).get(0).asNode();
	}

	public static List<Node> getNodesByLabel(String label) {
		return runCypherQuery("MATCH (n:" + label + ") RETURN n").stream().map(record -> record.get(0).asNode()).collect(Collectors.toList());
	}
	
	public static List<Node> updateNodesByLabel(String label, String targetProperty, Object targetValue) {
		Map<String, Object> params = new HashMap<>();
		params.put("targetValue", targetValue);
		return runParametrizedCypherQuery("MATCH (n:" + label + ") SET n." + targetProperty + "=$targetValue  RETURN n", params)
																	.stream().map(record -> record.get(0).asNode()).collect(Collectors.toList());
	}
	
	public static List<Node> getNodesByLabelAndProperty(String label, String property, Object value) {
		Map<String, Object> params = new HashMap<>();
		params.put("value", value);
		return runParametrizedCypherQuery("MATCH (n:" + label + ") WHERE n." + property + "= $value RETURN n", params)
																	.stream().map(record -> record.get(0).asNode()).collect(Collectors.toList());
	}

	public static List<Record> getNodesByLabelAndProperty(String label, String property, String value) {
		Map<String, Object> params = new HashMap<>();
		params.put("value", value);
		return runParametrizedCypherQuery("MATCH (n:" + label + ") WHERE n." + property + "= $value RETURN n", params);
	}
	
	public static List<Record> getNodesByLabelAndPropertyCaseInsensitive(String label, String property, String value) {
		Map<String, Object> params = new HashMap<>();
		params.put("value", value);
		return runParametrizedCypherQuery("MATCH (n:" + label + ") WHERE toLower(n." + property + ")= toLower($value) RETURN n", params);
	}
	
	public static List<Node> updateNodesByLabelAndProperty(String label, String property, Object value, String targetProperty, Object targetValue) {
		Map<String, Object> params = new HashMap<>();
		params.put("value", value);
		params.put("targetValue", targetValue);
		return runParametrizedCypherQuery("MATCH (n:" + label + ") WHERE n." + property + "= $value SET n." + targetProperty + "=$targetValue  RETURN n", params)
																	.stream().map(record -> record.get(0).asNode()).collect(Collectors.toList());
	}

//	public static List<Record> deleteNodeAndRelationshipsByNodeId(long id) {
//		return runCypherQuery("MATCH (n) WHERE id(n)=" + id + " DETACH DELETE n");
//	}
	
	public static List<Record> deleteNodeAndRelationshipsByNodeId(String id) {
		return runCypherQuery("MATCH (n) WHERE n.uid=\"" + id + "\" DETACH DELETE n");
	}

	public static String UID_FIELD = "uid";

	public static Node getUIDNode(String label, String uniqueIdentifier) {
//		Node cacheNode = getUIDNodeFromCache(label, uniqueIdentifier);
//		if (cacheNode != null) {
//			System.out.println("hit");
//			return cacheNode;
//		}

		List<Record> records = getNodesByLabelAndProperty(label, UID_FIELD, uniqueIdentifier);

		if (records.size() > 1) {
			throw new IllegalStateException("Multiple nodes found for label: '" + label + "': " + ", uniqueIdentifier: '" + uniqueIdentifier + "'.");
		} else if (records.size() == 1) {
			Node retrievedNode = records.get(0).get(0).asNode();
//			putUIDNodeIntoCache(label, uniqueIdentifier, retrievedNode);
			return retrievedNode;
		} else {
			return null;
		}
	}

//	public synchronized static Node createUninitializedUIDNodeIfNotExistent(String label, String uniqueIdentifier) {
//		Node uidNode = getUIDNode(label, uniqueIdentifier);
//
//		Map<String, Object> params = new HashMap<>();
//		params.put("uniqueIdentifier", uniqueIdentifier);
//
//		if (uidNode == null) {
//			uidNode = runParametrizedCypherQuery("CREATE (n:" + label + " {" + UID_FIELD + ": $uniqueIdentifier}) RETURN n", params).get(0).get(0).asNode();
//			setProperty(uidNode, LEXICAL_FORM_INITIALIZED, Boolean.valueOf("false"));
//		}
//		// debug
//		//System.out.println("node element ID: " + uidNode.elementId() + " " + uidNode.get(UID_FIELD));
//		//
//
//		return uidNode;
//	}
	
	public synchronized static Node createUIDNodeIfNotExistent(String label, String uniqueIdentifier) {
		Node uidNode = getUIDNode(label, uniqueIdentifier);

		Map<String, Object> params = new HashMap<>();
		params.put("uniqueIdentifier", uniqueIdentifier);

		if (uidNode == null) {
			uidNode = runParametrizedCypherQuery("CREATE (n:" + label + " {" + UID_FIELD + ": $uniqueIdentifier}) RETURN n", params).get(0).get(0).asNode();
		}
		// debug
		//System.out.println("node element ID: " + uidNode.elementId() + " " + uidNode.get(UID_FIELD));
		//

		return uidNode;
	}

	public static void setProperty(Node node, String name, Object value) {
		Map<String, Object> params = new HashMap<>();
		params.put("value", value);

		//runParametrizedCypherQuery("MATCH (n) WHERE id(n)=" + node.id() + " SET n." + name + " = $value RETURN n", params).get(0).get(0).asNode();
		runParametrizedCypherQuery("MATCH (n) WHERE n.uid=" + node.get(UID_FIELD) + " SET n." + name + " = $value RETURN n", params).get(0).get(0).asNode();
	}
	
	public static void setPropertyAsList(Node node, String name, Object value) {
		Map<String, Object> params = new HashMap<>();
		params.put("value", value);

		//runParametrizedCypherQuery("MATCH (n) WHERE id(n)=" + node.id() + " SET n." + name + " = $value RETURN n", params).get(0).get(0).asNode();
		runParametrizedCypherQuery("MATCH (n) WHERE n.uid=" + node.get(UID_FIELD) + " SET n." + name + " = join($value,';') RETURN n", params).get(0).get(0).asNode();
	}

	private static List<Record> getRelationships(Node sourceNode, Node targetNode, String relationship) {
		String relationshipType = convertToRelationshipType(relationship);
		//return runCypherQuery("MATCH (source) -[r:" + relationshipType + "]-> (destination) WHERE ID(source) = " + sourceNode.id() + " AND ID(destination) = " + targetNode.id() + " RETURN r");
		return runCypherQuery("MATCH (source) -[r:" + relationshipType + "]-> (destination) WHERE source.uid = " + sourceNode.get(UID_FIELD) + " AND destination.uid = " + targetNode.get(UID_FIELD) + " RETURN r");
		
	}

	public synchronized static Relationship createRelationship(Node sourceNode, Node targetNode, String relationship) {
		//return createRelationship(sourceNode.id(), targetNode.id(), relationship);
		return createRelationship(sourceNode.get(UID_FIELD).asString(), targetNode.get(UID_FIELD).asString(), relationship);
	}

//	public static Relationship createRelationship(long sourceNodeId, long destinationNodeId, String relationship) {
//		String relationshipType = convertToRelationshipType(relationship);
//		return runCypherQuery("MATCH (source), (destination) WHERE ID(source) = " + sourceNodeId + " AND ID(destination) = " + destinationNodeId + " CREATE (source)-[r:" + relationshipType + "]->(destination) RETURN r").get(0).get("r").asRelationship();
//	}
	
	public synchronized static Relationship createRelationship(String sourceNodeId, String destinationNodeId, String relationship) {
		String relationshipType = convertToRelationshipType(relationship);
		return runCypherQuery("MATCH (source), (destination) WHERE source.uid = \"" + sourceNodeId + "\" AND destination.uid = \"" + destinationNodeId + "\" CREATE (source)-[r:" + relationshipType + "]->(destination) RETURN r").get(0).get("r").asRelationship();
		//return runCypherQuery("MATCH (source), (destination) WHERE source.uid = " + sourceNodeId + " AND destination.uid = " + destinationNodeId + " CREATE (source)-[r:" + relationshipType + "]->(destination) RETURN r").get(0).get("r").asRelationship();
	}

	public synchronized static Relationship createRelationshipIfNotExistent(Node sourceNode, Node targetNode, String relationship) {
		List<Record> records = getRelationships(sourceNode, targetNode, relationship);

		if (records.size() == 0) {
			return createRelationship(sourceNode, targetNode, relationship);
		} else if (records.size() == 1) {
			logger.warn("GraphDB data info: existing relationship will be returned for source {} and target {}.", sourceNode, targetNode);
			return records.get(0).get(0).asRelationship();
		} else {
			logger.warn("GraphDB data warning: {} relationships already exist for source {} and target {}.", records.size(), sourceNode, targetNode);
			//throw new IllegalStateException();
			return records.get(0).get(0).asRelationship();
		}
	}

//	public static int mergeRelationship(long sourceNodeId, long destinationNodeId, String relationship) {
//		String relationshipType = convertToRelationshipType(relationship);
//		return runCypherQuery("MATCH (source), (destination) WHERE ID(source) = " + sourceNodeId + " AND ID(destination) = " + destinationNodeId + " MERGE (source)-[r:" + relationshipType + "]->(destination) RETURN ID(r)").get(0).get("ID(r)").asInt();
//	
//	}
	
	public static int mergeRelationship(String sourceNodeId, String destinationNodeId, String relationship) {
		String relationshipType = convertToRelationshipType(relationship);
		//return runCypherQuery("MATCH (source), (destination) WHERE source.uid = " + sourceNodeId + " AND destination.uid = " + destinationNodeId + " MERGE (source)-[r:" + relationshipType + "]->(destination) RETURN ID(r)").get(0).get("ID(r)").asInt();
		return runCypherQuery("MATCH (source), (destination) WHERE source.uid = \"" + sourceNodeId + "\" AND destination.uid = \"" + destinationNodeId + "\" MERGE (source)-[r:" + relationshipType + "]->(destination) RETURN ID(r)").get(0).get("ID(r)").asInt();
	
	}

	private static String convertToRelationshipType(String stringToConvert) {
		String relationshipType = stringToConvert.replaceAll(" ", "_");
		return relationshipType;
	}

}
