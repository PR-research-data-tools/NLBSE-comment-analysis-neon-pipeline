package ch.unibe.scg.comment.analysis.neon.cli.task;

import ch.unibe.scg.comment.analysis.neon.cli.InstancesBuilder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Preprocess the comments using @Link{InstanceBuilder}
 *
 */
public class T1Preprocess {

	private final String database;
	private final String data;

	public T1Preprocess(String database, String data) {
		this.database = database;
		this.data = data;
	}

	public void run() throws SQLException {
		try (
				Connection connection = DriverManager.getConnection("jdbc:sqlite:" + this.database);
				Statement statement = connection.createStatement()
		) {
			statement.executeUpdate("PRAGMA foreign_keys = on");
			statement.executeUpdate("CREATE TABLE " + this.data + "_1_preprocessed AS SELECT * FROM " + this.data
					+ "_0_raw WHERE 1 = 0");
			List<String> categories = this.categories(statement);
			try (
					ResultSet result = statement.executeQuery("SELECT * FROM " + this.data + "_0_raw");
					PreparedStatement insert = this.insert(connection, categories)
			) {
				while (result.next()) {
					String clazz = result.getString("class");
					int stratum = result.getInt("stratum");
					String comment = result.getString("comment");
					insert.setString(1, clazz);
					insert.setInt(2, stratum);
					insert.setString(3, InstancesBuilder.preprocess(comment)); //preprocess the comment
					for (int i = 0; i < categories.size(); i = i + 1) {
						insert.setString(4 + i, InstancesBuilder.preprocess(result.getString(4 + i)));
					}
					insert.executeUpdate();
				}
			}
		}
	}

	private PreparedStatement insert(Connection connection, List<String> categories) throws SQLException {
		return connection.prepareStatement(
				"INSERT INTO " + this.data + "_1_preprocessed (class, stratum, comment, " + String.join(",",
						categories.stream().map(c -> String.format("\"%s\"", c)).collect(Collectors.toList())
				) + ") VALUES (?, ?, ?, " + String.join(",",
						categories.stream().map(c -> "?").collect(Collectors.toList())
				) + ")");
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
