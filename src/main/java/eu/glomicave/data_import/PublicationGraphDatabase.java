/**
 * Contains all database interface methods related to publications.
 */

package eu.glomicave.data_import;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;

import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import eu.glomicave.persistence.CoreGraphDatabase;
import eu.glomicave.persistence.PredefinedCategories;
import eu.glomicave.persistence.PredefinedRelations;
import eu.glomicave.config.GlobalParamsConfig;
import eu.glomicave.data_import.extract_entities.ExtractEntities;


public class PublicationGraphDatabase {
	private static final Logger logger = LogManager.getLogger(PublicationGraphDatabase.class);
	
	private static final int ABRIDGE_MAX_NUM = 100;
	
	public static String UID_FIELD = "uid";
	public static String LEXICAL_FORM_INITIALIZED = "initialized"; 
	
	private static HashMap<String, Node> lexicalFormToNode;
	private static HashMap<String, Node> lexicalFormLowerCaseToNode;
	
	private static StanfordCoreNLP pipeline;
	
	static int poolSize = GlobalParamsConfig.MAX_POOL_SIZE;
	//static int poolSize = 11;

	static {
		Properties props = new Properties();
		props.setProperty("annotators", "tokenize,ssplit");
		pipeline = new StanfordCoreNLP(props);
	}

	
	/** 
	 * Create publication node in GraphDB.
	 * 
	 * @param publication
	 * @return
	 */
	static Node createPublicationNodeWithProperties(Publication publication) {
		Node publicationNode = CoreGraphDatabase.createUIDNodeIfNotExistent(PredefinedCategories.PUBLICATION.toString(), publication.doi);

		CoreGraphDatabase.setProperty(publicationNode, "sqlId", publication.sqlId);
		CoreGraphDatabase.setProperty(publicationNode, "doi", publication.doi);
		CoreGraphDatabase.setProperty(publicationNode, "s2Id", publication.s2Id);
		CoreGraphDatabase.setProperty(publicationNode, "title", publication.title);
		CoreGraphDatabase.setProperty(publicationNode, "year", publication.year);
		CoreGraphDatabase.setProperty(publicationNode, "authors", publication.authors);
		CoreGraphDatabase.setProperty(publicationNode, "paperAbstract", publication.paperAbstract);
		//publicationNode = CoreGraphDatabase.getNodeById(publicationNode.id());
		publicationNode = CoreGraphDatabase.getNodeById(publicationNode.get(UID_FIELD).asString());

		return publicationNode;
	}


