package eu.glomicave.persistence;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.internal.InternalEntity;
import org.neo4j.driver.internal.InternalNode;
import org.neo4j.driver.internal.InternalRelationship;
import org.neo4j.driver.internal.value.StringValue;

public class GraphGeneration {
	private static long nextNodeId = 0;
	private static long nextRelationshipId = 0;

	public static InternalNode createNode(String label, String text) {
		Set<String> labels = new HashSet<String>();
		labels.add(label);
		Map<String, Value> properties = new HashMap<>();
		properties.put("text", new StringValue(text));

		return new InternalNode(nextNodeId++, labels, properties);
	}

	public static HashMap<String, InternalEntity> createNodeChain(String... texts) {
		return createNodeChain(null, texts);
	}

	public static HashMap<String, InternalEntity> createNodeChain(InternalNode parent, String... texts) {
		HashMap<String, InternalEntity> entities = new HashMap<>();

		String text = texts[texts.length - 1];
		if (parent != null) { // add parent text to text
			String parentText = parent.get("text").asString();
			text = text + " " + parentText;
		}

		InternalNode InternalNode = createNode(PredefinedCategories.CONCEPT.toString(), text);
		entities.put(text, InternalNode);

		if (parent != null) { // create parent InternalRelationship
			entities.put(InternalNode.get("text") + "_IS_" + parent.get("text"), createRelationship(InternalNode, parent, PredefinedRelations.IS.toString()));

		}

		InternalNode previousInternalNode = InternalNode;
		for (int index = texts.length - 2; index >= 0; index--) {
			text = texts[index] + " " + text;
			InternalNode = createNode(PredefinedCategories.CONCEPT.toString(), text);
			entities.put(text, InternalNode);
			entities.put("text_IS_" + previousInternalNode.get("text"), createRelationship(InternalNode, previousInternalNode, PredefinedRelations.IS.toString()));
			previousInternalNode = InternalNode;
		}

		return entities;
	}

	public static List<InternalEntity> createRelationship(String sourceText, InternalNode destination, String type) {
		InternalNode source = createNode(PredefinedCategories.CONCEPT.toString(), sourceText);
		InternalRelationship InternalRelationship = createRelationship(source, destination, type);
		return Arrays.asList(source, destination, InternalRelationship);
	}

	public static List<InternalEntity> createRelationship(InternalNode source, String destinationText, String type) {
		InternalNode destination = createNode(PredefinedCategories.CONCEPT.toString(), destinationText);
		InternalRelationship InternalRelationship = createRelationship(source, destination, type);
		return Arrays.asList(source, destination, InternalRelationship);
	}

	public static InternalRelationship createRelationship(InternalNode source, InternalNode destination, PredefinedRelations predefinedRelation) {
		return createRelationship(source, destination, predefinedRelation.toString());
	}

	public static InternalRelationship createRelationship(InternalNode source, InternalNode destination, String type) {
		InternalRelationship InternalRelationship = new InternalRelationship(nextRelationshipId, source.id(), destination.id(), type);
		nextRelationshipId++;
		return InternalRelationship;
	}

	public static List<InternalRelationship> createNodeTypedRelationship(InternalNode source, InternalNode relationInternalNode, InternalNode destination, PredefinedRelations predefinedRelation) {
		return createNodeTypedRelationship(source, relationInternalNode, destination, predefinedRelation.toString());
	}

	public static HashMap<String, InternalEntity> createANDCompound(String element1Text, String element2Text) {
		HashMap<String, InternalEntity> entities = new HashMap<>();

		InternalNode element1 = createNode(PredefinedCategories.CONCEPT.toString(), element1Text);
		entities.put("element1", element1);
		InternalNode element2 = createNode(PredefinedCategories.CONCEPT.toString(), element2Text);
		entities.put("element2", element2);

		InternalNode compound = createNode(PredefinedCategories.CONCEPT.toString(), element1Text + " AND " + element2Text);
		entities.put("compound", compound);
		entities.put("element1ANDCompound", createRelationship(compound, element1, PredefinedRelations.AND.toString()));
		entities.put("element2ANDCompound", createRelationship(compound, element2, PredefinedRelations.AND.toString()));

		return entities;
	}

	public static InternalRelationship createRelationship(InternalNode source, InternalNode destination, String type, String modifier) {
		Map<String, Value> properties = Map.of("modifier", Values.value(modifier));
		InternalRelationship relationship = new InternalRelationship(nextRelationshipId, source.id(), destination.id(), type, properties);
		nextRelationshipId++;
		return relationship;
	}

	public static HashMap<String, InternalEntity> createANDCompoundWithBaseNode(String baseNodeText, String element1Text, String element2Text) {
		HashMap<String, InternalEntity> entities = createANDCompound(element1Text + " " + baseNodeText, element2Text + " " + baseNodeText);

		InternalNode baseNode = createNode(PredefinedCategories.CONCEPT.toString(), baseNodeText);
		entities.put("baseNode", baseNode);

		entities.put("element1ISBaseNode", createRelationship((InternalNode) entities.get("element1"), baseNode, PredefinedRelations.IS.toString()));
		entities.put("element2ISBaseNode", createRelationship((InternalNode) entities.get("element2"), baseNode, PredefinedRelations.IS.toString()));
		return entities;
	}

	private static List<InternalRelationship> createNodeTypedRelationship(InternalNode source, InternalNode relationInternalNode, InternalNode destination, String type) {
		InternalRelationship InternalRelationship1 = new InternalRelationship(nextRelationshipId, source.id(), relationInternalNode.id(), type);
		nextRelationshipId++;

		InternalRelationship InternalRelationship2 = new InternalRelationship(nextRelationshipId, relationInternalNode.id(), destination.id(), type);
		nextRelationshipId++;
		return Arrays.asList(InternalRelationship1, InternalRelationship2);
	}
}
