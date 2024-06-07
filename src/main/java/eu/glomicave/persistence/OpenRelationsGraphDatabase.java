package eu.glomicave.persistence;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;

/**
 * Contains all database interface methods related to open relations.
 * 
 */

public class OpenRelationsGraphDatabase {
	
	private static final String FACT_SUBJECT = "subject";
	private static final String FACT_RELATION = "relation";
	private static final String FACT_OBJECT = "object";
	private static final String FACT_POLARITY = "polarity";
	private static final String FACT_MODALITY = "modality";
	private static final String FACT_ATTRIBUTION = "attribution";
	
	public static final String UID_FIELD = "uid";
	
	
	
	public static Node addFactNode(String subject, String relation, String object, String polarity, String modality, String attribution) {
		if (subject == null || subject.length() == 0 || relation == null || relation.length() == 0 
				|| object == null || object.length() == 0) {
			throw new IllegalStateException("'subject', 'relation' or 'object' must not be null or have length 0.");
		}
		
		String uniqueIdentifier = subject + "/" + relation + "/" + object;

		Node node = CoreGraphDatabase.createUIDNodeIfNotExistent(PredefinedCategories.FACT.toString(), uniqueIdentifier);
		CoreGraphDatabase.setProperty(node, FACT_SUBJECT, subject);
		CoreGraphDatabase.setProperty(node, FACT_RELATION, relation);
		CoreGraphDatabase.setProperty(node, FACT_OBJECT, object);
		CoreGraphDatabase.setProperty(node, FACT_POLARITY, polarity);
		CoreGraphDatabase.setProperty(node, FACT_MODALITY, modality);		
		CoreGraphDatabase.setProperty(node, FACT_ATTRIBUTION, attribution);				
		
		return node;
	}
	
	public static Node addPolarityNode(String polarity) {
		List<String> polarities = Arrays.asList("POSITIVE", "NEGATIVE");
		if (!polarities.contains(polarity.toUpperCase())) {
			throw new IllegalStateException("polarity should be 'POSITIVE' or 'NEGATIVE'");
		}
		
		Node node = CoreGraphDatabase.createUIDNodeIfNotExistent(PredefinedCategories.POLARITY.toString(), polarity.toUpperCase());

		return node;
	}

	public static Node addModalityNode(String modality) {
		List<String> modalities = Arrays.asList("CERTAINTY", "POSSIBILITY");
		if (!modalities.contains(modality.toUpperCase())) {
			throw new IllegalStateException("polarity should be of 'CERTAINTY' or 'POSSIBILITY'");
		}
		
		Node node = CoreGraphDatabase.createUIDNodeIfNotExistent(PredefinedCategories.MODALITY.toString(), modality.toUpperCase());

		return node;
	}
	
	public static Node addAttributionNode(String attribution) {
		Node node = CoreGraphDatabase.createUIDNodeIfNotExistent(PredefinedCategories.ATTRIBUTION.toString(), attribution);

		return node;
	}
	
	// Added 14.10.2022
	
	public static Node getLexicalFormNode(String lexicalFormIdentifier) {
		Node lexicalForm = CoreGraphDatabase.getUIDNode(PredefinedCategories.LEXICAL_FORM.toString(), lexicalFormIdentifier);
		// if unsuccessful, then use case non-sensitive search
		if (lexicalForm == null) {
			List<Record> records = CoreGraphDatabase.getNodesByLabelAndPropertyCaseInsensitive(PredefinedCategories.LEXICAL_FORM.toString(), UID_FIELD, lexicalFormIdentifier);
			if (records.size() >= 1) {
				lexicalForm = records.get(0).get(0).asNode();

				return lexicalForm;
			}
			else {
				return null;
			}
		}
		
		return lexicalForm;
	}
	
	public static Node getSentenceNode(String sentenceUID) {
		return CoreGraphDatabase.getUIDNode(PredefinedCategories.SENTENCE.toString(), sentenceUID);
	}
	
	public static void addSubjectObjectConnection(Node lexicalFormNodeSubj, Node lexicalFormNodeObj) {
		CoreGraphDatabase.createRelationshipIfNotExistent(lexicalFormNodeSubj, lexicalFormNodeObj, PredefinedRelations.OIE_RELATED_WITH.toString());		
		CoreGraphDatabase.createRelationshipIfNotExistent(lexicalFormNodeObj, lexicalFormNodeSubj, PredefinedRelations.OIE_RELATED_WITH.toString());		
	}
	
	public static Relationship addLexicalFormToFactConnection(Node lexicalFormNode, Node factNode) {
		return CoreGraphDatabase.createRelationshipIfNotExistent(lexicalFormNode, factNode, PredefinedRelations.HAS_FACT.toString());
	}
	
	public static Relationship addFactToPolarityConnection(Node factNode, Node polarityNode) {
		return CoreGraphDatabase.createRelationshipIfNotExistent(factNode, polarityNode, PredefinedRelations.HAS_POLARITY.toString());
	}
	
	public static Relationship addFactToModalityConnection(Node factNode, Node modalityNode) {
		return CoreGraphDatabase.createRelationshipIfNotExistent(factNode, modalityNode, PredefinedRelations.HAS_MODALITY.toString());
	}
	
	public static Relationship addFactToAttributionConnection(Node factNode, Node attributionNode) {
		return CoreGraphDatabase.createRelationshipIfNotExistent(factNode, attributionNode, PredefinedRelations.HAS_ATTRIBUTION.toString());
	}
	
	public static Relationship addFactToSentenceConnection(Node factNode, Node sentenceNode) {
		//return CoreGraphDatabase.createRelationshipIfNotExistent(factNode, sentenceNode, PredefinedRelations.APPEARS_IN.toString());
		return CoreGraphDatabase.createRelationshipIfNotExistent(factNode, sentenceNode, PredefinedRelations.FACT_APPEARS_IN.toString());
	
	}
	
}
