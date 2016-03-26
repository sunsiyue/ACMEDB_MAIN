package org.apache.derby.impl.services.cache;

final class LFUDA extends Policy {
	private double L = 0;
	private double totalprio;
	public LFUDA(int maxSize) {
		super(maxSize);
		totalprio = 0.0;
	}

	synchronized void addEntry(CacheEntry entry) {
		count++;
		double prio = 1.0 + L;
		entries.add(new Item(entry,prio));
		totalprio+= prio;
		
	}

	synchronized CacheEntry findVictim(boolean forUncache) {
		Item victim = findMin();
		L = victim.prio;
		if (forUncache)
			entries.remove(victim);
		return victim.entry;
	}

	void incrHit(CacheEntry e) {
		count++;
		incPrio(e);
		double prio =0.0;
		Item found = null;
		for (Item i:entries){
			if (i.entry == e){
				prio = i.prio;
				found = i;
				break;
			}
		}
		totalprio -= prio;
		found.incFreq();
		prio = found.freq + L;
		totalprio +=prio;
		found.prio = prio;
	}

	String Name() {
		return "LFUDA";
	}

}
