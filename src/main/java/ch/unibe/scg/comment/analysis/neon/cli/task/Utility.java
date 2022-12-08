package ch.unibe.scg.comment.analysis.neon.cli.task;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;

public class Utility {

	public static String resource(String path) throws IOException {
		try (
				StringWriter writer = new StringWriter();
				InputStreamReader reader = new InputStreamReader(Utility.class.getClassLoader()
						.getResourceAsStream(path))
		) {
			reader.transferTo(writer);
			return writer.toString();
		}
	}

}
