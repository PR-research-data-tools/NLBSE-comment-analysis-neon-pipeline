package ch.unibe.scg.comment.analysis.neon.cli.task;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.neon.engine.XMLReader;
import org.neon.model.Heuristic;
import org.neon.model.Result;
import org.neon.engine.Parser;
import weka.core.Attribute;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class T5PrepareSentencesWithNLPPatterns {

	private final String database;
	private final String data;
	private File heuristicFile;
	private List<String> categories;
	private List<String> featureNames;
	private final Path directory;
	private static CSVPrinter csvPrinter;
	private LinkedHashMap <String, Integer> featuresMapping;

	public T5PrepareSentencesWithNLPPatterns(String database, String data, Path directory) {
		this.database = database;
		this.data = data;
		this.directory = directory;
	}

	public void run() throws SQLException, IOException {
		try (
				Connection connection = DriverManager.getConnection("jdbc:sqlite:" + this.database);
				Statement statement = connection.createStatement()
		) {
			statement.executeUpdate("PRAGMA foreign_keys = on");
			//statement.executeUpdate(Utility.resource("sql/5_sentence_heuristic_mapping.sql")
					//.replaceAll("\\{\\{data}}", this.data));
			//PreparedStatement insert = connection.prepareStatement("INSERT INTO " + this.data + "_5_sentence_heuristic_mapping (comment_sentence_id, comment_sentence, heuristics, category) VALUES (?, ?, ?, ?)");
			this.categories = this.categories(statement);

			//Get the neon generated heuristics and store them in a file
			try (
					ResultSet result = statement.executeQuery(
							"SELECT heuristics FROM " + this.data + "_5_extractors")
			) {
				Path heuristicsPath = Files.createTempFile("heuristics", ".xml");
				Files.write(heuristicsPath, result.getBytes("heuristics"));
				this.heuristicFile = heuristicsPath.toFile();
			}
			this.featureNames = this.featureNames();
			//create the csv with given headers in addition to the featureNames.
			this.createCSV("comment_sentence_id","comment_sentence", "category" );

			for (String category : this.categories ) {
				try (
						ResultSet result = statement.executeQuery(
								"SELECT comment_sentence_id, comment_sentence, category  FROM " + this.data + "_3_sentence_mapping_clean WHERE category = \"" + category
										+ "\"")
				) {
					while (result.next()) {
						int id = result.getInt("comment_sentence_id");
						String sentence = result.getString("comment_sentence");
						String sentence_category = result.getString("category");
						Set<String> matchedFeatureNames = this.matchedFeatureNames(sentence);
						this.storeMatchedFeatures(id, sentence, matchedFeatureNames, sentence_category);

/*						String heuristics = this.getHeuristics(sentence); //matchedFeatureNames.stream().collect(Collectors.joining("#"))
						insert.setInt(1,id);
						insert.setString(2,sentence);
						insert.setString(3,heuristics);
						insert.setString(4,sentence_category);
						insert.execute();*/
					}
				}
			}
		} finally {
			csvPrinter.flush();
		}
	}

	/**
	 * Add rows to the CSV as per the data
	 * @param comment_sentence_id sentence id of the comment, it is not unique
	 * @param comment_sentence sentence
	 * @param category category the sentence belongs to
	 * @param matchedFeatureNames the heuristic features extracted from the sentence
	 * @throws IOException
	 */

	private void storeMatchedFeatures(int comment_sentence_id, String comment_sentence, Set<String> matchedFeatureNames, String category) throws IOException {

		Collection<Object> record = new ArrayList<Object>(this.featureNames.size()+3);
		LinkedHashMap <String, Integer> featuresMapping_ = new LinkedHashMap((Map<? extends String, ? extends Integer>) featuresMapping.clone());

		if(!matchedFeatureNames.isEmpty()){
			for(String featureName: matchedFeatureNames){
				featuresMapping_.put(featureName,1);
			}
		}
		record.add(comment_sentence_id);
		record.add(comment_sentence);
		record.addAll(featuresMapping_.values());
		record.add(category);

		csvPrinter.printRecord(record);
	}

	/**
	 * Create the CSV with the headers plus featureNames
	 * @param comment_sentence_id sentence id of the comment, it is not unique
	 * @param comment_sentence sentence
	 * @param category category the sentence belongs to
	 * @throws IOException
	 */
	private void createCSV(String comment_sentence_id, String comment_sentence, String category) throws IOException {
		try{
			BufferedWriter bufferedWriter = Files.newBufferedWriter(this.directory.resolve(String.format("%s-sentenceHeuristicMapping.csv", this.data)),
						StandardOpenOption.APPEND,StandardOpenOption.CREATE);
			csvPrinter = new CSVPrinter(bufferedWriter,CSVFormat.DEFAULT.withFirstRecordAsHeader());
			featuresMapping = new LinkedHashMap<>(this.featureNames.size());
			ArrayList<String> headers = new ArrayList<String>(this.featureNames.size()+3);

			headers.add(comment_sentence_id);
			headers.add(comment_sentence);
			for(String name: this.featureNames){
				featuresMapping.put(name,0);
			}
			headers.addAll(featuresMapping.keySet());
			headers.add(category);
			csvPrinter.printRecord(headers);

		} catch(Exception e){
			System.out.println("Creating CSV error!");
			e.printStackTrace();
		}
	}

	/**
	 * Find the heuristic features extracted from the sentence (text) using NEON.
	 * @param text comment sentence
	 * @return heuristics extracted from the sentence. It can be empty.
	 */
	private Set<String> matchedFeatureNames(String text) {
		return Parser.getInstance().extract(text, this.heuristicFile)
				.stream()
				.map(r -> this.featureName(this.category(r.getSentenceClass()), r.getHeuristic()))
				.collect(Collectors.toSet());
	}

	/**
	 * Extracted all heuristic features (NLP patterns) from NEON
	 * @return all features
	 */
	private List<String> featureNames() {
		return XMLReader.read(this.heuristicFile)
				.stream()
				.collect(Collectors.groupingBy(
						Heuristic::getText,
						Collectors.mapping(h -> this.category(h.getSentence_class()), Collectors.toSet())
				))
				.entrySet()
				.stream()
				.flatMap(e -> e.getValue().stream().map(c -> this.featureName(c, e.getKey())))
				.collect(Collectors.toSet())
				.stream()
				.sorted()
				.collect(Collectors.toList());
	}

	/**
	 * Finds the category matching the heuristic class. As NEON processes labels, normalization is required.
	 *
	 * @param heuristicClass
	 * @return
	 */
	private String category(String heuristicClass) {
		return this.categories.stream()
				.filter(c -> this.normalize(c).equals(this.normalize(heuristicClass)))
				.findFirst()
				.get();
	}

	private String normalize(String s) {
		return s.toLowerCase().replaceAll("[^a-z0-9]", "");
	}

	/**
	 *
	 * @param text comment sentence
	 * @return list of heuristic (NLP patterns) extracted from the comment sentence
	 * @throws SQLException
	 * @throws IOException
	 */
	private String getHeuristics(String text) throws IOException {
		ArrayList<Result> results = new ArrayList<>();
		ArrayList<String> sentence_heuristics =  new ArrayList<>();

		results = Parser.getInstance().extract(text, this.heuristicFile);
		for(Result r: results){
			sentence_heuristics.add(r.getHeuristic());
		}
		return sentence_heuristics.stream().collect(Collectors.joining("#"));
	}

	/**
	 * @return "categories"
	 */
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

	/**
	 * @return "heuristic-[category]-[heuristic]"
	 */
	private String featureName(String category, String heuristic) {
		return String.format("heuristic-%s-%s", category, heuristic);
	}
}
