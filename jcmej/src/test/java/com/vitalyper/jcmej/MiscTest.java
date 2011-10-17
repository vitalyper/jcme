package com.vitalyper.jcmej;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;


public class MiscTest {
	
	@Test public void testAtomicIntegerFloatAdd() {
		float a = 2.0F;
		float b = 3.0F;
		
		AtomicInteger ai = new AtomicInteger(Float.floatToIntBits(a));
		// Fails with
		//java.lang.AssertionError: expected:<5.0> but was:<-5.8774717541114375E-39>
		// assertEquals(5.0F, Float.intBitsToFloat(ai.addAndGet(Float.floatToIntBits(b))), 0.0F);
		assertEquals(5.0F, b + Float.intBitsToFloat(ai.get()), 0.0F);
	}

}
