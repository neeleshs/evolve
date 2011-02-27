package com.motn.evolve.examples;

import com.motn.evolve.JdbcRevision;

public class Revision0 extends JdbcRevision{
	public Revision0(String revision, String lastRevision) {
		super(revision, lastRevision);
	}
	public void up(){
		exec("CREATE TABLE schema_version(version integer not null)",false);
		exec("INSERT INTO  schema_version value(-1)",false);
	}

	public void down(){
		exec("DROP TABLE schema_version",false);
	}

}
