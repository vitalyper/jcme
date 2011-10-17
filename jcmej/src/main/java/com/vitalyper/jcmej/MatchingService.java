package com.vitalyper.jcmej;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;

import com.google.gson.Gson;

@Path("/")
@Produces("application/json")
public class MatchingService {
	static public final String OK = "ok";
	
	static Gson gson = new Gson();
	static Logger logger = Logger.getLogger(MatchingService.class);
	
	volatile Map<Side, Float> maxBySide = initMax();
	volatile Map<Float, String> buy = new HashMap<Float, String>();
	volatile Map<Float, String> sell = new HashMap<Float, String>();
	Stats stats = new Stats();
	
	Lock maxLock = new ReentrantLock();
	Lock buyLock = new ReentrantLock();
	Lock sellLock = new ReentrantLock();
	
	static Map<Side, Float> initMax() {
		Map<Side, Float> ret = new TreeMap<Side, Float>();
		ret.put(Side.B, 0.0F);
		ret.put(Side.S, 0.0F);
		return ret;
	}
	
	@Path("/match/")
	@POST
	public Response postMatch(String json) {
		long start = System.nanoTime();
		postMatchInternal(json);
		final float duration = (System.nanoTime() - start) / 1000000.0F;
		
		// Call update stats on a separate thread not to hold processing
		new Thread() {
			@Override
			public void run() {
				stats.updateStats(duration);
			}
		}.start();
		
		return Response.ok(OK).build();
	}
	
	@Path("/reset/")
	@GET
	public Response getReset() {
		maxBySide = initMax();
		buy.clear();
		sell.clear();
		stats = new Stats();
		
		return Response.ok(OK).build();
	}
	
	@Path("/results/")
	@GET
	public Response getResults() {
		return Response.ok(gson.toJson(stats.toMap())).build();
	}
	
	@Path("/extra/")
	@GET
	public Response getExtra() {
		List<Float> extraKeys = new ArrayList<Float>(buy.keySet());
		extraKeys.addAll(sell.keySet());
		return Response.ok(gson.toJson(extraKeys)).build();
	}
	
	@Path("/max/{side}")
	@GET
	public Response getMax(@PathParam("side") String side) {
		Float max = maxBySide.get(Side.fromValue(side));
		logger.debug(String.format("Max for %s is %f", side, max));
		return Response.ok(gson.toJson(max)).build();
	}
	
	void postMatchInternal(String json) {
		MatchItem matchItem = gson.fromJson(json, MatchItem.class);
		Side side = Side.fromValue(matchItem.getId().substring(0, 1));
		
		float maxFloat = maxBySide.get(side);
		if (matchItem.getPrice() > maxFloat) {
			maxLock.lock();
			try {
				maxBySide.put(side, matchItem.getPrice());
			}
			finally {
				maxLock.unlock();
			}
		}
		
		Map<Float, String> inMap = new HashMap<Float, String>();
		Map<Float, String> otherMap = new HashMap<Float, String>();
		Lock inLock = new ReentrantLock();
		Lock otherLock = new ReentrantLock();
		if (side.equals(Side.B)) {
			inMap = buy;
			otherMap = sell;
			inLock = buyLock;
			otherLock = sellLock;
		}
		else if (side.equals(Side.S)) {
			inMap = sell;
			otherMap = buy;
			inLock = sellLock;
			otherLock = buyLock;
		}
		updateMaps(matchItem.getPrice(), matchItem.getId(), inMap, otherMap, inLock, otherLock);
		
		logger.debug(String.format(
			"match. side %s, new-float: %f, max-float: %f, in-sz-a: %d, other-sz-a: %d",
			side, matchItem.getPrice(), maxFloat, inMap.size(), otherMap.size()));
	}
	
	void updateMaps(
		float newVal, 
		String id, 
		Map<Float, String> inMap, 
		Map<Float, String> otherMap,
		Lock inLock,
		Lock otherLock) {
		
		// Only add to passed in side if other doesn't have a match
		if (!otherMap.containsKey(newVal)) {
			inLock.lock();
			try {
				inMap.put(newVal, id);
			}
			finally {
				inLock.unlock();
			}
		}
		
		// Remove from the other side
		if (otherMap.containsKey(newVal)) {
			otherLock.lock();
			try {
				otherMap.remove(newVal);
			}
			finally {
				otherLock.unlock();
			}
		}
	}
	
	static enum Side {
		B, S;
		
		static Side fromValue(String input) {
			for (Side s : Side.values()) {
				// Use case insensitive match
				if (s.name().equalsIgnoreCase(input)) {
					return s;
				}
			}
			throw new IllegalArgumentException(String.format("No enum for input string %s.", input));
		}
	}
	
	static class Stats {
		static final float DEFAULT_MIN = 9999999.0F;
		AtomicInteger cnt = new AtomicInteger();
		AtomicInteger min = new AtomicInteger(Float.floatToIntBits(DEFAULT_MIN));
		AtomicInteger max = new AtomicInteger();
		AtomicInteger avg = new AtomicInteger();
		AtomicInteger total = new AtomicInteger();
		
		public Stats() {
			this(0, DEFAULT_MIN, 0.0F, 0.0F, 0.0F); 
		};
		
		public Stats(int cnt, float min, float max,
				float avg, float total) {
			this.cnt.set(cnt);
			this.min.set(Float.floatToIntBits(min));
			this.max.set(Float.floatToIntBits(max));
			this.avg.set(Float.floatToIntBits(avg));
			this.total.set(Float.floatToIntBits(total));
		}

		public int getCnt() {
			return cnt.get();
		}

		public float getMin() {
			return Float.intBitsToFloat(min.get());
		}

		public float getMax() {
			return Float.intBitsToFloat(max.get());
		}

		public float getAvg() {
			return Float.intBitsToFloat(avg.get());
		}

		public float getTotal() {
			return Float.intBitsToFloat(total.get());
		}
		
		public void updateStats(float duration) {
			cnt.set(cnt.incrementAndGet());
			total.set(Float.floatToIntBits(duration + getTotal()));
			avg.set(Float.floatToIntBits(getTotal() / cnt.get()));
			
			if (duration < getMin()) {
				min.set(Float.floatToIntBits(duration));
			}
			
			if (duration > getMax()) {
				max.set(Float.floatToIntBits(duration));
			}
		}
		
		public Map<String, Object> toMap() {
			Map<String, Object> outMap = new TreeMap<String, Object>();
			
			outMap.put("cnt", getCnt());
			outMap.put("min", getMin());
			outMap.put("max", getMax());
			outMap.put("avg", getAvg());
			outMap.put("total", getTotal());
			
			return outMap;
		}
	}
	
	static class MatchItem {
		float price;
		String id;
		public MatchItem(float price, String id) {
			this.price = price;
			this.id = id;
		}
		public float getPrice() {
			return price;
		}
		public void setPrice(float price) {
			this.price = price;
		}
		public String getId() {
			return id;
		}
		public void setId(String id) {
			this.id = id;
		}
	}
}
