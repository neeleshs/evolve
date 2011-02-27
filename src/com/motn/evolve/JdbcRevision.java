package com.motn.evolve;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import javax.sql.DataSource;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public abstract class JdbcRevision extends Revision{
	Properties p;
	Connection connection;
	private String schemaTable;
	public JdbcRevision(String revision, String lastRevision) {
		super(revision,lastRevision);
		 p= new Properties();
		    InputStream is =
	            Thread.currentThread().getContextClassLoader().getResourceAsStream("evolver.properties");
		    
	    try {
			p.load(is);
			schemaTable = p.getProperty("version.table","schema_version");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	public void begin(){
		if(connection==null){
			try{
				setupConnection();
				connection.setAutoCommit(false);
			}catch(Exception e){
				throw new RuntimeException(e);
			}
		}
	}

	public void revSynch(String rev){
		java.sql.PreparedStatement s=null;
		try {
			s = connection.prepareStatement("UPDATE "+schemaTable+" SET VERSION=?");
			s.setString(1, rev);
			s.executeUpdate();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}finally{
		    closeStatement(s);
		}
	}
    private void closeStatement(Statement s){
    		try{
		      if(s!=null) s.close();
		    }catch(Exception e){
		        throw new RuntimeException(e);
		    }

    }	
    private void closeResultSet(ResultSet r){
    		try{
		      if(r!=null) r.close();
		    }catch(Exception e){
		        throw new RuntimeException(e);
		    }

    }
	public void cleanup(){
		if(connection!=null)
			try {
				connection.close();
				connection = null;
			} catch (SQLException e) {down();
				throw new RuntimeException(e);
			}
	}
		
	
	protected void revert() {
		try {
			if(connection!=null && !connection.isClosed()) connection.rollback();
		} catch (SQLException e1) {
			throw new RuntimeException(e1);
		}
	}
	
	public void exec(String sql,boolean ignoreError){
		System.out.println("    "+sql);
		Statement stmt=null;
		try {
			stmt = connection.createStatement();
			stmt.execute(sql);
		} catch (SQLException e) {
			if(!ignoreError)
				throw new RuntimeException(e);
			System.out.println("WARNING:AN SQL ERROR WAS THROWN:"+e);
		}finally{
		  closeStatement(stmt);
		}
	}

	public String currentStrRev(){
		begin();
		Statement stmt=null;
		ResultSet r =null;
		try {
			stmt = connection.createStatement();
			r = stmt.executeQuery("select version from "+schemaTable);
			String rev="";
			while(r.next()){
				rev =r.getString(1);
			}
			return rev;
		} catch (SQLException e) {
			return "";
		}finally{
		    closeResultSet(r);
		    closeStatement(stmt);
		}
	}

	public void setupConnection() throws Exception{
		if(p.containsKey("spring.beans") && p.containsKey("datasource.bean") ){
			ApplicationContext context = new ClassPathXmlApplicationContext(p.getProperty("spring.beans"));
			DataSource dataSource=(DataSource) context.getBean(p.getProperty("datasource.bean"));
			connection = dataSource.getConnection();
		}else{
			Class.forName(p.getProperty("driver"));
			connection = DriverManager.getConnection(p.getProperty("url"), p.getProperty("user"), p.getProperty("password"));
		}
	}

	public void commit(){
		try{
			connection.commit(); 
		}catch(Exception e){
			throw new RuntimeException(e);
		}finally{
			if(connection!=null)
				try {
					connection.close();
				} catch (SQLException e) {down();
					throw new RuntimeException(e);
				}
		}
	}
}
