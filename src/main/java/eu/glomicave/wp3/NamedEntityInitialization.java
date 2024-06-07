package eu.glomicave.wp3;


import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.SocketException;
import com.opencsv.CSVReader;
import org.neo4j.driver.types.Node;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import eu.glomicave.config.GlobalParamsConfig;
import eu.glomicave.persistence.CoreGraphDatabase;
import eu.glomicave.persistence.NamedEntityGraphDatabase;
import eu.glomicave.persistence.PredefinedCategories;
import eu.glomicave.persistence.PredefinedRelations;
import eu.glomicave.config.AmazonS3Config;
import eu.glomicave.persistence.AmazonS3;


public class NamedEntityInitialization {
	private static final Logger logger = LogManager.getLogger(NamedEntityInitialization.class);
	
	static int poolSize = GlobalParamsConfig.MAX_POOL_SIZE;
	
	// Nested class to run parallel threads
	final class WorkerThread implements Runnable {
	    private String command;
	    private String[] fields;
	    private int uidColIdx;
	    private int[] lfColIds;
	    private int lfColStartIdx;
	    private String entityCategory;
	    private int entityCategoryColIdx;
	    private String ontologyName;
	    private int sourceOntologyColIdx;
	    
	    public WorkerThread(String command, String[] fields,  int uidColIdx, int[] lfColIds, int lfColStartIdx, 
						String entityCategory, int entityCategoryColIdx, String ontologyName,  int sourceOntologyColIdx){
	        this.command=command;
	        this.fields=fields;
		    this.uidColIdx=uidColIdx;
		    this.lfColIds=lfColIds;
		    this.lfColStartIdx=lfColStartIdx;
		    this.entityCategory=entityCategory;
		    this.entityCategoryColIdx=entityCategoryColIdx;
		    this.ontologyName=ontologyName;
		    this.sourceOntologyColIdx=sourceOntologyColIdx;
	    }

	    @Override
	    public void run() {
	        logger.info(Thread.currentThread().getName()+" Start. Process line number = "+command);
	    	//System.out.println(Thread.currentThread().getName()+" Start. Process line number = "+command);
	        //processCommand(fields, uidIdx, lfIds, ontologyName, entityCategory, sourceIdx);
	        processCommand();
	        logger.info(Thread.currentThread().getName()+" End.");
	        //System.out.println(Thread.currentThread().getName()+" End.");
	    }

	    //private void processCommand(String[] fields, int uidIdx, int[] lfIds, String ontologyName, String entityCategory, int sourceIdx) {
	    private void processCommand() {
	    	try {
		    	NamedEntityInitialization.addNERecord(fields, uidColIdx, lfColIds, lfColStartIdx, 
		    							entityCategory, entityCategoryColIdx, ontologyName, sourceOntologyColIdx);
			} catch (Exception e) {
				logger.error(e);
			}
	    }
	    
	    @Override
	    public String toString(){
	        return this.command;
	    }
	}

