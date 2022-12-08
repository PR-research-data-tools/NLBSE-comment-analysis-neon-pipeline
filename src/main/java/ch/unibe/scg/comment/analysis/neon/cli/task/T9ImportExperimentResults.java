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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Import experiment results of Cross validation to the database
 */
public class T9ImportExperimentResults {

	private final String database;
	private final String data;
	private final Path directory;

	public T9ImportExperimentResults(String database, String data, Path directory) {
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
			statement.executeUpdate(Utility.resource("sql/9_experiment_results.sql")
					.replaceAll("\\{\\{data}}", this.data));
			List<String> columns = this.columns(statement);
			try (
					PreparedStatement insert = this.insert(connection, columns)
			) {
				for (String prefix : Files.list(this.directory)
						.filter(p -> p.getFileName().toString().endsWith(".arff") && p.getFileName()
								.toString()
								.startsWith("0-0-"))
						.map(p -> p.getFileName().toString().split("\\.")[0])
						.collect(Collectors.toList())) {
					this.insert(insert, prefix);
				}
			}
		}
	}

	private void insert(PreparedStatement insert, String prefix) throws SQLException, IOException {
		String[] parts = prefix.split("-");
		String category = parts[2];
		boolean tfidf = parts[3].equals("tfidf");
		boolean heuristic = parts.length == 5 ? parts[4].equals("heuristic") : parts[3].equals("heuristic");
		try (
				CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader()
						.parse(Files.newBufferedReader(this.directory.resolve(String.format("%s-results.csv", prefix))))
		) {
			for (CSVRecord record : parser) {
				insert.setString(1, record.get("Key_Dataset"));
				insert.setInt(2, (int) Double.parseDouble(record.get("Key_Run")));
				insert.setInt(3, (int) Double.parseDouble(record.get("Key_Fold")));
				insert.setString(4, record.get("Key_Scheme"));
				insert.setString(5, record.get("Key_Scheme_options"));
				insert.setInt(6, (int) Double.parseDouble(record.get("Key_Scheme_version_ID")));
				insert.setDouble(7, Double.parseDouble(record.get("Date_time")));
				insert.setInt(8, (int) Double.parseDouble(record.get("Number_of_training_instances")));
				insert.setInt(9, (int) Double.parseDouble(record.get("Number_of_testing_instances")));
				insert.setInt(10, (int) Double.parseDouble(record.get("Number_correct")));
				insert.setInt(11, (int) Double.parseDouble(record.get("Number_incorrect")));
				insert.setInt(12, (int) Double.parseDouble(record.get("Number_unclassified")));
				insert.setDouble(13, Double.parseDouble(record.get("Percent_correct")));
				insert.setDouble(14, Double.parseDouble(record.get("Percent_incorrect")));
				insert.setDouble(15, Double.parseDouble(record.get("Percent_unclassified")));
				insert.setDouble(16, Double.parseDouble(record.get("Kappa_statistic")));
				insert.setDouble(17, Double.parseDouble(record.get("Mean_absolute_error")));
				insert.setDouble(18, Double.parseDouble(record.get("Root_mean_squared_error")));
				insert.setDouble(19, Double.parseDouble(record.get("Relative_absolute_error")));
				insert.setDouble(20, Double.parseDouble(record.get("Root_relative_squared_error")));
				insert.setDouble(21, Double.parseDouble(record.get("SF_prior_entropy")));
				insert.setDouble(22, Double.parseDouble(record.get("SF_scheme_entropy")));
				insert.setDouble(23, Double.parseDouble(record.get("SF_entropy_gain")));
				insert.setDouble(24, Double.parseDouble(record.get("SF_mean_prior_entropy")));
				insert.setDouble(25, Double.parseDouble(record.get("SF_mean_scheme_entropy")));
				insert.setDouble(26, Double.parseDouble(record.get("SF_mean_entropy_gain")));
				insert.setDouble(27, Double.parseDouble(record.get("KB_information")));
				insert.setDouble(28, Double.parseDouble(record.get("KB_mean_information")));
				insert.setDouble(29, Double.parseDouble(record.get("KB_relative_information")));
				insert.setDouble(30,
						record.get("True_positive_rate") == null
								? null
								: Double.parseDouble(record.get("True_positive_rate"))
				);
				insert.setInt(31, (int) Double.parseDouble(record.get("Num_true_positives")));
				insert.setDouble(32,
						record.get("False_positive_rate") == null
								? null
								: Double.parseDouble(record.get("False_positive_rate"))
				);
				insert.setInt(33, (int) Double.parseDouble(record.get("Num_false_positives")));
				insert.setDouble(34,
						record.get("True_negative_rate") == null
								? null
								: Double.parseDouble(record.get("True_negative_rate"))
				);
				insert.setInt(35, (int) Double.parseDouble(record.get("Num_true_negatives")));
				insert.setDouble(36,
						record.get("False_negative_rate") == null
								? null
								: Double.parseDouble(record.get("False_negative_rate"))
				);
				insert.setInt(37, (int) Double.parseDouble(record.get("Num_false_negatives")));
				insert.setDouble(38,
						record.get("IR_precision") == null
								? null
								: Double.parseDouble(record.get("IR_precision"))
				);
				insert.setDouble(39,
						record.get("IR_recall") == null
								? null
								: Double.parseDouble(record.get("IR_recall"))
				);
				insert.setDouble(40,
						record.get("F_measure") == null
								? null
								: Double.parseDouble(record.get("F_measure"))
				);
				insert.setDouble(41,
						record.get("Matthews_correlation") == null
								? null
								: Double.parseDouble(record.get("Matthews_correlation"))
				);
				insert.setDouble(42,
						record.get("Area_under_ROC") == null
								? null
								: Double.parseDouble(record.get("Area_under_ROC"))
				);
				insert.setDouble(43,
						record.get("Area_under_PRC") == null
								? null
								: Double.parseDouble(record.get("Area_under_PRC"))
				);
				insert.setDouble(44,
						record.get("Weighted_avg_true_positive_rate") == null
								? null
								: Double.parseDouble(record.get("Weighted_avg_true_positive_rate"))
				);
				insert.setDouble(45,
						record.get("Weighted_avg_false_positive_rate") == null
								? null
								: Double.parseDouble(record.get("Weighted_avg_false_positive_rate"))
				);
				insert.setDouble(46,
						record.get("Weighted_avg_true_negative_rate") == null
								? null
								: Double.parseDouble(record.get("Weighted_avg_true_negative_rate"))
				);
				insert.setDouble(47,
						record.get("Matthews_correlation") == null
								? null
								: Double.parseDouble(record.get("Weighted_avg_false_negative_rate"))
				);
				insert.setDouble(48,
						record.get("Weighted_avg_IR_precision") == null
								? null
								: Double.parseDouble(record.get("Weighted_avg_IR_precision"))
				);
				insert.setDouble(49,
						record.get("Weighted_avg_IR_recall") == null
								? null
								: Double.parseDouble(record.get("Weighted_avg_IR_recall"))
				);
				insert.setDouble(50,
						record.get("Weighted_avg_F_measure") == null
								? null
								: Double.parseDouble(record.get("Weighted_avg_F_measure"))
				);
				insert.setDouble(51,
						record.get("Weighted_avg_matthews_correlation") == null
								? null
								: Double.parseDouble(record.get("Weighted_avg_matthews_correlation"))
				);
				insert.setDouble(52,
						record.get("Weighted_avg_area_under_ROC") == null
								? null
								: Double.parseDouble(record.get("Weighted_avg_area_under_ROC"))
				);
				insert.setDouble(53,
						record.get("Weighted_avg_area_under_PRC") == null
								? null
								: Double.parseDouble(record.get("Weighted_avg_area_under_PRC"))
				);
				insert.setDouble(54,
						record.get("Unweighted_macro_avg_F_measure") == null
								? null
								: Double.parseDouble(record.get("Unweighted_macro_avg_F_measure"))
				);
				insert.setDouble(55,
						record.get("Unweighted_micro_avg_F_measure") == null
								? null
								: Double.parseDouble(record.get("Unweighted_micro_avg_F_measure"))
				);
				insert.setDouble(56, Double.parseDouble(record.get("Elapsed_Time_training")));
				insert.setDouble(57, Double.parseDouble(record.get("Elapsed_Time_testing")));
				insert.setDouble(58, Double.parseDouble(record.get("UserCPU_Time_training")));
				insert.setDouble(59, Double.parseDouble(record.get("UserCPU_Time_testing")));
				insert.setInt(60, (int) Double.parseDouble(record.get("UserCPU_Time_millis_training")));
				insert.setInt(61, (int) Double.parseDouble(record.get("UserCPU_Time_millis_testing")));
				insert.setInt(62, (int) Double.parseDouble(record.get("Serialized_Model_Size")));
				insert.setInt(63, (int) Double.parseDouble(record.get("Serialized_Train_Set_Size")));
				insert.setInt(64, (int) Double.parseDouble(record.get("Serialized_Test_Set_Size")));
				insert.setDouble(65, Double.parseDouble(record.get("Coverage_of_Test_Cases_By_Regions")));
				insert.setDouble(66, Double.parseDouble(record.get("Size_of_Predicted_Regions")));
				insert.setString(67, record.get("Summary"));
				insert.setInt(68, tfidf ? 1 : 0);
				insert.setInt(69, heuristic ? 1 : 0);
				insert.setString(70, category);
				insert.executeUpdate();
			}
		}
	}

	private List<String> columns(Statement statement) throws SQLException {
		List<String> columns = new ArrayList<>();
		try (
				ResultSet result = statement.executeQuery(
						"SELECT name FROM PRAGMA_TABLE_INFO('" + this.data + "_9_experiment_results')")
		) {
			while (result.next()) {
				columns.add(result.getString("name"));
			}
		}
		return columns;
	}

	private PreparedStatement insert(Connection connection, List<String> columns) throws SQLException {
		return connection.prepareStatement("INSERT INTO " + this.data + "_9_experiment_results (" + String.join(",",
				columns.stream().map(c -> String.format("\"%s\"", c)).collect(Collectors.toList())
		) + ") VALUES (" + String.join(",", columns.stream().map(c -> "?").collect(Collectors.toList())) + ")");
	}

}
