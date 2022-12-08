package ch.unibe.scg.comment.analysis.neon.cli.task;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/** Map the sentences from comment category to the categories where it is classified to find if have unclassified sentences.
 * @Note: One sentence can belong to multiple categories and not all sentences put in the categories are full-fledged sentence.
 *
 */
public class T3MapSentences {

	private final String database;
	private final String data;

	public T3MapSentences(String database, String data) {
		this.database = database;
		this.data = data;
	}

	public void run() throws SQLException, IOException {
		try (
				Connection connection = DriverManager.getConnection("jdbc:sqlite:" + this.database);
				Statement statement = connection.createStatement()
		) {
			statement.executeUpdate("PRAGMA foreign_keys = on");
			statement.executeUpdate(Utility.resource("sql/3_sentence_mapping.sql")
					.replaceAll("\\{\\{data}}", this.data));
			statement.executeUpdate(Utility.resource("sql/3_sentence_mapping_clean.sql")
					.replaceAll("\\{\\{data}}", this.data));
			try (
					PreparedStatement insert = connection.prepareStatement("INSERT INTO " + this.data
							+ "_3_sentence_mapping (comment_sentence_id, category_sentence_id, strategy, similarity) VALUES (?, ?, ?, ?)");
					ResultSet result = statement.executeQuery("SELECT class FROM " + this.data + "_1_preprocessed")
			) {
				while (result.next()) {
					String clazz = result.getString("class");
					Map<String, Map<Integer, String>> sentences = this.sentences(connection, clazz);
					for (Map.Entry<Integer, String> commentEntry : sentences.get("comment").entrySet()) {
						int commentSentenceId = commentEntry.getKey();
						String commentSentence = commentEntry.getValue();
						for (Map.Entry<String, Map<Integer, String>> categoryEntry : sentences.entrySet()) {
							if (categoryEntry.getKey().equals("comment")) {
								continue;
							}
							for (Map.Entry<Integer, String> categorySentenceEntry : categoryEntry.getValue()
									.entrySet()) {
								int categorySentenceId = categorySentenceEntry.getKey();
								String categorySentence = categorySentenceEntry.getValue();
								if (commentSentence.equals(categorySentence)) {
									this.mapping(insert, commentSentenceId, categorySentenceId, "equals", 1.0);
								} else if (commentSentence.contains(categorySentence)) {
									this.mapping(
											insert,
											commentSentenceId,
											categorySentenceId,
											"contains",
											1.0 * categorySentence.length() / commentSentence.length()
									);
								} else if (commentSentence.replaceAll("[.!?]$", "")
										.contains(categorySentence.replaceAll("[.!?]$", ""))) {
									this.mapping(
											insert,
											commentSentenceId,
											categorySentenceId,
											"contains-stripped",
											1.0 * categorySentence.length() / commentSentence.length()
									);
								} else if (commentSentence.replaceAll("[^a-z0-9]", "")
										.contains(categorySentence.replaceAll("[^a-z0-9]", ""))) {
									this.mapping(
											insert,
											commentSentenceId,
											categorySentenceId,
											"contains-a-z-0-9",
											1.0 * categorySentence.length() / commentSentence.length()
									);
								}
							}
						}
					}
				}
			}
		}
	}

	private void mapping(
			PreparedStatement insert, int commentSentenceId, int categorySentenceId, String strategy, double similarity
	) throws SQLException {
		insert.setInt(1, commentSentenceId);
		insert.setInt(2, categorySentenceId);
		insert.setString(3, strategy);
		insert.setDouble(4, similarity);
		insert.executeUpdate();
	}

	public Map<String, Map<Integer, String>> sentences(Connection connection, String clazz) throws SQLException {
		Map<String, Map<Integer, String>> sentences = new HashMap<>();
		try (
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery(
						"SELECT id, category, sentence FROM " + this.data + "_2_sentence WHERE class = '" + clazz + "'")
		) {
			while (result.next()) {
				int id = result.getInt("id");
				String category = result.getString("category");
				String sentence = result.getString("sentence");
				if (!sentences.containsKey(category)) {
					sentences.put(category, new HashMap<>());
				}
				sentences.get(category).put(id, sentence);
			}
		}
		return sentences;
	}

}