	/**  
	 * Parses single named entity record.
	 * 
	 * @param fields		the fields of a record.
	 * @param uidColIdx		index of the field that will be used as a unique identifier 
	 * 		  				of the entity when storing into knowledge graph.
	 * 		  				If uid should be included as a lexical form of the node,
	 * 		  				this index should be passed also in the lfColIds parameter.
	 * @param lfIds			indexes of the fields that contain names/synonyms of the entity.
	 * 		  				If null, the field lfColStartIdx should be considered.
	 * @param lfColStartIdx index of the first field that contains lexical form,
	 * 		  				all fields starting from this index will be considered.
	 * 		  				This parameter value is only accounted when lfIds is null.	
	 * @param entityCategory if not null, contains a category that will label entities, e.g. "PROTEIN".
	 * @param entityCategoryColIdx index of the field that contains the label of the entities.
	 * @param ontologyName	if not null, contains string identifier for the ontology name 
	 * 						that will be added as prefix, e.g. "NLE-BIO-KG".
	 * @param sourceOntologyColIdx index of the field that contains ontology name, if ontologyName field is null.
	 * @throws IOException	if any reading error occurs.
	 */
	public static void addNERecord(String[] fields,  int uidColIdx, int[] lfColIds, int lfColStartIdx, 
			String entityCategory, int entityCategoryColIdx, String ontologyName,  int sourceOntologyColIdx) throws IOException {
		//String[] fields = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
		String uid = null;
		Node namedEntityNode = null;
		Node lexicalFormNode = null;
		
		// Read node uid
		if (uidColIdx >= 0 && uidColIdx < fields.length) {
			uid = fields[uidColIdx];
		} else {
			logger.error("Index for the uid column is out of range.");
			throw new IOException();
		}

		// Read entity category from fields if not passed
		if (entityCategory == null && entityCategoryColIdx >= 0 && sourceOntologyColIdx < fields.length) {
			entityCategory = fields[entityCategoryColIdx];
		}
		
		// Read ontology name from fields if not passed
		if (ontologyName == null) {
			if (sourceOntologyColIdx >= 0 && sourceOntologyColIdx < fields.length) {
				ontologyName = fields[sourceOntologyColIdx];
			} else {
				logger.error("Can't read ontology name.");
				throw new IOException();
			}
		}
		
		// Create entity node
		namedEntityNode = NamedEntityGraphDatabase.addNamedEntityNode(ontologyName, uid);
		if (entityCategory != null) {
			// Check for predefined entity categories
			PredefinedCategories entityLabel = null;
			for(PredefinedCategories category: PredefinedCategories.values()) {
				if (category.name().equalsIgnoreCase(entityCategory)) {
					entityLabel = category;
				}
			}
			if (entityLabel != null) {
				CoreGraphDatabase.addLabel(namedEntityNode, entityLabel.toString());	
			} else {
				CoreGraphDatabase.addLabel(namedEntityNode, PredefinedCategories.NAMED_ENTITY.toString());	
			}
		}
		
		// Read and create lexical forms
		if (lfColIds != null) {	
			// Add lexical forms
			for (int i: lfColIds) {
				if (i < 0 || i >= fields.length) {
					break;
				}
				String syn = fields[i];
				if (!syn.trim().isEmpty()) {
					lexicalFormNode = NamedEntityGraphDatabase.addLexicalFormNode(syn);
					try {
						NamedEntityGraphDatabase.addNamedEntitiyToLexicalFormConnection(namedEntityNode, lexicalFormNode);
					} catch (Exception e) {
						logger.error(e);
					}
				}
			}
		} else if (lfColStartIdx >= 0 && lfColStartIdx < fields.length) {
			// Add all remained fields in a row as lexical forms
			for (int i = lfColStartIdx; i < fields.length; i++) {
				String lf = fields[i];
				if (!lf.trim().isEmpty()) {
					lexicalFormNode = NamedEntityGraphDatabase.addLexicalFormNode(lf);
					try {
						NamedEntityGraphDatabase.addNamedEntitiyToLexicalFormConnection(namedEntityNode, lexicalFormNode);
					} catch (Exception e) {
						logger.error(e);
					}
				}
			}
		} else {
			logger.error("Values for lexical form column indices can't be interpreted.");
			throw new IOException();
		}
	}

	
	/** 1. This section includes methods for reading data from Amazon S3 cloud storage. */

	
	/** 
	 * Loads named entities from Amazon S3 object. 
	 * It will process in parallel asynchronously the number of lines specifies at poolSize class variable.
	 * 
	 * @param abridge		if True, load only first 100 records for the test.
	 * @param s3filekey		path to the S3 object.
	 * @param uidColIdx		index of the field that will be used as a unique identifier 
	 * 		  				of the entity when storing into knowledge graph.
	 * 		  				If uid should be included as a lexical form of the node,
	 * 		  				this index should be passed also in the lfColIds parameter.
	 * @param lfIds			indexes of the fields that contain names/synonyms of the entity.
	 * 		  				If null, the field lfColStartIdx should be considered.
	 * @param lfColStartIdx index of the first field that contains lexical form,
	 * 		  				all fields starting from this index will be considered.
	 * 		  				This parameter value is only accounted when lfIds is null.	
	 * @param entityCategory if not null, contains a category that will label entities, e.g. "PROTEIN".
	 * @param entityCategoryColIdx index of the field that contains the label of the entities.
	 * @param ontologyName	if not null, contains string identifier for the ontology name 
	 * 						that will be added as prefix, e.g. "NLE-BIO-KG".
	 * @param sourceOntologyColIdx index of the field that contains ontology name, if ontologyName field is null.
	 * @throws IOException	if any reading error occurs.
	 * @throws FileNotFoundException if object not found.
	 */
	 public static void addNamedEntitiesFromS3CSV(boolean abridge, String s3filekey, 
										int uidColIdx, int[] lfColIds, int lfColStartIdx, 
										String entityCategory, int entityCategoryColIdx, 
										String ontologyName, int sourceOntologyColIdx) throws IOException, FileNotFoundException {
		 
		final ExecutorService pool = Executors.newFixedThreadPool(poolSize);
		
		// node counts
		int entityNodesCounts = CoreGraphDatabase.countNodeType(PredefinedCategories.NAMED_ENTITY.toString());
		int lfNodesCount = CoreGraphDatabase.countNodeType(PredefinedCategories.LEXICAL_FORM.toString());
		// relation counts
		int hasLFCounts = CoreGraphDatabase.countRelationType(PredefinedRelations.HAS_LF.toString());
	 
		logger.info("Start loading ontology from S3 object: {}", s3filekey);
		
		int processed = 0;
		try {
			CSVReader csvReader = new CSVReader(new BufferedReader(new InputStreamReader(AmazonS3.readFileUsingS3Client(s3filekey))));	
			// skip header
			String[] fields = csvReader.readNext(); 
			while (fields != null) {
				try {
					fields = csvReader.readNext();
					if (fields == null) {
						break;
					}
					pool.execute(new NamedEntityInitialization().new WorkerThread("" + processed, 
							fields, uidColIdx, lfColIds, lfColStartIdx, 
							entityCategory, entityCategoryColIdx, ontologyName, sourceOntologyColIdx));
					
					processed++;
					
					if (processed % 100 == 0) {
						logger.info("{} lines submitted.", processed);
					}
	
					if (abridge && processed > 100) {
						break;
					}
				} catch(SocketException e) {
					logger.error("Socket error. Trying to resume reading from line {}.", processed, e);
					// reset connection
					AmazonS3Config.getInstance().reconnect();
					// resume reading from last processed line
					csvReader = new CSVReader(new BufferedReader(new InputStreamReader(AmazonS3.readFileUsingS3Client(s3filekey))));
					for (int i = 0; i <= processed; i++)
						csvReader.readNext();
					
					continue;	
				}	
			}
			// wait until all treads finish
			pool.shutdown();
	        while (!pool.isTerminated()) {}
	        
	        logger.info("Finished all threads");

			csvReader.close();
		} catch(Exception e) {
			logger.error("Error reading gene entities", e);
			throw new IOException();
		}
		
		logger.info("{} entity records processed.", processed);
		
		// node counts
		entityNodesCounts = CoreGraphDatabase.countNodeType(PredefinedCategories.NAMED_ENTITY.toString()) - entityNodesCounts;
		lfNodesCount = CoreGraphDatabase.countNodeType(PredefinedCategories.LEXICAL_FORM.toString()) - lfNodesCount;
		// relation counts
		hasLFCounts = CoreGraphDatabase.countRelationType(PredefinedRelations.HAS_LF.toString()) - hasLFCounts;
        
        logger.info("{} 'NAMED_ENTITY', {} 'LEXICAL_FORM' nodes created.", entityNodesCounts, lfNodesCount);
        logger.info("{} 'HAS_LF' relations added.", hasLFCounts);
		 
	 }
	 
