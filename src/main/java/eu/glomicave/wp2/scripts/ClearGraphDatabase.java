package eu.glomicave.wp2.scripts;

import eu.glomicave.persistence.CoreGraphDatabase;

public class ClearGraphDatabase {

	public static void main(String[] args) {
		CoreGraphDatabase.clearDatabase();
		CoreGraphDatabase.closeDriver();
	}
}
