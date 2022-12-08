package ch.unibe.scg.comment.analysis.neon.cli.task;

import ch.unibe.scg.comment.analysis.neon.cli.InstancesBuilder;
import weka.core.converters.ArffSaver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Prepares all features (text+NLP) and labels into one dataset and separate them according to each paritition
 * @datbase input database (sqlite for now)
 * @data language under analysis
 * @extractorsPartition number of partitions
 */
public class T6PrepareDatasets {

	private final String database;
	private final String data;
	private final int extractorsPartition;

	public T6PrepareDatasets(String database, String data, int extractorsPartition) {
		super();
		this.database = database;
		this.data = data;
		this.extractorsPartition = extractorsPartition;
	}

	public void run() throws Exception {
		try (
				Connection connection = DriverManager.getConnection("jdbc:sqlite:" + this.database);
				Statement statement = connection.createStatement()
		) {
			statement.executeUpdate("PRAGMA foreign_keys = on");
			statement.executeUpdate(Utility.resource("sql/6_dataset.sql").replaceAll("\\{\\{data}}", this.data));
			List<String> categories = this.categories(statement);
			Map<Integer, String> sentences = new HashMap<>();

			// key = partition, value = {key=id, value = [categories]}}
			Map<Integer, Map<Integer, Set<String>>> partitions = new HashMap<>();
			try (
					ResultSet result = statement.executeQuery(
							"SELECT partition, m.comment_sentence_id AS id, m.comment_sentence AS sentence, m.category FROM "
									+ this.data + "_3_sentence_mapping_clean AS m JOIN " + this.data
									+ "_4_sentence_partition_workshop AS p ON (p.comment_sentence_id = m.comment_sentence_id AND p.category = m.category)")
			) {
				while (result.next()) {
					int partition = result.getInt("partition");
					int id = result.getInt("id");
					String sentence = result.getString("sentence");
					String category = result.getString("category");
					sentences.put(id, sentence);

					if (!partitions.containsKey(partition)) {
						partitions.put(partition, new HashMap<>());
					}
					if (!partitions.get(partition).containsKey(id)) {
						partitions.get(partition).put(id, new HashSet<>());
					}
					partitions.get(partition).get(id).add(category);
				}
			}
			try (
					PreparedStatement insert = connection.prepareStatement("INSERT INTO " + this.data
							+ "_6_dataset (partition, extractors_partition, dataset) VALUES (?, ?, ?)")
			) {
				//for each partition (training, testing)
				for (Map.Entry<Integer, Map<Integer, Set<String>>> partition : partitions.entrySet()) {
					InstancesBuilder builder = this.instancesBuilder(statement, categories, partition.getKey());
					//iterate the sentences (sentence -> {categories})
					for (Map.Entry<Integer, Set<String>> sentence : partition.getValue().entrySet()) {
						builder.add(sentences.get(sentence.getKey()), sentence.getValue());
					}
					try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
						ArffSaver saver = new ArffSaver();
						saver.setInstances(builder.build()); //build the tfidf features and heuristic features
						saver.setDestination(buffer);
						saver.writeBatch();
						insert.setInt(1, partition.getKey()); //partition
						insert.setInt(2, this.extractorsPartition); //ignored
						insert.setBytes(3, buffer.toByteArray()); //(sentence -> {categories})
						insert.executeUpdate();
					}
				}
			}
		}
	}

	private InstancesBuilder instancesBuilder(
			Statement statement, List<String> categories, int partition
	) throws SQLException, IOException {
		try (
				ResultSet result = statement.executeQuery(
						"SELECT heuristics, dictionary FROM " + this.data + "_5_extractors WHERE partition = "
								+ this.extractorsPartition + "")
		) {
			result.next();
			Path heuristics = Files.createTempFile("heuristics", ".xml");
			Files.write(heuristics, result.getBytes("heuristics"));
			Path dictionary = Files.createTempFile("dictionary", ".csv");
			Files.write(dictionary, result.getBytes("dictionary"));
			//create an instance builder for each partition
			return new InstancesBuilder(
					String.format("%s-features-%d-%d", this.data, this.extractorsPartition, partition),
					categories,
					heuristics.toFile(),
					dictionary.toFile()
			);
		}
	}

	private List<String> categories(Statement statement) throws SQLException {
		List<String> categories = new ArrayList<>();
		try (
				ResultSet result = statement.executeQuery(
						"SELECT name FROM PRAGMA_TABLE_INFO('" + this.data + "_0_raw') ORDER BY name ASC")
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
