package eu.glomicave.persistence;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.opencsv.CSVWriter;
import org.json.JSONArray;
import org.json.JSONObject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import eu.glomicave.config.AmazonAthenaConfig;
import eu.glomicave.data_import.SemanticScholarAPI;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.Row;
import software.amazon.awssdk.services.athena.paginators.GetQueryResultsIterable;
import software.amazon.awssdk.services.s3.model.CommonPrefix;


public class PublicationDatabase {
	private static Logger logger = LogManager.getLogger(PublicationDatabase.class);
	
	private static PublicationDatabase instance = null;
	
	private static final int ABRIDGE_THRESHOLD = 3;
	
	// tables to generate
	public static final String PUBLICATIONS = "publications";
	// codes for the source column
	private static final int INITIAL = 1;
	private static final int REFERENCE_BASED = 3;
	private static final int CITATION_BASED = 4;
	// temporary file options
	private static final String CSV_EXTENSION = "csv";
	// tab symbol should be used to separate columns in AWS Athena tables
	private static final char CSV_SEPARATOR = '\t';	
	// path prefix to table data in s3
	private String athenaTableS3Prefix;

	private String athenaDatabase;
	
	// latest document id in the table before appending new data
	private int lastDocumentId = -1;
	// table-related file part index to be moved into s3
	// the last index of currently existing Athena table partition is curTablePart-1
	private int curTablePart = 0;
	// dois in table before update
	private List<String> prevDOIs = null;
	
	private Path localTmpDir;
	private Path localCsvFile;
	
	// list of new DOIs to be added
	List<List<String>> curDOIs = null;
	
	private boolean abridge = false;	// indicates short version of processing pipeline
	
	/** Create class instance if not exists. */
	public static void initPublicationDatabase(boolean abridge, String localDataDir) {
		logger.info("*** Initialize publication database ***");
		if (instance == null) 
			instance = new PublicationDatabase(localDataDir, abridge);	
	}
	
	
	/** Class constructor. */
    private PublicationDatabase(String localDataDir, boolean abridge) {
    	this.abridge = abridge;
   
		// s3 location for all files related to the table
		athenaTableS3Prefix = AmazonAthenaConfig.getInstance().getAthenaTablesS3Prefix()+PUBLICATIONS+"/";
    	
		athenaDatabase = AmazonAthenaConfig.getInstance().getDatabase();
		try {
			// get info about latest data chunks
			if (AmazonAthenaTableManager.isTableInDatabase(PUBLICATIONS, athenaDatabase)) {
				lastDocumentId = getMaxPublicationId();
				curTablePart = getMaxTablePartId() + 1;
				// get all existing dois from table
			    prevDOIs = getAllDOIs();
			}
			else {
				//createPublicationsTable(false);
				lastDocumentId = -1;
				curTablePart = 0;
			}
			// init local data storage
			localTmpDir = Files.createDirectories(Paths.get(localDataDir));
    		localCsvFile = Paths.get(localTmpDir.toString(), PUBLICATIONS+"_part-"+curTablePart+"."+CSV_EXTENSION);
		} catch (Exception e) {
			//e.printStackTrace();
	    	logger.error("Error while initializing publications database.", e);
		} 
	    
		if (prevDOIs != null) {
	    	logger.info("There are {} already processed DOIs found in the publication database.", prevDOIs.size());
	    }
    }
	
    /** Get class instance. */
	public static PublicationDatabase getInstance() {
		return instance;
	}
	
	public int getCurTablePart() {
		return curTablePart;
	}
	
	public int getLastTablePart() {
		return curTablePart-1;
	}
	
	
	// Read initial DOI lists.
	
