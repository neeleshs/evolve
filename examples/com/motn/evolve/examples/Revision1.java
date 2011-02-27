package com.motn.evolve.examples;

import com.motn.evolve.JdbcRevision;

public class Revision1 extends JdbcRevision{
	
	public Revision1(String revision, String lastRevision) {
		super(revision, lastRevision);
	}

	public void up(){
		exec("CREATE TABLE example_person(id integer not null, name varchar(256))",false);
	}

	public void down(){
		exec("DROP table example_person",false);
	}

}