	/** 
	 * Overloaded method.
	 * Loads named entities from Amazon S3 object.
	 * It will process in parallel asynchronously the number of lines specifies at poolSize class variable.
	 * 
	 * @param abridge		if True, load only first 100 records for the test.
	 * @param s3filekey		path to the S3 object.
	 * @param uidColIdx		index of the field that will be used as a unique identifier 
	 * 		  				of the entity when storing into knowledge graph.
	 * 		  				If uid should be included as a lexical form of the node,
	 * 		  				this index should be passed also in the lfColIds parameter.
	 * @param lfIds			indexes of the fields that contain names/synonyms of the entity.
	 * @param ontologyName	if not null, contains string identifier for the ontology name 
	 * 						that will be added as prefix, e.g. "NLE-BIO-KG".
	 * @throws IOException	if any reading error occurs.
	 * @throws FileNotFoundException if object not found.
	 */
	 public static void addNamedEntitiesFromS3CSV(boolean abridge, String s3filekey, 
			 							int uidColIdx, int[] lfColIds, String ontologyName) throws IOException, FileNotFoundException {
		 int lfColStartIdx = -1;
		 String entityCategory = null;
		 int entityCategoryColIdx = -1;
		 int sourceOntologyColIdx = -1;
		 
		 if (lfColIds == null) {
			 logger.error("Indices poiting lexical form columns are missing. Parameter 'lfColIds' can't be null.");
			 throw new IOException();
		 }
		 
		 addNamedEntitiesFromS3CSV(abridge, s3filekey, uidColIdx, lfColIds, lfColStartIdx, 
				 			entityCategory, entityCategoryColIdx, ontologyName, sourceOntologyColIdx);
	 }
	 
