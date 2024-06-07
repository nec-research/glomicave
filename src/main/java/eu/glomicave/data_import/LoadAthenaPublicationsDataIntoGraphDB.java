package eu.glomicave.data_import;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import eu.glomicave.config.GlobalParamsConfig;
import eu.glomicave.persistence.CoreGraphDatabase;
import eu.glomicave.persistence.PredefinedCategories;
import eu.glomicave.persistence.PublicationDatabase;
import eu.glomicave.wp3.NamedEntityInitialization;

public class LoadAthenaPublicationsDataIntoGraphDB {
	private static final Logger logger = LogManager.getLogger(LoadAthenaPublicationsDataIntoGraphDB.class);
	
	private static final int ABRIDGE_MAX_NUM = 10;
	
	//static int poolSize = 11;
	static int poolSize = GlobalParamsConfig.MAX_POOL_SIZE;
	
	final class WorkerThread implements Runnable {
	    private String command;
	    String doi;
	    
	    public WorkerThread(String s, String doi){
	        this.command=s;
	        this.doi=doi;
	    }

	    @Override
	    public void run() {
	        //System.out.println(Thread.currentThread().getName()+" Start. Process line number = "+command);
	        logger.info(Thread.currentThread().getName()+" Start. Process line number = "+command);
	    	processCommand(doi);
	        //System.out.println(Thread.currentThread().getName()+" End.");
	        logger.info(Thread.currentThread().getName()+" End.");
	    }

	    private void processCommand(String doi) {
	    	try {
				LoadAthenaPublicationsDataIntoGraphDB.integrateAthenaPublicationIntoGraphDB(doi);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				logger.error(e);
			}
	    }
	    
	    @Override
	    public String toString(){
	        return this.command;
	    }
	}

	
	/** 
	 * Integrate all publications from the publications table into GraphDB. 
	 * This will create publication nodes in GraphDB.
	 * 
	 */
	public static void integrateAthenaPublicationsIntoGraphDB(boolean abridge) throws Exception {
		List<String> dois = null;		
		final ExecutorService pool = Executors.newFixedThreadPool(poolSize);
		// node counts
		int publicationNodesCounts = CoreGraphDatabase.countNodeType(PredefinedCategories.PUBLICATION.toString());
		
		logger.info("*** Integrate publication data into Graph DB ***");
		
		dois = PublicationDatabase.getAllDOIs();

		if (abridge) {
			dois = dois.subList(0, Math.min(ABRIDGE_MAX_NUM, dois.size()));
			logger.info("! Shortened pipeline: {} DOIs will be considered.", dois.size());
		}

		// iterate over dois and import each record
		int processed = 0;
		for (String doi : dois) {
			try {
				pool.execute(new LoadAthenaPublicationsDataIntoGraphDB().new WorkerThread("" + processed, doi));
			} catch (Exception e) {
				logger.error("Error adding publication '{}'.", doi, e);
			}
			//integrateAthenaPublicationIntoGraphDB(doi);
			processed++;
			
			logger.info(processed + " / " + dois.size() + " publications processed.");
		}
		pool.shutdown();
        while (!pool.isTerminated()) {}
        
        publicationNodesCounts = CoreGraphDatabase.countNodeType(PredefinedCategories.PUBLICATION.toString()) - publicationNodesCounts;
        
        logger.info("Finished all threads.");
        logger.info("Publications records processed: {}. New publication nodes created: {}", processed, publicationNodesCounts);
	}
	
	
	/** 
	 * Integrate only publications from a specified part in the publications table.
	 * 
	 */
	public static void integrateAthenaPublicationsPartIntoGraphDB(boolean abridge, int part) throws Exception {
		List<String> dois = null;		
		final ExecutorService pool = Executors.newFixedThreadPool(poolSize);
		// node counts
		int publicationNodesCounts = CoreGraphDatabase.countNodeType(PredefinedCategories.PUBLICATION.toString());
		
		logger.info("*** Integrate publication data into Graph DB ***");
		
		dois = PublicationDatabase.getAllDOIs(part);
		if (dois == null) {
			logger.info("No new publications to add into graph database.");
			return;
		} else {
			logger.info("DOIs found in partition {} of the publication database: {}.", part, dois.size());
		}

		if (abridge) {
			dois = dois.subList(0, Math.min(ABRIDGE_MAX_NUM, dois.size()));
			logger.info("! Shortened pipeline: {} DOIs will be considered.", dois.size());
		}

		// iterate over dois and import each record
		int processed = 0;
		for (String doi : dois) {
			try {
				pool.execute(new LoadAthenaPublicationsDataIntoGraphDB().new WorkerThread("" + processed, doi));
			} catch (Exception e) {
				logger.error("Error adding publication '{}'.", doi, e);
			}
			//integrateAthenaPublicationIntoGraphDB(doi);
			processed++;
			
			logger.info(processed + " / " + dois.size() + " publications processed.");
		}
		pool.shutdown();
        while (!pool.isTerminated()) {}
        
        publicationNodesCounts = CoreGraphDatabase.countNodeType(PredefinedCategories.PUBLICATION.toString()) - publicationNodesCounts;
        
        logger.info("Finished all threads.");
        logger.info("{} publications records processed, {} PUBLICATION nodes created.", processed, publicationNodesCounts);
	}
	
	
	/** Create a publication node in a GraphDB for a single publication.
	 * 
	 * @param doi
	 * @return
	 * @throws Exception
	 */
	private static Publication integrateAthenaPublicationIntoGraphDB(String doi) throws Exception {
		Publication publication = new Publication();
		Map<String, String> publicaionData = PublicationDatabase.getPublicationData(doi);
		
		publication.sqlId = Integer.parseInt(publicaionData.get("id"));
		publication.doi = doi;
		publication.title = publicaionData.get("title");
		publication.year = publicaionData.get("year");
		publication.authors = publicaionData.get("authors");
		publication.paperAbstract = publicaionData.get("abstract");
		
		if (publication.paperAbstract != null && publication.paperAbstract.length() != 0) {
			PublicationGraphDatabase.createPublicationNodeWithProperties(publication);
			logger.info("Node for publication '{}' (re-)created.", doi);
		} else {
			logger.warn("Node for publication '{}' can't be added: no abstract text.", doi);
		}

		return publication;
	}
	
}
