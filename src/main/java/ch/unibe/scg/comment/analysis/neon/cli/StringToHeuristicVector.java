package ch.unibe.scg.comment.analysis.neon.cli;

import org.neon.engine.Parser;
import org.neon.engine.XMLReader;
import org.neon.model.Heuristic;
import weka.core.Attribute;
import weka.core.Capabilities;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SparseInstance;
import weka.filters.SimpleStreamFilter;
import weka.filters.UnsupervisedFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class StringToHeuristicVector extends SimpleStreamFilter implements UnsupervisedFilter {

	private final Parser parser;
	private int index;
	private List<String> categories;
	private File heuristics;
	private Instances outputFormat;
	private List<String> featureNames;

	public StringToHeuristicVector() {
		super();
		this.parser = Parser.getInstance();
		this.index = -1;
	}

	public List<String> getCategories() {
		return this.categories;
	}

	public void setCategories(List<String> categories) {
		this.categories = categories;
	}

	public File getHeuristics() {
		return this.heuristics;
	}

	public void setHeuristics(File heuristics) {
		this.heuristics = heuristics;
	}

	@Override
	public String globalInfo() {
		return "Converts the string attribute called 'text' to heuristics using the provided heuristics file for NEON and categories";
	}

	@Override
	public Capabilities getCapabilities() {
		Capabilities result = super.getCapabilities();
		result.disableAll();
		// attributes
		result.enableAllAttributes();
		result.enable(Capabilities.Capability.MISSING_VALUES);
		// class
		result.enableAllClasses();
		result.enable(Capabilities.Capability.MISSING_CLASS_VALUES);
		result.enable(Capabilities.Capability.NO_CLASS);
		return result;
	}

	@Override
	protected Instances determineOutputFormat(Instances inputFormat) throws Exception {
		if (inputFormat.attribute("text") == null) {
			return inputFormat;
		}
		if (this.heuristics == null) {
			throw new Exception("No heuristics file specified!");
		}
		if (this.categories == null) {
			throw new Exception("No categories specified!");
		}
		this.index = inputFormat.attribute("text").index();
		this.featureNames = this.featureNames();
		ArrayList<Attribute> newAttributes = new ArrayList<>();
		for (int i = 0; i < inputFormat.numAttributes(); i = i + 1) {
			newAttributes.add((Attribute) inputFormat.attribute(i).copy());
		}
		for (String name : this.featureNames) {
			newAttributes.add(new Attribute(name, List.of("0", "1")));
		}
		this.outputFormat = new Instances(inputFormat.relationName(), newAttributes, inputFormat.numInstances());
		return this.outputFormat;
	}

	@Override
	protected Instance process(Instance instance) throws Exception {
		if (this.index == -1) {
			return instance;
		}
		Set<String> matchedFeatureNames = this.matchedFeatureNames(instance.stringValue(this.index));
		int numAttributes = instance.numAttributes();
		double[] values = new double[numAttributes + this.featureNames.size()];
		int i = 0;
		for (; i < numAttributes; i = i + 1) {
			values[i] = instance.value(i);
		}
		for (; i < values.length; i = i + 1) {
			values[i] = matchedFeatureNames.contains(this.featureNames.get(i - numAttributes)) ? 1 : 0;
		}
		instance = new SparseInstance(instance.weight(), values);
		instance.setDataset(this.outputFormat);
		return instance;
	}

	private Set<String> matchedFeatureNames(String text) {
		return this.parser.extract(text, this.heuristics)
				.stream()
				.map(r -> this.featureName(this.category(r.getSentenceClass()), r.getHeuristic()))
				.collect(Collectors.toSet());
	}

	private List<String> featureNames() {
		return XMLReader.read(this.heuristics)
				.stream()
				.collect(Collectors.groupingBy(Heuristic::getText,
						Collectors.mapping(h -> this.category(h.getSentence_class()), Collectors.toSet())
				))
				.entrySet()
				.stream()
				.flatMap(e -> e.getValue().stream().map(c -> this.featureName(c, e.getKey())))
				.collect(Collectors.toSet())
				.stream()
				.sorted()
				.collect(Collectors.toList());
	}

	/**
	 * @return "heuristic-[category]-[heuristic]"
	 */
	private String featureName(String category, String heuristic) {
		return String.format("heuristic-%s-%s", category, heuristic);
	}

	/**
	 * Finds the category matching the heuristic class. As NEON processes labels, normalization is required.
	 *
	 * @param heuristicClass
	 * @return
	 */
	private String category(String heuristicClass) {
		return this.categories.stream()
				.filter(c -> this.normalize(c).equals(this.normalize(heuristicClass)))
				.findFirst()
				.get();
	}

	private String normalize(String s) {
		return s.toLowerCase().replaceAll("[^a-z0-9]", "");
	}

}