	/** 
	 * Overloaded method.
	 * Loads named entities from Amazon S3 object.
	 * It will process in parallel asynchronously the number of lines specifies at poolSize class variable.
	 * 
	 * @param abridge		if True, load only first 100 records for the test.
	 * @param s3filekey		path to the S3 object.
	 * @param uidColIdx		index of the field that will be used as a unique identifier 
	 * 		  				of the entity when storing into knowledge graph.
	 * 		  				If uid should be included as a lexical form of the node,
	 * 		  				this index should be passed also in the lfColIds parameter.
	 * @param lfIds			indexes of the fields that contain names/synonyms of the entity.
	 * @param entityCategory contains a category that will label entities, e.g. "PROTEIN".
	 * @param ontologyName	if not null, contains string identifier for the ontology name 
	 * 						that will be added as prefix, e.g. "NLE-BIO-KG".
	 * @throws IOException	if any reading error occurs.
	 * @throws FileNotFoundException if object not found.
	 */
	public static void addNamedEntitiesFromS3CSV(boolean abridge, String s3filekey, 
											int uidColIdx, int[] lfColIds, 
											String entityCategory, String ontologyName) throws IOException, FileNotFoundException {
		
		int lfColStartIdx = -1;
		int entityCategoryColIdx = -1;
		int sourceOntologyColIdx = -1;
		 
		if (lfColIds == null) {
			logger.error("Indices poiting lexical form columns are missing. Parameter 'lfColIds' can't be null.");
			throw new IOException();
		}
		
		addNamedEntitiesFromS3CSV(abridge, s3filekey, uidColIdx, lfColIds, lfColStartIdx, 
									entityCategory, entityCategoryColIdx, ontologyName, sourceOntologyColIdx);
	}
	 
