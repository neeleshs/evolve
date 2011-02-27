package com.motn.evolve;


public abstract class Revision implements IRevision{
	protected String revision;
	protected String lastRevision;
	public Revision(String revision, String lastRevision) {
		this.revision=revision;
		this.lastRevision=lastRevision;
	}

	public enum Go{
		UP{
			
			public void evolve(Revision rev){
				rev.up();
			}
		},
		DOWN{
			public void evolve(Revision rev){
				rev.down();
			}	
			
		};
		public abstract  void evolve(Revision rev);
	}
	public abstract void up();
	public abstract void down();
	public void evolve(String revision,Go evolve){
		begin();
		try {
			System.out.println("Migrating "+ evolve +" to revision "+revision);
			evolve.evolve(this);
			revSynch(revision);
			System.out.println("DONE");
			commit();
		} catch(Exception e){
			revert();
			throw new RuntimeException(e);
		}finally{
			cleanup();
		}
	}

	protected abstract void revert();
	protected abstract void commit() ;
	protected abstract void cleanup();
	protected abstract void begin();
	public abstract String currentStrRev();
	protected abstract void revSynch(String revision);
	public void goDown(){
		evolve(lastRevision, Go.DOWN);
	}
	
	public void goUp(){
		evolve(revision, Go.UP);
	}

}
