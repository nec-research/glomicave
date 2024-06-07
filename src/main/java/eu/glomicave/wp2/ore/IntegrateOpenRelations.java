package eu.glomicave.wp2.ore;

/**
 * This script integrates extracted open relations into current knowledge graph 
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.types.Node;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import eu.glomicave.config.AmazonS3Config;
import eu.glomicave.persistence.AmazonS3;
import eu.glomicave.persistence.CoreGraphDatabase;
import eu.glomicave.persistence.OpenRelationsGraphDatabase;
import eu.glomicave.persistence.PredefinedCategories;
import eu.glomicave.persistence.PredefinedRelations;


public class IntegrateOpenRelations {
	private static final Logger logger = LogManager.getLogger(IntegrateOpenRelations.class);
	
	static String UID_FIELD = "uid";
	// input file should have the following columns: subject,relation,object,polarity,modality,attribution,sentence,sentence_uid,...
	// use only first 8 columns from the input file
	static final int COLS_NUMBER = 8; 
	// nodes
	static final String FACT = PredefinedCategories.FACT.toString();
	static final String ATTRIBUTION = PredefinedCategories.ATTRIBUTION.toString();
	static final String MODALITY = PredefinedCategories.MODALITY.toString();
	static final String POLARITY = PredefinedCategories.POLARITY.toString();
	// relations
	static final String HAS_FACT = PredefinedRelations.HAS_FACT.toString();
	static final String FACT_APPEARS_IN = PredefinedRelations.FACT_APPEARS_IN.toString();
	static final String HAS_ATTRIBUTION = PredefinedRelations.HAS_ATTRIBUTION.toString();
	static final String HAS_MODALITY = PredefinedRelations.HAS_MODALITY.toString();
	static final String HAS_POLARITY = PredefinedRelations.HAS_POLARITY.toString();
	
	
	/**
	 * Loads OpenIE extracted facts into graph database from Amazon S3 object.
	 * The facts should be represented in a CSV file with the following column ordering:
	 * 'subject,relation,object,polarity,modality,attribution,sentence,sentence_uid'.
	 * 
	 * @param abridge		if True, load only first 100 records for the test.
	 * @param s3filekey		path to the S3 object.
	 */
	public static void addOpenRelationsFromS3CSV(boolean abridge, String s3filekey) throws IOException {
		int processed = 0;	
		String[] fields;
		
		int factNodesCounts = CoreGraphDatabase.countNodeType(FACT);
		int attributionNodesCounts = CoreGraphDatabase.countNodeType(ATTRIBUTION);
		int modalityNodeCounts = CoreGraphDatabase.countNodeType(MODALITY);
		int polarityNodeCounts = CoreGraphDatabase.countNodeType(POLARITY);
		
		int hasFactCounts = CoreGraphDatabase.countRelationType(HAS_FACT);
		int factAppearsInCounts = CoreGraphDatabase.countRelationType(FACT_APPEARS_IN);
		int hasAttributionCounts = CoreGraphDatabase.countRelationType(HAS_ATTRIBUTION);
		int hasModalityCounts = CoreGraphDatabase.countRelationType(HAS_MODALITY);
		int hasPolarityCounts = CoreGraphDatabase.countRelationType(HAS_POLARITY);
		
		logger.info("Loading OpenIE relations from S3 object '{}'.", s3filekey);
		
		try {
			CSVReader csvReader = new CSVReaderBuilder(new InputStreamReader(AmazonS3.readFileUsingS3Client(s3filekey)))
					//.withCSVParser(new CSVParserBuilder().withSeparator('\t').build())
	                .build();
			
			fields = csvReader.readNext(); // skip header
			while (fields != null) {
				if (abridge && processed > 100) {
					break;
				}
				
				try {
					fields = csvReader.readNext();
					if (fields == null || fields.length < COLS_NUMBER) {
						continue;
					}				
					String subject = fields[0];
					String relation = fields[1];
					String object = fields[2];
					String polarity = fields[3];
					String modality = fields[4];
					String attribution = fields[5];
					String sentenceUID = fields[7];

					Node lexicalFormNodeSubj = OpenRelationsGraphDatabase.getLexicalFormNode(subject);
					Node lexicalFormNodeObj = OpenRelationsGraphDatabase.getLexicalFormNode(object);
						
					// Add fact node only if subject is different from object to exclude self-loops
					//if (lexicalFormNodeSubj != null && lexicalFormNodeObj != null && lexicalFormNodeSubj.id() == lexicalFormNodeObj.id()) continue;
					if (lexicalFormNodeSubj != null && lexicalFormNodeObj != null 
							&& lexicalFormNodeSubj.get(UID_FIELD) == lexicalFormNodeObj.get(UID_FIELD)) {
						continue;
					}
					
					Node factNode = OpenRelationsGraphDatabase.addFactNode(subject, relation, object, polarity, modality, attribution);
					
					// Add connections to fact node
					if (lexicalFormNodeSubj != null) {
						OpenRelationsGraphDatabase.addLexicalFormToFactConnection(lexicalFormNodeSubj, factNode);
					}				
					if (lexicalFormNodeObj != null) {
						OpenRelationsGraphDatabase.addLexicalFormToFactConnection(lexicalFormNodeObj, factNode);
					}				
					
					// Connect subject and object nodes with bidirectional relation OIE_RELATED_WITH
					if ((lexicalFormNodeSubj != null) && (lexicalFormNodeObj != null)) {
						OpenRelationsGraphDatabase.addSubjectObjectConnection(lexicalFormNodeSubj, lexicalFormNodeObj);
					}
					
					// Add polarity node
					Node polarityNode = OpenRelationsGraphDatabase.addPolarityNode(polarity);
					OpenRelationsGraphDatabase.addFactToPolarityConnection(factNode, polarityNode);				
					
					// Add modality node
					Node modalityNode = OpenRelationsGraphDatabase.addModalityNode(modality); 
					OpenRelationsGraphDatabase.addFactToModalityConnection(factNode, modalityNode);				
					
					// Add attribution node and links
					if(attribution != null && !attribution.trim().isEmpty()) {
						Node attributionNode = OpenRelationsGraphDatabase.addAttributionNode(attribution);
						OpenRelationsGraphDatabase.addFactToAttributionConnection(factNode, attributionNode);	
					}				
					
					// Add connection to sentence
					Node sentenceNode = OpenRelationsGraphDatabase.getSentenceNode(sentenceUID);
					if (sentenceNode != null) {
						OpenRelationsGraphDatabase.addFactToSentenceConnection(factNode, sentenceNode);
					}
					
					processed++;
					
					logger.info("Record " + processed + " processed.");
					
				} catch(SocketException se) {
					logger.error("Socket error. Trying to resume reading from line {}.", processed);
					// reset connection
					AmazonS3Config.getInstance().reconnect();
					// resume reading from last processed line
					csvReader = new CSVReader(new BufferedReader(new InputStreamReader(AmazonS3.readFileUsingS3Client(s3filekey))));
					for (int i = 0; i <= processed; i++) {
						csvReader.readNext();
					}
					continue;	
					
				} catch(Exception e1) {
					logger.error("Error reading fact entry no. {} because of exception {}. Trying to resume.", processed, e1);
					continue;
				}
			}
			csvReader.close();
			
		} catch(Exception e2) {
			logger.error("Error reading fact entries.", e2.getMessage(), e2);
			throw new IOException();
		}
		
		logger.info("Fact loading finished. {} OpenIE entries processed.", processed);
		
		factNodesCounts = CoreGraphDatabase.countNodeType(FACT) - factNodesCounts;
		attributionNodesCounts = CoreGraphDatabase.countNodeType(ATTRIBUTION) - attributionNodesCounts;
		modalityNodeCounts = CoreGraphDatabase.countNodeType(MODALITY) - modalityNodeCounts;
		polarityNodeCounts = CoreGraphDatabase.countNodeType(POLARITY) - polarityNodeCounts;
		
		hasFactCounts = CoreGraphDatabase.countRelationType(HAS_FACT) - hasFactCounts;
		factAppearsInCounts = CoreGraphDatabase.countRelationType(FACT_APPEARS_IN) - factAppearsInCounts;
		hasAttributionCounts = CoreGraphDatabase.countRelationType(HAS_ATTRIBUTION) - hasAttributionCounts;
		hasModalityCounts = CoreGraphDatabase.countRelationType(HAS_MODALITY) - hasModalityCounts;
		hasPolarityCounts = CoreGraphDatabase.countRelationType(HAS_POLARITY) - hasPolarityCounts;
		
		logger.info("Added node types into Graph DB: "
				+ "\n - {} facts, {} attributions, {} modalities, {} polarities."
				+ "\nAdded relation types into Graph DB: : "
				+ "\n - {} has_fact, {} fact_appears_in, {} has_attribution, {} has_modality, {} has_polarity.",
				factNodesCounts, attributionNodesCounts, modalityNodeCounts, polarityNodeCounts, 
				hasFactCounts, factAppearsInCounts, hasAttributionCounts, hasModalityCounts, hasPolarityCounts);
	}
	
	
	/**
	 * Loads OpenIE extracted facts into graph database.
	 * The facts should be represented in a CSV file with the following column ordering:
	 * 'subject,relation,object,polarity,modality,attribution,sentence,sentence_uid'.
	 * 
	 * @param abridge		if True, load only first 100 records for the test.
	 * @param filename		path to the file.
	 */
	public static void addOpenRelationsFromCSV(boolean abridge, String filename) throws Exception {
		int processed = 0;
		// count nodes
		int factNodesCounts = CoreGraphDatabase.countNodeType(FACT);
		int attributionNodesCounts = CoreGraphDatabase.countNodeType(ATTRIBUTION);
		int modalityNodeCounts = CoreGraphDatabase.countNodeType(MODALITY);
		int polarityNodeCounts = CoreGraphDatabase.countNodeType(POLARITY);
		// count relations
		int hasFactCounts = CoreGraphDatabase.countRelationType(HAS_FACT);
		int factAppearsInCounts = CoreGraphDatabase.countRelationType(FACT_APPEARS_IN);
		int hasAttributionCounts = CoreGraphDatabase.countRelationType(HAS_ATTRIBUTION);
		int hasModalityCounts = CoreGraphDatabase.countRelationType(HAS_MODALITY);
		int hasPolarityCounts = CoreGraphDatabase.countRelationType(HAS_POLARITY);
		
		logger.info("Loading phenotypic traits from file: '{}'.", filename);
		
		try {
			// count number of lines
			BufferedReader bufferedReader = new BufferedReader(new FileReader(filename));
			String input = "";
			int count = 0;
			while((input = bufferedReader.readLine()) != null) {
			     count++;
			}
			bufferedReader.close();
			logger.info("{} lines found.", count);

			// read file as CSV and parse the data
	    	CSVReader csvReader = new CSVReader(new FileReader(filename));
			// skip header
			String[] fields = csvReader.readNext(); 
			while (fields != null) {
				try {
					fields = csvReader.readNext();
					if (fields == null || fields.length < COLS_NUMBER) {
						continue;
					}
					String subject = fields[0];
					String relation = fields[1];
					String object = fields[2];
					String polarity = fields[3];
					String modality = fields[4];
					String attribution = fields[5];
					String sentenceUID = fields[7];

					Node lexicalFormNodeSubj = OpenRelationsGraphDatabase.getLexicalFormNode(subject);
					Node lexicalFormNodeObj = OpenRelationsGraphDatabase.getLexicalFormNode(object);
						
					// Add fact node only if subject is different from object to exclude self-loops
					//if (lexicalFormNodeSubj != null && lexicalFormNodeObj != null && lexicalFormNodeSubj.id() == lexicalFormNodeObj.id()) continue;
					if (lexicalFormNodeSubj != null && lexicalFormNodeObj != null 
							&& lexicalFormNodeSubj.get(UID_FIELD) == lexicalFormNodeObj.get(UID_FIELD)) {
						continue;
					}
					
					Node factNode = OpenRelationsGraphDatabase.addFactNode(subject, relation, object, polarity, modality, attribution);
					
					// Add connections to fact node
					if (lexicalFormNodeSubj != null) {
						OpenRelationsGraphDatabase.addLexicalFormToFactConnection(lexicalFormNodeSubj, factNode);
					}				
					if (lexicalFormNodeObj != null) {
						OpenRelationsGraphDatabase.addLexicalFormToFactConnection(lexicalFormNodeObj, factNode);
					}				
					
					// Connect subject and object nodes with bidirectional relation OIE_RELATED_WITH
					if ((lexicalFormNodeSubj != null) && (lexicalFormNodeObj != null)) {
						OpenRelationsGraphDatabase.addSubjectObjectConnection(lexicalFormNodeSubj, lexicalFormNodeObj);
					}
					
					// Add polarity node
					Node polarityNode = OpenRelationsGraphDatabase.addPolarityNode(polarity);
					OpenRelationsGraphDatabase.addFactToPolarityConnection(factNode, polarityNode);				
					
					// Add modality node
					Node modalityNode = OpenRelationsGraphDatabase.addModalityNode(modality); 
					OpenRelationsGraphDatabase.addFactToModalityConnection(factNode, modalityNode);				
					
					// Add attribution node and links
					if(attribution != null && !attribution.trim().isEmpty()) {
						Node attributionNode = OpenRelationsGraphDatabase.addAttributionNode(attribution);
						OpenRelationsGraphDatabase.addFactToAttributionConnection(factNode, attributionNode);	
					}				
					
					// Add connection to sentence
					Node sentenceNode = OpenRelationsGraphDatabase.getSentenceNode(sentenceUID);
					if (sentenceNode != null) {
						OpenRelationsGraphDatabase.addFactToSentenceConnection(factNode, sentenceNode);
					}
					
					processed++;
					
					if (processed % 10 == 0) {
						logger.info("Record " + processed + " processed.");
					}
					
					if (abridge && processed > 100) {
						break;
					}
				} catch(IOException e1) {
					logger.error("Error reading fact record no. {}.", processed);
					continue;
				}
			} 	
			// close file
			csvReader.close();	
		} catch(Exception e2) {
			 logger.error("Error reading file with fact records due to exception: {}.", e2.getMessage());
			 throw new Exception("Errors while loading facts, see the log history.");
		}
		
		logger.info("Fact loading finished. {} fact records processed.", processed);
		
		factNodesCounts = CoreGraphDatabase.countNodeType(FACT) - factNodesCounts;
		attributionNodesCounts = CoreGraphDatabase.countNodeType(ATTRIBUTION) - attributionNodesCounts;
		modalityNodeCounts = CoreGraphDatabase.countNodeType(MODALITY) - modalityNodeCounts;
		polarityNodeCounts = CoreGraphDatabase.countNodeType(POLARITY) - polarityNodeCounts;
		
		hasFactCounts = CoreGraphDatabase.countRelationType(HAS_FACT) - hasFactCounts;
		factAppearsInCounts = CoreGraphDatabase.countRelationType(FACT_APPEARS_IN) - factAppearsInCounts;
		hasAttributionCounts = CoreGraphDatabase.countRelationType(HAS_ATTRIBUTION) - hasAttributionCounts;
		hasModalityCounts = CoreGraphDatabase.countRelationType(HAS_MODALITY) - hasModalityCounts;
		hasPolarityCounts = CoreGraphDatabase.countRelationType(HAS_POLARITY) - hasPolarityCounts;
		
		logger.info("Added node types to the graph DB: "
				+ "\n - {} facts, {} attributions, {} modalities, {} polarities."
				+ "\nAdded relation types to the graph DB: : "
				+ "\n - {} has_fact, {} fact_appears_in, {} has_attribution, {} has_modality, {} has_polarity.",
				factNodesCounts, attributionNodesCounts, modalityNodeCounts, polarityNodeCounts, 
				hasFactCounts, factAppearsInCounts, hasAttributionCounts, hasModalityCounts, hasPolarityCounts);
	}
}

