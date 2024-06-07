# GLOMICAVE-KG

This repository contains Java code of the GLOMICAVE project - GLobal OMICs data integraion on Animal, Vegetal and Environment sectors (https://glomicave.eu/). 

Here we describe how to run the software developed under Work Package 2 to build and update GLOMICAVE Knowledge Graph of multi-omics concepts extracted from external ontologies and scientific texts. Current version of the software is restricted to the analysis of abstact texts, however same approach can be applied to the analysis of the entire paper texts, excluding tables and image captions.

Created GLOMICAVE Knowledge Graph is used as the basis for a literature search to examine the evidence for the hypotheses generated using GLOMICAVE platform tools.

## Lisence

This code is provided under GPL-3 license.


## Java version

JavaSE-15 (tested with Java SE 17.0.3).


## Pipeline versions

To run data extraction we developed a command-line tool ```glomicave-kg``` packaged as Java jar file that can be run using folliwing synax:

```
java -jar glomicave-kg [-ahilV]
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
      <pipelineName>       Name of the pipeline to run.
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

### Part 1. Cloud-integrated pipeline verions

To run cloud version of the pipeline one need to configure access to Amazon account and allow working with Amazon S3 object storage, Amazon Athena query service and Amazon Neptune graph database. 

We assume that the listed Amazon Web Services (AWS) are used for the following purposes:
* S3 object storage: store input files requred by the pipeline, like dictionaries and ontologies of omics entities, lists of seed publications, OpenIE-mined facts etc.
* Athena query service: query tables with extracted publication data.
* Neptune: graph database where the Knowledge Graph should be created or updated.

Instead of Amazon Neptune it's possibe to use Neo4J instance as the graph database within any cloud version of the pipeline.


#### Amazon access settings

Settings to allow access to the AWS on the machine or inside a Docker container where the code will be executed.



#### 1. Running 'full' pipeline


Here is an example of running full end-to-end cloud-integrated pipeline to build knowledge graph from external ontologies and provided paper abstracts. Some steps will be executed in paraller within 5 threads.

We assume that folders `logs, config` and relevan subfolders that contain configuration files have been created inside the directory with the executable .JAR file. Deafult logger configuration assumes that all application logs will be written into `logs` folder.

```
java -jar glomicave-kg 
--cfg_s3 "./config/s3/aws_s3_config.xml" 
--cfg_sqldb "./config/sqldb/aws_athena_gold_config.xml" 
--cfg_graphdb "./config/graphdb/graphdb_config.xml" 
--gene_ontology "raw/data/ontologies/nle-bio-kg/gene-info.csv" 
--ec_codes "raw/data/ontologies/ec-codes/ec-enzymes.csv" 
--extra_ontology "raw/data/ontologies/wp-annotations/wp-manually-annotated.csv" 
--traits "raw/data/phenotypes/wp4_traits.csv" 
--wp "raw/data/wikipathways/wikipathways-20220110-rdf-wp/wp/" 
--dois "raw/data/publications/publication-dois.txt" 
--threads 5 
--cfg_logs "./config/log4j2.xml"
full
```

**Note 1.** Use option `-a` to test the pipeline execution with the limited amount of data. Given that option it will use only first 100 records from each ontology and retrieve limited information from only 5 publications and no more than 2 citing or referenced papers. 

**Note 2.** This will always clear existing publication data from SQL database and rebuild the knowledge graph from stratch in the graph database. 


To check created node types we can execute the following commands in the final graph database:

```
MATCH (n) RETURN distinct labels(n), count(*);
```

Sample output (option `-a` was given to run the pipeline):

```
╒═══════════════════════════════════════════╤════════╕
│labels(n)                                  │count(*)│
╞═══════════════════════════════════════════╪════════╡
│["NAMED_ENTITY"]                           │266     │
├───────────────────────────────────────────┼────────┤
│["LEXICAL_FORM"]                           │2485    │
├───────────────────────────────────────────┼────────┤
│["NAMED_ENTITY", "TRAIT"]                  │73      │
├───────────────────────────────────────────┼────────┤
│["NAMED_ENTITY", "PATHWAY"]                │40      │
├───────────────────────────────────────────┼────────┤
│["NAMED_ENTITY", "METABOLITE"]             │594     │
├───────────────────────────────────────────┼────────┤
│["GENE_PRODUCT", "NAMED_ENTITY"]           │591     │
├───────────────────────────────────────────┼────────┤
│["GENE_PRODUCT", "NAMED_ENTITY", "PROTEIN"]│3       │
├───────────────────────────────────────────┼────────┤
│["NAMED_ENTITY", "PROTEIN"]                │196     │
├───────────────────────────────────────────┼────────┤
│["PUBLICATION"]                            │11      │
├───────────────────────────────────────────┼────────┤
│["SENTENCE"]                               │105     │
└───────────────────────────────────────────┴────────┘
```

And for created relation types can be calculated with:

```
MATCH (n)-[r]->(m) RETURN distinct type(r), count(*);
```

Sample output (option `-a` was given to run the pipeline):

```
╒═════════════════════╤════════╕
│type(r)              │count(*)│
╞═════════════════════╪════════╡
│"HAS_LF"             │2640    │
├─────────────────────┼────────┤
│"SYNONYM_WITH"       │3650    │
├─────────────────────┼────────┤
│"COOCCURS_WITH"      │20      │
├─────────────────────┼────────┤
│"APPEARS_IN"         │15      │
├─────────────────────┼────────┤
│"IS_PART_OF_PATHWAY" │1303    │
├─────────────────────┼────────┤
│"IS_PART_OF_SENTENCE"│105     │
└─────────────────────┴────────┘
```



#### 2. Running 'addOntology' pipeline

This pipline version can be used when we need to integrate data from extra ontology to the existing knowledge graph without rebuilding it from scratch.

List of entities from external ontologies should be provided in a CSV-file of a special structure (see file format examples). Data from each ontology should be passed in a separate file using `--extra_ontology` option.

```
java -jar glomicave-kg 
-a 
--cfg_s3 "./config/s3/aws_s3_config.xml" 
--cfg_graphdb "./config/graphdb/graphdb_config.xml" 
--extra_ontology "raw/data/ontologies/chebi/chebi_compounds-file_to_upload.csv" 
--extra_ontology "raw/data/ontologies/uniprot/uniprotkb_reviewed_true_2024_02_23-file_to_upload.csv" 
--threads 11 
--cfg_logs "./config/log4j2.xml"
addOntology
```

**Note.** Remove `-a` option to run the pipeline without restrictions on the number of entities to analyse.


#### 3. Running 'addPublications' pipeline

This pipline version can be used when we need to update the knowledge graph with new publication data.

Initial (or ‘seed’ DOIs for the database generation) are stored in a file `raw/data/publications/publication-dois.txt` inside Amazon S3 bucket `glomicave-storage-nec`.
You can add new seed DOIs that should be added to the currently uploaded into the database into the folder: `raw/data/publications/updates/` under the files `publications-add-<number_of_the_update>.txt`. 

**Note.** Each file should contain only new DOIs, no need to copy previous records.

List of DOIs of new papers should be recorded into a text file (one DOI per row, see file format examples) and provided using `--dois` option.

```
java -jar glomicave-kg 
--cfg_s3 "./config/s3/aws_s3_config.xml"  
--cfg_sqldb "./config/sqldb/aws_athena_gold_config.xml" 
--cfg_graphdb "./config/graphdb/graphdb_config.xml" 
--dois "raw/data/publications/updates/publications-add-1.txt" 
--nrefs 10 
--ncits 10
--threads 10 
--cfg_logs "./config/log4j2.xml"
addPublications
```


#### 4. Running 'loadFacts' pipeline

This pipline version can be used when we need to add text-mined facts using Open Information Extraction (OpenIE) into the knowledge graph.

List of OpenIE-mined facts should be provided in a CSV-file of a special structure (see file format examples) using `--facts` option.

```
java -jar glomicave-kg 
--cfg_s3 "./config/s3/aws_s3_config.xml"  
--cfg_graphdb "./config/graphdb/graphdb_config.xml" 
--facts "raw/data/openie/triples_final.csv" 
--cfg_logs "./config/log4j2.xml"
loadFacts
```


#### 5. Running 'addTraits' pipeline

This pipline version can be used when we need to add phenotypic traits/phenotypes into the knowledge graph.

List of traits should be provided in a CSV-file of a special structure (see file format examples) using `--traits` option.

```
java -jar glomicave-kg 
--cfg_s3 "./config/s3/aws_s3_config.xml"  
--cfg_graphdb "./config/graphdb/graphdb_config.xml" 
--traits "raw/data/phenotypes/wp4_traits.csv" 
--cfg_logs "./config/log4j2.xml"
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


#### 1. Running 'full' pipeline

Here is an example of running full end-to-end pipeline to build knowledge graph on the local server.

We assume that all data are stored on the local machine, folders `logs, config` and relevan subfolders that contain configuration files have been created inside the directory with the executable .JAR file. Deafult logger configuration assumes that all application logs will be written into `logs` folder.

Make sure that you have provided `-l` to run all parts of the pipeline on a local server.

```
java -jar glomicave-kg 
-l
--cfg_sqldb "./config/sqldb/sqldb_config.xml" 
--cfg_graphdb "./config/graphdb/graphdb_config.xml" 
--gene_ontology "../data/ontologies/gene_ontology/nle-bio-kg/gene-info.csv" 
--ec_codes "../data/ontologies/ec-numbers/ec-enzymes.csv" 
--extra_ontology "../data/ontologies/extra_ontologies/wp-annotations/wp-manually-annotated.csv" 
--traits "../data/phenotypes/wp4_traits.csv" 
--wp "../data/ontologies/wikipathways/wikipathways-20220110-rdf-wp/wp/" 
--dois "../data/publications/dois/publication-init-dois.csv" 
--threads 11 
--cfg_logs "./config/log4j2.xml"
full
```


#### 2. Running 'addOntology' pipeline

This pipline version can be used when we need to integrate data from extra ontology to the existing knowledge graph without rebuilding it from scratch.

List of entities from external ontologies should be provided in a CSV-file of a special structure (see file format examples). Data from each ontology should be passed in a separate file using `--extra_ontology` option.

```
java -jar glomicave-kg 
-l 
--cfg_graphdb "./config/graphdb/graphdb_config.xml" 
--extra_ontology "../data/ontologies/extra_ontologies/uniprot/uniprotkb_reviewed_true_2024_02_23-file_to_upload.csv" 
--extra_ontology "../data/ontologies/extra_ontologies/chebi/chebi_compounds-file_to_upload.csv" 
--threads 11 
--cfg_logs "./config/log4j2.xml"
addOntology
```

#### 3. Running 'addPublications' pipeline

This pipline version can be used when we need to update the knowledge graph with new publication data.

List of DOIs of new papers should be recorded into a text file (one DOI per row, see file format examples) and provided using `--dois` option.

```
java -jar glomicave-kg 
-l
--cfg_sqldb "./config/sqldb/sqldb_config.xml" 
--cfg_graphdb "./config/graphdb/graphdb_config.xml" 
--dois "../data/publications/dois/updates/publications-add-2.txt"
--nrefs 10 
--ncits 10
--threads 10 
--cfg_logs "./config/log4j2.xml"
addPublications
```


#### 4. Running 'loadFacts' pipeline

This pipline version can be used when we need to add text-mined facts using Open Information Extraction (OpenIE) into the knowledge graph.

List of OpenIE-mined facts should be provided in a CSV-file of a special structure (see file format examples) using `--facts` option.

```
java -jar glomicave-kg 
-l
--cfg_graphdb "./config/graphdb/graphdb_config.xml" 
--facts "../data/openie/2023-10/triples_final.csv" 
--cfg_logs "./config/log4j2.xml"
loadFacts
```


#### 5. Running 'addTraits' pipeline

This pipline version can be used when we need to add phenotypic traits/phenotypes into the knowledge graph.

List of traits should be provided in a CSV-file of a special structure (see file format examples) using `--traits` option.

```
java -jar glomicave-kg 
-l
--cfg_graphdb "./config/graphdb/graphdb_config.xml" 
--traits "../data/phenotypes/wp4_traits.csv" 
--cfg_logs "./config/log4j2.xml"
addTraits
```


## Configuration files.

### Logging settings

Create file with logger settings, where one can put the path to the folder where to store application logs.




### Configurations for the S3 object storage







## External ontology data


- 'data\ncbi\gene_info' can be downloaded from https://ftp.ncbi.nih.gov/gene/DATA/gene_info.gz



### Publication data

DOIs should be provided in a text file, each doi should be exactly of the following format:

```
10.1038/s41598-018-31605-0
10.1021/acs.jafc.0c02129
10.1007/s11306-018-1414-0
10.3168/jds.2014-8067

```

**Note.** In case record will contain extra symbols, like *https://doi.org/* or *doi.org* it will be considered as a separate record and may cause duplication of the information in the database.



## Building docker image and running the tool from docker container





