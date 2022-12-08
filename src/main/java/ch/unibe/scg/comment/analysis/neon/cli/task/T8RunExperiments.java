package ch.unibe.scg.comment.analysis.neon.cli.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.experiment.Experiment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Load all training (0-0-categoryName_feature) arff files, and train the mentioned classifiers in experiment.xml.
 * Configures/Runs the Experiment (experiment.xml) for each feature set and set input and outfile .xml file.
 * @data language under analysis
 * @directory directory of the dataset where arff files and other intermediate data is saved
 */
public class T8RunExperiments {

	private static final Logger LOGGER = LoggerFactory.getLogger(T8RunExperiments.class);
	private final String data;
	private final Path directory;
	private final int threads;

	public T8RunExperiments(String data, Path directory, int threads) {
		super();
		this.data = data;
		this.directory = directory;
		this.threads = threads;
	}

	public void run() throws IOException, InterruptedException, SQLException {
		String experimentConfigurationTemplate = Utility.resource("WithoutClassBal-ChangedOutClass.xml");
		ExecutorService executor = Executors.newFixedThreadPool(this.threads);
		// prefix: <training/testing-category-featureset> eg., 0-0-licensecopyright-tfidf
		for (String prefix : Files.list(this.directory)
				.filter(p -> p.getFileName().toString().endsWith(".arff") && p.getFileName()
						.toString()
						.startsWith("0-0-"))
				.map(p -> p.getFileName().toString().split("\\.")[0])
				.collect(Collectors.toList())) {
			// save experimenter configuration for each category
			Path configuration = this.directory.resolve(Paths.get(String.format("%s.xml", prefix)));
			Files.writeString(configuration,
					experimentConfigurationTemplate.replaceAll("\\{\\{prefix}}",
							this.directory.resolve(prefix).toAbsolutePath().toString()
					)
							.replaceAll("\\{\\{numSlots}}", "4")
			);
			executor.submit(() -> {
				LOGGER.info("{} training {}...", this.data, prefix);
				try {
					// Configures/Runs the Experiment from the command line.
					Experiment.main(new String[]{"-l", configuration.toFile().getAbsolutePath(), "-r"});
					LOGGER.info("{} training {} finished", this.data, prefix);
				} catch (Throwable e) {
					LOGGER.warn("{} training {} failed", this.data, prefix, e);
				}
			});
		}
		executor.shutdown();
		executor.awaitTermination(2, TimeUnit.HOURS);
	}

}