	/** Read list of dois from AWS S3 object. */
	public void addInitDOIsFromS3Object(String s3ObjectKey) {
		logger.info("Reading seed DOIs from Amazon S3 object...");
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(AmazonS3.readFileUsingS3Client(s3ObjectKey)))) {
	    	curDOIs = getDOIfromStream(reader);
			logger.info("Count DOIs for further processing: {}.", curDOIs.size());
		} catch (IOException e) {
			//e.printStackTrace();
	    	logger.error("Error while reading list of DOIs.", e);
		}
	}
	
	/** Read list of dois from local txt file. */
	public void addInitDOIsFromFile(String doiFilePath) {
	    try (BufferedReader reader = new BufferedReader(new FileReader(doiFilePath))) {
	    	//curDOIs = getDOIfromStream(reader);
	    	curDOIs = getDOIfromStream(reader);
	    	logger.info("DOIs count for further processing: {}.", curDOIs.size());
	    } catch(IOException e) {
	    	//e.printStackTrace();
	    	logger.error("Error while reading list of DOIs.", e);
	    }
	}
	
	/** Helper method to get initial doi list from stream reader. */
	private List<List<String>> getDOIfromStream(BufferedReader reader) throws IOException {
	    List<List<String>> dois = new ArrayList<>();		
		
    	int processed = 0;
    	int readLines = 0;
	    String line;
    	while ((line = reader.readLine()) != null)
		{
    		line = line.strip();
			readLines++;
    		// check if doi already in table
			if (prevDOIs != null && prevDOIs.contains(line)) {
				continue;
			}
    		
			// check is element is already in list and add new doi
			int j = 0;
			while(j < dois.size() && !dois.get(j).get(0).contains(line)) {
				j++;
			}
			if (j >= dois.size()) {
				// if element is not in the list
				List<String> row = new ArrayList<>();
				row.add(line);
				row.add(Integer.toString(INITIAL));
				dois.add(new ArrayList<>(row));
				
				// debug
				logger.info("New DOI found: '{}'. Line no: {}, prevDOIs contains: {}.", line, readLines, prevDOIs.contains(line));
				
				processed++;
			}
			if (abridge && processed > 5) {
				logger.info("! Exit due to shortened pipeline. New {} out of {} DOIs selected for further processing. !", processed, readLines);
				break;
			}
		}
    	logger.info("New {} out of {} DOIs selected for further processing.", processed, readLines);
    	return dois;
	}
	
	
	private List<List<String>> getDOIAndSourceFromStream(BufferedReader reader) throws IOException {
	    List<List<String>> dois = new ArrayList<>();		
		
    	int processed = 0;
	    String line;
    	while ((line = reader.readLine()) != null)
		{
    		// check if doi already in table
			if (prevDOIs != null && prevDOIs.contains(line.split(",")[0].strip())) {
				continue;
			}
    		
			// check is element is already in list and add new doi
			int j = 0;
			while(j < dois.size() && !dois.get(j).get(0).contains(line)) {
				j++;
			}
			if (j >= dois.size()) {
				List<String> row = new ArrayList<>();
				for(String value: line.split(","))
		        {
					row.add(value);
		        }
				dois.add(new ArrayList<>(row));
				
				processed++;
			}
			if (abridge && processed > 5) {
				break;
			}
		}
    	return dois;
	}
	
	
	// Extract publication data from SemanticScholarAPI.
	
	/** Extract all citation- and reference-based dois and add to current doi list (max ~2 mln dois possible). 
	 * 
	 * @param nrefs maximum number of references to explore per publication
	 * @param ncits maximum number of citing papers to explore per publication
	 * 
	 * @throws IOException 
	 * */
	public void retrieveCitationsAndReferences(int nrefs, int ncits) throws IOException {
		boolean sleep = true;
		// iterate only over currently uploaded doi list
		int initDOIsize = 0;
		
		if (curDOIs != null) {
			initDOIsize = curDOIs.size();
		}
		
		String tmpRefCitationsFile =localTmpDir.toString()+"/dois_w_ref_and_citations.csv";
		FileWriter writer = new FileWriter(tmpRefCitationsFile); 
		
		logger.info("*** Retrieving citation and reference data ***");
		logger.info("Count DOIs for citation and reference retrieval: {}.", initDOIsize);
		
		if (nrefs > -1) {
			logger.info("Max number of {} references will be considered per publication.", nrefs);
		}
		if (ncits > -1) {
			logger.info("Max number of {} citing papers will be considered per publication.", ncits);
		}
		
		if (abridge) {
			// if shortened pipeline
			initDOIsize = Math.min(initDOIsize, ABRIDGE_THRESHOLD);
			logger.info("! Shortened pipeline: {} DOIs will be searched for references and citations. !", initDOIsize);
		}
		
		for (int doiIndex = 0; doiIndex < initDOIsize; doiIndex++) {
			List<String> doi = curDOIs.get(doiIndex);
			
			logger.info("Record {} / {}.", doiIndex+1, initDOIsize);
			try {
				logger.info("Retrieving references for DOI '{}'.", doi.get(0));
				// get references
				JSONObject jsonObject = SemanticScholarAPI.getReferences(doi.get(0), sleep);
				
				if (jsonObject != null) {
					JSONArray jsonArray = jsonObject.getJSONArray("data");
					if (jsonArray != null) {
						logger.info(jsonArray.length() + " reference papers found.");

						if (abridge) {
							// keep only three last references for the test
							while (jsonArray.length() >= ABRIDGE_THRESHOLD) {
								jsonArray.remove(0);
							}
							logger.info("! Shortened pipeline. Only {} reference DOIs will be considered. !", jsonArray.length());
						}
						
						int countRef = 0;
						int processedRef = 0;
						for (Object entry : jsonArray) {
							// take only nrefs references
							if (nrefs > -1 && countRef >= nrefs) {
								logger.info("Max number of references reached. Only {} references will be considered for this publication.", nrefs);
								break;
							}
							countRef++;
							
							JSONObject citedPaper = ((JSONObject) entry).getJSONObject("citedPaper");
							Object externalIds = citedPaper.get("externalIds");
							if (externalIds instanceof JSONObject && ((JSONObject) externalIds).has("DOI")) {
								String newDOI = ((JSONObject) externalIds).getString("DOI").strip();

								// check is element is already in list and add new doi
								if (prevDOIs != null && prevDOIs.contains(newDOI)) {
									continue;
								}
								
								int j = 0;
								while(j < curDOIs.size() && !curDOIs.get(j).get(0).contains(newDOI)) {
									j++;
								}
								// add new citation
								if (j >= curDOIs.size()) {
									List<String> citerow = new ArrayList<>();
									citerow.add(newDOI);
									citerow.add(Integer.toString(CITATION_BASED));
									curDOIs.add(new ArrayList<>(citerow));
									
									processedRef++;
									
									// log into file 
									//writer.write(newDOI+'\t'+Integer.toString(CITATION_BASED)+System.lineSeparator());
									writer.write(newDOI+CSV_SEPARATOR+Integer.toString(CITATION_BASED)+System.lineSeparator());
								}
							}
						}
						logger.info("New {} out of {} references added to the DOIs processing list.", processedRef, jsonArray.length());
					}
				}
				else {
					logger.warn("Could not retrieve referencdes for the record index {} with DOI '{}'.", doiIndex, doi.get(0));
					//System.err.println("Could not retrieve publication with DOI: '" + doi + "'.");
				}

				logger.info("Retrieving citing papers for DOI '{}'.", doi.get(0));
				// get citations
				jsonObject = SemanticScholarAPI.getCitations(doi.get(0), sleep);
				if (jsonObject != null) {
					JSONArray jsonArray = jsonObject.getJSONArray("data");

					if (jsonArray != null) {
						logger.info(jsonArray.length() + " citing papers found.");
						
						if (abridge) {
							while (jsonArray.length() >= ABRIDGE_THRESHOLD) {
								jsonArray.remove(0);
							}
							logger.info("! Shortened pipeline. Only {} citing DOIs will be considered. !", jsonArray.length());
						}
						
						int countCits = 0;
						int processedCits = 0;
						for (Object entry : jsonArray) {
							// take only ncits citing documents
							if (ncits > -1 && countCits >= ncits) {
								logger.info("Max number of citings reached. Only {} citing papers will be considered for this publication.", ncits);
								break;
							}
							countCits++;
							
							JSONObject citingPaper = ((JSONObject) entry).getJSONObject("citingPaper");
							Object externalIds = citingPaper.get("externalIds");
							if (externalIds instanceof JSONObject && ((JSONObject) externalIds).has("DOI")) {
								String newDOI = ((JSONObject) externalIds).getString("DOI").strip();
								
								// check is element is already in list and add new doi
								if (prevDOIs != null && prevDOIs.contains(newDOI)) {
									continue;
								}
								
								int j = 0;
								while(j < curDOIs.size() && !curDOIs.get(j).get(0).contains(newDOI)) {
									j++;
								}
								// add new reference
								if (j >= curDOIs.size()) {
									List<String> refrow = new ArrayList<>();
									refrow.add(newDOI);
									refrow.add(Integer.toString(REFERENCE_BASED));
									curDOIs.add(new ArrayList<>(refrow));
									
									processedCits++;
									
									// log into file 
									//writer.write(newDOI+'\t'+Integer.toString(REFERENCE_BASED)+System.lineSeparator());
									writer.write(newDOI+CSV_SEPARATOR+Integer.toString(REFERENCE_BASED)+System.lineSeparator());
								}
							}
						}
						logger.info("New {} out of {} citing papers added to the DOIs processing list.", processedCits, jsonArray.length());
					}
				}
				else {
					logger.warn("Could not retrieve citing papers for the record index {} with DOI '{}'.", doiIndex, doi.get(0));
					//System.err.println("Could not retrieve publication with DOI: '" + doi + "'.");
				}

			} catch (Exception e) {
				//e.printStackTrace();
				logger.error("Error while retrieving citations and references for the record index {} with DOI '{}'.", doiIndex, doi, e);
			}
		}
		writer.close();
		logger.info("Finished reference and citation retrieval.");
	}
	
	
	/** Extract citation- and reference-based dois for existing records pointed by ids in the publications table.
	 * @throws IOException */
	public void retrieveCitationsAndReferencesForPublicationIds(int startIdx, int lastIdx, int source, int nrefs, int ncits) throws IOException {
		List<List<String>> dois = new ArrayList<>();	
		
		try {
			List<List<Datum>> resultRows = AmazonAthenaTableManager.executeQueryWithResultRows(
					String.format("SELECT doi, source FROM %s WHERE id BETWEEN %s AND %s;", PUBLICATIONS, startIdx, lastIdx));
			
			if (resultRows == null) {
				return;
			}
			
			for (List<Datum> row : resultRows) {
				List<String> refrow = new ArrayList<>();
				
				if (row.get(1) != null && Integer.valueOf(row.get(1).varCharValue()) == source) {
					refrow.add(row.get(0).varCharValue());
					refrow.add(row.get(1).varCharValue());
					
					dois.add(new ArrayList<>(refrow));
				}
			}
			
			if (dois.size() < 1) {
				return;
			}
			
			this.curDOIs = dois;
			
			retrieveCitationsAndReferences(nrefs, ncits);
			
			
		} catch (Exception e) {
			//e.printStackTrace();
			logger.error("Error while retrieving citations and references for publication in table for indices in range [{} - {}].", startIdx, lastIdx, e);
		}
	}
	
	
	/** Extract citation- and reference-based dois for existing publication records in the publications table loaded as initial dois.
	 * @throws IOException */
	public void retrieveCitationsAndReferencesForPublicationsInTable(int nrefs, int ncits) throws IOException {
		retrieveCitationsAndReferencesForPublicationIds(0, lastDocumentId, INITIAL, nrefs, ncits);
	}
	
	
	/** Add publication doi, source, title, year, authors, abstract data into csv table. */
	public void addPublicationData() {
		if (curDOIs == null) {
			return;
		}
		
		logger.info("*** Retrieving text data for the collected DOIs ***");
		logger.info("Count DOIs to be processed: {}.", curDOIs.size());
		try {
	        CSVWriter writer  = new CSVWriter(
					new FileWriter(localCsvFile.toString()), 
					CSV_SEPARATOR, 
					CSVWriter.DEFAULT_QUOTE_CHARACTER, 
					CSVWriter.DEFAULT_ESCAPE_CHARACTER, 
					CSVWriter.DEFAULT_LINE_END);

			for (int doiIndex = 0; doiIndex < curDOIs.size(); doiIndex++) {
				logger.info("Record {} / {}.", doiIndex+1, curDOIs.size());
				
				String doi = curDOIs.get(doiIndex).get(0);
				String source = curDOIs.get(doiIndex).get(1);
				// check if doi is already in the table
				if (prevDOIs != null && prevDOIs.contains(doi)) {
					continue;
				}
				
				try {						
					JSONObject jsonObject = SemanticScholarAPI.querySemanticScholar(doi, true);
		
					if (jsonObject != null) {
						String title = "";
						String year = "";
						String authors = "";
						String abstr = "";
		
						if (!jsonObject.isNull("title")) {
							title = (String) jsonObject.get("title");
							title = title.replaceAll("[\"\t\r\n]", " ").trim();
						}
						
						if (!jsonObject.isNull("year")) {
							year = String.valueOf(jsonObject.get("year"));
						}
							
						JSONArray jsonArray = jsonObject.getJSONArray("authors");
						if (jsonArray != null) {
							for (Object entry : jsonArray) {
								if (entry instanceof JSONObject && ((JSONObject) entry).has("name")) {
									authors += "; "+((JSONObject) entry).getString("name");
								}
							}
							authors = authors.replaceAll("[\"\t\r\n]", " ").trim().substring(1);
						}

						if (!jsonObject.isNull("abstract")) {
							abstr = (String) jsonObject.get("abstract");
							abstr = abstr.replaceAll("[\"\t\r\n]", " ").trim();
						}
						
						writer.writeNext(new String[]{Integer.toString(doiIndex+lastDocumentId+1), Integer.toString(curTablePart), doi, source, title, year, authors, abstr});					
						writer.flush();
						logger.info("Text data for DOI '{}' added.", doi);
						logger.info((doiIndex + 1) + " / " + curDOIs.size() + " titles and abstracts retrieved via SemanticScholar API.");
					}
					else {
						logger.warn("Could not retrieve data for the record index {} with DOI '{}'", doiIndex, doi);
						//System.err.println("Could not retrieve data for publication '" + doi + "'.");
					}
				} catch (Exception e2) {
					//e2.printStackTrace();
					logger.error("Error extracting info for the record index {} with DOI '{}'", doiIndex, curDOIs.get(doiIndex).get(0), e2);
				}
			}
			writer.close();
			
		} catch (IOException e1) {
			//e1.printStackTrace();
			logger.error("Error while extracting publication data.", e1);
		}
		logger.info("Finished retrieving text data for the collected DOIs.");
	}
	
	
	/** Add publication doi, source, title, year, authors, abstract data into csv table. */
	public void addPublicationDataBatch() {
		// TBD: Batch request to retrieve publication data from Semantic Scholar.
		
	}
	
	/** Add publication doi, source, title, year, authors, abstract data in csv format into s3. */
	public void addPublicationDataAndWriteToS3() {
		System.out.println("Found " + curDOIs.size() + " new DOIs to retrieve title and abstract.");
		// S3 object name to write content 
		try {

			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			OutputStreamWriter streamWriter = new OutputStreamWriter(stream);
			//CSVWriter writer = new CSVWriter(streamWriter);
			
	        CSVWriter writer  = new CSVWriter(
					//new FileWriter(localCsvFile.toString()), 
	        		streamWriter,
					CSV_SEPARATOR, 
					CSVWriter.DEFAULT_QUOTE_CHARACTER, 
					CSVWriter.DEFAULT_ESCAPE_CHARACTER, 
					CSVWriter.DEFAULT_LINE_END);

			for (int doiIndex = 0; doiIndex < curDOIs.size(); doiIndex++) {
				String doi = curDOIs.get(doiIndex).get(0);
				String source = curDOIs.get(doiIndex).get(1);
				
				// check if doi is already in the table
				if (prevDOIs != null && prevDOIs.contains(doi)) {
					continue;
				}
				
				try {
					JSONObject jsonObject = SemanticScholarAPI.querySemanticScholar(doi, true);
		
					if (jsonObject != null) {
						String title = "";
						String year = "";
						String authors = "";
						String abstr = "";
		
						if (!jsonObject.isNull("title")) {
							title = (String) jsonObject.get("title");
							title = title.replaceAll("[\"\t\r\n]", " ").trim();
						}
						
						if (!jsonObject.isNull("year")) {
							year = (String) jsonObject.get("abstract");
							year = year.replaceAll("[\"\t\r\n]", " ").trim();
						}
							
						JSONArray jsonArray = jsonObject.getJSONArray("authors");
						if (jsonArray != null) {
							for (Object entry : jsonArray) {
								if (entry instanceof JSONObject && ((JSONObject) entry).has("name")) {
									authors += "; "+((JSONObject) entry).getString("name");
								}
							}
							authors = authors.replaceAll("[\"\t\r\n]", " ").trim();
						}

						if (!jsonObject.isNull("abstract")) {
							abstr = (String) jsonObject.get("abstract");
							abstr = abstr.replaceAll("[\"\t\r\n]", " ").trim();
						}
						
						writer.writeNext(new String[]{Integer.toString(doiIndex+lastDocumentId+1), Integer.toString(curTablePart), doi, source, title, year, authors, abstr});					
						writer.flush();
						logger.info((doiIndex + 1) + " / " + curDOIs.size() + " titles and abstracts retrieved via SemanticScholar API.");
					}
					else {
						logger.warn("Could not retrieve data for the record index {} with DOI '{}'.", doiIndex, doi);
						System.err.println("Could not retrieve data for publication '" + doi + "'.");
					}
				} catch (Exception e2) {
					//e2.printStackTrace();
					logger.error("Error extracting info for the record index {} with DOI '{}'.", doiIndex, curDOIs.get(doiIndex).get(0), e2);
				}
			}
			writer.close();
			// write to s3 from byte array
			AmazonS3.putS3ObjectFromBytes(stream.toByteArray(), athenaTableS3Prefix+"/"+curTablePart+"/"+PUBLICATIONS+"_part-"+Integer.toString(curTablePart)+".csv");
			stream.close();
			
			lastDocumentId = getMaxPublicationId();
			curTablePart++;

		} catch (Exception e1) {
			//e1.printStackTrace();
			logger.error("Error while extracting publication data", e1);
		}
	}
	
	
	// Work with publication tables in Athena.
	
	
	/** Create table in Athena from s3 files. */
	public static void createPublicationsTable() {
		createPublicationsTable(false);
	}

	/** Create table in Athena from s3 files.
	 * May decide between ICEBERG table and standard table type (doesn't support INSERT and UPDATE queries). 
	 */
	public static void createPublicationsTable(boolean icebergTable) {
		String s3Location = AmazonAthenaConfig.getInstance().getAthenaS3Bucket()+"/" +
										AmazonAthenaConfig.getInstance().getAthenaTablesS3Prefix()+PUBLICATIONS+"/";
		
		logger.info("Table '{}' will be created from '{}' location.", PUBLICATIONS, s3Location);
		
		if (icebergTable) {
			AmazonAthenaTableManager.executeQuery(String.format("""			
					CREATE TABLE IF NOT EXISTS `%s` (
					id INT,
					part INT,
					doi STRING,
					source INT,
					title STRING,
					year STRING,
					authors STRING,
					abstract STRING)
					LOCATION
					'%s'
					TBLPROPERTIES ( 'table_type' ='ICEBERG' );""", 
					PUBLICATIONS, s3Location));		
			
		} else {
			AmazonAthenaTableManager.executeQuery(String.format("""
					CREATE EXTERNAL TABLE IF NOT EXISTS `%s` (
					id INT,
					part INT,
					doi STRING,
					source INT,
					title STRING,
					year STRING,
					authors STRING,
					abstract STRING)
					ROW FORMAT SERDE 'org.apache.hadoop.hive.serde2.OpenCSVSerde'
					WITH SERDEPROPERTIES ( "separatorChar" = "\t" )
					LOCATION
					'%s';""", 
					PUBLICATIONS, s3Location));		
			}
	}

	/** Drop and recreate publications table from existing s3 data. */
	public static void dropPublicationsTable(boolean clearS3Data) {
		AmazonAthenaTableManager.dropTable(PUBLICATIONS, clearS3Data);
	}

	// Move filled data file with publication data into s3 bucket.
	
	/** Move file with new data chunk into s3 and update publications table. 
	 *	
	 * @return	number of the moved table partition, or -1 if no new data added to AWS Athena table storage 
	 */
	public int appendDataToAthena() {
		try {
			if (Files.size(localCsvFile) > 0) {							
				String newAthenaObject = athenaTableS3Prefix+curTablePart+"/"+PUBLICATIONS+"_part-"+Integer.toString(curTablePart)+".csv";  
				
				AmazonS3.putS3Object(localCsvFile.toString(), newAthenaObject);
				logger.info("Publication data moved to " + newAthenaObject + ".");
				// generate publications table from data in Athena
				if (!AmazonAthenaTableManager.isTableInDatabase(PUBLICATIONS, athenaDatabase)) {
					createPublicationsTable(false);
					logger.info("Publications table generated in Athena.");					
				}

				lastDocumentId = getMaxPublicationId();
				curTablePart++;
				
				return curTablePart-1;
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("Error while moving data to Athena database.", e);
		}
		// if no new data added - return '-1'
		logger.info("No new data to append.");
		return -1;
	}
	
	// Manipulations with publications data in Athena table.

	/** Get all DOIs of the publications in the table.  */
	public static List<String> getAllDOIs() {
		logger.info("Getting list of all DOIs from the publications database...");
		return AmazonAthenaTableManager.executeQueryWithResultColumn(
				"SELECT DISTINCT doi FROM "+ PUBLICATIONS +";", 0);
	}
	
	/** Get all DOIs of the publications in the table for specific part.  */
	public static List<String> getAllDOIs(int part) {
		logger.info("Getting list of DOIs for part {} from the publications database...", part);
		return AmazonAthenaTableManager.executeQueryWithResultColumn(
				"SELECT DISTINCT doi FROM "+ PUBLICATIONS + " WHERE part="+ String.valueOf(part)+";", 0);
	}
	
	/** Get publication record id in table by doi. */
	public static int getPublicationId(String doi) {
		int id = -1;
		try {
			GetQueryResultsIterable qResults = AmazonAthenaTableManager.executeQueryWithResults(
					String.format("SELECT id FROM %s WHERE doi='%s';", PUBLICATIONS, doi));
			
	        for (GetQueryResultsResponse result : qResults) {
				boolean first = true;
				if (result.resultSet().rows().size() > 1) {
					throw new Exception("Single result query returned more than one result.");
				}
			    for (Row row : result.resultSet().rows()) {
			    	if (first) {
			    		first = false;
			    	} else {
			    		id = Integer.valueOf(row.data().get(0).varCharValue());					
		    		}
		    	}
	        }
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("Error getting publication id", e);
		}
		return id;
	}
	
	public static String getAbstract(int documentId) {
		try {
			GetQueryResultsIterable qResults = AmazonAthenaTableManager.executeQueryWithResults(
					String.format("SELECT abstract FROM %s WHERE id=%s;", PUBLICATIONS, documentId));
			
			for (GetQueryResultsResponse result : qResults) {
				boolean first = true;
				if (result.resultSet().rows().size() > 1) {
					return null;
				}
			    for (Row row : result.resultSet().rows()) {
			    	if (first) {
			    		first = false;
			    	} else {
			    		return row.data().get(0).varCharValue();					
		    		}
		    	}
	        }
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("Error getting publication abstract", e);
		}
		return null;
	}
	
	/** Returns title, year, authors and abstract of a publication by doi. */
	public static Map<String, String> getPublicationData(String doi) {
		try {
			List<List<Datum>> resultRows = AmazonAthenaTableManager.executeQueryWithResultRows(
					String.format("SELECT id, title, year, authors, abstract FROM %s WHERE doi='%s';", PUBLICATIONS, doi));
			
			if (resultRows == null) {
				return null;
			}
			
			Map<String, String> publicationData = new HashMap<>();
			
			publicationData.put("id", resultRows.get(0).get(0).varCharValue());
			publicationData.put("title", resultRows.get(0).get(1).varCharValue());
			publicationData.put("year", resultRows.get(0).get(2).varCharValue());
			publicationData.put("authors", resultRows.get(0).get(3).varCharValue());
			publicationData.put("abstract", resultRows.get(0).get(4).varCharValue());
			
			return publicationData;
			
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("Error reading publication data", e);
		}
		return null;
	}
		
	/** Return maximum publication id in the current table.  */
	public static int getMaxPublicationId() throws Exception {
		try {
			List<String> resultList = AmazonAthenaTableManager.executeQueryWithResultColumn(
					String.format("SELECT max(id) FROM %s;", PUBLICATIONS), 0);
			
			if (resultList.size() > 1) {
				throw new Exception("Single result query returned more than one result.");
			} else if (resultList.size() == 1 && resultList.get(0) != null) {
				return Integer.valueOf(resultList.get(0));	
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("Error analysing publication ids", e);
		}
		return -1;
	}
	
	/** Get latest number of a datachunck in s3 with the data for the table */
	public int getMaxTablePartId() {
		List<CommonPrefix> dirList = AmazonS3.getDirList(athenaTableS3Prefix);
		// pattern to extract the part number
		Pattern pattern = Pattern.compile("^.*(\\d+)/$");
		int part = -1;
		
		for (CommonPrefix object : dirList) {
			String prefix = object.prefix();	 
			// then try matching
			Matcher matcher = pattern.matcher(prefix);
			if(matcher.matches() && (Integer.parseInt(matcher.group(1)) > part)) {
			    part = Integer.parseInt(matcher.group(1));    				
			}
		}
		
		// TBD: check also in table max of part field and return max of s3 folder or table value
		try {
			List<String> resultList = AmazonAthenaTableManager.executeQueryWithResultColumn(
					String.format("SELECT max(part) FROM %s;", PUBLICATIONS), 0);
			
			if (resultList.size() > 1) {
				throw new Exception("Single result query returned more than one result.");
			} else if (resultList.size() == 1 && resultList.get(0) != null) {
				int maxPart = Integer.valueOf(resultList.get(0));
				if (part < maxPart) {
					return maxPart;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("Error analysing publication table", e);
		}
		
		return part;
	}
}
		

	