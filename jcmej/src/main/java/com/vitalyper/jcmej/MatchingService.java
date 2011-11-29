package com.vitalyper.jcmej;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

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
	
	static Map<Side, Float> maxBySide = initMax();
	static Map<Float, String> buy = Collections.synchronizedMap(new HashMap<Float, String>());
	static Map<Float, String> sell = Collections.synchronizedMap(new HashMap<Float, String>());
	Stats stats = new Stats();
	
	static Map<Side, Float> initMax() {
		Map<Side, Float> ret = new TreeMap<Side, Float>();
		ret.put(Side.B, 0.0F);
		ret.put(Side.S, 0.0F);
		return Collections.synchronizedMap(ret);
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
	
	synchronized void postMatchInternal(String json) {
		MatchItem matchItem = gson.fromJson(json, MatchItem.class);
		Side side = Side.fromValue(matchItem.getId().substring(0, 1));
		
		float maxFloat = maxBySide.get(side);
		if (matchItem.getPrice() > maxFloat) {
			maxBySide.put(side, matchItem.getPrice());
		}
		
		if (side.equals(Side.B)) {
			float newVal = matchItem.getPrice();
			if (!sell.containsKey(newVal)) {
				buy.put(newVal, matchItem.getId());
			}
			// Remove from the other side
			if (sell.containsKey(newVal)) {
				sell.remove(newVal);
			}
		}
		else if (side.equals(Side.S)) {
			float newVal = matchItem.getPrice();
			if (!buy.containsKey(newVal)) {
				sell.put(newVal, matchItem.getId());
			}
			// Remove from the other side
			if (buy.containsKey(newVal)) {
				buy.remove(newVal);
			}
		}
		
		logger.debug(String.format(
			"match. side %s, new-float: %f, max-float: %f, buy-map-sz: %d, sell-map-sz-a: %d",
			side, matchItem.getPrice(), maxFloat, buy.size(), sell.size()));
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
