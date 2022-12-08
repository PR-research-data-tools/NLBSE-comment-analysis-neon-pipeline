package ch.unibe.scg.comment.analysis.neon.cli.task;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Prepare the partitions based on a different logic than T4PartitionSentences
 * @partitions number of partitions to create. Currently, we have training (0_0) and testing (1_0) split.
 */
public class T4PartitionSentencesWorkshop {
	private final String database;
	private final String data;
	private final int[] partitions;

	public T4PartitionSentencesWorkshop(String database, String data, int[] partitions) {
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
			statement.executeUpdate(Utility.resource("sql/4_sentence_partition_workshop.sql")
					.replaceAll("\\{\\{data}}", this.data));
			Map<String, Set<String>> otherCategories = new HashMap<>();
			List<String> categories = this.categories(statement);
			//find other categories than the category to make positive and negative instances
			for(String category: categories){
				List<String> other_categories = findOtherCategories(categories, category);
				if(!otherCategories.containsKey(category)){
					otherCategories.put(category, new HashSet<>(other_categories));
				}
			}
			try (
					PreparedStatement insert = connection.prepareStatement("INSERT INTO " + this.data
							+ "_4_sentence_partition_workshop (comment_sentence_id, partition, category, instance_type) VALUES (?, ?, ?, ?)");
					ResultSet result = statement.executeQuery(
							"SELECT comment_sentence_id, stratum, category FROM " + this.data
									+ "_3_sentence_mapping_clean")
			) {

				//key=category, value = {key=id, value = stratum}
				Map<String, Map<Integer, Integer>> positives = new HashMap<>();
				Map<String, Map<Integer, Integer>> negatives = new HashMap<>();

				while (result.next()) {
					int id = result.getInt("comment_sentence_id");
					int stratum = result.getInt("stratum");
					String category = result.getString("category");

					//build positive instances
					if (!positives.containsKey(category)) {
						positives.put(category, new HashMap<>());
					}
					if (!positives.get(category).containsKey(id)) {
						positives.get(category).put(id, stratum);
					}
				}

				for(Map.Entry<String, Map<Integer, Integer>> a_category: positives.entrySet()){
					if(!negatives.containsKey(a_category.getKey())){
						negatives.put(a_category.getKey(), new HashMap<>());
					}

					List<String> other_categories = findOtherCategories(categories, a_category.getKey());
					//put all other categories sentences (that do not match with positive instance of the category as negative instances
					for(String other_category: other_categories){
						for(Integer _id: positives.get(other_category).keySet()){
							//if the id already belongs to the positive, do not add it to the negative
							if(!positives.get(a_category.getKey()).containsKey(_id)){
								negatives.get(a_category.getKey()).
										put(_id,positives.get(other_category).get(_id));
							}
						}
					}
				}

				//transform positives (1) and negatives (0) into partitions_category
				HashMap<String, Map<String, Map<Integer, Set<Integer>>>> positives_negatives = new HashMap<>();

				positives_negatives = transform(positives_negatives,positives,categories,"1");
				positives_negatives = transform(positives_negatives,negatives,categories,"0");

				// round-robin, fill partitions from strata, one at a time
				// select from the smallest stratum for each category to achieve best balancing
				for(Map.Entry<String, Map<String, Map<Integer, Set<Integer>>>> instance_type: positives_negatives.entrySet()){
					for(Map.Entry<String, Map<Integer, Set<Integer>>> aCategory: instance_type.getValue().entrySet()){
						for (Map.Entry<Integer, Set<Integer>> strata : aCategory.getValue().entrySet()) {
							// treat strata as independent
							while(!strata.getValue().isEmpty()) {
								//assign to partitions based on proportion of the training and testing
								int total_instances = strata.getValue().size();
								int training_proportion = (int) Math.ceil((this.partitions[0] * total_instances) / 100.0f);
								int testing_proportion = (int) Math.ceil((this.partitions[1] * total_instances) / 100.0f);
								int[] partitions_ = new int[]{training_proportion, testing_proportion};
								int cursor = 0;
								while (Arrays.stream(partitions_).sum() > 0) {
									// ...until no partition wants any more
									if (partitions_[cursor] > 0) {
										Optional<Integer> id = this.getFirstElement(strata.getValue()); //select first comment_id from the stratum to have fix training and test split
										if (id.isPresent()) {
											// might have exhausted population
											this.removeSentence(strata.getValue(), id.get());
											insert.setInt(1, id.get());
											insert.setInt(2, cursor);
											insert.setString(3, aCategory.getKey());
											insert.setString(4, instance_type.getKey());
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
	}

	private HashMap<String, Map<String, Map<Integer, Set<Integer>>>> transform(
			HashMap<String, Map<String, Map<Integer, Set<Integer>>>> positives_negatives,
			Map<String, Map<Integer, Integer>> positives,
			List<String> categories,
			String instance_type) {
		//for each category, swap its stratum and values to have the same format for the partitioning
		for(String aCategory: categories){
			for(Map.Entry<Integer, Integer> sentences_stratum: positives.get(aCategory).entrySet()){
				Integer stratum_id = sentences_stratum.getValue();
				Integer sentence_id = sentences_stratum.getKey();

				if(!positives_negatives.containsKey(instance_type)){
					positives_negatives.put(instance_type, new HashMap<>());
				}
				if(!positives_negatives.get(instance_type).containsKey(aCategory)){
					positives_negatives.get(instance_type).put(aCategory, new HashMap<>());
				}
				if(!positives_negatives.get(instance_type).get(aCategory).containsKey(stratum_id)){
					positives_negatives.get(instance_type).get(aCategory).put(stratum_id, new HashSet<>());
				}
				positives_negatives.get(instance_type).get(aCategory).get(stratum_id).add(sentence_id);
			}
		}
		return positives_negatives;
	}

	private List<String> findOtherCategories(List<String> categories, String category) {
		ArrayList<String> otherCategories = new ArrayList<>(List.copyOf(categories));
		otherCategories.remove(category);
		return otherCategories;
	}

	private void removeSentence(Set<Integer> strata, Integer id) {
		Iterator<Integer> iterator = strata.iterator();
		while (iterator.hasNext()) {
			Integer entry = iterator.next();
			if(entry == id){
				iterator.remove();
				break;
			}
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

	private <E> Optional<E> getFirstElement(Collection<E> e) {
		return e.stream().findFirst();
	}
}
