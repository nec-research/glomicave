package eu.glomicave.wp3;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileReader;

import eu.glomicave.persistence.SQLDatabase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class DOIInitialization {
	private static final Logger logger = LogManager.getLogger(DOIInitialization.class);

	private static final int ABRIDGE_MAX_NUM = 5;
	
	private static int MANUALLY_ADDED = 1;
	private static int RANDOMLY_SAMPLED = 2;

	public static void readManuallyProvidedRelevantDOIsToDatabase(boolean abridge, String filepath) throws IOException, FileNotFoundException {
		File file = new File(filepath);
		
		int processed = 0;		
		
		// calculate number of records in file
		int readLines = 0;
		try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
	    	while (bufferedReader.readLine() != null)
			{
	    		readLines++;
			}
	    	logger.info("Seed DOIs found: {}", readLines);
		}
		
		try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
			String line;
	    	// add seed DOIs to the database
			while ((line = bufferedReader.readLine()) != null) {
				line = line.strip();
				SQLDatabase.insertDOI(line, MANUALLY_ADDED);
				
				processed++;
				
				if (abridge && processed > ABRIDGE_MAX_NUM) {
					logger.info("! Shortened pipeline. Only {} DOIs were considered !", processed);
					break;
				}
			}
		}
		
		logger.info("Num added papers: " + processed);
	}
	
	
	public static void addManuallyProvidedRelevantDOIsToDatabase(boolean abridge) {
		SQLDatabase.insertDOI("10.1155/2014/608579", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1021/acs.jproteome.9b00688", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1038/s41598-018-31605-0", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1021/acs.jafc.0c02129", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1007/s11306-018-1414-0", MANUALLY_ADDED);

		if (abridge) {
			return;
		}

		SQLDatabase.insertDOI("10.3168/jds.2014-8067", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1111/age.12044", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1016/j.meatsci.2013.02.014", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1016/j.livsci.2013.02.020", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1007/s11033-014-3343-y", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1111/jbg.12325", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1111/age.12731", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1093/jxb/eraa302", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1104/pp.19.00086", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.3390/metabo10030096", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1101/2020.05.12.088096", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1038/s41396-019-0571-0", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1038/s41467-020-19006-2", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1101/2020.11.23.394700", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1038/nature16461", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1016/j.watres.2020.115955", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.3389/fmicb.2016.01033", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.3390/proteomes7020016", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1038/s41396-018-0187-9", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1016/j.scitotenv.2018.08.328", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1186/s13068-018-1121-0", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1016/bs.aibe.2020.04.001", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1186/s13742-015-0073-6", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.3389/fmicb.2020.593006", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1101/680553", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1186/s13068-020-01679-y", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1007/s00253-013-5220-3", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1186/s13068-018-1195-8", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1016/j.watres.2016.08.008", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1016/j.jclepro.2020.121646", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.3390/app10010135", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1186/s12934-015-0218-4", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.3389/fmicb.2016.00778", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1128/AEM.00895-19", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1016/j.biortech.2013.03.188", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1159/000479108", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1186/s13068-014-0146-2", MANUALLY_ADDED);
		SQLDatabase.insertDOI("10.1111/1462-2920.13437", MANUALLY_ADDED);
	}

	public static void addRandomlySampledDOIsToDatabase() {
		// Random papers not on the topics (negative samples)
		SQLDatabase.insertDOI("10.1201/b11943-6", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1073/pnas.091062498", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1016/S0262-4079(19)31082-6", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1080/09515089.2014.996285", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1021/JA02242A004", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1126/SCIENCE.285.5428.727", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1111/JOFI.12723", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.2139/ssrn.2574963", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1017/chol9780521300063.005", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1093/eurheartj/ehu046", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1007/s00211-015-0717-6", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.3390/sym9030033", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.5121/ijcga.2019.9401", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1145/3388767.3408944", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1021/acsnano.0c03697", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1007/s100190100124", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1038/s41467-019-09351-2", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1016/j.jare.2013.07.006", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1257/JEP.31.2.211", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1038/s41467-018-07761-2", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1016/J.ETI.2019.100341", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.4231/Y00Z-N127", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1017/S0007123414000477", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1007/978-3-319-40118-8_5", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1130/L610.1", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1016/J.EPSL.2017.02.025", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1086/228311", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.2307/2393644", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1016/j.cub.2018.06.054", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.2139/ssrn.3437946", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1002/9781118785317.WEOM080026", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.31820/pt.28.3.2", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1111/BEER.12171", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1016/J.COMPSTRUCT.2019.02.002", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1016/J.COMPOSITESB.2014.12.034", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1086/406755", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.2307/2234133", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.2307/1912773", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1007/S11135-006-9018-6", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.2307/2109186", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.2307/2075319", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1016/J.FRL.2018.07.004", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1504/IJFERM.2014.065651", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1007/s10796-014-9492-7", RANDOMLY_SAMPLED);
		SQLDatabase.insertDOI("10.1016/j.future.2013.01.010", RANDOMLY_SAMPLED);
	}

}
