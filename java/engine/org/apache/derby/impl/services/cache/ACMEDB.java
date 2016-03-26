package org.apache.derby.impl.services.cache;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.cache.Cacheable;
import org.apache.derby.shared.common.sanity.SanityManager;

final class ACMEDB implements ReplacementPolicy{
	private final Policy[] realCache;
	private final Policy[] expert;

	private final Distribution dist;
	private final AtomicBoolean isShrinking = new AtomicBoolean();

	private final ArrayList<Holder> cacheholder;


	private final int POLICY_COUNT = 9;
	//setting up experts//
	//general policies//
	private final int use_LRU = 1;
	private final int use_MRU = 1;
	private final int use_LFU = 1;
	private final int use_FIFO = 1;
	private final int use_LIFO = 1;
	private final int use_MFU = 1;
	private final int use_LFUDA = 1;
	private final int use_LRFU = 0;
	//database-specific policies//
	private final int use_LRUK = 0;
	private final int NUM_POLICIES = use_LRU + use_MRU + use_LFU + use_FIFO 
									+ use_LIFO + use_MFU + use_LFUDA + use_LRFU + use_LRUK;
	private final ConcurrentCache cacheManager;
	private final int maxSize;
	private final boolean LOG_ENABLED = false;

	ACMEDB (ConcurrentCache cacheManager, int maxSize){
		this.cacheManager = cacheManager;
		this.maxSize = maxSize;
		dist = new Distribution(NUM_POLICIES);
		expert = new Policy[NUM_POLICIES];
		realCache = new Policy[NUM_POLICIES];
		registerExpert();
		cacheholder = new ArrayList<Holder>();
	}

	private void registerExpert(){
		int count = 0;
		if (use_LRU == 1){
			expert[count] = new LRU(maxSize); realCache[count] = new LRU(maxSize);
			count++;
		}
		if (use_MRU == 1){
			expert[count] = new MRU(maxSize); realCache[count] = new MRU(maxSize);
			count++;
		}
		if (use_LFU == 1){
			expert[count] = new LFU(maxSize); realCache[count] = new LFU(maxSize);
			count++;
		}
		if (use_FIFO == 1){
			expert[count] = new FIFO(maxSize); realCache[count] = new FIFO(maxSize);
			count++;
		}
		if (use_LIFO == 1){
			expert[count] = new LIFO(maxSize); realCache[count] = new LIFO(maxSize);
			count++;
		}
		if (use_MFU == 1){
			expert[count] = new MFU(maxSize); realCache[count] = new MFU(maxSize);
			count++;
		}
		if (use_LFUDA == 1){
			expert[count] = new LFUDA(maxSize); realCache[count] = new LFUDA(maxSize);
			count++;
		}
		if (use_LRFU == 1){
			expert[count] = new LRFU(maxSize); realCache[count] = new LRFU(maxSize);
			count++;
		}
//		if (use_LRUK == 1){
//			expert[count] = new LRUK(maxSize); realCache[count] = new LRUK(maxSize);
//			count++;
//		}
		if  (LOG_ENABLED)
			System.out.println(count +" experts.");
	}
	
	private void checkExpertCache(CacheEntry e){
		for (Policy p:expert){
			if (p.exists(e)){
				p.hit();
				p.incrHit(e);
				if (LOG_ENABLED) System.out.println(p.Name()+" hits.");
			} else {
				p.miss();
				if (LOG_ENABLED) System.out.println(p.Name()+" misses.");
			}
		}
	}
	
	public void insertEntry(CacheEntry entry) throws StandardException {
		//real cache missed, checking expert cache
		checkExpertCache(entry);
		//add new entry
		final int size;
		synchronized (cacheholder){
			size = cacheholder.size();
			if (size < maxSize){
				cacheholder.add(new Holder(entry));
				addToVirtualCache(entry);
				return;
			}
		}

		if (size > maxSize) {
			//shrinks the cache if needed
			BackgroundCleaner cleaner = cacheManager.getBackgroundCleaner();
			if (cleaner != null) {
				cleaner.scheduleShrink();
			} else {
				doShrink();
			}
		}
		//add new item
		Holder h = findVictim(size>=maxSize);
		if (h == null) {
			// didn't find a victim, so we need to grow
			synchronized (cacheholder) {
				cacheholder.add(new Holder(entry));
				addToVirtualCache(entry);
			}
		}
		else {
			CacheEntry e = h.getEntry();
			final Cacheable dirty;
			boolean needUpdate = false;
			e.lock();
			try{
				if (!isEvictable(e, h)) {
					return;
				}
				Cacheable c = e.getCacheable();
				if (!c.isDirty()) {
					h.switchEntry(entry);
					needUpdate = true;
					cacheManager.evictEntry(c.getIdentity());
				}
				BackgroundCleaner cleaner = cacheManager.getBackgroundCleaner();
				if (cleaner != null && cleaner.scheduleClean(e)) {
					// Successfully scheduled the clean operation. We can't
					// evict it until the clean operation has finished. Since
					// we'd like to be as responsive as possible, move on to
					// the next entry instead of waiting for the clean
					// operation to finish.
					return;
				}

				dirty = c;
			} finally {
				e.unlock();
			}
			if (needUpdate){
				updateVirtualCache(e, entry);
			}
		}

	}

