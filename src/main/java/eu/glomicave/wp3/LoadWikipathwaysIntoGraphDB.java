package eu.glomicave.wp3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.CommonPrefix;

import org.neo4j.driver.types.Node;

import eu.glomicave.persistence.CoreGraphDatabase;
import eu.glomicave.persistence.NamedEntityGraphDatabase;
import eu.glomicave.persistence.PredefinedCategories;
import eu.glomicave.persistence.PredefinedRelations;
import eu.glomicave.config.GlobalParamsConfig;
import eu.glomicave.persistence.AmazonS3;


public class LoadWikipathwaysIntoGraphDB {
	private static final Logger logger = LogManager.getLogger(LoadWikipathwaysIntoGraphDB.class);

	private static final String WP = "WP";
	private static final String ORGANISM_NAME = "organism_name";
	private static final String WP_TITLE = "title";
	private static final String WP_DESCRIPTION = "description";
	
	static int poolSize = GlobalParamsConfig.MAX_POOL_SIZE;
	
	
	/*** Major methods ***/
	
	/** 
	 * Load WikiPathways data from local file system.
	 * 
	 * @param abridge
	 * @param dataFolder
	 * @throws IOException
	 */
	public static void loadWikipathwaysIntoGraphDB(boolean abridge, String dataFolder) throws IOException {
		Path rootPath = Paths.get(dataFolder);
		List<Path> paths = Files.walk(rootPath).filter(Files::isDirectory).collect(Collectors.toList());
		paths.remove(rootPath); // remove root folder
		
		Collections.sort(paths); // sort in alphabetical order

		for (Path organismPath : paths) {
			List<Path> filePaths = Files.walk(organismPath).filter(Files::isRegularFile).collect(Collectors.toList());

			for (Path filePath : filePaths) {
				//System.out.println(filePath);
				logger.info(filePath);

				Model model = RDFDataMgr.loadModel(filePath.toUri().toString());

				processWPModel(model);
			}

			if (abridge) {
				break;
			}
		}
	}			
	
	/** 
	 * Load WikiPathways data from Amazon S3 bucket.
	 * 
	 * @param abridge
	 * @param s3DirPrefix
	 */
	public static void loadWikipathwaysS3IntoGraphDB(boolean abridge, String s3DirPrefix) {
		// Adopted version for AWS S3 storage
		
		final ExecutorService pool = Executors.newFixedThreadPool(poolSize);
		
		final class WorkerThread implements Runnable {
		    private String command;
		    private Model model;
		    
		    public WorkerThread(String s, Model model){
		        this.command=s;
		        this.model=model;
		    }

		    @Override
		    public void run() {
		        //System.out.println(Thread.currentThread().getName()+" Start. "+command);
		        logger.info(Thread.currentThread().getName()+" Start. "+command);
		        processCommand(model);
		        //System.out.println(Thread.currentThread().getName()+" End.");
		        logger.info(Thread.currentThread().getName()+" End.");
		    }

		    private void processCommand(Model model) {
			    processWPModel(model);
		    }
		    
		    @Override
		    public String toString(){
		        return this.command;
		    }
		}
		
		logger.info("Loading Wikipathway data.");	
		
		try {
			List<CommonPrefix> paths = AmazonS3.getDirList(s3DirPrefix);
			
			for (CommonPrefix organismPath : paths) {
				List<S3Object> filePaths = AmazonS3.getObjectList(organismPath.prefix());
				
				for (S3Object filePath : filePaths) {				
					logger.info(filePath.key());
					
					Model model = ModelFactory.createDefaultModel();  
				    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(AmazonS3.readFileUsingS3Client(filePath.key())))) {
				    	model.read(bufferedReader, null, "TTL");
				    	// Process the model in a separate thread
				    	pool.execute(new WorkerThread("Reading: " + filePath.key(), model));
				    }		
				}
				
				if (abridge) {
					break;
				}
			}
			
			pool.shutdown();
	        while (!pool.isTerminated()) {}
	        
