package eu.glomicave.data_import.extract_entities;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.io.*;
import java.util.*;

public class ExtractEntities {
    public static void main(String args[]) throws IOException {
        Set<String> entities = readEntities("./data/extract_entities/entities.txt");
        List<List<String>> sentences = readAbstractSentences("./data/extract_entities/abstract_sentences.txt");

        //StanfordCoreNLP pipeline = CoreNLPUtils.StanfordDepNNParser();
        HashMap<String, String> newEntities = new HashMap<>();

        System.out.println("Discovering new entities ... ");

        for (List<String> record: sentences) {       	        	
            HashSet<String> candidates = new HashSet<>();

            String doi = record.get(0);
            String sentInd = record.get(1);
            String sentence = record.get(2);
            
            //
            sentence = "Our data revealed that flesh firmness, respiration rate, ethylene production, metacaspase (MC) activity, superoxide anion (O2 •- ) production rate, relative electrical conductivity (REC), Hydrogen peroxide (H2 O2 ), and malondialdehyde (MDA) contents in  Golden Delicious  were higher than in  Fuji  during ripening.";
            
            candidates = extractNamedEntities(sentence);

            // Check if extracted candidates from a sentence are in the list of entities, 
            // return only those that are on the lists
            for (String cand: candidates) {
                if (entities.contains(cand)) {
                    newEntities.put(doi + "_" + sentInd, cand);
                }
            }
        }

        System.out.println("Writing results ... ");
        BufferedWriter bw = null;
        File file = new File("data/glomicave/new_entities2.txt");
        FileWriter fw = new FileWriter(file);
        bw = new BufferedWriter(fw);

        for (String key: newEntities.keySet()) {
            bw.write(key + "\t" + newEntities.get(key) + "\n");
        }

        bw.close();
    }
    
    /** Parse a sentence and extract single nouns and combination of words that
     * can be potentially considered as named entities in the downstream analysis.
     * 
     * @param sentence
     * @return set of potential single- and multi-word named entities
     */
    public static HashSet<String> extractNamedEntities(String sentence) {
    	StanfordCoreNLP pipeline = CoreNLPUtils.StanfordDepNNParser();
        HashSet<String> candidates = new HashSet<>();
        
        SemanticGraph semGraph = CoreNLPUtils.parse(pipeline, sentence);
        Annotation document = new Annotation(sentence);
        
        try {
            pipeline.annotate(document);
        } catch (Exception e) {
            System.out.println("Pipeline annotation error");
        }

        ObjectArrayList<IndexedWord> words = getWords(document);

        List<IndexedWord> nouns = getNouns(words);
        List<ObjectArrayList<IndexedWord>> compoundNouns = new ArrayList<>();
        for (IndexedWord w: nouns) {
            candidates.add(w.word());
            candidates.add(w.lemma());            
            
            try {
                compoundNouns.add(CoreNLPUtils.getChainedNouns(words, w.index()));
            }
            catch (Exception e) {
                //ObjectArrayList<IndexedWord> cWords = CoreNLPUtils.getChainedNouns(words, w.index());
                System.out.println("");
            }
        }

        //int i = 0;
        for (ObjectArrayList<IndexedWord> compoundNoun: compoundNouns) {
            candidates.add(CoreNLPUtils.listOfWordsToWordsString(compoundNoun));
            candidates.add(CoreNLPUtils.listOfWordsToLemmaString(compoundNoun));

            try {
                IndexedWord testWord = CoreNLPUtils.getRootFromWordList(semGraph, compoundNoun);
            } catch (Exception e) {
                System.out.println("NPE");
                continue;
            }

            IndexedWord rootWord = CoreNLPUtils.getRootFromWordList(semGraph, compoundNoun);

            try {
                SemanticGraph subgraph = CoreNLPUtils.getSubgraph(semGraph, rootWord);

                if (!subgraph.isEmpty()) {
                    Collection<IndexedWord> roots = semGraph.getRoots();
                    Set<IndexedWord> allWords = new HashSet<>();
                    allWords.add(rootWord);
                    allWords.addAll(subgraph.getChildren(rootWord));

                    ObjectArrayList<IndexedWord> constituents = CoreNLPUtils.getSortedWordsFromSetOfWords(allWords);
                    System.out.print("");

                    SubConstituent subconstituents = new SubConstituent(semGraph, rootWord, constituents);
                    subconstituents.clearSubConstituentsAndCandidates();
                    subconstituents.generateSubConstituentsFromLeft();

                    //ObjectOpenHashSet <String> stringSubconstituents = subconstituents.getStringSubConstituents();

                    candidates.addAll(subconstituents.getStringSubConstituents());

                    ObjectOpenHashSet<ObjectArrayList<IndexedWord>> subconstits = subconstituents.getSubConstituents();
                    for (ObjectArrayList<IndexedWord> subConstList: subconstits) {
                        candidates.add(CoreNLPUtils.listOfWordsToLemmaString(subConstList));
                    }
                }

            } catch (Exception e) {
                System.out.println("Error in subgraph");
                continue;
            }
        }
        
        return candidates;
    	
    }

    public static ObjectArrayList<IndexedWord> getWords(Annotation document) {
        ObjectArrayList<IndexedWord> words = new ObjectArrayList<>();
        //A CoreMap is a sentence with annotations
        List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);
        //SemanticGraph semanticGraph = null;
        for (CoreLabel token: tokens) {
            words.add(new IndexedWord(token));
        }
        return words;
    }

    public static List<IndexedWord> getNouns(List<IndexedWord> words) {
        List<IndexedWord> nouns = new ArrayList<>();
        for (IndexedWord w: words) {
            if (CoreNLPUtils.isNoun(w.tag())) {
                nouns.add(w);
            }
        }
        return nouns;
    }

    public static Set<String> readEntities(String filename) throws IOException {
        Set<String> entities = new HashSet<>();

        BufferedReader br = new BufferedReader(new FileReader(filename));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            entities.add(line);
            // process the line.
        }
        br.close();

        return entities;
    }

    public static List<List<String>> readAbstractSentences(String filename) throws IOException {
        List<List<String>> sentences = new ArrayList<>();

        BufferedReader br = new BufferedReader(new FileReader(filename));
        String line;
        line = br.readLine();
        while ((line = br.readLine()) != null) {
            line = line.trim();
            String [] lineSplit = line.split("\t");
            // index 0 = doi, index 1 = index, index 2 = sentence
            List<String> split = Arrays.asList(lineSplit);
            sentences.add(split);
        }
        br.close();

        return sentences;
    }
}