	private void updateVirtualCache(CacheEntry oldEntry, CacheEntry newEntry) {
		//for realCache, replace the oldEntry with the newEntry
		for (Policy p:realCache){
			p.release(oldEntry);
			p.addEntry(newEntry);
		}
		//for expert, select the victim according to the policy, and replace with the newEntry
		for (Policy p:expert){
			p.findVictim(true);
			p.addEntry(newEntry);
		}

	}

	private boolean isEvictable(CacheEntry e, Holder h) {
		if (h.getEntry()!= e){
			return false;
		}

		if (e.isKept()){
			return false;
		}
		if (SanityManager.DEBUG) {
			SanityManager.ASSERT(e.isValid(), "Holder contains invalid entry");
			SanityManager.ASSERT(!h.isEvicted(), "Holder is evicted");
		}
		return true;
	}

	private Holder findVictim(boolean allowsEviction) throws StandardException{
		if (!allowsEviction) return null;
		int selected = dist.sample();
		CacheEntry e = realCache[selected].findVictim(false);
		if (LOG_ENABLED)
			System.out.println("EXPERT: "+realCache[selected].Name());
		synchronized (cacheholder){
			for (Holder h:cacheholder)
				if (h.getEntry() == e) 
					return h;
		}
		return null;
	}

	private void addToVirtualCache(CacheEntry entry) {
		for (int i = 0; i<NUM_POLICIES;i++){
			expert[i].addEntry(entry);
			realCache[i].addEntry(entry);
		}

	}

	public void doShrink() {
		if (isShrinking.compareAndSet(false, true)) {
			try {
				shrinkMe();
			} finally {
				// allow others to call shrinkMe()
				isShrinking.set(false);
			}
		}
	}

	private void shrinkMe() {
		if (SanityManager.DEBUG) {
			SanityManager.ASSERT(isShrinking.get(),
					"Called shrinkMe() without ensuring exclusive access");
		}
		/*final int size;
		final Holder h;
		synchronized (cacheholder){
			size = cacheholder.size();
		}

		if (size <= maxSize)
			return;
		try{
			h = findVictim(false);
			final CacheEntry e = h.getEntry();
			int index = cacheholder.indexOf(h);
			if (e == null){
				removeHolder(index,h);
				return;
			}
			try {
				if (!isEvictable(e, h)) {
					return;
				}

				final Cacheable c = e.getCacheable();
				if (c.isDirty()) {
					return;
				}

				h.setEvicted();
				cacheManager.evictEntry(c.getIdentity());
				removeHolder(index, h);

			} finally {
				e.unlock();
			}

		} catch (StandardException se){
		}
*/
	}

	private void removeHolder(int pos, Holder h) {
		synchronized (cacheholder) {
			Holder removed = cacheholder.remove(pos);
			if (SanityManager.DEBUG) {
				SanityManager.ASSERT(removed == h, "Wrong Holder removed");
			}
		}
	}

	private void updatePrio(CacheEntry e){
		for (Policy p:realCache){
			p.incrHit(e);
		}
		checkExpertCache(e);
		//readjusts policy
		for (int i = 0; i< NUM_POLICIES;i++)
			dist.setProb(i, dist.getProb(i)*Math.exp(-0.5*expert[i].missed()));
		dist.normalize();
		if (LOG_ENABLED){
			for (int i=0; i< NUM_POLICIES;i++) 
				System.out.print(realCache[i].Name()+": "+ dist.getProb(i)+" ");
			System.out.println();
		}
	}

	private void removeFromCache(CacheEntry e){
		for (Policy p:expert){
			p.release(e);
		}
		for (Policy p:realCache){
			p.release(e);
		}
	}

	class Holder implements Callback{
		private CacheEntry entry;
		private boolean evicted;
		Holder (CacheEntry e){
			this.entry = e;
			e.setCallback(this);
		}

		public void switchEntry(CacheEntry e) {
			e.setCallback(this);
			e.setCacheable(entry.getCacheable());
			entry = e;
		}

		synchronized CacheEntry getEntry(){
			return this.entry;
		}

		synchronized void setEvicted() {
			if (SanityManager.DEBUG) {
				SanityManager.ASSERT(!evicted, "Already evicted");
			}
			evicted = true;
			entry = null;
		}

		synchronized boolean isEvicted() {
			return evicted;
		}

		public void access(){
			updatePrio(this.entry);
		}
		public void free(){
			CacheEntry e = this.entry;
			entry = null;
			// let others know that a free entry is available
			cacheholder.remove(this);
			removeFromCache(e);
		}
	}

}
