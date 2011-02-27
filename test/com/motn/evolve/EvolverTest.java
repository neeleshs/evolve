package com.motn.evolve;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;


public class EvolverTest {
	@Test
	public void sorting(){
		List<String> list = Arrays.asList("0_","1_","2.1.1_","23_");
		int[] indexes = Evolver.indexes(list, "22.2", "99999999");
		Assert.assertEquals(-1,indexes[0]);
		System.out.println(list);
	}
}