	/** Initialize mappings between lexical form texts and node instances in graphDB.
	 * 
	 */
	public static void initializeLexicalFormNodeMaps() {
		logger.info("*** Initializing mappings to lexical forms nodes ***");
		
		if (lexicalFormToNode == null || lexicalFormLowerCaseToNode == null) {
			lexicalFormToNode = new HashMap<String, Node>();
			lexicalFormLowerCaseToNode = new HashMap<String, Node>();

			//List<Node> lexicalFormNodes = CoreGraphDatabase.getNodesByLabel(PredefinedCategories.LEXICAL_FORM.toString());
			// get all lexical forms and set the initialization property to 'initialized'
			List<Node> lexicalFormNodes = CoreGraphDatabase.updateNodesByLabel(
											PredefinedCategories.LEXICAL_FORM.toString(), 
											LEXICAL_FORM_INITIALIZED, Boolean.valueOf("true"));
			
			for (Node node : lexicalFormNodes) {
				String lexicalFormString = node.get(CoreGraphDatabase.UID_FIELD).asString();
				lexicalFormToNode.put(lexicalFormString, node);

				String lexicalFormLowerCaseString = node.get(CoreGraphDatabase.UID_FIELD).asString().toLowerCase();
				lexicalFormLowerCaseToNode.put(lexicalFormLowerCaseString, node);
				
				// set lexical form node as initialized
				//CoreGraphDatabase.setProperty(node, LEXICAL_FORM_INITIALIZED, Boolean.valueOf("true"));
			}
		}

		logger.info("lexicalFormToNode: {} initialized.", lexicalFormToNode.size());
		logger.info("lexicalFormLowerCaseToNode: {} initialized.", lexicalFormLowerCaseToNode.size());
	}
	
	
	/** 
	 * Initialize mappings between lexical form texts and node instances in graphDB 
	 * only for nodes with the property 'initialized' = False
	 */
	public static void initializeNewLexicalFormNodeMaps() {
		logger.info("*** Initializing mappings to recently added lexical forms nodes ***");
		
		if (lexicalFormToNode == null || lexicalFormLowerCaseToNode == null) {
			lexicalFormToNode = new HashMap<String, Node>();
			lexicalFormLowerCaseToNode = new HashMap<String, Node>();

			// get only not-initialized lexical forms and set them to 'initialized'
			List<Node> lexicalFormNodes = CoreGraphDatabase.updateNodesByLabelAndProperty(
												PredefinedCategories.LEXICAL_FORM.toString(), 
												LEXICAL_FORM_INITIALIZED, Boolean.valueOf("false"),
												LEXICAL_FORM_INITIALIZED, Boolean.valueOf("true"));
			
			for (Node node : lexicalFormNodes) {
				String lexicalFormString = node.get(CoreGraphDatabase.UID_FIELD).asString();
				lexicalFormToNode.put(lexicalFormString, node);

				String lexicalFormLowerCaseString = node.get(CoreGraphDatabase.UID_FIELD).asString().toLowerCase();
				lexicalFormLowerCaseToNode.put(lexicalFormLowerCaseString, node);
				
				// set lexical form node as initialized
				//CoreGraphDatabase.setProperty(node, LEXICAL_FORM_INITIALIZED, Boolean.valueOf("true"));
			}
		}

		logger.info("lexicalFormToNode: {} initialized.", lexicalFormToNode.size());
		logger.info("lexicalFormLowerCaseToNode: {} initialized.", lexicalFormLowerCaseToNode.size());
	}

	
	/** 
	 * Connect lexical_form nodes to a sentence node based on simple single token matches.
	 * 
	 */
	public static void connectSentenceNodeWithLexicalFormNodesSingleTokens(Node sentenceNode) {
		//CoreGraphDatabase.runCypherQuery("MATCH (n)-[r:" + PredefinedRelations.APPEARS_IN.toString() + "]->() WHERE id(n)=" + sentenceNode.elementId() + " DELETE r");
		CoreGraphDatabase.runCypherQuery("MATCH (n)-[r:" + PredefinedRelations.APPEARS_IN.toString() + "]->() WHERE n.uid=" + sentenceNode.get(UID_FIELD) + " DELETE r");
		//CoreGraphDatabase.runCypherQuery("MATCH (n)-[r:" + PredefinedRelations.APPEARS_IN_LOWERCASE.toString() + "]->() WHERE id(n)=" + sentenceNode.elementId() + " DELETE r");
		CoreGraphDatabase.runCypherQuery("MATCH (n)-[r:" + PredefinedRelations.APPEARS_IN_LOWERCASE.toString() + "]->() WHERE n.uid=" + sentenceNode.get(UID_FIELD) + " DELETE r");

		Value value = sentenceNode.get("tokens");
		//List<String> tokens = value.asList(t -> t.toString());
		List<String> tokens = Arrays.asList(value.toString().split(";"));
		tokens = tokens.stream().map(t -> t.substring(1, t.length() - 1)).collect(Collectors.toList()); 
//			System.out.println(tokens);

		for (String token : tokens) {
			if (lexicalFormToNode.containsKey(token)) {
//					System.out.println("found: " + token);
				Node lexicalFormNode = lexicalFormToNode.get(token);
				CoreGraphDatabase.createRelationshipIfNotExistent(lexicalFormNode, sentenceNode, PredefinedRelations.APPEARS_IN.toString());
				//CoreGraphDatabase.createRelationship(lexicalFormNode, sentenceNode, PredefinedRelations.APPEARS_IN.toString());
			} else if (lexicalFormLowerCaseToNode.containsKey(token.toLowerCase())) {
//					System.out.println("found lowercase: " + token);
				Node lexicalFormNode = lexicalFormLowerCaseToNode.get(token.toLowerCase());
				CoreGraphDatabase.createRelationshipIfNotExistent(lexicalFormNode, sentenceNode, PredefinedRelations.APPEARS_IN_LOWERCASE.toString());
				//CoreGraphDatabase.createRelationship(lexicalFormNode, sentenceNode, PredefinedRelations.APPEARS_IN_LOWERCASE.toString());
			} else { }
		}
	}
	
	
	/** 
	 * Clear existing APPEARS_IN and APPEARS_IN_LOWERCASE relations and connect 
	 * lexical_form nodes to a sentence node based on advanced sentence parsing. 
	 * 
	 * @param sentenceNode
	 */
	public static void connectSentenceNodeWithLexicalFormNodes(Node sentenceNode) {
		// Clear first appers_in relations for the sentence node
		CoreGraphDatabase.runCypherQuery("MATCH (n)-[r:" + PredefinedRelations.APPEARS_IN.toString() + "]->() WHERE n.uid=" + sentenceNode.get(UID_FIELD) + " DELETE r");
		CoreGraphDatabase.runCypherQuery("MATCH (n)-[r:" + PredefinedRelations.APPEARS_IN_LOWERCASE.toString() + "]->() WHERE n.uid=" + sentenceNode.get(UID_FIELD) + " DELETE r");

		connectSentenceNodeWithNewLexicalFormNodes(sentenceNode);
	}
	
	
	/** 
	 * Connect lexical_form nodes to a sentence node based on advanced sentence parsing.
	 * 
	 * @param sentenceNode
	 */
	public static int connectSentenceNodeWithNewLexicalFormNodes(Node sentenceNode) {
		int connectionsCount = 0;
		String sentenceText = sentenceNode.get("text").toString();
		
		logger.info("Check matches with {} lexical forms.", lexicalFormToNode.size());
		
		HashSet<String> candidates = ExtractEntities.extractNamedEntities(sentenceText);
        for (String cand: candidates) {
        	// trim
        	cand = cand.trim();
        	// check match to the lexical form independently on the first letter case
    		if (lexicalFormToNode.containsKey(StringUtils.uncapitalize(cand))) {
				logger.info("found: " + cand);
				//System.out.println("found: " + cand);
				Node lexicalFormNode = lexicalFormToNode.get(StringUtils.uncapitalize(cand));
				CoreGraphDatabase.createRelationshipIfNotExistent(lexicalFormNode, sentenceNode, PredefinedRelations.APPEARS_IN.toString());
				connectionsCount++;
			} 
    		else if (lexicalFormToNode.containsKey(StringUtils.capitalize(cand))) {
				Node lexicalFormNode = lexicalFormToNode.get(StringUtils.capitalize(cand));
				CoreGraphDatabase.createRelationshipIfNotExistent(lexicalFormNode, sentenceNode, PredefinedRelations.APPEARS_IN.toString());
				connectionsCount++;
			}
    		// compare in lowercase if searched term is longer than 3 symbols
			else if (cand.length() > 3 && lexicalFormLowerCaseToNode.containsKey(cand.toLowerCase())) {
				logger.info("found lowercase: " + cand);
				//System.out.println("found lowercase: " + cand);
				Node lexicalFormNode = lexicalFormLowerCaseToNode.get(cand.toLowerCase());
				CoreGraphDatabase.createRelationshipIfNotExistent(lexicalFormNode, sentenceNode, PredefinedRelations.APPEARS_IN_LOWERCASE.toString());
				connectionsCount++;
			}
			else {
				//logger.info("No matches for candidate '{}' among new lexical forms.", cand);
			}
        }
        
        return connectionsCount;
	}
	
	
	/** 
	 * Create a node for a single sentence in GraphDB.
	 * 
	 * @param sentence
	 * @return
	 */
	public static Node createSentenceNode(Sentence sentence) {
		Node sentenceNode = CoreGraphDatabase.createUIDNodeIfNotExistent(PredefinedCategories.SENTENCE.toString(), sentence.uid);
		CoreGraphDatabase.setProperty(sentenceNode, "index", sentence.index);
		CoreGraphDatabase.setProperty(sentenceNode, "text", sentence.text);
		//CoreGraphDatabase.setProperty(sentenceNode, "tokens", sentence.tokens);
		CoreGraphDatabase.setProperty(sentenceNode, "tokens", String.join(";",sentence.tokens));
		
		//sentenceNode = CoreGraphDatabase.getNodeById(sentenceNode.id());
		sentenceNode = CoreGraphDatabase.getNodeById(sentenceNode.get(UID_FIELD).asString());
		return sentenceNode;
	}
	
	
	/** 
	 * Create sentence nodes from already existing publication nodes and connect each sentence node with all relevant lexical forms in GraphDB.
	 * 
	 */
	public static void createSentenceNodes() {
		// node counts
		int sentenceNodesCounts = CoreGraphDatabase.countNodeType(PredefinedCategories.SENTENCE.toString());
		// relation counts
		int isPartOfSentenceCounts = CoreGraphDatabase.countRelationType(PredefinedRelations.IS_PART_OF_SENTENCE.toString());
		int appearsInCounts = CoreGraphDatabase.countRelationType(PredefinedRelations.APPEARS_IN.toString());
		int appearsInLowercaseCounts = CoreGraphDatabase.countRelationType(PredefinedRelations.APPEARS_IN_LOWERCASE.toString());
		
		final ExecutorService pool = Executors.newFixedThreadPool(poolSize);
		
		// helper class for parallelization
		
		final class WorkerThread implements Runnable {
		    private String command;
		    private Node sentenceNode;
		    
		    public WorkerThread(String s, Node sentenceNode){
		        this.command=s;
		        this.sentenceNode=sentenceNode;
		    }

		    @Override
		    public void run() {
		        //System.out.println(Thread.currentThread().getName()+" Start. "+command);
		    	logger.info(Thread.currentThread().getName()+" Start. "+command);
		        processCommand(sentenceNode);
		        //System.out.println(Thread.currentThread().getName()+" End.");
		        logger.info(Thread.currentThread().getName()+" End.");
		    }

		    private void processCommand(Node sentenceNode) {
		    	connectSentenceNodeWithLexicalFormNodes(sentenceNode);
		    }
		    
		    @Override
		    public String toString(){
		        return this.command;
		    }
		}
		
		// Create sentence node and integrate into GraphDB
		
		logger.info("*** Creating sentence nodes for publications in the GraphDB ***");
		
		List<Node> publicationNodes = CoreGraphDatabase.getNodesByLabel(PredefinedCategories.PUBLICATION.toString());
		logger.info("Publication nodes retrieved: {}.", publicationNodes.size());

		for (int publicationIndex = 0; publicationIndex < publicationNodes.size(); publicationIndex++) {
			Node publicationNode = publicationNodes.get(publicationIndex);
			String text = publicationNode.get("paperAbstract").asString();
			String documentDOI = publicationNode.get("doi").asString();
			try {
				if (text != null && text.length() != 0) {
					List<Sentence> sentences = splitSentences(text);
					for (Sentence sentence : sentences) {			
						sentence.uid = documentDOI + "/" + sentence.index;
						
						// Stop if sentence node exists
						Node uidNode = CoreGraphDatabase.getUIDNode(PredefinedCategories.SENTENCE.toString(), sentence.uid);
						if (uidNode != null) {
							logger.info("Data for publication index {} already exist.", publicationIndex);
							break;
						}
						// Create sentence node
						Node sentenceNode = createSentenceNode(sentence);
						// Link sentence to publication
						CoreGraphDatabase.createRelationshipIfNotExistent(
								sentenceNode, publicationNode, PredefinedRelations.IS_PART_OF_SENTENCE.toString());
						//CoreGraphDatabase.createRelationship(sentenceNode, publicationNode, PredefinedRelations.IS_PART_OF_SENTENCE.toString());
						
						// Link sentence to lexical forms
						pool.execute(new WorkerThread("Reading: " + sentence.uid, sentenceNode));
						//connectSentenceNodeWithLexicalFormNodes(sentenceNode);
					}
				}
			} catch (Exception e) {
				logger.error("Error creating sentence nodes for publication {} with doi '{}'.", publicationIndex, documentDOI, e);
			}
			logger.info("Publication {} / {} procesed.", publicationIndex+1, publicationNodes.size());
		}
		pool.shutdown();
        while (!pool.isTerminated()) {}
        
		// node counts
		sentenceNodesCounts = CoreGraphDatabase.countNodeType(PredefinedCategories.SENTENCE.toString()) - sentenceNodesCounts;
		// relation counts
		isPartOfSentenceCounts = CoreGraphDatabase.countRelationType(PredefinedRelations.IS_PART_OF_SENTENCE.toString()) - isPartOfSentenceCounts;
		appearsInCounts = CoreGraphDatabase.countRelationType(PredefinedRelations.APPEARS_IN.toString()) - appearsInCounts;
		appearsInLowercaseCounts = CoreGraphDatabase.countRelationType(PredefinedRelations.APPEARS_IN_LOWERCASE.toString()) - appearsInLowercaseCounts;
        
        logger.info("Finished all threads.");
        logger.info("{} SENTENCE nodes created.", sentenceNodesCounts);
        logger.info("Relations added: "
        		+ "{} 'IS_PART_OF_SENTENCE', {} 'APPEARS_IN', {} 'APPEARS_IN_LOWERCASE'.", 
        					isPartOfSentenceCounts, appearsInCounts, appearsInLowercaseCounts);
	}

