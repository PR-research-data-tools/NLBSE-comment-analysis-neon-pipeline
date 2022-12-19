package ch.unibe.scg.comment.analysis.neon.cli.task;

import ch.unibe.scg.comment.analysis.neon.cli.InstancesBuilder;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.core.converters.CSVSaver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prepares all features (text+NLP) and labels into one dataset and separate
 * them according to each paritition
 *
 * @datbase input database (sqlite for now)
 * @data language under analysis
 * @extractorsPartition number of partitions
 */
public class T6PrepareDatasetWorkshop {

	private final String database;
	private final String data;
	private final int extractorsPartition;
	private final Path directory;

	public T6PrepareDatasetWorkshop(String database, String data, int extractorsPartition, Path directory) {
		super();
		this.database = database;
		this.data = data;
		this.extractorsPartition = extractorsPartition;
		this.directory = directory;
	}

	public void run() throws Exception {
		try (
				Connection connection = DriverManager.getConnection("jdbc:sqlite:" + this.database);
				Statement statement = connection.createStatement()
		) {
			statement.executeUpdate("PRAGMA foreign_keys = on");
			statement.executeUpdate(Utility.resource("sql/6_dataset_workshop.sql").replaceAll("\\{\\{data}}", this.data));
			List<String> categories = this.categories(statement);

			// key = partition, value = {key=category, {key=instance_type (negative,positive), value = sentence}}}
			Map<String, Map<Integer, Map<String, List<String>>>> partitions = new HashMap<>();

			try (
					ResultSet result = statement.executeQuery(
							"SELECT DISTINCT p.partition, p.comment_sentence_id AS id, m.comment_sentence, p.category, p.instance_type FROM "
									+ this.data + "_4_sentence_partition_workshop AS p JOIN " + this.data
									+ "_3_sentence_mapping_clean AS m ON (p.comment_sentence_id = m.comment_sentence_id)")
			) {
				while (result.next()) {
					int partition = result.getInt("partition");
					int id = result.getInt("id");
					String sentence = result.getString("comment_sentence");
					String category = result.getString("category");
					String instance_type = result.getString("instance_type");

					// Change HasMap organization, now a category (most external key) has two fields 0 => training and 1 => testing
					// Each (training/testing) field has two sub-fields 0 => negative instances, 1 => positive instances
					if (!partitions.containsKey(category)) {
						partitions.put(category, new HashMap<>());
					}
					if (!partitions.get(category).containsKey(partition)) {
						partitions.get(category).put(partition, new HashMap<>());
					}
					if (!partitions.get(category).get(partition).containsKey(instance_type)) {
						partitions.get(category).get(partition).put(instance_type, new ArrayList<>());
					}
					partitions.get(category).get(partition).get(instance_type).add(sentence);
				}
			}
			try (
					PreparedStatement insert = connection.prepareStatement("INSERT INTO " + this.data
							+ "_6_dataset_workshop (partition, extractors_partition, category, dataset) VALUES (?, ?, ?, ?)")
			) {
				for (Map.Entry<String, Map<Integer, Map<String, List<String>>>> a_category : partitions.entrySet()) {
					//create instance builder for each category

					Instances trainingInstances = null; // Re-use training instances for TF-IDF testing
					for (Map.Entry<Integer, Map<String, List<String>>> partition : a_category.getValue().entrySet()) {
						// Since we rely on HashMap order, ensure that 0-kay is retrieved first
						if (partition.getKey() == 1 && trainingInstances == null) { // 0 => training and 1 => testing
							System.out.println("HashMap issue: 0-key (training) is expected before 1-key (testing)!");
							System.exit(-1);
						} else if (partition.getKey() == 0 && trainingInstances != null) {
							System.out.println("HashMap issue: trainingInstances was not cleaned while preparing training instances!");
							System.exit(-1);
						}
						//create instance builder for each category
						InstancesBuilder builder = this.instancesBuilder(statement, categories, a_category.getKey(), partition.getKey(), trainingInstances);
						//iterate the sentences (instance_type -> {sentences})
						for (Map.Entry<String, List<String>> sentences : partition.getValue().entrySet()) {
							for(String a_sentence: sentences.getValue()){
								builder.add(a_sentence,a_category.getKey(),sentences.getKey());
							}
						}

						try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
							ArffSaver saver = new ArffSaver();
							saver.setInstances(builder.build()); //build the tfidf features and heuristic features
							saver.setDestination(buffer);
							saver.writeBatch();
							insert.setInt(1, partition.getKey()); //partition
							insert.setInt(2, this.extractorsPartition); //ignored
							insert.setString(3, a_category.getKey()); //category
							insert.setBytes(4, buffer.toByteArray()); //(sentence -> {categories})
							insert.executeUpdate();
						}

						trainingInstances = builder.getTrainingInstances();
					}
				}
			}
		}
	}
	/**
	 * create an instance builder for each category
	 * @param statement
	 * @param category category for which the builder needs to be created
	 * @param partition
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
	private InstancesBuilder instancesBuilder(
			Statement statement, List<String> categories, String category, int partition, Instances trainingInstances
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

			return new InstancesBuilder(
					String.format("%s-features-%d-%d", this.data, this.extractorsPartition, partition),
					categories,
					category,
					heuristics.toFile(),
					dictionary.toFile(),
                    partition == 0, // 0 => training, 1 => testing
                    trainingInstances // must be null during training
			);
		}
	}

	/**
	 * create an instance builder for all categories of each partition
	 * @param statement
	 * @param categories all categories
	 * @param partition
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
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