	/**
	 * Overloaded method.
	 * Loads named entities from Amazon S3 object.
	 * It will process in parallel asynchronously the number of lines specifies at poolSize class variable.
	 * 
	 * @param abridge		if True, load only first 100 records for the test.
	 * @param s3filekey		path to the S3 object.
	 * @param uidColIdx		index of the field that will be used as a unique identifier 
	 * 		  				of the entity when storing into knowledge graph.
	 * 		  				If uid should be included as a lexical form of the node,
	 * 		  				this index should be passed also in the lfColIds parameter.
	 * @param lfColStartIdx index of the first field that contains lexical form,
	 * 		  				all fields starting from this index will be considered.
	 * @param entityCategoryColIdx index of the field that contains the label of the entities.
	 * @param sourceOntologyColIdx index of the field that contains ontology name.
	 * @throws IOException	if any reading error occurs.
	 * @throws FileNotFoundException if object not found.
	 */
	 public static void addNamedEntitiesFromS3CSV(boolean abridge, String s3filekey, int uidColIdx, int lfColStartIdx, 
			 							int entityCategoryColIdx, int sourceOntologyColIdx) throws IOException, FileNotFoundException {
		 int[] lfColIds=null;
		 String entityCategory=null;
		 String ontologyName=null;
		 
		 addNamedEntitiesFromS3CSV(abridge, s3filekey, uidColIdx, lfColIds, lfColStartIdx, 
				 			entityCategory, entityCategoryColIdx, ontologyName, sourceOntologyColIdx);
 
	 }
	
	
	/** 2. This section includes methods for reading data from local CSV files. */
	
	
	/** 
	 * Load named entities from local CSV file. 
	 * It will process in parallel asynchronously the number of lines specifies at poolSize class variable.
	 * 
	 * @param abridge			If True, load only first 100 records for the test.
	 * @param filepath			Path to the S3 object.
	 * @param uidColIdx		index of the field that will be used as a unique identifier 
	 * 		  				of the entity when storing into knowledge graph.
	 * 		  				If uid should be included as a lexical form of the node,
	 * 		  				this index should be passed also in the lfColIds parameter.
	 * @param lfIds			indexes of the fields that contain names/synonyms of the entity.
	 * 		  				If null, the field lfColStartIdx should be considered.
	 * @param lfColStartIdx index of the first field that contains lexical form,
	 * 		  				all fields starting from this index will be considered.
	 * 		  				This parameter value is only accounted when lfIds is null.	
	 * @param entityCategory if not null, contains a category that will label entities, e.g. "PROTEIN".
	 * @param entityCategoryColIdx index of the field that contains the label of the entities.
	 * @param ontologyName	if not null, contains string identifier for the ontology name 
	 * 						that will be added as prefix, e.g. "NLE-BIO-KG".
	 * @param sourceOntologyColIdx index of the field that contains ontology name, if ontologyName field is null.
	 * @throws IOException	if any reading error occurs.
	 * @throws FileNotFoundException if object not found.
	 */	
	public static void addNamedEntitiesFromCSV(boolean abridge, String filepath, 
											int uidColIdx, int[] lfColIds, int lfColStartIdx, 
											String entityCategory, int entityCategoryColIdx, 
											String ontologyName, int sourceOntologyColIdx) throws IOException, FileNotFoundException {
	
		final ExecutorService pool = Executors.newFixedThreadPool(poolSize);
		
		// node counts
		int entityNodesCounts = CoreGraphDatabase.countNodeType(PredefinedCategories.NAMED_ENTITY.toString());
		int lfNodesCount = CoreGraphDatabase.countNodeType(PredefinedCategories.LEXICAL_FORM.toString());
		// relation counts
		int hasLFCounts = CoreGraphDatabase.countRelationType(PredefinedRelations.HAS_LF.toString());
		 
		logger.info("Start loading ontology from file: {}", filepath);
		
		int processed = 0;
		
    	long noOfLines = -1;
    	try(LineNumberReader lineNumberReader = new LineNumberReader(new FileReader(filepath))) {
	    	lineNumberReader.skip(Long.MAX_VALUE);
	      	noOfLines = lineNumberReader.getLineNumber() + 1;
    	}
      	
      	logger.info("{} records found.", noOfLines);

	    try (CSVReader csvReader = new CSVReader(new FileReader(filepath))) {
			// skip header
			String[] fields = csvReader.readNext(); 
			
			while (fields != null) {
				try {
					fields = csvReader.readNext();
					if (fields == null) {
						break;
					}
					pool.execute(new NamedEntityInitialization().new WorkerThread("" + processed, 
							fields, uidColIdx, lfColIds, lfColStartIdx, 
							entityCategory, entityCategoryColIdx, ontologyName, sourceOntologyColIdx));
					
					processed++;
					
					if (processed % 100 == 0) {
						logger.info("{} lines submitted.", processed);
					}
	
					if (abridge && processed > 100) {
						break;
					}
				} catch(Exception e1) {
					logger.error("Exception while reading data from line {}. Trying to continue with next line.", processed, e1);					
					continue;	
				}	
			}
			// wait until all treads finish
			pool.shutdown();
	        while (!pool.isTerminated()) {}
	        
	        logger.info("Finished all threads");

			csvReader.close();
		} catch(Exception e2) {
			logger.error("Error reading gene ontology entities", e2);
			throw new IOException();
		}
		
		logger.info("{} entity records processed.", processed);
		
		// node counts
		entityNodesCounts = CoreGraphDatabase.countNodeType(PredefinedCategories.NAMED_ENTITY.toString()) - entityNodesCounts;
		lfNodesCount = CoreGraphDatabase.countNodeType(PredefinedCategories.LEXICAL_FORM.toString()) - lfNodesCount;
		// relation counts
		hasLFCounts = CoreGraphDatabase.countRelationType(PredefinedRelations.HAS_LF.toString()) - hasLFCounts;
        
        logger.info("{} 'NAMED_ENTITY', {} 'LEXICAL_FORM' nodes created.", entityNodesCounts, lfNodesCount);
        logger.info("{} 'HAS_LF' relations added.", hasLFCounts);
	}
	