	/**
	 * Connect initialized lexical forms with existing sentence nodes in the graph database.
	 */
	public static void connectNewLexicalFormsWithExistingSentenceNodes(boolean abridge) {
		final ExecutorService pool = Executors.newFixedThreadPool(poolSize);
		
		int connectionsEstablished = 0;
		
		final class WorkerThread implements Callable<Integer> {
		    private String command;
		    private Node sentenceNode;
		    
		    public WorkerThread(String s, Node sentenceNode){
		        this.command=s;
		        this.sentenceNode=sentenceNode;
		    }
		    
		    @Override
		    public Integer call() {
		    	int connectionsCount = 0;
		        System.out.println(Thread.currentThread().getName()+" Start. "+command);
		        connectionsCount = processCommand(sentenceNode);
		        System.out.println(Thread.currentThread().getName()+" End.");
		        return connectionsCount;
		    }

		    private int processCommand(Node sentenceNode) {
		    	return connectSentenceNodeWithNewLexicalFormNodes(sentenceNode);
		    }
		    
		    @Override
		    public String toString(){
		        return this.command;
		    }
		}
		
		// Get all sentence nodes from graph database
		List<Node> sentenceNodes = CoreGraphDatabase.getNodesByLabel(PredefinedCategories.SENTENCE.toString());
		// Tasks
		Collection<Callable<Integer>> tasks = new ArrayList<Callable<Integer>>();

		int processed = 0;
		for (Node sentenceNode :sentenceNodes) {
			if (abridge && processed > ABRIDGE_MAX_NUM) {
				// in case it's a shortened version of the pipeline for test
				logger.info("! Shortened pipeline. Only {} sentences can be processed !", processed);
				break;
			}
			
			String text = sentenceNode.get("text").asString();
			String sentenceUID = sentenceNode.get("uid").asString();
			try {
				if (text != null && text.length() > 0) {
					// connect sentence with lexical forms
					tasks.add(new WorkerThread("Reading: " + sentenceUID, sentenceNode));				
				}
			} catch (Exception e) {
				logger.error("Error creating connecting sentence {} to lexical forms.", sentenceUID, e);
				continue;
			}
			processed++;
		}
		// execute all tasks
		List<Future<Integer>> results;
		try {
	        logger.info("Execute all threads...");
			results = pool.invokeAll(tasks);
			for (Future<Integer> res : results) {
				  if (!res.isCancelled()) {
				    try {
					    Integer relCount = res.get();
					    connectionsEstablished += relCount;
				    } catch (ExecutionException e) {
					    logger.error("Failed to get result", e);
					    continue;
					} catch (InterruptedException e) {
					    logger.error("Interrupted", e);
					    Thread.currentThread().interrupt();
					}
				}
			}
		} catch (InterruptedException e) {
			logger.error("Failed to execute threads while establishing links between sentence and lexical forms.", e);
	    }

		pool.shutdown();
        while (!pool.isTerminated()) {}
        
        logger.info("Finished all threads.");
        logger.info("{} sentence nodes processed. {} connections created or re-established.", processed, connectionsEstablished); 
	}
	
