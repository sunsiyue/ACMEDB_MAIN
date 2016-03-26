package org.apache.derby.impl.services.cache;
/*
 * Class for a probability distribution.
 * Each expert has a weight associated to it, and the higher the weight,
 * the more likely the expert will get selected in the next round. Based
 * on the success/failure of the expert during the caching process, they
 * will get rewarded/penalized accordingly. 
 */
final class Distribution {
	/* 
	 * Number of items in the distribution
	 */
	private int num_entries; 
	/*
	 * Record the interval of the each probability
	 * The interval [0,1] is divided into (num_entries) consecutive intervals
	 * with interval length p[i], stretched from begin[i] to end[i] 
	 */
	private double[] begin;
	private double[] end;
	private double[] p;
	/*
	 * Constructor
	 * Create a distribution with n items
	 * @param n  number of items
	 */
	Distribution (int n){
		assert (n > 0);
		this.num_entries = n;
		// initialize the array
		begin = new double[num_entries];
		end = new double[num_entries];
		p = new double[num_entries];
		double curr = 0.0;
		// at the start, the distribution is equal 
		for (int i = 0; i < n; i++ ){
			begin[i] = curr;
			p[i] = 1.0 / n;
			end[i] = begin[i] + p[i];
			curr = end[i];
		}
		// ensure the distribution ends at 1.0
		if (end[n-1] != 1.0) 
			end[n-1] = 1.0;
	}
	/*
	 * int sample()
	 * draw a random number amongst the distribution based on the probability
	 * @return  a random number between [0,num_entries - 1], chosen stochastically 
	 */
	int sample(){
		//generate random number between 0 - 1
		double draw = Math.random();
		return sample_bsearch(draw,0,num_entries - 1);
	}

	private int sample_bsearch(double draw, int left, int right){
		assert (left <= right);
		int half = (left + right) / 2;
		if ((begin[half] <= draw) && (end[half]>=draw)){
			return half;
		} else if (draw > end[half]) 
			return sample_bsearch(draw, half+1,right);
		else if (draw < begin[half])
			return sample_bsearch(draw, left,half-1);
		return -1; //error
	}
	
	double getProb(int i){
		assert (i>=0);
		assert (i<num_entries);
		return p[i];
	}
	
	void setProb(int i, double prob){
		assert (i>=0);
		assert (i<num_entries);
		p[i] = prob;
	}
	
	void normalize(){
		double sum = 0.0;
		for (int i=0;i<num_entries;i++) sum+=p[i];
		for (int i=0;i<num_entries;i++) p[i]=p[i]/sum;
	}
	
	void distribute(){
		begin[0] = 0.0;
		for (int i=0;i<num_entries-1;i++){
			end[i] = begin[i] + p[i];
			begin[i+1] = end[i];
		}
		end[num_entries-1] = 1.0;
	}
	

}