	/** 
	 * Overloaded method.
	 * Loads named entities from local CSV file.
	 * It will process in parallel asynchronously the number of lines specifies at poolSize class variable.
	 * 
	 * @param abridge		if True, load only first 100 records for the test.
	 * @param s3filekey		path to the S3 object.
	 * @param uidColIdx		index of the field that will be used as a unique identifier 
	 * 		  				of the entity when storing into knowledge graph.
	 * 		  				If uid should be included as a lexical form of the node,
	 * 		  				this index should be passed also in the lfColIds parameter.
	 * @param lfIds			indexes of the fields that contain names/synonyms of the entity.
	 * @param ontologyName	if not null, contains string identifier for the ontology name 
	 * 						that will be added as prefix, e.g. "NLE-BIO-KG".
	 * @throws IOException	if any reading error occurs.
	 * @throws FileNotFoundException if object not found.
	 */
	public static void addNamedEntitiesFromCSV(boolean abridge, String filepath, 
								int uidColIdx, int[] lfColIds, String ontologyName) throws IOException, FileNotFoundException {
		
		int lfColStartIdx = -1;
		String entityCategory = null;
		int entityCategoryColIdx = -1;
		int sourceOntologyColIdx = -1;
		 
		if (lfColIds == null) {
			logger.error("Indices poiting lexical form columns are missing. Parameter 'lfColIds' can't be null.");
			throw new IOException();
		}
		
		addNamedEntitiesFromCSV(abridge, filepath, uidColIdx, lfColIds, lfColStartIdx, 
									entityCategory, entityCategoryColIdx, ontologyName, sourceOntologyColIdx);
	}
	
	/** 
	 * Overloaded method.
	 * Loads named entities from local CSV file.
	 * It will process in parallel asynchronously the number of lines specifies at poolSize class variable.
	 * 
	 * @param abridge		if True, load only first 100 records for the test.
	 * @param s3filekey		path to the S3 object.
	 * @param uidColIdx		index of the field that will be used as a unique identifier 
	 * 		  				of the entity when storing into knowledge graph.
	 * 		  				If uid should be included as a lexical form of the node,
	 * 		  				this index should be passed also in the lfColIds parameter.
	 * @param lfIds			indexes of the fields that contain names/synonyms of the entity.
	 * @param entityCategory contains a category that will label entities, e.g. "PROTEIN".
	 * @param ontologyName	if not null, contains string identifier for the ontology name 
	 * 						that will be added as prefix, e.g. "NLE-BIO-KG".
	 * @throws IOException	if any reading error occurs.
	 * @throws FileNotFoundException if object not found.
	 */
	public static void addNamedEntitiesFromCSV(boolean abridge, String filepath, 
											int uidColIdx, int[] lfColIds, 
											String entityCategory, String ontologyName) throws IOException, FileNotFoundException {
		
		int lfColStartIdx = -1;
		int entityCategoryColIdx = -1;
		int sourceOntologyColIdx = -1;
		 
		if (lfColIds == null) {
			logger.error("Indices poiting lexical form columns are missing. Parameter 'lfColIds' can't be null.");
			throw new IOException();
		}
		
		addNamedEntitiesFromCSV(abridge, filepath, uidColIdx, lfColIds, lfColStartIdx, 
									entityCategory, entityCategoryColIdx, ontologyName, sourceOntologyColIdx);
	}
	
	/** 
	 * Overloaded method.
	 * Loads named entities from local CSV file.
	 * It will process in parallel asynchronously the number of lines specifies at poolSize class variable.
	 * 
	 * @param abridge		if True, load only first 100 records for the test.
	 * @param s3filekey		path to the S3 object.
	 * @param uidColIdx		index of the field that will be used as a unique identifier 
	 * 		  				of the entity when storing into knowledge graph.
	 * 		  				If uid should be included as a lexical form of the node,
	 * 		  				this index should be passed also in the lfColIds parameter.
	 * @param lfColStartIdx index of the first field that contains lexical form,
	 * 		  				all fields starting from this index will be considered.
	 * @param entityCategoryColIdx index of the field that contains the label of the entities.
	 * @param sourceOntologyColIdx index of the field that contains ontology name.
	 * @throws IOException	if any reading error occurs.
	 * @throws FileNotFoundException if object not found.
	 */
	public static void addNamedEntitiesFromCSV(boolean abridge, String filepath, 
											int uidColIdx, int lfColStartIdx, 
											int entityCategoryColIdx, int sourceOntologyColIdx) throws IOException, FileNotFoundException {
		
		int[] lfColIds = null;
		String entityCategory = null;
		String ontologyName = null;
		
		addNamedEntitiesFromCSV(abridge, filepath, uidColIdx, lfColIds, lfColStartIdx,
									entityCategory, entityCategoryColIdx, ontologyName, sourceOntologyColIdx);
	}
}
