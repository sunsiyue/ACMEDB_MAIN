package org.apache.derby.impl.services.cache;

final class LIFO extends Policy {

	public LIFO(int maxSize) {
		super(maxSize);
	}


	synchronized void addEntry(CacheEntry entry) {
		count++;
		entries.add(new Item(entry,(double) count));
	}

	synchronized CacheEntry findVictim(boolean forUncache) {
		Item victim = findMax();
		if (forUncache)
			entries.remove(victim);
		return victim.entry;
	}

	void incrHit(CacheEntry e) {
		count++;
	}

	String Name() {
		return "LIFO";
	}

}
