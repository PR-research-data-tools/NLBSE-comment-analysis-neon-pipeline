package ch.unibe.scg.comment.analysis.neon.cli.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.rules.OneR;
import weka.classifiers.rules.ZeroR;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.converters.ArffLoader;
import weka.filters.Filter;
import weka.filters.supervised.instance.ClassBalancer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Load training and testing dataset, build various classifiers, export results to csv
 */
public class T10BuildClassifiers {

	private static final Logger LOGGER = LoggerFactory.getLogger(T10BuildClassifiers.class);
	private final String data;
	private final Path directory;
	private final int threads;

	public T10BuildClassifiers(String data, Path directory, int threads) {
		super();
		this.data = data;
		this.directory = directory;
		this.threads = threads;
	}

	public void run() throws IOException, InterruptedException {
		ExecutorService executor = Executors.newFixedThreadPool(this.threads);
		Files.list(this.directory).filter(p -> p.getFileName().toString().endsWith(".arff") && p.getFileName()
						.toString()
						.startsWith("0-0-"))
				.forEach(p -> {
					String training = p.getFileName().toString().split("\\.")[0];
					String test = training.replaceAll("^0-0-", "1-0-");
					executor.submit(() -> {
						LOGGER.info("{} build classifiers {}...", this.data, training);
						try {
							// training
							ArffLoader trainingLoader = new ArffLoader();
							trainingLoader.setFile(p.toFile());
							Instances trainingInstances = trainingLoader.getDataSet();
							trainingInstances.setClassIndex(trainingInstances.numAttributes() - 1);
							// test
							ArffLoader testLoader = new ArffLoader();
							testLoader.setFile(this.directory.resolve(String.format("%s.arff", test)).toFile());
							Instances testInstances = testLoader.getDataSet();
							testInstances.setClassIndex(testInstances.numAttributes() - 1);
							/* commented out for NLBSE
							// zero rule
							this.trainAndTest(
									new ZeroR(),
									training,
									"zeror",
									new Instances(trainingInstances),
									new Instances(testInstances),
									false
							);
							// one rule
							this.trainAndTest(
									new OneR(),
									training,
									"oner",
									new Instances(trainingInstances),
									new Instances(testInstances),
									false
							);
							// naive bayes
							this.trainAndTest(
									new NaiveBayes(),
									training,
									"naivebayes",
									new Instances(trainingInstances),
									new Instances(testInstances),
									false
							);
							// j48
							this.trainAndTest(
									new J48(),
									training,
									"j48",
									new Instances(trainingInstances),
									new Instances(testInstances),
									false
							);

							 */
							// random forest
							this.trainAndTest(
									new RandomForest(),
									training,
									"randomforest",
									new Instances(trainingInstances),
									new Instances(testInstances),
									false
							);
							LOGGER.info("{} build classifiers {} done", this.data, training);
						} catch (Throwable e) {
							LOGGER.warn("{} build classifiers {} failed", this.data, training, e);
						}
					});
				});
		executor.shutdown();
		executor.awaitTermination(2, TimeUnit.HOURS);
	}

	/**
	 * build the classifier and test on the testing dataset.
	 * @param classifier classifier to build
	 * @param prefix 0-0-categoryName
	 * @param postfix classifier name
	 * @param trainingInstances training data
	 * @param testInstances testing data
	 * @param balance if class balance to use or not
	 * @throws Exception
	 */
	private void trainAndTest(
			Classifier classifier,
			String prefix,
			String postfix,
			Instances trainingInstances,
			Instances testInstances,
			boolean balance
	) throws Exception {
		classifier.buildClassifier(balance ? this.balance(trainingInstances) : trainingInstances);
		SerializationHelper.write(this.directory.resolve(String.format("%s-%s.classifier", prefix, postfix))
				.toAbsolutePath()
				.toString(), classifier);
		String output = "type,tp,fp,tn,fn,w_pr,w_re,w_f_measure\n";
		Evaluation evaluation = new Evaluation(trainingInstances);
		evaluation.evaluateModel(classifier, trainingInstances);
		output = String.format(
				"%straining,%d,%d,%d,%d,%f,%f,%f\n",
				output,
				(int) evaluation.numTruePositives(1),
				(int) evaluation.numFalsePositives(1),
				(int) evaluation.numTrueNegatives(1),
				(int) evaluation.numFalseNegatives(1),
				evaluation.weightedPrecision(),
				evaluation.weightedRecall(),
				evaluation.weightedFMeasure()
		);
		evaluation = new Evaluation(trainingInstances);
		evaluation.evaluateModel(classifier, testInstances);
		output = String.format(
				"%stest,%d,%d,%d,%d,%f,%f,%f\n",
				output,
				(int) evaluation.numTruePositives(1),
				(int) evaluation.numFalsePositives(1),
				(int) evaluation.numTrueNegatives(1),
				(int) evaluation.numFalseNegatives(1),
				evaluation.weightedPrecision(),
				evaluation.weightedRecall(),
				evaluation.weightedFMeasure()
		);
		Files.writeString(this.directory.resolve(String.format("%s-%s-outputs.csv", prefix, postfix)), output);
	}

	private Instances balance(Instances instances) throws Exception {
		ClassBalancer classBalancer = new ClassBalancer();
		classBalancer.setInputFormat(instances);
		instances = Filter.useFilter(instances, classBalancer);
		return instances;
	}

}