	        logger.info("Finished all threads.");
			
		} catch(Exception e) {
				logger.error("Error loading Wikipathway data", e);
		}
		
		logger.info("Wikipathway data loaded.");	
	}
	
	
	/** 
	 * Process a single WikiPathways record (model).
	 * 
	 * @param model
	 */
	private static void processWPModel(Model model) {
		Resource wpMetabolite = model.createResource("http://vocabularies.wikipathways.org/wp#Metabolite");
		Resource wpGeneProduct = model.createResource("http://vocabularies.wikipathways.org/wp#GeneProduct");
		Resource wpPathway = model.createResource("http://vocabularies.wikipathways.org/wp#Pathway");
		Resource wpProtein = model.createResource("http://vocabularies.wikipathways.org/wp#Protein");
	
		ResIterator pathwayResIterator = model.listResourcesWithProperty(RDF.type, wpPathway);
		Resource pathway = null;
		while (pathwayResIterator.hasNext()) {
			pathway = pathwayResIterator.next();
			if (pathway.hasProperty(DCTerms.isPartOf) == false) {
				break;
			}
		}
		
		Node pathwayNode = NamedEntityGraphDatabase.addNamedEntityNode(WP, pathway.getLocalName());
		CoreGraphDatabase.addLabel(pathwayNode, PredefinedCategories.PATHWAY.toString());

		Property property = model.getProperty("http://vocabularies.wikipathways.org/wp#organismName");
		String organismName = pathway.getProperty(property).getString();
		CoreGraphDatabase.setProperty(pathwayNode, ORGANISM_NAME, organismName);

		property = model.getProperty("http://purl.org/dc/elements/1.1/title");
		if (property != null && pathway.getProperty(property) != null) {
			String title = pathway.getProperty(property).getString();
			CoreGraphDatabase.setProperty(pathwayNode, WP_TITLE, title);
		}
			
		property = model.getProperty("http://purl.org/dc/terms/description");
		if (property != null && pathway.getProperty(property) != null) {
			String description = pathway.getProperty(property).getString();
			CoreGraphDatabase.setProperty(pathwayNode, WP_DESCRIPTION, description);
		}

		ResIterator partsOfPathwayIterator = model.listResourcesWithProperty(DCTerms.isPartOf, pathway);
		while (partsOfPathwayIterator.hasNext()) {
			try {
					
				Resource partOfPathway = partsOfPathwayIterator.next();
	
				if (partOfPathway.hasProperty(RDF.type, wpGeneProduct)) {
					addPartOfPathwayToGraphDB(pathwayNode, partOfPathway, PredefinedCategories.GENE_PRODUCT.toString());
				}
				else if (partOfPathway.hasProperty(RDF.type, wpMetabolite)) {
					addPartOfPathwayToGraphDB(pathwayNode, partOfPathway, PredefinedCategories.METABOLITE.toString());
				}
				else if (partOfPathway.hasProperty(RDF.type, wpPathway)) {
					// if subpathway doesn't exist  - create and check also :
					addPartOfPathwayToGraphDB(pathwayNode, partOfPathway, PredefinedCategories.PATHWAY.toString());
				}
	//			else if (partOfPathway.hasProperty(RDF.type, wpProtein)) {
	//				// if subpathway doesn't exist  - create and check also :
	//				addPartOfPathwayToGraphDB(pathwayNode, partOfPathway, PredefinedCategories.PROTEIN.toString());
	//			}
			} catch (Exception e) {
				logger.error("Skip loading data record due to error:", e);
				
				continue;
			}
		}
		
		// upload protein instances that were missing after the first run
		partsOfPathwayIterator = model.listResourcesWithProperty(DCTerms.isPartOf, pathway);
		while (partsOfPathwayIterator.hasNext()) {
			Resource partOfPathway = partsOfPathwayIterator.next();

			if (partOfPathway.hasProperty(RDF.type, wpProtein)) {
				// if subpathway doesn't exist  - create and check also :
				addPartOfPathwayToGraphDB(pathwayNode, partOfPathway, PredefinedCategories.PROTEIN.toString());
			}
		}	
	}
	
	/**
	 * Helper method to integrate a part of a pathway into GraphDB.
	 * 
	 * @param pathwayNode
	 * @param partOfPathway
	 * @param category
	 */
	private static void addPartOfPathwayToGraphDB(Node pathwayNode, Resource partOfPathway, String category) {
		Node node = NamedEntityGraphDatabase.addNamedEntityNode(WP, partOfPathway.getURI());
		CoreGraphDatabase.addLabel(node, category);
		//String lexicalForm = partOfPathway.getProperty(RDFS.label).getObject().toString();
		String lexicalForm = partOfPathway.getProperty(RDFS.label).getObject().toString().replace("\"","").replace("\\","");
		Node lexicalFormNode = NamedEntityGraphDatabase.addLexicalFormNode(lexicalForm);
		NamedEntityGraphDatabase.addNamedEntitiyToLexicalFormConnection(node, lexicalFormNode);

		//CoreGraphDatabase.createRelationship(geneProductNode, pathwayNode, PredefinedRelations.IS_PART_OF_PATHWAY.toString());
		CoreGraphDatabase.createRelationshipIfNotExistent(node, pathwayNode, PredefinedRelations.IS_PART_OF_PATHWAY.toString());
	}

	
	/*** Corner cases ***/
	
	/** 
	 * Load only proteins from WikiPathways data stored in Amazon S3 bucket.
	 * 
	 * @param abridge
	 * @param s3DirPrefix
	 * @throws IOException
	 */
	public static void loadWikipathwaysProteinsS3IntoGraphDB(boolean abridge, String s3DirPrefix) throws IOException {
		
		// Adopted version for AWS S3 storage
		
		logger.info("Loading Wikipathway data.");	
		
		try {
			List<CommonPrefix> paths = AmazonS3.getDirList(s3DirPrefix);
			for (CommonPrefix organismPath : paths) {
				List<S3Object> filePaths = AmazonS3.getObjectList(organismPath.prefix());
				for (S3Object filePath : filePaths) {				
					logger.info(filePath.key());
					
					Model model = ModelFactory.createDefaultModel();  
				    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(AmazonS3.readFileUsingS3Client(filePath.key())))) {
				    	model.read(bufferedReader, null, "TTL");
				    }
				    
				    processProteinDataFromWPModel(model);
				}
				
				if (abridge) {
					break;
				}
			}
			
			logger.info("Wikipathway data loaded.");	
			
		} catch(Exception e) {
				logger.error("Error loading Wikipathway data", e);
		}
	}	
	
	/** Load only protein data from WikiPathways files stored at a local file system.
	 * 
	 * @param abridge
	 * @param dataFolder
	 * @throws IOException
	 */
	public static void loadWikipathwaysProteinsIntoGraphDB(boolean abridge, String dataFolder) throws IOException {
		//String dataFolder = "./data/wikipathways/wikipathways-20220110-rdf-wp/wp";
		Path rootPath = Paths.get(dataFolder);
		List<Path> paths = Files.walk(rootPath).filter(Files::isDirectory).collect(Collectors.toList());
		paths.remove(rootPath); // remove root folder

		for (Path organismPath : paths) {
			List<Path> filePaths = Files.walk(organismPath).filter(Files::isRegularFile).collect(Collectors.toList());

			for (Path filePath : filePaths) {
				System.out.println(filePath);
				logger.info(filePath);

				Model model = RDFDataMgr.loadModel(filePath.toUri().toString());

				processProteinDataFromWPModel(model);
			}

			if (abridge) {
				break;
			}
		}
	}			
	
	/** 
	 * Process only protein data from a WikiPathways model.
	 * 
	 * @param model
	 */
	private static void processProteinDataFromWPModel(Model model) {
		Resource wpPathway = model.createResource("http://vocabularies.wikipathways.org/wp#Pathway");				
		Resource wpProtein = model.createResource("http://vocabularies.wikipathways.org/wp#Protein");
		ResIterator pathwayResIterator = model.listResourcesWithProperty(RDF.type, wpPathway);
		Resource pathway = null;
		while (pathwayResIterator.hasNext()) {
			pathway = pathwayResIterator.next();
			if (pathway.hasProperty(DCTerms.isPartOf) == false) {
				break;
			}
		}
		
		Node pathwayNode = NamedEntityGraphDatabase.addNamedEntityNode(WP, pathway.getLocalName());
		CoreGraphDatabase.addLabel(pathwayNode, PredefinedCategories.PATHWAY.toString());

		Property property = model.getProperty("http://vocabularies.wikipathways.org/wp#organismName");
		String organismName = pathway.getProperty(property).getString();
		CoreGraphDatabase.setProperty(pathwayNode, ORGANISM_NAME, organismName);
		
		ResIterator partsOfPathwayIterator = model.listResourcesWithProperty(DCTerms.isPartOf, pathway);
		while (partsOfPathwayIterator.hasNext()) {
			Resource partOfPathway = partsOfPathwayIterator.next();
			
			if (partOfPathway.hasProperty(RDF.type, wpProtein)) {
				// if subpathway doesn't exist  - create and check also 
				addPartOfPathwayToGraphDB(pathwayNode, partOfPathway, PredefinedCategories.PROTEIN.toString());
			}
		}
	}
	

	/** 
	 * Load only metabolites from WikiPathways data stored in Amazon S3 bucket.
	 * 
	 * @param abridge
	 * @param s3DirPrefix
	 * @throws IOException
	 */
	public static void loadWikipathwaysMetaboliteS3IntoGraphDB(boolean abridge, String s3DirPrefix) throws IOException {
		
		// Adopted version for AWS S3 storage
		
		logger.info("Loading Wikipathway data.");	
		
		try {
			List<CommonPrefix> paths = AmazonS3.getDirList(s3DirPrefix);
			for (CommonPrefix organismPath : paths) {
				List<S3Object> filePaths = AmazonS3.getObjectList(organismPath.prefix());
				for (S3Object filePath : filePaths) {				
					logger.info(filePath.key());
					
					Model model = ModelFactory.createDefaultModel();  
				    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(AmazonS3.readFileUsingS3Client(filePath.key())))) {
				    	model.read(bufferedReader, null, "TTL");
				    }
				    
				    processMetaboliteDataFromWPModel(model);
				}
				
				if (abridge) {
					break;
				}
			}
			
			logger.info("Wikipathway data loaded.");	
			
		} catch(Exception e) {
				logger.error("Error loading Wikipathway data", e);
		}
	}	
	
	/** 
	 * Load only metabolite data from WikiPathways files stored at a local file system.
	 * 
	 * @param abridge
	 * @param dataFolder
	 * @throws IOException
	 */
	public static void loadWikipathwaysMetaboliteIntoGraphDB(boolean abridge, String dataFolder) throws IOException {
		//String dataFolder = "./data/wikipathways/wikipathways-20220110-rdf-wp/wp";
		Path rootPath = Paths.get(dataFolder);
		List<Path> paths = Files.walk(rootPath).filter(Files::isDirectory).collect(Collectors.toList());
		paths.remove(rootPath); // remove root folder

		for (Path organismPath : paths) {
			List<Path> filePaths = Files.walk(organismPath).filter(Files::isRegularFile).collect(Collectors.toList());

			for (Path filePath : filePaths) {
				System.out.println(filePath);
				logger.info(filePath);

				Model model = RDFDataMgr.loadModel(filePath.toUri().toString());

				processMetaboliteDataFromWPModel(model);
			}

			if (abridge) {
				break;
			}
		}
	}			
	
	
	/** 
	 * Process only metabolite data from a WikiPathways model.
	 * 
	 * @param model
	 */
	private static void processMetaboliteDataFromWPModel(Model model) {
		Resource wpPathway = model.createResource("http://vocabularies.wikipathways.org/wp#Pathway");				
		Resource wpMetabolite = model.createResource("http://vocabularies.wikipathways.org/wp#Metabolite");
		ResIterator pathwayResIterator = model.listResourcesWithProperty(RDF.type, wpPathway);
		Resource pathway = null;
		while (pathwayResIterator.hasNext()) {
			pathway = pathwayResIterator.next();
			if (pathway.hasProperty(DCTerms.isPartOf) == false) {
				break;
			}
		}
		
		Node pathwayNode = NamedEntityGraphDatabase.addNamedEntityNode(WP, pathway.getLocalName());
		CoreGraphDatabase.addLabel(pathwayNode, PredefinedCategories.PATHWAY.toString());
	
		Property property = model.getProperty("http://vocabularies.wikipathways.org/wp#organismName");
		String organismName = pathway.getProperty(property).getString();
		CoreGraphDatabase.setProperty(pathwayNode, ORGANISM_NAME, organismName);
		
		ResIterator partsOfPathwayIterator = model.listResourcesWithProperty(DCTerms.isPartOf, pathway);
		while (partsOfPathwayIterator.hasNext()) {
			Resource partOfPathway = partsOfPathwayIterator.next();
			
			if (partOfPathway.hasProperty(RDF.type, wpMetabolite)) {
				// if subpathway doesn't exist  - create and check also 
				addPartOfPathwayToGraphDB(pathwayNode, partOfPathway, PredefinedCategories.METABOLITE.toString());
			}
		}
	}

}

