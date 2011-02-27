package com.motn.evolve;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Evolver {
	
	private static final String END_SQL_MARKER = "!endsql!";
	private static final String MAX_REVS = "99999999";
	static Comparator<String> revComparator = new Comparator<String>(){
		public int compare(String file1, String file2) {
			if(file1==null ||"".equals(file1.trim())) return -1;
			if(file2==null ||"".equals(file2.trim())) return 1;
			String[] parts1 = file1.split("_")[0].split("\\.");
			String[] parts2 = file2.split("_")[0].split("\\.");
			int index1=0,index2=0;
			while(index1<parts1.length && index2<parts2.length){
				int version1 = Integer.parseInt(parts1[index1]);
				int version2 = Integer.parseInt(parts2[index2]);
				if(version1!=version2) return version1-version2;
				index1++;index2++;
			}
			return parts1.length-parts2.length;
		}};

	/**
	 * @param args
	 */
	public static void main(String args[]){
		try {
			String toRev =MAX_REVS;
			if(args!=null && args.length==1) toRev = args[0];
			JdbcRevision revision = new JdbcRevision(null,null){
				@Override
				public void down() {
				}
				@Override
				public void up() {
				}};
			String pkg=revision.p.getProperty("package");
			revision.begin();
			String currentVersion =revision.currentStrRev();
			revision.cleanup();
			System.out.println("DB AT "+currentVersion+":"+toRev);
			List<IRevision> revisions = loadRevisions(pkg,currentVersion,toRev);
			if(revComparator.compare(currentVersion,toRev)>0){
				Collections.reverse(revisions);
				for (IRevision rev : revisions) {
					rev.goDown();
				}
			}else{
				for (IRevision rev : revisions) {
					rev.goUp();
				}
			}
			revision.begin();
			currentVersion =revision.currentStrRev();
			System.out.println("DB AT VERSION "+currentVersion+",Total revs loaded:"+revisions.size());
			revision.cleanup();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private static List<IRevision> loadRevisions(String basePackage, String currentVersion, String toRev) {
		URL resource = Thread.currentThread().getContextClassLoader().getResource(basePackage.replace('.', '/'));
		String resourceFolder = resource.getFile();
		File file = new File(resourceFolder);
		String[] sqlFiles = file.list(new FilenameFilter(){
			public boolean accept(File dir, String name) {
				return name.endsWith(".sql");
			}});
		List<String> fileList = Arrays.asList(sqlFiles);
		int[] indexes=indexes(fileList, currentVersion, toRev);
		List<IRevision> m = new ArrayList<IRevision>();
		for(int i=indexes[0];i<indexes[1];i++){
			m.add(loadSQLRevision(basePackage, fileList.get(i),i>0?fileList.get(i-1):"0_BASE.sql"));
		}
		return m;
	}
	
	protected static int[] indexes(List<String> fileList,String currentVersion,String toRev){
		sortFiles(fileList);
		int nextRevIndex = findRev(currentVersion,fileList)+1;
		int toRevIndex= findRev(toRev,fileList);
		toRevIndex=toRevIndex<0?fileList.size():toRevIndex+1;
		if(nextRevIndex > toRevIndex){
			int tmp=nextRevIndex;
			nextRevIndex=toRevIndex;
			toRevIndex=tmp;
		}
		return new int[]{nextRevIndex,toRevIndex};
		
	}
	protected static int findRev(String currentVersion, List<String> fileList) {
		for (int i=0;i<fileList.size();i++) {
			if(fileList.get(i).startsWith(currentVersion+"_")) return i;
		}
		return -1;
	}

	protected static void sortFiles(List<String> fileList) {
		Collections.sort(fileList, revComparator);
	}

	private static IRevision loadSQLRevision(String basePackage,
			String sqlFile, String lastRevisionFile) {
		InputStream sqlStream = Evolver.class.getClassLoader().getResourceAsStream(basePackage.replace('.', '/')+"/"+sqlFile);
		if (sqlStream==null) return null;
		BufferedReader r =new BufferedReader(new InputStreamReader(sqlStream));
		StringBuffer upLines=new StringBuffer("");
		StringBuffer downLines=new StringBuffer("");
		String line=null;
		boolean readingUp=false;
		boolean readingDown=false;
		boolean ignoreErrors=false;
		try {
			boolean spStarted=false;
			StringBuilder sp=new StringBuilder();
			boolean multilineComment=false;
			
			while((line=r.readLine())!=null){
				line = line.trim();
				if(line.startsWith("#IGNORE_ERRORS")) ignoreErrors=true; 
				if(line.endsWith("*/") && multilineComment){
					multilineComment=false;
					continue;
				}
				if(line.startsWith("--")||line.startsWith("//") || (line.startsWith("/*") && line.endsWith("*/"))) continue;
				if(line.startsWith("/*") || multilineComment){
					multilineComment=true;
					continue;
				}
				if(line.startsWith("#UP")){
					readingUp=true;
					continue;
				}
				if(line.startsWith("#DOWN")){
					readingDown=true;
					readingUp=false;
					continue;
				}
				if(line.startsWith("#SPEND")){
					spStarted=false;
					sp.append(";");
					sp.append(END_SQL_MARKER);
					line=sp.toString();
				}else if(line.startsWith("#SPBEGIN")){
					spStarted=true;
					sp.setLength(0);
					continue;
				}
				if(spStarted){
					sp.append(line).append("  ");
					continue;
				}
				if(line.endsWith(";")) {
					line= line.substring(0,line.length()-1);
					line+=END_SQL_MARKER;
				}
				if(readingUp) upLines.append(line).append("  ");
				if(readingDown) downLines.append(line).append("  ");
			}
			return new SQLRevision(upLines, downLines,sqlFile.substring(0,sqlFile.indexOf("_")),lastRevisionFile.substring(0,lastRevisionFile.indexOf("_")),ignoreErrors);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}finally{
			try {
				r.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
}


class SQLRevision extends JdbcRevision{

	private final StringBuffer upLines;
	private final StringBuffer downLines;
	private final boolean ignoreErrors;

	public SQLRevision(StringBuffer upLines, StringBuffer downLines,String currentRevision,String lastRevision, boolean ignoreErrors) {
		super(currentRevision,lastRevision);
		this.upLines = upLines;
		this.downLines = downLines;
		this.ignoreErrors = ignoreErrors;
	}

	@Override
	public void down() {
		runCommands(downLines);
	}

	private void runCommands(StringBuffer commandList) {
	
		String[] commands = commandList.toString().split("!endsql!");
		for (String command : commands) {
			command = command.trim();
			if(!("".equals(command) || command.startsWith("#") || command.startsWith("--")|| command.startsWith("//"))){
				if(command.endsWith(";")) command=command.substring(0,command.length()-1);
				exec(command,ignoreErrors);
			}
			
		}
	}

	@Override
	public void up() {
		runCommands(upLines);
	}
	
	
}
