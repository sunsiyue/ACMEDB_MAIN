package org.apache.derby.impl.services.cache;

final class LRU extends Policy{
	LRU(int maxSize) {
		super(maxSize);
		// TODO Auto-generated constructor stub
	}

	synchronized void addEntry(CacheEntry entry) {
		count++;
		entries.add(new Item(entry, (double) count));
	}

	synchronized CacheEntry findVictim(boolean forUncache) {
		Item victim = findMin();
		if (forUncache)
			entries.remove(victim);
		return victim.entry;
	}

	void incrHit(CacheEntry e) {
		count++;
		adjustPrio(e, count);
	}

	String Name() {
		return "LRU";
	}

}
