package eu.glomicave.wp3.ms3_1;

import org.neo4j.driver.types.Node;

import eu.glomicave.persistence.CoreGraphDatabase;
import eu.glomicave.persistence.NamedEntityGraphDatabase;

public class AddExemplaryWP4HiddenRelationshipPrediction {

	public static String PREDICTOR = "PREDICTOR";
	public static String PREDICTION = "PREDICTION";

	public static String MAKES_PREDICTION = "MAKES_PREDICTION";
	public static String INPUT = "INPUT";
	public static String TARGET = "TARGET";

	public static String PREDICTABILITY = "Predictability";

	public static void main(String[] args) {
		CoreGraphDatabase.runCypherQuery("MATCH (n)-[r:" + MAKES_PREDICTION + "]->() DELETE r");
		CoreGraphDatabase.runCypherQuery("MATCH (n)-[r:" + INPUT + "]->() DELETE r");
		CoreGraphDatabase.runCypherQuery("MATCH (n)-[r:" + TARGET + "]->() DELETE r");

		CoreGraphDatabase.runCypherQuery("MATCH (n:" + PREDICTOR + ") DETACH DELETE n");
		CoreGraphDatabase.runCypherQuery("MATCH (n:" + PREDICTION + ") DETACH DELETE n");

		Node predictorNode1 = NamedEntityGraphDatabase.addNamedEntityNode("WP4", "M:33");
		CoreGraphDatabase.addLabel(predictorNode1, PREDICTOR);
		Node predictionNode1 = NamedEntityGraphDatabase.addNamedEntityNode("WP4", "M:33_P1");
		CoreGraphDatabase.setProperty(predictionNode1, PREDICTABILITY, 0.7349);
		CoreGraphDatabase.createRelationship(predictorNode1, predictionNode1, MAKES_PREDICTION);

		Node predictorNode2 = NamedEntityGraphDatabase.addNamedEntityNode("WP4", "T:345");
		CoreGraphDatabase.addLabel(predictorNode2, PREDICTOR);
		Node predictionNode2 = NamedEntityGraphDatabase.addNamedEntityNode("WP4", "T:345_P1");
		CoreGraphDatabase.setProperty(predictionNode2, PREDICTABILITY, 0.8345);
		CoreGraphDatabase.createRelationship(predictorNode2, predictionNode2, MAKES_PREDICTION);

//		Node alphaDglucopyranoseNode = GraphDatabase.getNodeById(XXXXX); // TODO: not in database
//		GraphDatabase.createRelationship(predictionNode1, alphaDglucopyranoseNode, TARGET);

		//Node HXK2Node = CoreGraphDatabase.getNodeById(13234);
		Node HXK2Node = CoreGraphDatabase.getNodeById("13234");
		CoreGraphDatabase.createRelationship(predictionNode2, HXK2Node, INPUT);

		//Node tomatoYieldNode = CoreGraphDatabase.getNodeById(120584);
		Node tomatoYieldNode = CoreGraphDatabase.getNodeById("120584");
		CoreGraphDatabase.createRelationship(predictionNode1, tomatoYieldNode, TARGET);
		CoreGraphDatabase.createRelationship(predictionNode2, tomatoYieldNode, TARGET);

		CoreGraphDatabase.closeDriver();
	}

}
