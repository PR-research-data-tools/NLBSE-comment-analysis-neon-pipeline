Install dependencies and create shaded jar:

```
mvn package
```

Run simple smoke test, there should be an output.xml in the project folder.

```
java -jar target/cli-0.0.1-SNAPSHOT.jar src/test/resources/text.txt src/test/resources/heuristics.xml output.xml
```
