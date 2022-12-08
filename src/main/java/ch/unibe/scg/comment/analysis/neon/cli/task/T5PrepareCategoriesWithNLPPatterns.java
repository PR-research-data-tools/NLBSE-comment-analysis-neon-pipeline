package ch.unibe.scg.comment.analysis.neon.cli.task;

import org.neon.model.Condition;
import org.neon.model.Heuristic;
import org.neon.pathsFinder.engine.Parser;
import org.neon.pathsFinder.engine.PathsFinder;
import org.neon.pathsFinder.model.GrammaticalPath;
import org.neon.pathsFinder.model.Sentence;

import java.io.IOException;
import java.sql.Array;
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
import java.util.stream.Collectors;

public class T5PrepareCategoriesWithNLPPatterns {

	private final String database;
	private final String data;

	public T5PrepareCategoriesWithNLPPatterns(String database, String data) {
		this.database = database;
		this.data = data;
	}

	public void run() throws SQLException, IOException {
		try (
				Connection connection = DriverManager.getConnection("jdbc:sqlite:" + this.database); Statement statement = connection.createStatement()
		) {
			statement.executeUpdate("PRAGMA foreign_keys = on");
			statement.executeUpdate(Utility.resource("sql/5_category_heuristic_mapping.sql")
					.replaceAll("\\{\\{data}}", this.data));
			PreparedStatement insert = connection.prepareStatement("INSERT INTO " + this.data + "_5_category_heuristic_mapping (category, heuristics) VALUES (?, ?)");

			Map<String, List<String>> categoryMappingSentences = new HashMap<>();
			Map<String, List<String>> heuristicsMapping = new HashMap<>();
			ArrayList<Heuristic> heuristics = new ArrayList<>();
			ArrayList<String> patterns = new ArrayList<>();

			for (String category : this.categories(statement)) {
				try (
						ResultSet result = statement.executeQuery(
								"SELECT category, comment_sentence FROM " + this.data + "_3_sentence_mapping_clean WHERE category = \"" + category
										+ "\"");
				) {
					while (result.next()) {
						if (!categoryMappingSentences.containsKey(category)) {
							categoryMappingSentences.put(category, new ArrayList<>());
						}
							categoryMappingSentences.get(category).add(result.getString("comment_sentence"));
					}
				}
			}

			for (Map.Entry<String, List<String>> aCategory : categoryMappingSentences.entrySet()) {
				if(!heuristicsMapping.containsKey(aCategory.getKey())){
					heuristicsMapping.put(aCategory.getKey(), new ArrayList<>());
				}
				heuristics.addAll(this.heuristics(aCategory.getKey(), aCategory.getValue()));

				for(Heuristic anHeuristic : heuristics)
				{
					String aPattern = anHeuristic.getText();
					if(!aPattern.isEmpty())
					{
						patterns.add(aPattern);
					}
				}

				heuristicsMapping.put(aCategory.getKey(),patterns);
				for (int i = 0; i <patterns.size();i++)
				{
					insert.setString(1,aCategory.getKey());
					insert.setString(2, patterns.get(i));
					insert.execute();

				}

			}
		}
	}

	/**
	 * get the heuristics for each category using NEON
	 * @param category a category from the taxonomy
	 * @param entries all sentences of the category
	 * @return heuristics for the category collected from NEON
	 */
	private ArrayList<Heuristic> heuristics(String category, List<String> entries) {
		ArrayList<Sentence> sentences = Parser.getInstance().parse(String.join("\n\n", entries)); //sentences: a list of each sentence with its type (declarative|Interrogative) and graph received from NEON, graph: morphology analysis of the sentence.
		ArrayList<GrammaticalPath> paths = PathsFinder.getInstance().discoverCommonPaths(sentences); //minimized identical path identified from all the sentences heuristic by comparing their conditions
		return paths.stream().map(p -> {
			Heuristic heuristic = new Heuristic();
			heuristic.setConditions(p.getConditions().stream().map(s -> {
				Condition condition = new Condition();
				condition.setConditionString(s);
				return condition;
			}).collect(Collectors.toCollection(ArrayList::new)));
			heuristic.setType(p.getDependenciesPath());
			heuristic.setSentence_type(p.identifySentenceType());
			heuristic.setText(p.getTemplateText());
			heuristic.setSentence_class(category);
			return heuristic;
		}).collect(Collectors.toCollection(ArrayList::new));
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
