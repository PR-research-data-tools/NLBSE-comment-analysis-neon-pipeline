package ch.unibe.scg.comment.analysis.neon.cli.task;

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

/** Store the sentences for each training and testing partition
 * @datbase input database (sqlite for now)
 * @data language under analysis
 */
public class T5StorePartitionSentences {

	private final String database;
	private final String data;

	public T5StorePartitionSentences(String database, String data) {
		this.database = database;
		this.data = data;
	}

	public void run() throws Exception {
		try (
				Connection connection = DriverManager.getConnection("jdbc:sqlite:" + this.database);
				Statement statement = connection.createStatement()
		) {
			statement.executeUpdate("PRAGMA foreign_keys = on");
			statement.executeUpdate(Utility.resource("sql/5_sentences_partitions.sql").replaceAll("\\{\\{data}}", this.data));
			Map<Integer, Map<String, Map<String, String>>> partitions = new HashMap<>();
			for (String category : this.categories(statement)) {
				try (
						ResultSet result = statement.executeQuery("SELECT class, comment_sentence, partition FROM " + this.data
								+ "_3_sentence_mapping_clean JOIN " + this.data + "_4_sentence_partition_workshop on ("
								+ this.data + "_4_sentence_partition_workshop.comment_sentence_id = " + this.data
								+ "_3_sentence_mapping_clean.comment_sentence_id) WHERE "+ this.data + "_4_sentence_partition_workshop.category = \"" + category
								+ "\"")
				) {
					while (result.next()) {
						int partition = result.getInt("partition");
						String comment_sentence = result.getString("comment_sentence");
						String class_name = result.getString("class");
						if (!partitions.containsKey(partition)) {
							partitions.put(partition, new HashMap<>());
						}
						if (!partitions.get(partition).containsKey(category)) {
							partitions.get(partition).put(category, new HashMap<>());
						}

						if(!partitions.get(partition).get(category).containsKey(comment_sentence)) {
							partitions.get(partition).get(category).put(comment_sentence,class_name);
						}
					}
				}
			}
			try (
					PreparedStatement insert = connection.prepareStatement("INSERT INTO " + this.data
							+ "_5_sentences_partitions (class, comment_sentence, partition, category) VALUES ( ?, ?, ?, ?)")
			) {
				for (Map.Entry<Integer, Map<String, Map<String, String>>> partition : partitions.entrySet()) {
					for (Map.Entry<String, Map<String, String>> category :partition.getValue().entrySet()){
						for (Map.Entry<String, String> class_comment: category.getValue().entrySet()) {
							insert.setString(1, class_comment.getValue());
							insert.setString(2, class_comment.getKey());
							insert.setInt(3, partition.getKey());
							insert.setString(4, category.getKey());
							insert.executeUpdate();
						}
					}
				}
			}
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
