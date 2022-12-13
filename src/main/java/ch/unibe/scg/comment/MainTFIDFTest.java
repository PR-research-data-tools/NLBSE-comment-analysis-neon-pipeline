package ch.unibe.scg.comment;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.DictionaryBuilder;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.core.stemmers.IteratedLovinsStemmer;
import weka.core.stopwords.Rainbow;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.FixedDictionaryStringToWordVector;
import weka.filters.unsupervised.attribute.StringToWordVector;

public class MainTFIDFTest {

    public static void main(String[] args) throws Exception {

        List<String> trainingSet = Arrays.asList(
                "rain today",
                "Today outside",
                "watch season premiere",
                "Today day today today",
                "movie premiers");

        List<String> testingSet = Arrays.asList(
                "Today oscar today today today",
                "Today oscar today today",
                "Today oscar today",
                "Today oscar");

        // -------

        // create training instances

        ArrayList<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("text", (List<String>) null, null));
        Instances trainingInstances = new Instances("prepare", attributes, trainingSet.size());
        for (String sentence : trainingSet) {
            DenseInstance instance = new DenseInstance(1);
            instance.setValue(trainingInstances.attribute("text"), sentence);
            trainingInstances.add(instance);
        }

        // -------

        // create testing instances

        attributes = new ArrayList<>();
        attributes.add(new Attribute("text", (List<String>) null, null));
        Instances testingInstances = new Instances("prepare", attributes, trainingSet.size());
        for (String sentence : testingSet) {
            DenseInstance instance = new DenseInstance(1);
            instance.setValue(testingInstances.attribute("text"), sentence);
            testingInstances.add(instance);
        }

        // ------------

        // create dictionary

        Path trainingDictPath = Paths.get("training_dictionary.csv");
        extractDictionary(trainingInstances, trainingDictPath);

        // ------

        // TF IDF on training set
        FixedDictionaryStringToWordVector trainingFilter = getTfIdfFilter(trainingInstances, trainingDictPath);
        Instances trainingTfIdfInstances = Filter.useFilter(trainingInstances, trainingFilter);

        Path trainingPath = Paths.get("training_features.arff");
        ArffSaver saver = new ArffSaver();
        saver.setInstances(trainingTfIdfInstances);
        saver.setFile(trainingPath.toFile());
        saver.writeBatch();

        // ----------

        // TF IDF on testing set using the same filter

        DictionaryBuilder builder = trainingFilter.getDictionaryHandler();
        Instances testingTfIdfInstances = Filter.useFilter(testingInstances, trainingFilter);

        Path testingPath = Paths.get("testing_features.arff");
        saver = new ArffSaver();
        saver.setInstances(testingTfIdfInstances);
        saver.setFile(testingPath.toFile());
        saver.writeBatch();

        System.out.println(testingTfIdfInstances.instance(0));
        Instance tfidf = builder.vectorizeInstance(testingInstances.instance(0));
        System.out.println(tfidf);

        // ----------

        // TF IDF on testing set using a new filter

        FixedDictionaryStringToWordVector testingFilter = getTfIdfFilter(testingInstances, trainingDictPath);
        Instances testingTfIdfInstances2 = Filter.useFilter(testingInstances, testingFilter);

        Path testingPath2 = Paths.get("testing_features_new_filter.arff");
        saver = new ArffSaver();
        saver.setInstances(testingTfIdfInstances2);
        saver.setFile(testingPath2.toFile());
        saver.writeBatch();

        // ----------

        // TF IDF on testing set using a new filter and dictionary

        Path testingDictPath = Paths.get("testing_dictionary.csv");
        extractDictionary(testingInstances, testingDictPath);

        FixedDictionaryStringToWordVector testingFilter2 = getTfIdfFilter(testingInstances, testingDictPath);
        Instances testingTfIdfInstances3 = Filter.useFilter(testingInstances, testingFilter2);

        Path testingPath3 = Paths.get("testing_features_new_filter_dict.arff");
        saver = new ArffSaver();
        saver.setInstances(testingTfIdfInstances3);
        saver.setFile(testingPath3.toFile());
        saver.writeBatch();

    }

    private static void extractDictionary(Instances copyTrainingInstances, Path dictPath) throws Exception {
        StringToWordVector dictFilter = new StringToWordVector();
        dictFilter.setOutputWordCounts(true);
        dictFilter.setLowerCaseTokens(true);
        dictFilter.setDoNotOperateOnPerClassBasis(true);
        dictFilter.setStopwordsHandler(new Rainbow()); // stopwords list based on
        // http://www.cs.cmu.edu/~mccallum/bow/rainbow/
        dictFilter.setStemmer(new IteratedLovinsStemmer());
        // filter.setWordsToKeep(this.wordsToKeep);
        dictFilter.setAttributeIndicesArray(new int[] { 0 }); // first attribute is sentence
        dictFilter.setDictionaryFileToSaveTo(dictPath.toFile());
        dictFilter.setInputFormat(copyTrainingInstances);

        Filter.useFilter(copyTrainingInstances, dictFilter);
    }

    private static FixedDictionaryStringToWordVector getTfIdfFilter(Instances instances, Path dictPath)
            throws Exception {

        int i = instances.attribute("text").index() + 1;
        FixedDictionaryStringToWordVector filter = new FixedDictionaryStringToWordVector();
        filter.setLowerCaseTokens(true);
        filter.setStopwordsHandler(new Rainbow());
        filter.setStemmer(new IteratedLovinsStemmer());
        filter.setOutputWordCounts(true);
        filter.setTFTransform(true);
        filter.setIDFTransform(true);
        // trainigFilter.setAttributeNamePrefix("tfidf-");
        filter.setAttributeIndices(String.format("%d-%d", i, i));
        filter.setDictionaryFile(dictPath.toFile());
        filter.setInputFormat(instances);

        // fix broken m_count in dictionary build, any positive constant will work
        Field mCount = DictionaryBuilder.class.getDeclaredField("m_count");
        mCount.setAccessible(true);
        Field mVectorizer = FixedDictionaryStringToWordVector.class.getDeclaredField("m_vectorizer");
        mVectorizer.setAccessible(true);
        mCount.set(mVectorizer.get(filter), 1000);

        return filter;
    }
}
