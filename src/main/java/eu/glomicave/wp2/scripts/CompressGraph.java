package eu.glomicave.wp2.scripts;

import java.util.List;

import org.neo4j.driver.Record;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;

import eu.glomicave.persistence.CoreGraphDatabase;
import eu.glomicave.persistence.PredefinedCategories;
import eu.glomicave.persistence.PredefinedRelations;

public class CompressGraph {
	
	public static String UID_FIELD = "uid";

	public static void main(String[] args) {

		// delete concepts that are only connected to a single instance node:
		CoreGraphDatabase.runCypherQuery("match (n)-[:" + PredefinedRelations.IS.toString() + "]->(m) where apoc.node.degree(m)=1 DETACH DELETE m");

		// delete purely transitive concepts:
		CoreGraphDatabase.runCypherQuery("match (n)-[:" + PredefinedRelations.IS.toString() + "]->(m)-[:" + PredefinedRelations.IS.toString() + "]->(o) where apoc.node.degree(m)=2 " //
				+ "create (n)-[:" + PredefinedRelations.IS.toString() + "]->(o) " //
				+ "detach delete m");

		// merge adjective and -ness nodes:
		List<Record> newNodeRecords = CoreGraphDatabase.runCypherQuery("MATCH (n)-[:" + PredefinedRelations.ADJECTIVE_OF.toString() + "]->(m) " //
				+ "CREATE (newNode:" + PredefinedCategories.ADJ_NOUN.toString() + " {text: n.text+\"/\"+m.text}) " //
				+ "return n, m, newNode");

		for (Record newNodeRecord : newNodeRecords) {
			Node newNode = newNodeRecord.get("newNode").asNode();
			Node n = newNodeRecord.get("n").asNode();
			List<Record> records = CoreGraphDatabase.runCypherQuery("MATCH (l)-[r]->(n) WHERE id(n)=" + n.id() + " return l, r");
			for (Record record : records) {
				Node l = record.get("l").asNode();
				Relationship r = record.get("r").asRelationship();
				//CoreGraphDatabase.createRelationship(l.id(), newNode.id(), r.type());
				CoreGraphDatabase.createRelationship(l.get(UID_FIELD).asString(), newNode.get(UID_FIELD).asString(), r.type());
			}

			Node m = newNodeRecord.get("m").asNode();
			records = CoreGraphDatabase.runCypherQuery("MATCH (l)-[r]->(m) WHERE id(m)=" + m.id() + " return l, r");
			for (Record record : records) {
				Node l = record.get("l").asNode();
				Relationship r = record.get("r").asRelationship();
				//CoreGraphDatabase.createRelationship(l.id(), newNode.id(), r.type());
				CoreGraphDatabase.createRelationship(l.get(UID_FIELD).asString(), newNode.get(UID_FIELD).asString(), r.type());
			}

			//CoreGraphDatabase.deleteNodeAndRelationshipsByNodeId(n.id());
			//CoreGraphDatabase.deleteNodeAndRelationshipsByNodeId(m.id());
			CoreGraphDatabase.deleteNodeAndRelationshipsByNodeId(n.get(UID_FIELD).asString());
			CoreGraphDatabase.deleteNodeAndRelationshipsByNodeId(m.get(UID_FIELD).asString());
		}

		// remove relationship hierarchy:
		List<Record> records = CoreGraphDatabase.runCypherQuery("MATCH (m)-[]->(n:" + PredefinedCategories.RELATION_NODE.toString() + ")-[r]->(o) WHERE type(r)<>'" + PredefinedRelations.REL_INSTANCE_OF.toString() + "' return n,m,o");
		for (Record record : records) {
			Node m = record.get("m").asNode();
			Node n = record.get("n").asNode();
			Node o = record.get("o").asNode();

			//CoreGraphDatabase.createRelationship(m.id(), o.id(), n.get("text").asString());
			CoreGraphDatabase.createRelationship(m.get(UID_FIELD).asString(), o.get(UID_FIELD).asString(), n.get("text").asString());
			//CoreGraphDatabase.deleteNodeAndRelationshipsByNodeId(n.id());
			CoreGraphDatabase.deleteNodeAndRelationshipsByNodeId(n.get(UID_FIELD).asString());
		}

		// delete all sub-concepts that are not used:
		while (CoreGraphDatabase.runCypherQuery("MATCH (m)-[r:" + PredefinedRelations.IS + "]->(n) WHERE apoc.node.degree(m)=1 RETURN count(m)").get(0).get("count(m)").asInt() > 0) {
			records = CoreGraphDatabase.runCypherQuery("MATCH (m)-[r:" + PredefinedRelations.IS + "]->(n) WHERE apoc.node.degree(m)=1 DETACH DELETE m");
		}

		// delete all unconnected nodes:
		CoreGraphDatabase.runCypherQuery("match (n) where apoc.node.degree(n)=0 DELETE n");

		// replace JOINs:
		records = CoreGraphDatabase.runCypherQuery("MATCH (n)-[:" + PredefinedRelations.JOIN + "]->()-[r]->(o) return n,o,r");
		for (Record record : records) {
			Node n = record.get("n").asNode();
			Node o = record.get("o").asNode();
			Relationship r = record.get("r").asRelationship();

			//CoreGraphDatabase.mergeRelationship(n.id(), o.id(), r.type());
			CoreGraphDatabase.mergeRelationship(n.get(UID_FIELD).asString(), o.get(UID_FIELD).asString(), r.type());
		}
		records = CoreGraphDatabase.runCypherQuery("MATCH ()-[:" + PredefinedRelations.JOIN + "]->(m) return m");
		for (Record record : records) {
			Node m = record.get("m").asNode();
			//CoreGraphDatabase.deleteNodeAndRelationshipsByNodeId(m.id());
			CoreGraphDatabase.deleteNodeAndRelationshipsByNodeId(m.get(UID_FIELD).asString());
		}

		CoreGraphDatabase.closeDriver();
	}
}
