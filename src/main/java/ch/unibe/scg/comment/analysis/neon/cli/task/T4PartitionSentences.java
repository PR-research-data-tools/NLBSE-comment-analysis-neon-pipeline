package ch.unibe.scg.comment.analysis.neon.cli.task;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Prepare the partitions from the whole dataset.
 * @partitions number of partitions to create. Currently we have training (0_0) and testing (1_0) split.
 */
public class T4PartitionSentences {

	private final String database;
	private final String data;
	private final int[] partitions;

	public T4PartitionSentences(String database, String data, int[] partitions) {
		this.database = database;
		this.data = data;
		this.partitions = partitions;
	}

	public void run() throws SQLException, IOException {
		try (
				Connection connection = DriverManager.getConnection("jdbc:sqlite:" + this.database);
				Statement statement = connection.createStatement()
		) {
			statement.executeUpdate("PRAGMA foreign_keys = on");
			statement.executeUpdate(Utility.resource("sql/4_sentence_partition.sql")
					.replaceAll("\\{\\{data}}", this.data));
			try (
					PreparedStatement insert = connection.prepareStatement("INSERT INTO " + this.data
							+ "_4_sentence_partition (comment_sentence_id, partition) VALUES (?, ?)");
					ResultSet result = statement.executeQuery(
							"SELECT comment_sentence_id, stratum, category FROM " + this.data
									+ "_3_sentence_mapping_clean")
			) {
				// build strata distributions from raw data
				Map<Integer, Map<String, Set<Integer>>> strata = new HashMap<>();

				while (result.next()) {
					int id = result.getInt("comment_sentence_id");
					int stratum = result.getInt("stratum");
					String category = result.getString("category");
					if (!strata.containsKey(stratum)) {
						strata.put(stratum, new HashMap<>());
					}
					if (!strata.get(stratum).containsKey(category)) {
						strata.get(stratum).put(category, new HashSet<>());
					}
					strata.get(stratum).get(category).add(id);

				}
				// round-robin, fill partitions from strata, one at a time
				// select from stratum and smallest categories to achieve best balancing
				for (Map<String, Set<Integer>> stratum : strata.values()) {
					// treat strata as independent
					while (!stratum.isEmpty()) {
						// take the category with the smallest population in a stratum
						Set<Integer> minCategory = this.minCategory(stratum);
						while (!minCategory.isEmpty()) {
							//assign to partitions based on proportion of the training and testing
							int total_instances =  minCategory.size();
							int training_proportion = (int) Math.ceil((this.partitions[0] * total_instances)/100.0f);
							int testing_proportion = (int) Math.ceil((this.partitions[1] * total_instances)/100.0f);
							int[] partitions_ = new int[]{training_proportion,testing_proportion};

							// assign to partition in round-robin fashion, partition by partition...
							//int[] partitions_ = this.partitions.clone();
							int cursor = 0;
							while (Arrays.stream(partitions_).sum() > 0) {
								// ...until no partition wants any more
								if (partitions_[cursor] > 0) {
									//Optional<Integer> id = this.random(minCategory); //select a random id from the minCategory
									Optional<Integer> id = this.getFirstElement(minCategory); //select first comment_id from the minCategory to have fix training and test split
									if (id.isPresent()) {
										// might have exhausted population
										this.removeSentence(stratum, id.get());
										insert.setInt(1, id.get());
										insert.setInt(2, cursor);
										insert.executeUpdate();
									}
									// even if we did not get any, there is nothing more to get, pretend we took something
									partitions_[cursor] = partitions_[cursor] - 1;
								}
								// advance to next partition
								cursor = (cursor + 1) % partitions_.length;
							}
						}
					}
				}
			}
		}
	}

	private Set<Integer> minCategory(Map<String, Set<Integer>> stratum) {
		return stratum.values().stream().sorted(Comparator.comparingInt(Set::size)).findFirst().get();
	}

	private void removeSentence(Map<String, Set<Integer>> stratum, Integer id) {
		Iterator<Map.Entry<String, Set<Integer>>> iterator = stratum.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, Set<Integer>> entry = iterator.next();
			entry.getValue().remove(id);
			if (entry.getValue().isEmpty()) {
				iterator.remove();
			}
		}
	}

	private <E> Optional<E> random(Collection<E> e) {
		return e.stream().skip((int) (e.size() * Math.random())).findFirst();
	}

	private <E> Optional<E> getFirstElement(Collection<E> e) {
		return e.stream().findFirst();
	}
}
