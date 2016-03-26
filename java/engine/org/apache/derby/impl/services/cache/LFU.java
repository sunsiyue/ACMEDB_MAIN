package org.apache.derby.impl.services.cache;

final class LFU extends Policy {

	public LFU(int maxSize) {
		super(maxSize);
	}

	synchronized void addEntry(CacheEntry entry) {
		count++;
		entries.add(new Item(entry,(double) count));
	}

	synchronized CacheEntry findVictim(boolean forUncache) {
		Item victim = findMin();
		if (forUncache)
			entries.remove(victim);
		return victim.entry;
	}

	void incrHit(CacheEntry e) {
		count++;
		incPrio(e);
	}

	String Name() {
		return "LFU";
	}

}
