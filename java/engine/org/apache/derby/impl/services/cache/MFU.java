package org.apache.derby.impl.services.cache;

final class MFU extends Policy {

	public MFU(int maxSize) {
		super(maxSize);
	}

	synchronized void addEntry(CacheEntry entry) {
		count++;
		entries.add(new Item(entry,1.0));
	}

	synchronized CacheEntry findVictim(boolean forUncache) {
		Item victim = findMax();
		if (forUncache)
			entries.remove(victim);
		return victim.entry;
	}

	void incrHit(CacheEntry e) {
		count++;
		incPrio(e);
	}

	String Name() {
		return "MFU";
	}

}
