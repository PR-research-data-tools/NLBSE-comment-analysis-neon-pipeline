## NLBSE Tool Competition Baseline: Code Comment Classification

This repository contains the source code for the baseline used in the [NLBSE’23 tool competition](https://nlbse2023.github.io/tools/) and partly in the [NLBSE'24 tool competition](https://nlbse2024.github.io/tools/) on code comment classification.

Participants of the competition must use the provided data on [NLBSE Tool Competition 2023:Data](https://github.com/nlbse2023/code-comment-classification) or
[NLBSE Tool Competition 2024:Data](https://github.com/nlbse2024/code-comment-classification)
to train/test their classifiers, which should outperform the baselines.

Details on how to participate in the competition can be found [here](https://colab.research.google.com/drive/1cW8iUPY9rTjZdXnGYtJ4ARBSISyKieWt#scrollTo=7ITz0v7mv4jV)
and [here](https://colab.research.google.com/drive/1GhpyzTYcRs8SGzOMH3Xb6rLfdFVUBN0P?usp=sharing).

## Contents of this package

---
- [NLBSE Tool Competition Baseline: Code Comment Classification](#nlbse-tool-competition-baseline-code-comment-classification)
- [Contents of this package](#contents-of-this-package)
- [Data and Results of Baseline](#data-results-baseline)
- [Folder structure](#folder-structure)
- [Build pipeline](#build-pipeline)
- [Pipeline Steps](#pipeline-steps)
- [Pipeline Output for Classification](#pipeline-for-classification)
- [Software Projects](#software-projects)
- [Baseline Results](#baseline-results)
- [Citing Related Work](#citing-related-work)
- [Follow-up Work ](#follow-work)

## Data and Results of Baseline

---
The data used for the baseline or pipeline can be found in the [NLBSE’23 tool competition](https://nlbse2023.github.io/tools/) and in the [NLBSE'24 tool competition](https://nlbse2024.github.io/tools/) repositories.

## Folder structure

---
- `data`: contains the database with the all files, from the ground truth to the intermediate files used in various steps to the final files.
- `lib`: Neon Tool and other required Jars
- `src/`: source code of the pipeline
  - `Main.java`: parse the CLI commands. 
  - `task/`: contains various tasks starting from preprocessing the data to preparing training and testing data to building classifiers and collecting output. 
  - `resources/`: required resources like sql, experiment configuration and heurisitcs for the pipeline
    - `sql/`: store all sql queries to create intermediate tables

## Build the pipeline

### Prerequisites
- Java 13 or higher
- Maven

### Install Required JARs

1. Download the following JAR files:
  - `jawjaw-1.0.2.jar` from [Google Code archive](https://code.google.com/archive/p/jawjaw/downloads])
  - `ws4j-1.0.1.jar` from [Google Code archive](https://code.google.com/archive/p/ws4j/downloads)

2. Install the JAR files into your local Maven repository:

   ```sh
   mvn install:install-file -DgroupId=edu.cmu.lti -DartifactId=jawjaw -Dversion=1.0.2 -Dpackaging=jar -Dfile=/path/to/lib/jawjaw-1.0.2.jar
   mvn install:install-file -DgroupId=edu.cmu.lti -DartifactId=ws4j -Dversion=1.0.1 -Dpackaging=jar -Dfile=/path/to/lib/ws4j-1.0.1.jar
   

3. Install dependencies and create shaded jar:
```
mvn clean install
mvn package
```

4. Run simple smoke test, there should be an output.xml in the project folder.

```
java -jar target/cli-0.0.1-SNAPSHOT.jar src/test/resources/text.txt src/test/resources/heuristics.xml output.xml
```

5. Run the pipeline on the machine (HPC server if required) where the cli commands 
- `-jar` refers to the required jar file
- `-d` refers to the languages we want to analyze, 
- `-t` refers to the tasks or steps we want to perform

```aidl
nohup java -jar comment-analysis-neon-0.0.1-SNAPSHOT.jar -D data/db.sqlite -d pharo,java,python -t 1-preprocess,2-split-sentences,3-map-sentences,4-partition-sentences,5-prepare-extractors,6-prepare-datasets,7-prepare-experiments,8-run-experiments,9-import-experiment-results,10-build-classifiers,11-import-classifier-outputs &
```

## Pipeline Steps

---
The pipeline have various tasks that help prepare the dataset for various tasks. 
The tasks are defined in the `src/tasks` folders that help preprocess the data (`T1Preprocess`), split it(`T2SplitSentences`,`T3MapSentences`), partition into training and testing split (`T6PrepareDatasetWorkshop`), prepare the features (`T7PrepareExperimentsWorkshop`), classify it (`T10BuildClassifiers`) and evaluate it (`T11ImportClassifiersOutputs`).

- **Preprocessing**. Before splitting, the manually-tagged class comments were preprocessed as follows:
    - We changed the sentences to lowercase, reduced multiple line endings to one, and removed special characters except for  `a-z0-9,.@#&^%!? \n`  since different languages can have different meanings for the symbols. For example, `$,:{}!!` are markup symbols in Pharo, while in Java it is `‘/* */ <p>`, and `#,`  in Python. For simplicity reasons, we removed all such special character meanings.
    - We replaced periods in numbers, "e.g.", "i.e.", etc, so that comment sentences do not get split incorrectly.
    - We removed extra spaces before and after comments or lines.

- **Splitting sentences**.
    - Since the classification is sentence-based, we split the comments into sentences.
    - As we use NEON tool to extract NLP features, we use the same splitting method to split the sentence. It splits the sentences based on selected characters `(\\n|:)`. This is another reason to remove some special characters to avoid unnecessary splitting.
    - Note: the sentences may not be complete sentences. Sometimes the annotators classified a relevant phrase a sentence into a category.
- **Partition selection**.
    - After splitting comments into  sentences, we split the sentence dataset in an 80/20 training-testing split.
    - The partitions are determined based on an algorithm in which we first determine the stratum of each class comment. The original paper gives more details on strata distribution.
    - Then, we follow a round-robin approach to fill training and testing partitions from the strata. We select a stratum, select the category with a minimum number of instances in it to achieve the best balancing, and assign it to the train or test partition based on the required proportions.

- **Feature preparation**. We use two kinds of features: TEXT and NLP.
    - For NLP features, we prepare a term-by-document matrix M, where each row represents a comment sentence (i.e., a sentence belongs to our language dataset composing CCTM) and each column represents the extracted feature.
    - To extract the NLP features, we use NEON, a tool proposed in the previous work of Andrea Di Sorbo. The tool detects NLP patterns from natural language-based sentences. We add the identified NLP patterns as feature columns in M, where each of them models the presence (modeled by 1) or absence (modeled by 0) of an NLP pattern in the comment sentences. In sum, each i\_th row represents a comment sentence, and j_th represents an NLP feature.
    - For the TEXT features, we apply typical preprocessing steps such as stop word removal, stemmer, and convert it a vector based on the TF-IDF approach. The first attribute is a sentence. In case of TEXT features, the rows are the comment sentences and the column represents a term contained in it. Each cell of the matrix represents the weight (or importance) of the j\_th term contained in the i_th comment sentence. The terms in M are weighted using the TF–IDF score.
    - We prepare such Matrix M for each category of each language. The last column of the Matrix represents the category.

- **Classification**. We used Weka to classify the comment sentences into each category using the Random Forest model (the baseline).

- **Evaluation**. We evaluated our baseline models (i.e., for each category) using standard evaluation metrics, precision, recall, and F1-score.

## Data Output for Classification

---
At the end, we provide a CSV file for each programming language (in the `input` folder) where each row represent a sentence (aka an instance) and each sentence contains six columns as follow:
- `comment_sentence_id` is the unique sentence ID;
- `class` is the class name referring to the source code file where the sentence comes from;
- `comment_sentence` is the actual sentence string, which is a part of a (multi-line) class comment;
- `partition` is the dataset split in training and testing, 0 identifies training instances and 1 identifies testing instances, respectively;
- `instance_type` specifies if an instance actually belongs to the given category or not: 0 for negative and 1 for positive instances;
- `category` is the ground-truth or oracle category.

## Software Projects

---
We extracted the class comments from selected projects.

- ### Java
  Details of six java projects.
    - Eclipse:  The version of the project referred to extracted class comments is available as [Raw Dataset](https://doi.org/10.5281/zenodo.4311839) on Zenodo. More detail about the project is available on GitHub [Eclipse](https://github.com/eclipse).

    - Guava: The version of the project referred to extracted class comments is available as [Raw Dataset](https://doi.org/10.5281/zenodo.4311839) on Zenodo. More detail about the project is available on GitHub [Guava](https://github.com/google/guava).

    - Guice: The version of the project referred to extracted class comments is available as [Raw Dataset](https://doi.org/10.5281/zenodo.4311839) on Zenodo. More detail about the project is available on GitHub [Guice](https://github.com/google/guice).

    - Hadoop:  The version of the project referred to extracted class comments is available as [Raw Dataset](https://doi.org/10.5281/zenodo.4311839) on Zenodo. More detail about the project is available on GitHub [Apache Hadoop](https://github.com/apache/hadoop)

    - Spark.csv: The version of the project referred to extracted class comments is available as [Raw Dataset](https://doi.org/10.5281/zenodo.4311839) on Zenodo. More detail about the project is available on GitHub [Apache Spark](https://github.com/apache/spark)

    - Vaadin: The version of the project referred to extracted class comments is available as [Raw Dataset](https://doi.org/10.5281/zenodo.4311839) on Zenodo. More detail about the project is available on GitHub [Vaadin](https://github.com/vaadin/framework)

- ### Pharo
  Contains the details of seven Pharo projects.
    - GToolkit: The version of the project referred to extracted class comments is available as [Raw Dataset](https://doi.org/10.5281/zenodo.4311839) on Zenodo.

    - Moose: The version of the project referred to extracted class comments is available as [Raw Dataset](https://doi.org/10.5281/zenodo.4311839) on Zenodo.

    - PetitParser: The version of the project referred to extracted class comments is available as [Raw Dataset](https://doi.org/10.5281/zenodo.4311839) on Zenodo.

    - Pillar: The version of the project referred to extracted class comments is available as [Raw Dataset](https://doi.org/10.5281/zenodo.4311839) on Zenodo.

    - PolyMath: The version of the project referred to extracted class comments is available as [Raw Dataset](https://doi.org/10.5281/zenodo.4311839) on Zenodo.

    - Roassal2: The version of the project referred to extracted class comments is available as [Raw Dataset](https://doi.org/10.5281/zenodo.4311839) on Zenodo.

    - Seaside: The version of the project referred to extracted class comments is available as [Raw Dataset](https://doi.org/10.5281/zenodo.4311839) on Zenodo.

- ### Python
  Details of the extracted class comments of seven Python projects.
    - Django: The version of the project referred to extract class comments is available as [Raw Dataset](https://doi.org/10.5281/zenodo.4311839) on Zenodo. More detail about the project is available on GitHub [Django](https://github.com/django)

    - IPython: The version of the project referred to extract class comments is available as [Raw Dataset](https://doi.org/10.5281/zenodo.4311839) on Zenodo. More detail about the project is available on GitHub[IPython](https://github.com/ipython/ipython)

    - Mailpile: The version of the project referred to extract class comments is available as [Raw Dataset](https://doi.org/10.5281/zenodo.4311839) on Zenodo. More detail about the project is available on GitHub [Mailpile](https://github.com/mailpile/Mailpile)

    - Pandas: The version of the project referred to extract class comments is available as [Raw Dataset](https://doi.org/10.5281/zenodo.4311839) on Zenodo. More detail about the project is available on GitHub [pandas](https://github.com/pandas-dev/pandas)

    - Pipenv: The version of the project referred to extract class comments is available as [Raw Dataset](https://doi.org/10.5281/zenodo.4311839) on Zenodo. More detail about the project is available on GitHub [Pipenv](https://github.com/pypa/pipenv)

    - Pytorch: The version of the project referred to extract class comments is available as [Raw Dataset](https://doi.org/10.5281/zenodo.4311839) on Zenodo. More detail about the project is available on GitHub [PyTorch](https://github.com/pytorch/pytorch)

    - Requests: The version of the project referred to extract class comments is available as [Raw Dataset](https://doi.org/10.5281/zenodo.4311839) on Zenodo. More detail about the project is available on GitHub [Requests](https://github.com/psf/requests/)

## Baseline Results

---
The summary of the baseline results are found in `baseline_results_summary.xlsx` in the repositories [NLBSE’23 tool competition: code comment classification](https://github.com/nlbse2023/code-comment-classification) and in the [NLBSE’24 tool competition: code comment classification](https://github.com/nlbse2024/code-comment-classification)

## Citing Related Work
Since you will be using our dataset (and possibly one of our notebooks) as well as the original work behind the dataset, please cite the following references in your paper:

```aidl
@article{rani2021,
  title={How to identify class comment types? A multi-language approach for class comment classification},
  author={Rani, Pooja and Panichella, Sebastiano and Leuenberger, Manuel and Di Sorbo, Andrea and Nierstrasz, Oscar},
  journal={Journal of systems and software},
  volume={181},
  pages={111047},
  year={2021},
  publisher={Elsevier}
}
```
```aidl
@inproceedings{DiSorboVPCP21,
   author    = {Di Sorbo, Andrea and Visaggio, Corrado Aaron and Di Penta, Massimiliano and Canfora, Gerardo and Panichella, Sebastiano},
   title     = {An NLP-based Tool for Software Artifacts Analysis},
   booktitle = { {IEEE} International Conference on Software Maintenance and Evolution,
                {ICSME} 2021, Luxembourg, September 27 - October 1, 2021},
   pages     = {569--573},
   publisher = { {IEEE} },
   year      = {2021},
   doi       = {10.1109/ICSME52107.2021.00058}
}
```

## Follow-up Work 
We have done some interesting future work in this direction. 

We trained and tested 19 binary classifiers (one for each category) using the Sentence Transformer architecture on the provided training and test sets.

The baseline classifiers are coined as STACC and proposed by [Al-Kaswan et al.](https://arxiv.org/abs/2302.13681)

The summary of the baseline results is found in `baseline_results_summary.xlsx` can be found in the [NLBSE’23 tool competition](https://nlbse2023.github.io/tools/) and in the [NLBSE'24 tool competition](https://nlbse2024.github.io/tools/) repositories.

We provide a notebook to [train our baseline classifiers](STACC_baseline.ipynb) and to [run the evaluations](https://colab.research.google.com/drive/1lvXuzdl_vSwMTCGIEfqTyQC1nzl22WCy?usp=sharing).


