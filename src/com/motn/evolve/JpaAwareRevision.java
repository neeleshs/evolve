package com.motn.evolve;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.FlushModeType;
import javax.persistence.Query;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public abstract class JpaAwareRevision extends Revision{
	private final Properties properties;
	private EntityManagerFactory emFactory;
	protected EntityManager entityManager;
	private EntityTransaction transaction;
	public JpaAwareRevision(String currentRevision,String lastRevision){
		super(currentRevision,lastRevision);
		 properties= new Properties();
		    InputStream is =
	            Thread.currentThread().getContextClassLoader().getResourceAsStream("evolver.properties");
		    
	    try {
	    	properties.load(is);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(properties.getProperty("spring.config.location","applicationContext.xml"));
		emFactory =  (EntityManagerFactory) context.getBean(properties.getProperty("entityManagerFactory","entityManagerFactory"));
		entityManager = emFactory.createEntityManager();
		entityManager.setFlushMode(FlushModeType.AUTO);
	}
	
	protected void exec(String sql){
		entityManager.createNativeQuery(sql).executeUpdate();
	}
	protected void revSynch(String rev) {
		entityManager.createNativeQuery("update schema_version set version='"+rev+"'").executeUpdate();
	}

	protected void cleanup() {
		entityManager.close();
	}
	protected void begin() {
		transaction = entityManager.getTransaction();
		transaction.begin();
	}

	public String currentStrRev(){
		try {
			List result = entityManager.createNativeQuery("select version from schema_version").getResultList();
			return result.get(0).toString();
		} catch (Exception e) {
			return "";
		}
	}
	
	protected <T> T find(String query,Class<T> type, Object... params) {
		List<T> results = findAll(query, type, params);
		return results.isEmpty()? null : results.get(0);
	}

	protected <T> List<T> findAll(String query,Class<T> type, Object... params) {
		Query q = entityManager.createQuery(query);
		int index=1;
		for (Object param: params) {
			q.setParameter(index++, param);
		}
		return (List<T>) q.getResultList();
	}

	@Override
	protected void commit() {
		entityManager.flush();
		transaction.commit();
	}

	@Override
	protected void revert() {
	  if(transaction!=null) transaction.rollback();	
	}
	
}
