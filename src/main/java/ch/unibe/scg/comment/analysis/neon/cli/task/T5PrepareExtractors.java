package ch.unibe.scg.comment.analysis.neon.cli.task;

import org.neon.engine.XMLReader;
import org.neon.model.Condition;
import org.neon.model.Heuristic;
import org.neon.pathsFinder.engine.Parser;
import org.neon.pathsFinder.engine.PathsFinder;
import org.neon.pathsFinder.engine.XMLWriter;
import org.neon.pathsFinder.model.GrammaticalPath;
import org.neon.pathsFinder.model.Sentence;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.stemmers.IteratedLovinsStemmer;
import weka.core.stopwords.Rainbow;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.StringToWordVector;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Extract the NLP (heuristic) features and text (Tfidf) features for each partition and prepares the features
 * @datbase input database (sqlite for now)
 * @data language under analysis
 * @wordsToKeep number of words to keep for tfidf
 * @useManualHeuristicFile set true to use external heuristic file (available in resource folder)
 */
public class T5PrepareExtractors {

	private final String database;
	private final String data;
	private final int wordsToKeep;
	private final boolean useManualHeuristicFile;

	public T5PrepareExtractors(String database, String data, int wordsToKeep, boolean useManualHeuristicFile) {
		this.database = database;
		this.data = data;
		this.wordsToKeep = wordsToKeep;
		this.useManualHeuristicFile = useManualHeuristicFile;
	}

	public void run() throws Exception {
		try (
				Connection connection = DriverManager.getConnection("jdbc:sqlite:" + this.database);
				Statement statement = connection.createStatement()
		) {
			statement.executeUpdate("PRAGMA foreign_keys = on");
			statement.executeUpdate(Utility.resource("sql/5_extractors.sql").replaceAll("\\{\\{data}}", this.data));
			Map<Integer, Map<String, List<String>>> partitions = new HashMap<>();
			for (String category : this.categories(statement)) {
				try (
						ResultSet result = statement.executeQuery("SELECT partition, comment_sentence FROM " + this.data
								+ "_3_sentence_mapping_clean JOIN " + this.data + "_4_sentence_partition_workshop on ("
								+ this.data + "_4_sentence_partition_workshop.comment_sentence_id = " + this.data
								+ "_3_sentence_mapping_clean.comment_sentence_id) WHERE " +this.data + "_4_sentence_partition_workshop.category = \"" + category
								+ "\"")
				) {
					while (result.next()) {
						int partition = result.getInt("partition");
						if (!partitions.containsKey(partition)) {
							partitions.put(partition, new HashMap<>());
						}
						if (!partitions.get(partition).containsKey(category)) {
							partitions.get(partition).put(category, new ArrayList<>());
						}
						partitions.get(partition).get(category).add(result.getString("comment_sentence"));
					}
				}
			}
			try (
					PreparedStatement insert = connection.prepareStatement("INSERT INTO " + this.data
							+ "_5_extractors (partition, heuristics, dictionary) VALUES (?, ?, ?)")
			) {
				for (Map.Entry<Integer, Map<String, List<String>>> partition : partitions.entrySet()) {
					ArrayList<Heuristic> heuristics = new ArrayList<>();

					if(useManualHeuristicFile){
						heuristics.addAll(this.readHeuristicsFromFile());
					}else{
						for (Map.Entry<String, List<String>> category : partition.getValue().entrySet()) {
							heuristics.addAll(this.heuristics(category.getKey(), category.getValue()));
						}
					}
					List<String> sentences = partition.getValue()
							.values()
							.stream()
							.reduce(new ArrayList<>(), (r, e) -> {
								r.addAll(e);
								return r;
							});
					insert.setInt(1, partition.getKey());
					insert.setBytes(2, this.heuristics(heuristics));
					insert.setBytes(3, this.dictionary(sentences));
					insert.executeUpdate();
				}
			}
		}
	}

	/**
	 * Write all heuristics to a temporary xml file
	 * @param heuristics
	 * @return
	 * @throws Exception
	 */
	private byte[] heuristics(ArrayList<Heuristic> heuristics) throws Exception {
		Path path = Files.createTempFile("heuristics", ".xml");
		try {
			XMLWriter.addXMLHeuristics(path.toFile(), heuristics);
			return Files.readAllBytes(path);
		} finally {
			path.toFile().delete();
		}
	}

