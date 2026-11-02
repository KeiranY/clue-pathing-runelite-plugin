package com.shortestclue;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class MissingGroupsTest
{
	private static final Set<Integer> SPADE = Set.of(1);
	private static final Set<Integer> HELMS = new LinkedHashSet<>(Arrays.asList(12, 10, 11));
	private static final Set<Integer> BRACELETS = Set.of(20, 21);

	@Test
	public void aGroupFoundNowhereIsCutToItsLowestId()
	{
		assertEquals(List.of(SPADE, Set.of(10), BRACELETS),
			ShortestCluePlugin.oneItemOfMissingGroups(List.of(SPADE, HELMS, BRACELETS), List.of(List.of(11, 10, 12))));
	}

	@Test
	public void everyGroupIsKeptWhenNothingIsMissing()
	{
		assertEquals(List.of(SPADE, HELMS, BRACELETS),
			ShortestCluePlugin.oneItemOfMissingGroups(List.of(SPADE, HELMS, BRACELETS), List.of()));
	}

	@Test
	public void onlyAGroupShortestPathNamesExactlyIsCut()
	{
		assertEquals(List.of(HELMS, Set.of(20)),
			ShortestCluePlugin.oneItemOfMissingGroups(List.of(HELMS, BRACELETS),
				List.of(List.of(10, 11), "not a group", List.of(21, 20))));
	}
}
