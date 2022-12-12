package ch.unibe.scg.comment.analysis.neon.cli;

import ch.unibe.scg.comment.analysis.neon.cli.task.*;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

public class Main {

	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) throws Exception {
		CommandLineParser parser = new DefaultParser();
		Options options = new Options();
		options.addOption(Option.builder("D").longOpt("database").required().hasArg().desc("database path").build());
		options.addOption(Option.builder("d")
				.longOpt("data")
				.required()
				.hasArgs()
				.valueSeparator(',')
				.desc("data source [pharo|java|python]")
				.build());
		options.addOption(Option.builder("t")
				.longOpt("task")
				.required()
				.hasArgs()
				.valueSeparator(',')
				.desc("task to perform, split by ',', [1-preprocess,2-split-sentences,3-map-sentences,4-partition-sentences,5-prepare-extractors,5-store-partition,6-prepare-datasets,7-prepare-experiments,8-run-experiments,9-import-experiment-results,10-build-classifiers,11-import-classifier-outputs]")
				.build());
		try {
			CommandLine line = parser.parse(options, args);
			String database = line.getOptionValue("database");
			for (String task : line.getOptionValues("task")) {
				for (String data : line.getOptionValues("data")) {
					LOGGER.info("Running {} on {}...", task, data);
					if ("1-preprocess".equals(task)) {
						(new T1Preprocess(database, data)).run();
					} else if ("2-split-sentences".equals(task)) {
						(new T2SplitSentences(database, data)).run();
					} else if ("3-map-sentences".equals(task)) {
						(new T3MapSentences(database, data)).run();
					} else if ("4-partition-sentences-workshop".equals(task)) {
						//create a fix testing and training split from the sentences and store it
						(new T4PartitionSentencesWorkshop(database, data, new int[]{80,20})).run();
					} else if ("5-prepare-extractors".equals(task)) {
						//set boolean variable true if you want to use explicit heuristic file
						(new T5PrepareExtractors(database, data, Integer.MAX_VALUE, false)).run();
					} else if ("5-prepare-extractors-workshop".equals(task)) {
						//set boolean variable true if you want to use explicit heuristic file, set number of threads to parallelize
						(new T5PrepareExtractorsWorkshop(database, data, Integer.MAX_VALUE, false)).run();
					} else if ("5-store-partition".equals(task)) {
						//store the sentences of training and testing split
						(new T5StorePartitionSentences(database, data)).run();
					} else if ("6-prepare-datasets-workshop".equals(task)) {
						//number of partitions
						(new T6PrepareDatasetWorkshop(database, data, 0,
								Paths.get(System.getProperty("user.dir"))
								.resolve("data")
								.resolve(data)
								.resolve("experiment"))).run();
					} else if ("7-prepare-experiments-workshop".equals(task)) {
						(new T7PrepareExperimentsWorkshop(
								database,
								data,
								Paths.get(System.getProperty("user.dir"))
										.resolve("data")
										.resolve(data)
										.resolve("experiment")
						)).run();
					} else if ("10-build-classifiers".equals(task)) {
						(new T10BuildClassifiers(
								data,
								Paths.get(System.getProperty("user.dir"))
										.resolve("data")
										.resolve(data)
										.resolve("experiment"),
								200
						)).run();
					} else if ("11-import-classifier-outputs".equals(task)) {
						(new T11ImportClassifierOutputs(
								database,
								data,
								Paths.get(System.getProperty("user.dir"))
										.resolve("data")
										.resolve(data)
										.resolve("experiment")
						)).run();
					} else {
						throw new IllegalArgumentException("task option is unknown");
					}
				}
			}
		} catch (ParseException | IllegalArgumentException e) {
			System.err.println(e.getMessage());
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("java -jar THIS.jar", options);
		}
	}

}
