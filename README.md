# GLOMICAVE-KG

This repository contains Java code of the GLOMICAVE project - GLobal OMICs data integraion on Animal, Vegetal and Environment sectors (https://glomicave.eu/). 

Here we describe how to run the software developed under Work Package 2 to build and update GLOMICAVE Knowledge Graph of multi-omics concepts extracted from external ontologies and scientific texts. Current version of the software is restricted to the analysis of abstact texts, however same approach can be applied to the analysis of the entire paper texts, excluding tables and image captions.

Created GLOMICAVE Knowledge Graph is used as the basis for a literature search to examine the evidence for the hypotheses generated using GLOMICAVE platform tools.

## License

We provide our code under GPL-3 license (https://www.gnu.org/licenses/gpl-3.0.html).


## Java version

JavaSE-15 (tested with Java SE 17.0.3).


## Pipeline versions

To run data extraction we developed a command-line tool ```glomicave-kg``` packaged as Java jar file that can be run using folliwing synax:

```
java -jar glomicave-kg.jar [-ahilV]
                    [-t=<pool_size>]  
                    [--cfg_logs=<cfg_logger_file>] 
                    [--cfg_s3=<cfg_s3_file>]
                    [--cfg_sqldb=<cfg_sqldb_file>] 
                    [--cfg_graphdb=<cfg_graphdb_file>]
                    [--gene_ontology=<gene_ontology_file>] 
                    [--ec_codes=<ec_codes_file>] 
                    [--extra_ontology=<extra_ontology_files>] 
                    [--traits=<traits_file>]
                    [--wp=<wp_dir>]
                    [--dois=<dois_file>]
                    [--ncits=<ncits>]
                    [--nrefs=<nrefs>] 
                    [--facts=<oie_triples_file>]
                    <pipelineName>
```

Below we describe the parameters and the options of the pipeline running command.

```
  <pipelineName>           Name of the pipeline to run.
                           Should be one of: 'full'| 'addPublications' | 'addOntology' | 'addTraits' | 'loadFacts'.

  -a, --abridge            Run shortend version of the pipeline to test only.

  -h, --help               Show this help message and exit.

  -i, --integrate          Integrate only stored publications data from Athena tables into GraphDB. 
                           This will prevent writing new publication data into Athena tables.
                           The option has effect only with 'addPublications' AWS cloud pipeline.

  -l, --local              Run version of the pipeline on local server. This means that config files 
                           for local instance of Neo4J and SQL database are expected. All ontologies 
                           and list of dois are to be uploaded from local file system.

  -V, --version            Print version information and exit.

  -t, --threads=<pool_size>
                           Max number of threads to execute in parallel.

  --cfg_logs=<cfg_logger_file>
                           Path to file 'log4j2.xml' with the logger settings.

  --cfg_s3=<cfg_s3_file>
                           Config file for S3.
                           Has only effect without option '-l | --local'.

  --cfg_sqldb=<cfg_sqldb_file>
                           Config file for SQL database.
                           Has no effect with pipelines 'addOntology', 'addTraits' and 'loadFacts'.

  --cfg_graphdb=<cfg_graphdb_file>
                           Config file for graph database.

  --gene_ontology=<gene_ontology_file>
                           File for NEC-curated gene ontology.
                           Has only effect with pipeline 'full'.

  --ec_codes=<ec_codes_file>
                           File for Enzyme Codes ontology.
                           Has only effect with pipeline 'full'.

  --extra_ontology=<extra_ontology_files>
                           List of files with any other ontologies stores in CSV format.
                           They will be uploaded after processing data from Wikipathways.

  --traits=<traits_file>
                           File for phenotypic traits.
                           Has only effect with pipelines 'full' and 'addTraits'.

  --wp=<wp_dir>
                           Directory with stored Wikipathway database.
                           Has only effect with pipeline 'full'

  --dois=<dois_file>
                           File with list of paper DOIs to analyse.
                           Has only effect with pipelines 'full' and 'addPublications'
  --ncits=<ncits>
                           Max number of citations per publication to explore when adding publications.
                           Has no effect with pipelines 'full' and 'addPublications'.
  --nrefs=<nrefs>
                           Max number of references per publication to explore when adding publications.
                           Has no effect with pipelines 'full' and 'addPublications'.

  --facts=<oie_triples_file>
                           File with OIE facts.
                           Has only effect with pipeline 'loadFacts'

```

## Part 1. Cloud-integrated pipeline verions

To run cloud version of the pipeline one need to configure access to Amazon account and allow working with Amazon S3 object storage, Amazon Athena query service and Amazon Neptune graph database. 

We assume that the listed Amazon Web Services (AWS) are used for the following purposes:
* S3 object storage: store input files requred by the pipeline, like dictionaries and ontologies of omics entities, lists of seed publications, OpenIE-mined facts etc.
* Athena query service: query tables with extracted publication data.
* Neptune: graph database where the Knowledge Graph should be created or updated.

Instead of Amazon Neptune it's possibe to use Neo4J instance as the graph database within any cloud version of the pipeline.


### Amazon access settings

To properly set up access to the cloud resources follow the instructions to generate AWS access keys and configurations: https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-configure.html

The generated 'config' and 'credintials' files are normally put into directory ~./aws. With properly configured access you should normally be able to use Amazon CLI tool to manage files inside the Amazon S3 bucket.
 

### Running 'full' pipeline

Here is an example of running full end-to-end cloud-integrated pipeline to build knowledge graph from external ontologies and provided paper abstracts.

We assume that folders `logs, config` and relevant subfolders that contain configuration files have been created inside the directory with the executable .JAR file. Deafult logger configuration assumes that all application logs will be written into `logs` folder.

```
java -jar glomicave-kg.jar \
--cfg_s3 "./config/s3/aws_s3_config.xml" \
--cfg_sqldb "./config/sqldb/aws_athena_config.xml" \
--cfg_graphdb "./config/graphdb/graphdb_config.xml" \
--gene_ontology "raw/data/ontologies/nle-bio-kg/gene-info.csv" \
--ec_codes "raw/data/ontologies/ec-codes/ec-enzymes.csv" \
--extra_ontology "raw/data/ontologies/wp-annotations/wp-manually-annotated.csv" \
--traits "raw/data/phenotypes/wp4_traits.csv" \
--wp "raw/data/wikipathways/wikipathways-20220110-rdf-wp/wp/" \
--dois "raw/data/publications/publication-dois.txt" \
--threads 5 \
--cfg_logs "./config/log4j2.xml" \
full
```


**Note 1.** Running this pipeline will always clear existing publication data from SQL database and rebuild the knowledge graph from stratch in the graph database. 

**Note 2.** Use option `-a` to test the pipeline execution with the limited amount of data. Given that option it will use only first 100 records from each ontology and retrieve limited information from only 5 publications and no more than 2 citing or referenced papers. 



### Other pipeline versions
#### 1. 'addOntology' pipeline

This pipline version can be used when we need to integrate data from extra ontology to the existing knowledge graph without rebuilding it from scratch.

List of entities from external ontologies should be provided in a CSV-file of a special structure (see file format examples). Data from each ontology should be passed in a separate file using `--extra_ontology` option.

```
java -jar glomicave-kg.jar \
-a \
--cfg_s3 "./config/s3/aws_s3_config.xml" \
--cfg_graphdb "./config/graphdb/graphdb_config.xml" \
--extra_ontology "raw/data/ontologies/chebi/chebi_compounds-file_to_upload.csv" \
--extra_ontology "raw/data/ontologies/uniprot/uniprotkb_reviewed_true_2024_02_23-file_to_upload.csv" \
--threads 11 \
--cfg_logs "./config/log4j2.xml" \
addOntology
```

**Note.** Remove `-a` option to run the pipeline without restrictions on the number of entities to analyse.


#### 2. 'addPublications' pipeline

This pipline version can be used when we need to update the knowledge graph with new publication data.

Initial (or ‘seed’ DOIs for the database generation) are stored in a file `raw/data/publications/publication-dois.txt` inside Amazon S3 bucket `glomicave-storage-nec`.
You can add new seed DOIs that should be added to the currently uploaded into the database into the folder: `raw/data/publications/updates/` under the files `publications-add-<number_of_the_update>.txt`. 

**Note.** Each file should contain only new DOIs, no need to copy previous records.

List of DOIs of new papers should be recorded into a text file (one DOI per row, see file format examples) and provided using `--dois` option.

```
java -jar glomicave-kg.jar \ 
--cfg_s3 "./config/s3/aws_s3_config.xml" \
--cfg_sqldb "./config/sqldb/aws_athena_config.xml" \
--cfg_graphdb "./config/graphdb/graphdb_config.xml" \
--dois "raw/data/publications/updates/publications-add-1.txt" \
--nrefs 10 \
--ncits 10 \
--threads 10 
--cfg_logs "./config/log4j2.xml" \
addPublications
```

**Note.**  This pipeline can be run with `--integrate` option when you don't want to update the publications tables stored in Amazon Athena, but only need to integrate publications into the knowledge graph. This can be especially helpful when some paper records where added into the Athena tables manually.


#### 3. 'loadFacts' pipeline

This pipline version is used to integrate text-mined facts obtained using Open Information Extraction (OpenIE) system into the knowledge graph.

List of OpenIE-mined facts should be provided in a CSV-file of a special structure (see file format examples) using `--facts` option.

```
java -jar glomicave-kg.jar \
--cfg_s3 "./config/s3/aws_s3_config.xml" \
--cfg_graphdb "./config/graphdb/graphdb_config.xml" \
--facts "raw/data/openie/triples_final.csv" \
--cfg_logs "./config/log4j2.xml" \
loadFacts
```

#### 4. 'addTraits' pipeline

This pipline version can be used when we need to add phenotypic traits/phenotypes into the knowledge graph.

List of traits should be provided in a CSV-file of a special structure (see file format examples) using `--traits` option.

```
java -jar glomicave-kg.jar \
--cfg_s3 "./config/s3/aws_s3_config.xml" \
--cfg_graphdb "./config/graphdb/graphdb_config.xml" \
--traits "raw/data/phenotypes/wp4_traits.csv" \
--cfg_logs "./config/log4j2.xml" \
addTraits
```


## Part 2. Local pipeline version

To run the pipeline on the local server one need to prepare SQL database and installed instance of Neo4J graph database.

### Deploying SQL database

#### Run mySQL on MacOS/Linux server

Normally you need to start SQL database. Depending on the system, there could be following commands:

```
/usr/local/mysql-8.0.12-macos10.13-x86_64/support-files/mysql.server start
/usr/local/mysql-8.0.12-macos10.13-x86_64/bin/mysql -u root -p 
```

#### Create SQL database and user

Login into running SQL database manager and execute commands to create application database, user and password.

Example for mySQL:

```
mysql> CREATE DATABASE glomicave_dev;
mysql> CREATE USER 'glomicave'@'localhost' IDENTIFIED WITH mysql_native_password BY 'glomicave';
mysql> CREATE USER 'glomicave'@'localhost' IDENTIFIED BY 'glomicave';
mysql> GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX, DROP, ALTER, CREATE TEMPORARY TABLES, LOCK TABLES ON glomicave_dev.* TO 'glomicave'@'localhost';
```

Example for MariaDB:

```
CREATE USER glomicave@localhost IDENTIFIED BY 'glomicave';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX, DROP, ALTER, CREATE TEMPORARY TABLES, LOCK TABLES ON glomicave_dev.* TO glomicave@localhost;
```


### Running 'full' pipeline

Here is an example of running full end-to-end pipeline to build knowledge graph on the local server.

We assume that all data are stored on the local machine, folders `logs, config` and relevan subfolders that contain configuration files have been created inside the directory with the executable .JAR file. Deafult logger configuration assumes that all application logs will be written into `logs` folder.

Make sure that you have provided `-l` to run all parts of the pipeline on a local server.

```
java -jar glomicave-kg.jar \
-l \
--cfg_sqldb "./config/sqldb/sqldb_config.xml" \
--cfg_graphdb "./config/graphdb/graphdb_config.xml" \
--gene_ontology "../data/ontologies/gene_ontology/nle-bio-kg/gene-info.csv" \
--ec_codes "../data/ontologies/ec-numbers/ec-enzymes.csv" \
--extra_ontology "../data/ontologies/extra_ontologies/wp-annotations/wp-manually-annotated.csv" \
--traits "../data/phenotypes/wp4_traits.csv" \
--wp "../data/ontologies/wikipathways/wikipathways-20220110-rdf-wp/wp/" \
--dois "../data/publications/dois/publication-init-dois.csv" \
--threads 11 \
--cfg_logs "./config/log4j2.xml" \
full
```

### Other pipeline versions
#### 1. 'addOntology' pipeline

This pipline version can be used when we need to integrate data from extra ontology to the existing knowledge graph without rebuilding it from scratch.

List of entities from external ontologies should be provided in a CSV-file of a special structure (see file format examples). Data from each ontology should be passed in a separate file using `--extra_ontology` option.

```
java -jar glomicave-kg.jar \
-l \
--cfg_graphdb "./config/graphdb/graphdb_config.xml" \
--extra_ontology "../data/ontologies/extra_ontologies/uniprot/uniprotkb_reviewed_true_2024_02_23-file_to_upload.csv" \
--extra_ontology "../data/ontologies/extra_ontologies/chebi/chebi_compounds-file_to_upload.csv" \
--threads 11 \
--cfg_logs "./config/log4j2.xml" \
addOntology
```

#### 2. 'addPublications' pipeline

This pipline version can be used when we need to update the knowledge graph with new publication data.

List of DOIs of new papers should be recorded into a text file (one DOI per row, see file format examples) and provided using `--dois` option.

```
java -jar glomicave-kg.jar \
-l \
--cfg_sqldb "./config/sqldb/sqldb_config.xml" \
--cfg_graphdb "./config/graphdb/graphdb_config.xml" \
--dois "../data/publications/dois/updates/publications-add-2.txt" \
--nrefs 10 \
--ncits 10 \
--threads 10 \
--cfg_logs "./config/log4j2.xml" \
addPublications
```

#### 3. 'loadFacts' pipeline

This pipline version is used to integrate text-mined facts obtained using OpenIE system into the knowledge graph.

List of OpenIE-mined facts should be provided in a CSV-file of a special structure (see file format examples) using `--facts` option.

```
java -jar glomicave-kg.jar \
-l \
--cfg_graphdb "./config/graphdb/graphdb_config.xml" \
--facts "../data/openie/2023-10/triples_final.csv" \
--cfg_logs "./config/log4j2.xml" \
loadFacts
```

#### 4. 'addTraits' pipeline

This pipline version can be used when we need to add phenotypic traits/phenotypes into the knowledge graph.

List of traits should be provided in a CSV-file of a special structure (see file format examples) using `--traits` option.

```
java -jar glomicave-kg.jar \
-l \
--cfg_graphdb "./config/graphdb/graphdb_config.xml" \
--traits "../data/phenotypes/wp4_traits.csv" \
--cfg_logs "./config/log4j2.xml" \
addTraits
```


### Getting statistics on the knowledge graph 

To check created node types we can execute the following commands in the final graph database in Cypher language:
```
MATCH (n) RETURN distinct labels(n), count(*);
```

Sample output:
```
╒══════════════════════════════════════════════╤════════╕
│labels(n)                                     │count(*)│
╞══════════════════════════════════════════════╪════════╡
│["LEXICAL_FORM"]                              │83586   │
├──────────────────────────────────────────────┼────────┤
│["NAMED_ENTITY"]                              │18177   │
├──────────────────────────────────────────────┼────────┤
│["NAMED_ENTITY", "PROTEIN"]                   │19882   │
├──────────────────────────────────────────────┼────────┤
│["GENE_PRODUCT", "NAMED_ENTITY"]              │33034   │
├──────────────────────────────────────────────┼────────┤
│["NAMED_ENTITY", "PATHWAY"]                   │2749    │
├──────────────────────────────────────────────┼────────┤
│["NAMED_ENTITY", "METABOLITE"]                │5687    │
├──────────────────────────────────────────────┼────────┤
│["GENE_PRODUCT", "NAMED_ENTITY", "PROTEIN"]   │2332    │
├──────────────────────────────────────────────┼────────┤
│["NAMED_ENTITY", "TRAIT"]                     │73      │
├──────────────────────────────────────────────┼────────┤
│["NAMED_ENTITY", "PROTEIN", "METABOLITE"]     │2       │
├──────────────────────────────────────────────┼────────┤
│["GENE_PRODUCT", "NAMED_ENTITY", "METABOLITE"]│1       │
├──────────────────────────────────────────────┼────────┤
│["GENE_PRODUCT", "NAMED_ENTITY", "PATHWAY"]   │1       │
├──────────────────────────────────────────────┼────────┤
│["PUBLICATION"]                               │11998   │
├──────────────────────────────────────────────┼────────┤
│["SENTENCE"]                                  │109725  │
├──────────────────────────────────────────────┼────────┤
│["FACT"]                                      │24669   │
├──────────────────────────────────────────────┼────────┤
│["POLARITY"]                                  │2       │
├──────────────────────────────────────────────┼────────┤
│["MODALITY"]                                  │2       │
├──────────────────────────────────────────────┼────────┤
│["ATTRIBUTION"]                               │77      │
└──────────────────────────────────────────────┴────────┘
```

Statistics on the relation types can be obtained with the following Cypher command:
```
MATCH (n)-[r]->(m) RETURN distinct type(r), count(*);
```

Sample output:
```
╒══════════════════════╤════════╕
│type(r)               │count(*)│
╞══════════════════════╪════════╡
│"SYNONYM_WITH"        │101716  │
├──────────────────────┼────────┤
│"HAS_LF"              │120050  │
├──────────────────────┼────────┤
│"COOCCURS_WITH"       │143254  │
├──────────────────────┼────────┤
│"APPEARS_IN"          │111083  │
├──────────────────────┼────────┤
│"APPEARS_IN_LOWERCASE"│8949    │
├──────────────────────┼────────┤
│"OIE_RELATED_WITH"    │2238    │
├──────────────────────┼────────┤
│"HAS_FACT"            │25285   │
├──────────────────────┼────────┤
│"IS_PART_OF_PATHWAY"  │114355  │
├──────────────────────┼────────┤
│"IS_PART_OF_SENTENCE" │109725  │
├──────────────────────┼────────┤
│"FACT_APPEARS_IN"     │25307   │
├──────────────────────┼────────┤
│"HAS_MODALITY"        │23936   │
├──────────────────────┼────────┤
│"HAS_POLARITY"        │23930   │
├──────────────────────┼────────┤
│"HAS_ATTRIBUTION"     │270     │
└──────────────────────┴────────┘
```


## Configuration files.

### Logging settings

Create file with logger settings, where one can put the path to the folder where to store application logs.

Here you can find an example of logger configuration file: https://github.com/nec-research/glomicave/blob/main/config/log4j2.xml


### Configurations for the S3 object storage

This file is used to identify Amazon S3 bucket to store the data, like input files for the cloud-integrated versions of the pipeline and Athena tables.

Here you can find an example of S3 configuration file: https://github.com/nec-research/glomicave/blob/main/config/s3/aws_s3_config.xml


## Input file formats

### External ontology files

Data from external dictionaries before being integrated into the knowledge graph should be written into a comma-separated CSV-file of the following structure:

`Source, Category, UID, Name, Synonym_1, ..., Synonym_N`

Each row contains information about a single entity recorded in columns:
* Source: name of the original database, ontology of a dataset. It will be added as a prefix into the entity ID in the knowledge graph.
* Category: label that should be assigned to the entity node in the graph databse. Should be one of the following: {NAMED_ENTITY, METABOLITE, GENE, GENE_PRODUCT, PROTEIN}.
* UID: udentifier in the original database.
* Name: textual name or abbreviation of the entity (e.g. gene name).
* Synonym_1, ..., Synonym_N: synonyms that may name the entity in literature or other databases.

Example:
```
source,category,uid,name,syn_0,syn_1,syn_2,syn_3,syn_4,syn_5,syn_6
UNIPROT,PROTEIN,A0A009IHW8,ABTIR_ACIB9,2' cyclic ADP-D-ribose synthase AbTIR,2'cADPR synthase AbTIR,EC 3.2.2.-,NAD(+) hydrolase AbTIR,EC 3.2.2.6,TIR domain-containing protein in A.baumannii,AbTIR
UNIPROT,PROTEIN,A0A023I7E1,ENG1_RHIMI,"Glucan endo-1,3-beta-D-glucosidase 1","Endo-1,3-beta-glucanase 1",EC 3.2.1.39,Laminarinase,RmLam81A,,
UNIPROT,PROTEIN,A0A024SC78,CUTI1_HYPJR,Cutinase,EC 3.1.1.74,,,,,
```

### Phenotypes/Traits

List of phenotypes ot traits before being integrated into the knowledge graph should be written into a comma-separated CSV-file of the following structure:

`Id, Symbol, Name, Synonyms`

Each row contains information about a single trait recorder in columns:
* Id: Train index.
* Symbol: Internal unique identifier or symbol that names the trait.
* Name: Textual name of the trait.
* Synonyms: Semicolon-separated list of synonyms that name the trait.

Example:
```
Id,Symbol,Name,Synonyms
1,BC1_A_1,Calf birth weight,birth weight
2,BC1_A_2,Gestation length,
3,BC5_A_1,"Concentration levels of phosphate, nitrite, nitrate, ammonium, oxygen",phosphate; nitrite; ammonium;  oxygen
```

### Publication DOIs file

DOIs should be provided in a text file, each doi should be exactly of the following format:

```
10.1038/s41598-018-31605-0
10.1021/acs.jafc.0c02129
10.1007/s11306-018-1414-0
10.3168/jds.2014-8067

```

**Note.** In case record contains extra symbols, like *https\://doi.org/* or *doi.org* it will be considered as a separate record and may cause duplication of the information in the database.


### OpenIE-derived facts

Text-mined facts are normally represanted by OpenIE systems as triples `(Subject, Relation, Object)`. Here we describe the structure of a comma-separated CSV-file that should be used to integrate OpenIE-derived facts into the knowledge graph:

`Subject, Relation, Object, Polarity, Modality, Attribution, Sentence, SentenceUID`

Each row contains information about a single fact recorder in following columns:

* Subject: Subject of a fact from considered triple (Subject, Relation, Object) (required).
* Relation: Text-extracted relation of a fact from considered triple (Subject, Relation, Object) (required).
* Object: Object of a fact from considered triple (Subject, Relation, Object) (required).
* Polarity: Polarity of a fact (POSITIVE or NEGATIVE) if provided (optional).
* Modality: Cernainty about a fact (CERTAINTY, POSSIBILITY) if provided (optional).
* Attribution: Context information about the fact (e.g. study outcome, hypothesis) if provided (optional).
* Sentence: Full text a sentence from which the fact was extracted (optional).
* SentenceUID: UID of a sentence in the knowledge graphs from which the fact was extracted (required).

Example:
```
subject,relation,object,polarity,modality,attribution,sentence,uid
abscisic acid,be,aba,POSITIVE,CERTAINTY,,"In this study, we report that HSP90 is essential for drought stress resistance in cassava by regulating abscisic acid (ABA) and hydrogen peroxide (H2 O2 ) using two specific protein inhibitors of HSP90 (geldanamycin (GDA) and radicicol (RAD)).",10.1111/nph.16346/3
further investigation,identify mecatalase1 as,protein,POSITIVE,CERTAINTY,,Further investigation identifies MeWRKY20 and MeCatalase1 as MeHSP90.9-interacting proteins.,10.1111/nph.16346/5
further investigation,identify mewrky20 as,protein,POSITIVE,CERTAINTY,,Further investigation identifies MeWRKY20 and MeCatalase1 as MeHSP90.9-interacting proteins.,10.1111/nph.16346/5
```

## Building Docker image and running the tool from Docker container

The whole application can be dockerized and executed inside a Docker container.

To build and run the app inside a Docker container suitable Java environment is needed. We tested the application with `eclipse-temurin:17-jdk-jammy` as a base Docker image.


## Executable files

Check assets for pre-compiled executable .JAR files: https://github.com/nec-research/glomicave/releases.


## Acknowledgment

NEC Laboratories Europe GmbH, Copyright (c) 2024, All rights reserved.

Authors: [Roman Siarheyeu](mailto:raman.siarheyeu@neclab.eu), [Kiril Gashteovski](mailto:kiril.gashteovski@neclab.eu).

