package eu.glomicave.wp3;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.types.Node;

import com.opencsv.CSVReader;

import eu.glomicave.config.AmazonS3Config;
import eu.glomicave.persistence.AmazonS3;
import eu.glomicave.persistence.CoreGraphDatabase;
import eu.glomicave.persistence.NamedEntityGraphDatabase;
import eu.glomicave.persistence.PredefinedCategories;

public class IntegrateWP4Traits {
	private static final Logger logger = LogManager.getLogger(IntegrateWP4Traits.class);

	private static final String WP4_TRAIT = "WP4_TRAIT";

	public static void run() throws Exception {
		HashMap<String, List<String>> wp4Traits = new HashMap<>();
		
		logger.info("Loading WP4 phenotype data");	

		wp4Traits.put("BC1_A_1", List.of("Calf birth weight", "birth weight"));
		wp4Traits.put("BC1_A_2", List.of("Gestation length"));
		wp4Traits.put("BC2_A_1", List.of("Oxidative state"));
		wp4Traits.put("BC2_A_2", List.of("Tenderness"));
		wp4Traits.put("BC2_A_3", List.of("pH"));
		wp4Traits.put("BC2_A_4", List.of("Water Holding Capacity", "WHC"));
		wp4Traits.put("BC2_A_5", List.of("Proteins"));
		wp4Traits.put("BC2_A_6", List.of("Fat"));
		wp4Traits.put("BC2_A_7", List.of("Water"));
		wp4Traits.put("BC2_A_8", List.of("Iron", "Fe"));
		wp4Traits.put("BC2_A_9", List.of("Niacin", "B3 vitamin", "B3"));
		wp4Traits.put("BC2_A_10", List.of("Color"));
		wp4Traits.put("BC1_B_1", List.of("Pregnancy status at day 40", "Pregnancy status", "Pregnancy"));
		wp4Traits.put("BC1_B_2", List.of("Pregnancy status at day 62", "Pregnancy status", "Pregnancy"));
		wp4Traits.put("BC1_B_3", List.of("Birth or calving", "birth", "calving"));
		wp4Traits.put("BC1_B_4", List.of("Miscarriage"));
		wp4Traits.put("BC1_B_5", List.of("Calf death", "death"));
		wp4Traits.put("BC1_B_6", List.of("Calf sex", "sex"));
		wp4Traits.put("BC1_B_7", List.of("Calving difficulties"));
		wp4Traits.put("BC2_B_1", List.of("Sensory evaluation (smell)", "smell"));
		wp4Traits.put("BC2_B_2", List.of("Sensory evaluation (taste)", "taste"));
		wp4Traits.put("BC2_B_3", List.of("Colour", "color"));
		wp4Traits.put("BC3_A_1", List.of("Relative growth rate", "RGR"));
		wp4Traits.put("BC3_A_2", List.of("Fruit size", "size"));
		wp4Traits.put("BC3_A_3", List.of("Starch"));
		wp4Traits.put("BC3_A_4", List.of("Total proteins", "proteins"));
		wp4Traits.put("BC3_A_5", List.of("Ascorbic acid", "Vitamine C"));
		wp4Traits.put("BC3_A_6", List.of("Sucrose"));
		wp4Traits.put("BC3_A_7", List.of("Hexoses"));
		wp4Traits.put("BC3_A_8", List.of("Total amino acids", "amino acids"));
		wp4Traits.put("BC3_A_9", List.of("Total phenolics", "phenolics"));
		wp4Traits.put("BC3_A_10", List.of("Total fruit weight", "fruit weight"));
		wp4Traits.put("BC3_A_11", List.of("Fruit stone weight", "stone weight"));
		wp4Traits.put("BC3_A_12", List.of("Fruit flesh weight", "flesh weight"));
		wp4Traits.put("BC4_A_13", List.of("Canopy height"));
		wp4Traits.put("BC4_A_2", List.of("Canopy width"));
		wp4Traits.put("BC4_A_3", List.of("Canopy segmented area"));
		wp4Traits.put("BC4_A_4", List.of("Hull area"));
		wp4Traits.put("BC4_A_5", List.of("Tuber yield per plant"));
		wp4Traits.put("BC4_A_6", List.of("Tuber angle"));
		wp4Traits.put("BC4_A_7", List.of("Number of roots"));
		wp4Traits.put("BC3_B_1", List.of("Climacteric", "Non-climacteric"));
		wp4Traits.put("BC3_B_2", List.of("Fruit colour"));
		wp4Traits.put("BC3_B_3", List.of("PPV infected", "healthy trees"));
		wp4Traits.put("BC3_B_4", List.of("Fruit shape"));
		wp4Traits.put("BC4_B_1", List.of("Root stage", "Root stages"));
		wp4Traits.put("BC5_A_1", List.of("Concentration levels of phosphate, nitrite, nitrate, ammonium, oxygen", "phosphate", "nitrite", "ammonium", " oxygen"));
		wp4Traits.put("BC5_A_2", List.of("Conversion rates of phosphate, nitrite, nitrate, ammonium, oxygen", "phosphate", "nitrite", "ammonium", " oxygen"));
		wp4Traits.put("BC5_A_3", List.of("Total biomass", "biomass"));
		wp4Traits.put("BC5_A_4", List.of("Bacterial counts"));
		wp4Traits.put("BC5_A_5", List.of("Abundance of ≈500 species (=metagenome assembled genomes - MAGs)", "metagenome assembled genomes", "MAGs", "MAG", "abundance of ≈500 species"));
		wp4Traits.put("BC5_A_6", List.of("Change in gene-expression level", "gene-expression level", "gene expression level"));
		wp4Traits.put("BC6_A_1", List.of("Methane production", "Methane"));
		wp4Traits.put("BC6_A_2", List.of("Chemical Oxygen Demand removal", "chemical oxygen demand removal", "COD removal"));
		wp4Traits.put("BC6_A_3", List.of("Volatil Solids removal", "VS removal", "volatil solids removal"));
		wp4Traits.put("BC6_A_4", List.of("Digestate COD"));
		wp4Traits.put("BC6_A_5", List.of("Digestate Total Solids", "TS", "digestate total solids"));
		wp4Traits.put("BC6_A_6", List.of("Digestate VS"));
		wp4Traits.put("BC6_A_7", List.of("Digestate Total Nitrogen", "TN", "digestate total nitrogen"));
		wp4Traits.put("BC6_A_8", List.of("Digestate ammonium nitrogen", "NH4-N"));
		wp4Traits.put("BC6_A_9", List.of("Digestate pH"));
		wp4Traits.put("BC5_B_1", List.of("Polyphosphate accumulating organisms"));
		wp4Traits.put("BC5_B_2", List.of("Glycogen-accumulating organisms"));
		wp4Traits.put("BC5_B_3", List.of("Denitrifiers"));
		wp4Traits.put("BC5_B_4", List.of("Nitrifiers"));
		wp4Traits.put("BC5_B_5", List.of("Other organisms"));
		wp4Traits.put("BC6_B_1", List.of("Hydrogenotrophic methanogens"));
		wp4Traits.put("BC6_B_2", List.of("Acetoclastic methanogens"));
		wp4Traits.put("BC6_B_3", List.of("Acetogens"));
		wp4Traits.put("BC6_B_4", List.of("Syntrophic acetate-oxidizing (SAO) bacteria", "SAO bacteria", "syntrophic acetate-oxidizing bacteria", "syntrophic acetate-oxidizing microbes"));
		wp4Traits.put("BC6_B_5", List.of("Syntrophic fatty acid-β-oxidizing bacteria", "syntrophic fatty acid-β-oxidizing microbes"));
		wp4Traits.put("BC6_B_6", List.of("Hydrolytic bacteria", "Hydrolytic microbes"));
		wp4Traits.put("PT_1", List.of("Tomato yield"));

		for (Entry<String, List<String>> entry : wp4Traits.entrySet()) {
			String traitName = entry.getKey();
			List<String> lexicalForms = entry.getValue();

			Node traitNode = NamedEntityGraphDatabase.addNamedEntityNode(WP4_TRAIT, traitName);
			CoreGraphDatabase.addLabel(traitNode, PredefinedCategories.TRAIT.toString());

			for (String lexicalForm : lexicalForms) {
				Node lexicalFormNode = NamedEntityGraphDatabase.addLexicalFormNode(lexicalForm);
				NamedEntityGraphDatabase.addNamedEntitiyToLexicalFormConnection(traitNode, lexicalFormNode);
			}
		}
		
		logger.info("WP4 phenotype data loaded.");	
	}
	
	
	/** 
	 *  Add traits from S3 object. 
	 *  Keep the traits file like: row_id, trait_uid, synonym_1, synonym_2, ...
	 */
	public static void addTraitsFromS3CSV(boolean abridge, String s3filekey) throws IOException, FileNotFoundException {
		String[] fields = null;
		
		logger.info("Loading traits from S3 object: {}", s3filekey);
		//try (CSVReader csvReader = new CSVReader(new BufferedReader(new InputStreamReader(AmazonS3.readFileUsingS3Client(s3filekey))))) {
		int processed = 0;		
		
		try {
			CSVReader csvReader = new CSVReader(new BufferedReader(new InputStreamReader(AmazonS3.readFileUsingS3Client(s3filekey))));
			
			fields = csvReader.readNext(); // skip header
			while (fields != null) {
				try {
					fields = csvReader.readNext();
					
					if (fields == null) {
						break;
					}
					
					String traitName = fields[1];
					Node traitNode = NamedEntityGraphDatabase.addNamedEntityNode(WP4_TRAIT, traitName);
					CoreGraphDatabase.addLabel(traitNode, PredefinedCategories.TRAIT.toString());
					for (int i = 2; i < fields.length; i++) {
						String[] lexicalForms = fields[i].split(";");
						// split synonyms into words
						for (String lexicalForm : lexicalForms) {
							if (!lexicalForm.trim().isEmpty()) {
								Node lexicalFormNode = NamedEntityGraphDatabase.addLexicalFormNode(lexicalForm.trim());
								NamedEntityGraphDatabase.addNamedEntitiyToLexicalFormConnection(traitNode, lexicalFormNode);
							}
						}
					}
					
					processed++;
					
					if (processed % 10 == 0) {
						logger.info("{} trait records processed.", processed);
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
		} catch(Exception e) {
			logger.error("Error reading traits.", e);
			throw new IOException();
		}
		
		logger.info("{} records loaded", processed);
	}

	
	/** 
	 * Add phenotypic traits.
	 * Read from local CSV file.
	 */
	public static void addTraitsFromCSV(boolean abridge, String filepath) throws IOException, FileNotFoundException {
		String[] fields = null;

		logger.info("Loading phenotypic traits from file: {}", filepath);
		
		int processed = 0;
		
	    try (CSVReader csvReader = new CSVReader(new FileReader(filepath))) {
			csvReader.readNext(); // skip header
			while ((fields = csvReader.readNext()) != null) {
				String traitName = fields[1];
				Node traitNode = NamedEntityGraphDatabase.addNamedEntityNode(WP4_TRAIT, traitName);
				CoreGraphDatabase.addLabel(traitNode, PredefinedCategories.TRAIT.toString());
				for (int i = 2; i < fields.length; i++) {
					String[] lexicalForms = fields[i].split(";");
					// split synonyms into words
					for (String lexicalForm : lexicalForms) {
						if (!lexicalForm.trim().isEmpty()) {
							Node lexicalFormNode = NamedEntityGraphDatabase.addLexicalFormNode(lexicalForm.trim());
							NamedEntityGraphDatabase.addNamedEntitiyToLexicalFormConnection(traitNode, lexicalFormNode);
						}
					}
				}
				
				processed++;
				
				if (processed % 10 == 0) {
					logger.info("{} trait records processed.", processed);
				}
				
				if (abridge && processed > 100) {
					break;
				}
			}
		} catch(Exception e) {
			logger.error("Error reading lexical forms for WP entities", e);
			throw new IOException();
		}
	    
		logger.info("{} records loaded", processed);
	}
}
