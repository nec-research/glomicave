package eu.glomicave.data_import;

import java.util.List;

public class Sentence {
	public String uid;
	public int index;
	public String text;
	public List<String> tokens;

	@Override
	public String toString() {
		return text + " (" + tokens + ")";
	}
}
