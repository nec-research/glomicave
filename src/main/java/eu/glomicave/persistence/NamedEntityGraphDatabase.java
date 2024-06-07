package eu.glomicave.persistence;

import java.util.List;

import org.neo4j.driver.Record;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;

/**
 * Contains all database interface methods related to named entities.
 */
public class NamedEntityGraphDatabase {

	private static final String NAMED_ENTITY_SOURCE = "source";
	private static final String NAMED_ENTITY_IDENTIFIER = "identifier";
	private static final String LEXICAL_FORM_INITIALIZED = "initialized"; 

	public static Node addNamedEntityNode(String source, String identifier) {
		String uniqueIdentifier = createUniqueIdentifier(source, identifier);
		Node node = CoreGraphDatabase.createUIDNodeIfNotExistent(PredefinedCategories.NAMED_ENTITY.toString(), uniqueIdentifier);
		CoreGraphDatabase.setProperty(node, NAMED_ENTITY_SOURCE, source);
		CoreGraphDatabase.setProperty(node, NAMED_ENTITY_IDENTIFIER, identifier);
		return node;
	}

	private static String createUniqueIdentifier(String source, String identifier) {
		if (source == null || source.length() == 0 || identifier == null || identifier.length() == 0) {
			throw new IllegalStateException("'source' and 'identifier' must not be null or have length 0.");
		} else {
			return source + "/" + identifier;
		}
	}

	public static Node addLexicalFormNode(String lexicalForm) {
		//return CoreGraphDatabase.createUIDNodeIfNotExistent(PredefinedCategories.LEXICAL_FORM.toString(), lexicalForm);
		Node node = CoreGraphDatabase.createUIDNodeIfNotExistent(PredefinedCategories.LEXICAL_FORM.toString(), lexicalForm);
		CoreGraphDatabase.setProperty(node, LEXICAL_FORM_INITIALIZED, Boolean.valueOf("false"));
		return node;
		
	}

	public static Relationship addNamedEntitiyToLexicalFormConnection(Node namedEntityNode, Node lexicalFormNode) {
		return CoreGraphDatabase.createRelationshipIfNotExistent(namedEntityNode, lexicalFormNode, PredefinedRelations.HAS_LF.toString());
	}

	public static List<Record> getLexicalFormsOfNamedEntities(String source) {
		return CoreGraphDatabase.runCypherQuery("MATCH (n:" + PredefinedCategories.NAMED_ENTITY.toString() + ")-->(l:LEXICAL_FORM) WHERE n.source='" + source + "' RETURN l");
	}

}
