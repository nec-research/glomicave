package eu.glomicave.wp2.evaluation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.neo4j.driver.internal.InternalEntity;
import org.neo4j.driver.internal.InternalNode;

import eu.glomicave.persistence.GraphGeneration;
import eu.glomicave.persistence.PredefinedCategories;
import eu.glomicave.persistence.PredefinedRelations;

public class RelationshipPropertiesReferenceGraphGeneration {

	public static String sentence1_1 = "Consumers demand healthy and palatable meat.";

	public static Set<InternalEntity> getGraphForSentence1_1() {
		Set<InternalEntity> entities = new HashSet<InternalEntity>();
		HashMap<String, InternalEntity> compoundEntities = GraphGeneration.createANDCompoundWithBaseNode("meat", "healthy", "palatable");
		entities.addAll(compoundEntities.values());
		entities.addAll(GraphGeneration.createRelationship("consumers", (InternalNode) compoundEntities.get("compound"), "demand"));
		return entities;
	}

	public static String sentence1_2 = "Both factors being affected by fat composition.";

	public static Set<InternalEntity> getGraphForSentence1_2() {
		Set<InternalEntity> entities = new HashSet<InternalEntity>();

		HashMap<String, InternalEntity> map2 = GraphGeneration.createNodeChain("fat", "composition");
		entities.addAll(map2.values());

		entities.addAll(GraphGeneration.createRelationship((InternalNode) map2.get("fat composition"), "factor", "affects"));
		return entities;
	}

	public static String sentence2 = "Red meat have relatively high concentration of saturated fatty acid and low concentration of the beneficial polyunsaturated fatty acid.";

	public static Set<InternalEntity> getGraphForSentence2() {
		Set<InternalEntity> entities = new HashSet<InternalEntity>();

		HashMap<String, InternalEntity> redMeat = GraphGeneration.createNodeChain("red", "meat");
		entities.addAll(redMeat.values());

		HashMap<String, InternalEntity> saturatedFattyAcid = GraphGeneration.createNodeChain("saturated", "fatty", "acid");
		entities.addAll(saturatedFattyAcid.values());

		HashMap<String, InternalEntity> beneficialPolyunsaturated_FattyAcid = GraphGeneration.createNodeChain((InternalNode) saturatedFattyAcid.get("fatty acid"), "beneficial", "polyunsaturated");
		entities.addAll(beneficialPolyunsaturated_FattyAcid.values());

		entities.add(GraphGeneration.createRelationship((InternalNode) redMeat.get("red meat"), (InternalNode) saturatedFattyAcid.get("saturated fatty acid"), "have_concentration_of", "relatively high"));

		entities.add(GraphGeneration.createRelationship((InternalNode) redMeat.get("red meat"), (InternalNode) beneficialPolyunsaturated_FattyAcid.get("beneficial polyunsaturated fatty acid"), "have_concentration_of", "low"));

		return entities;
	}

	public static String sentence5_1 = "Sixteen gene have significant effect on different lipid trait.";

	public static Set<InternalEntity> getGraphForSentence5_1() {
		// Sixteen genes were found to have significant effects on different lipid traits.
		Set<InternalEntity> entities = new HashSet<InternalEntity>();

		InternalNode sixteenGene = GraphGeneration.createNode(PredefinedCategories.CONCEPT.toString(), "sixteen gene");
		entities.add(sixteenGene);

		HashMap<String, InternalEntity> differentLipidTrait = GraphGeneration.createNodeChain("different", "lipid", "trait");
		entities.addAll(differentLipidTrait.values());

		entities.add(GraphGeneration.createRelationship(sixteenGene, (InternalNode) differentLipidTrait.get("different lipid trait"), "have_effect_on", "significant"));

		return entities;
	}

	public static String sentence5_2_1 = "NE1 and NE2 have large effect on the ratio of NE3/NE4.";

	public static Set<InternalEntity> getGraphForSentence5_2_1() {
		// Among these, CFL1 and MYOZ1 were found to have large effects on the ratio of 18:2/18:3, CRI1 on the amount of neutral adrenic acid (22:4 n-6), MMP1 on docosahexaenoic acid (22:6 n-3) and conjugated linoleic acid, PLTP on the ratio of n-6:n-3 and IGF2R on flavour.
		// NE1 and NE2 were found to have large effects on the ratio of NE3/NE4.

		Set<InternalEntity> entities = new HashSet<InternalEntity>();

		HashMap<String, InternalEntity> compoundEntities = GraphGeneration.createANDCompound("NE1", "NE2");
		entities.addAll(compoundEntities.values());

		InternalNode ne3 = GraphGeneration.createNode(PredefinedCategories.CONCEPT.toString(), "NE3");
		entities.add(ne3);

		InternalNode ne4 = GraphGeneration.createNode(PredefinedCategories.CONCEPT.toString(), "NE4");
		entities.add(ne4);

		InternalNode ratio1 = GraphGeneration.createNode(PredefinedCategories.CONCEPT.toString(), "the ratio of NE3/NE4");
		entities.add(ratio1);

		entities.add(GraphGeneration.createRelationship(ratio1, ne3, PredefinedRelations.CONTAINS));
		entities.add(GraphGeneration.createRelationship(ratio1, ne4, PredefinedRelations.CONTAINS));

		entities.add(GraphGeneration.createRelationship((InternalNode) compoundEntities.get("compound"), (InternalNode) ratio1, "have_effect_on", "large"));

		return entities;
	}

	public static String sentence6 = "Several gene associated with both lipid and organoleptic trait.";

	public static Set<InternalEntity> getGraphForSentence6() {
		// Several genes - ALDH2, CHRNE, CRHR2, DGAT1, IGFBP3, NEB, SOCS2, SUSP1, TCF12 and FOXO1 - also were found to be associated with both lipid and organoleptic traits although with smaller effect.
		// Several genes also were found to be associated with both lipid and organoleptic traits although with smaller effect.

		Set<InternalEntity> entities = new HashSet<InternalEntity>();

		HashMap<String, InternalEntity> severalGene = GraphGeneration.createNodeChain("several", "gene");
		entities.addAll(severalGene.values());

		HashMap<String, InternalEntity> compound = GraphGeneration.createANDCompoundWithBaseNode("trait", "lipid", "organoleptic");
		entities.addAll(compound.values());

		entities.add(GraphGeneration.createRelationship((InternalNode) severalGene.get("several gene"), (InternalNode) compound.get("compound"), "associated with"));

		return entities;
	}
}