	/** 
	 * Split sentence into words.
	 * 
	 * @param text
	 * @return
	 */
	public static List<Sentence> splitSentences(String text) {
		CoreDocument coreDocument = pipeline.processToCoreDocument(text);
		List<Sentence> sentences = new LinkedList<>();
		int index = 1;
		for (CoreSentence coreSentence : coreDocument.sentences()) {
			Sentence sentence = new Sentence();
			sentence.index = index++;
			sentence.text = coreSentence.text();
			sentence.tokens = coreSentence.tokens().stream().map(t -> t.originalText()).collect(Collectors.toList());
			sentences.add(sentence);
		}

		return sentences;
	}
	
	/** Create cooccurs_with relations when lexical forms are in the same sentence. */
	public static void createCooccursWithRelations() {
		logger.info("Re-create 'cooccurs_with' relations.");
		// Delete existing relations
		CoreGraphDatabase.runCypherQuery("MATCH (:" + PredefinedCategories.LEXICAL_FORM.toString() + ")-[r:" + PredefinedRelations.COOCCURS_WITH.toString() + "]->(:" + PredefinedCategories.LEXICAL_FORM.toString() + ") DELETE r");
		
		// Re-create relations
		CoreGraphDatabase.runCypherQuery("MATCH (lf1:" + PredefinedCategories.LEXICAL_FORM.toString() + ")-[r1:" + PredefinedRelations.APPEARS_IN.toString() + "]->(s:" + PredefinedCategories.SENTENCE.toString() + ")<-[r2:" + PredefinedRelations.APPEARS_IN.toString() + "]-(lf2:" + PredefinedCategories.LEXICAL_FORM.toString() + ") WHERE lf1<>lf2 MERGE (lf1)-[c:" + PredefinedRelations.COOCCURS_WITH.toString() + "]->(lf2)");
	}
	
	/** Create synonym_with relations when lexical forms are in the same sentence. */
	public static void createSynonymWithRelations() {
		logger.info("Re-create 'synonym_with' relations.");
		// Delete existing SYNONYM_WITH relations
		CoreGraphDatabase.runCypherQuery("MATCH (:" + PredefinedCategories.LEXICAL_FORM.toString() + ")-[r:" + PredefinedRelations.SYNONYM_WITH.toString() + "]->(:" + PredefinedCategories.LEXICAL_FORM.toString() + ") DELETE r");

		// Re-create SYNONYM_WITH relations
		CoreGraphDatabase.runCypherQuery("MATCH (lf1:" + PredefinedCategories.LEXICAL_FORM.toString() + ")<-[r1:" + PredefinedRelations.HAS_LF.toString() + "]-(s:" + PredefinedCategories.NAMED_ENTITY.toString() + ")-[r2:" + PredefinedRelations.HAS_LF.toString() + "]->(lf2:" + PredefinedCategories.LEXICAL_FORM.toString() + ") WHERE (lf1<>lf2) and NOT s:" + PredefinedCategories.TRAIT.toString() + " MERGE (lf1)-[c:" + PredefinedRelations.SYNONYM_WITH.toString() + "]->(lf2)");
	}
}

