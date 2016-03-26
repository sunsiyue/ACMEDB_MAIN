package org.apache.derby.impl.services.cache;

import java.util.ArrayList;

public abstract class Policy {

	private int maxSize;
	private int _missed;
	int count;

	class Item {
		CacheEntry entry;
		int freq;
		double prio;
		double crf;
		int last;
		boolean isLIR;
		boolean isResident;
		Item (CacheEntry e){
			this.entry = e;
		}
		Item (CacheEntry e, double p){
			this.entry = e;
			this.prio = p;
		}
		public void incFreq(){
			freq++;
		}
		int[] k = new int[2];
		int[] k_time = new int[2];
		long time;
	}
	ArrayList<Item> entries;
	Policy(int maxSize){
		this.maxSize = maxSize;
		this.entries = new ArrayList<Item>();
		this.count = 0;
	}

	abstract void addEntry(CacheEntry entry);
	abstract CacheEntry findVictim(boolean forUncache);
	abstract void incrHit(CacheEntry e);
	abstract String Name();
	
	boolean exists(CacheEntry e){
		for (Item i:entries)
			if (i.entry == e) return true;
		return false;
	}
	void release (CacheEntry e){
		for (Item i:entries){
				if (i.entry == e){
					entries.remove(i);
					break;
				}
			}
		
	}
	void hit(){
		_missed = 0;
	}
	void miss(){
		_missed = 1;
	}
	int missed(){
		return _missed;
	}
	
	Item findMin(){
		Item victim = null;
		for (Item i:entries){
			final CacheEntry e = i.entry;
			try{
				e.lock();
				if (!e.isKept()){
					if (victim == null){
						victim = i;
					}
					else {
						if (victim.prio > i.prio)
							victim = i;
					}
				}
					
			} finally {
				e.unlock();
			}
		}
		return victim;
	}
	
	Item findMax(){
		Item victim = null;
		for (Item i:entries){
			final CacheEntry e = i.entry;
			try{
				e.lock();
				if (!e.isKept()){
					if (victim == null){
						victim = i;
					}
					else {
						if (victim.prio < i.prio)
							victim = i;
					}
				}
					
			} finally {
				e.unlock();
			}
		}
		return victim;
	}
	
	void adjustPrio(CacheEntry e, double prio){
		for (Item i:entries){
			if (i.entry == e) {
				i.prio = prio;
				return;
			}
		}
	}
	
	void incPrio(CacheEntry e){
		for (Item i:entries){
			if (i.entry == e){
				i.prio++;
				return;
			}
		}
	}
}
