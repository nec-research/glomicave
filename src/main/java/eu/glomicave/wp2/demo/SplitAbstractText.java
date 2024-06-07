/* Split abstract texts into sentences and save into file. 	*/

package eu.glomicave.wp2.demo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import eu.glomicave.data_import.Sentence;


public class SplitAbstractText {

	// Read file
	private static final String file = "/Users/nle5293/Documents/__Projects/3-Glomicave/WP2/_term_recognition/abstract_text.txt";
	// Output file
	private static final String outfile = "/Users/nle5293/Documents/__Projects/3-Glomicave/WP2/_term_recognition/abstract_sentences.txt";
			
	public static void main(String[] args) throws Exception {
			
		long readLength = 0;
		long readLengthInMBPrinted = -1;
		int processed = 0;		

		
		String line;
		List<Sentence> sentences;
		
		// clear output file
		PrintWriter writer = new PrintWriter(outfile);
		writer.print("");
		writer.close();
		
		try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
			line = bufferedReader.readLine(); // skip header
			readLength += line.length();

			while ((line = bufferedReader.readLine()) != null) {
				readLength += line.length();
			
				long readLengthInMB = Math.round(readLength / 1024.0 / 1024.0);
				if (readLengthInMB > readLengthInMBPrinted) {
					System.out.println("num processed: " + processed);
					readLengthInMBPrinted = readLengthInMB;
				}

				// split line into sentences
				
				String[] fields = line.split("\t");
				String doi = fields[0];
				String text = fields[1];
				
				sentences = splitSentences(text);
				
				// print sentences into file
				try (BufferedWriter bw = new BufferedWriter(new FileWriter(outfile, true))) {
					for (Sentence sentence : sentences) {
						String outline = doi + "\t" + sentence.index + "\t" + sentence.text;

						bw.write(outline);
						bw.newLine();   // add new line, System.lineSeparator()
					}
				}
				
				processed++;
			}

		} catch(Exception e) {
			throw new IOException();
		}
	}				
	
	
	private static StanfordCoreNLP pipeline;

	static {
		Properties props = new Properties();
		props.setProperty("annotators", "tokenize,ssplit");
		pipeline = new StanfordCoreNLP(props);
	}

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
}
