package org.apache.derby.impl.services.cache;


final class LRFU extends Policy {

	public LRFU(int maxSize) {
		super(maxSize);
	}

	synchronized void addEntry(CacheEntry entry) {
		count ++;
		double crf = F(0);
		Item i = new Item(entry, crf);
		i.crf = crf;
		i.last = count;
		entries.add(i);
	}

	synchronized CacheEntry findVictim(boolean forUncache) {
		Item victim = findMin();
		if (forUncache)
			entries.remove(victim);
		return victim.entry;	}

	void incrHit(CacheEntry e) {
		count++;
		Item found = null;
		for (Item i:entries){
			if (i.entry == e){
				found = i;
				break;
			}
		}
		assert (found!= null);
		// update stats for cache-resident job
		double crf = F(0) + F(count - found.last) * found.crf;

		found.crf = crf;
		found.last = count;
		found.prio = crf;
	}

	String Name() {
		return "LRFU";
	}

	private double F(int i) {	
		return Math.pow(0.5,(0.5 * i));
	}

}
