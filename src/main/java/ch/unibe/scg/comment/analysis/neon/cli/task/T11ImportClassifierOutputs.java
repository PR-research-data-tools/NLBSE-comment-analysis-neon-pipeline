package ch.unibe.scg.comment.analysis.neon.cli.task;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * Import train/test classifier results to database
 * Warning: by default weka consider first class ({0 or a} in this case) as a target class.
 * Make sure to verify the confusion matrix (tp,fp,fn,tn) interpretation.
 */
public class T11ImportClassifierOutputs {

	private final String database;
	private final String data;
	private final Path directory;

	public T11ImportClassifierOutputs(String database, String data, Path directory) {
		super();
		this.database = database;
		this.data = data;
		this.directory = directory;
	}

	public void run() throws IOException, SQLException {
		try (
				Connection connection = DriverManager.getConnection("jdbc:sqlite:" + this.database);
				Statement statement = connection.createStatement()
		) {
			statement.executeUpdate("PRAGMA foreign_keys = on");
			statement.executeUpdate(Utility.resource("sql/11_classifier_outputs.sql")
					.replaceAll("\\{\\{data}}", this.data));
			try (
					PreparedStatement insert = connection.prepareStatement("INSERT INTO " + this.data
							+ "_11_classifier_outputs (category,classifier,features_tfidf,features_heuristic,type,tp,fp,tn,fn,w_pr,w_re,w_f_measure) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?,?,?,?)")
			) {
				for (String prefix : Files.list(this.directory)
						.filter(p -> p.getFileName().toString().endsWith("-outputs.csv"))
						.map(p -> p.getFileName().toString().split("\\.")[0])
						.collect(Collectors.toList())) {
					String[] parts = prefix.split("-");
					String category = parts[2];
					boolean tfidf = parts[3].equals("tfidf");
					boolean heuristic = parts.length == 7 ? parts[4].equals("heuristic") : parts[3].equals("heuristic");
					String classifier = parts.length == 7 ? parts[5] : parts[4];
					try (
							CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader()
									.parse(Files.newBufferedReader(this.directory.resolve(String.format("%s.csv",
											prefix
									))))
					) {
						for (CSVRecord record : parser) {
							insert.setString(1, category);
							insert.setString(2, classifier);
							insert.setInt(3, tfidf ? 1 : 0);
							insert.setInt(4, heuristic ? 1 : 0);
							insert.setString(5, record.get("type"));
							insert.setInt(6, (int) Double.parseDouble(record.get("tp")));
							insert.setInt(7, (int) Double.parseDouble(record.get("fp")));
							insert.setInt(8, (int) Double.parseDouble(record.get("tn")));
							insert.setInt(9, (int) Double.parseDouble(record.get("fn")));
							insert.setDouble(10,
									record.get("w_pr") == null
											? null
											: Double.parseDouble(record.get("w_pr"))
							);
							insert.setDouble(11,
									record.get("w_re") == null
											? null
											: Double.parseDouble(record.get("w_re"))
							);
							insert.setDouble(12,
									record.get("w_f_measure") == null
											? null
											: Double.parseDouble(record.get("w_f_measure"))
							);
							insert.executeUpdate();
						}
					}
				}
			}
		}
	}

}