	/**
	 * Read all heuristics from an explicit file
	 * @return
	 * @throws Exception
	 */
	private ArrayList<Heuristic> readHeuristicsFromFile() throws Exception {
		//File file = new File(Utility.class.getClassLoader().getResource("pharo_heuristics.xml").getFile());
		Path path = Files.createTempFile("temp-heuristics", ".xml");
		Files.write(path, Utility.resource(this.data + "_heuristics.xml").getBytes());

		ArrayList<Heuristic> heuristics = new ArrayList<>();
		try {
			heuristics = XMLReader.read(path.toFile());
		} catch (Exception var18) {
			System.err.println("Unable to read XML file");
		}finally {
			path.toFile().delete();
		}
		return heuristics;
	}

	/**
	 * Get heuristics for each category using NEON
	 * @param category a category from the taxonomy
	 * @param entries all sentences of the category
	 * @return heuristics for the category collected from NEON
	 */
	private ArrayList<Heuristic> heuristics(String category, List<String> entries) {
		ArrayList<Sentence> sentences = Parser.getInstance().parse(String.join("\n\n", entries)); //sentences: a list of each sentence with its type (declarative|Interrogative) and graph received from NEON, graph: morphology analysis of the sentence.
		ArrayList<GrammaticalPath> paths = PathsFinder.getInstance().discoverCommonPaths(sentences); //minimized identical path identified from all the sentences heuristic by comparing their conditions
		return paths.stream().map(p -> {
			Heuristic heuristic = new Heuristic();
			heuristic.setConditions(p.getConditions().stream().map(s -> {
				Condition condition = new Condition();
				condition.setConditionString(s);
				return condition;
			}).collect(Collectors.toCollection(ArrayList::new)));
			heuristic.setType(p.getDependenciesPath());
			heuristic.setSentence_type(p.identifySentenceType());
			heuristic.setText(p.getTemplateText());
			heuristic.setSentence_class(category);
			return heuristic;
		}).collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 * Gets text (tfidf) features after preprocessing (filtering) the data.
	 * @param sentences all sentences from all the categories
	 * @return words as features
	 * @throws Exception
	 */
	private byte[] dictionary(List<String> sentences) throws Exception {
		ArrayList<Attribute> attributes = new ArrayList<>();
		attributes.add(new Attribute("text", (List<String>) null, null));
		Instances instances = new Instances("prepare", attributes, sentences.size());
		for (String sentence : sentences) {
			DenseInstance instance = new DenseInstance(1);
			instance.setValue(instances.attribute("text"), sentence);
			instances.add(instance);
		}
		Path path = Files.createTempFile("dictionary", ".csv");
		try {
			//perform a series of steps for tfidf features
			StringToWordVector filter = new StringToWordVector();
			filter.setOutputWordCounts(true);
			filter.setLowerCaseTokens(true);
			filter.setDoNotOperateOnPerClassBasis(true);
			filter.setStopwordsHandler(new Rainbow()); //stopwords list based on http://www.cs.cmu.edu/~mccallum/bow/rainbow/
			filter.setStemmer(new IteratedLovinsStemmer());
			filter.setWordsToKeep(this.wordsToKeep);
			filter.setAttributeIndicesArray(new int[]{0}); // first attribute is sentence
			filter.setDictionaryFileToSaveTo(path.toFile());
			filter.setInputFormat(instances);
			Filter.useFilter(instances, filter);
			return Files.readAllBytes(path);
		} finally {
			path.toFile().delete();
		}
	}

	private List<String> categories(Statement statement) throws SQLException {
		List<String> categories = new ArrayList<>();
		try (
				ResultSet result = statement.executeQuery(
						"SELECT name FROM PRAGMA_TABLE_INFO('" + this.data + "_0_raw')")
		) {
			while (result.next()) {
				categories.add(result.getString("name"));
			}
		}
		categories.remove("class");
		categories.remove("stratum");
		categories.remove("comment");
		return categories;
	}
}
