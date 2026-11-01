package com.shortestclue;

import static net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirements.all;
import static net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirements.any;
import static net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirements.emptySlot;
import static net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirements.item;
import static net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirements.range;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.client.plugins.cluescrolls.clues.item.ItemRequirement;
import org.junit.Test;

public class ItemsNeededTest
{
	private static final int ITEM_COUNT = 20;
	private static final int HAT = 1;
	private static final int BOOTS = 2;
	private static final int RING = 3;
	private static final int RING_IMBUED = 4;
	private static final int RED_HAT = 5;
	private static final int RED_TOP = 6;
	private static final int TOP = 7;
	private static final int HAT_TRIMMED = 8;
	private static final int STAFF_FULL = 10;
	private static final int STAFF_EMPTY = 12;

	private static Item[] items(int... ids)
	{
		Item[] items = new Item[ids.length];
		for (int i = 0; i < ids.length; i++)
		{
			items[i] = new Item(ids[i], 1);
		}
		return items;
	}

	private static List<Set<Integer>> needed(Item[] carried, ItemRequirement... requirements)
	{
		return ShortestCluePlugin.itemsNeeded(requirements, carried, ITEM_COUNT, id -> false);
	}

	@Test
	public void notedItemsAreNotAskedFor()
	{
		// A range like RuneLite's combat bracelets holds the noted copies too (here the odd ids)
		assertEquals(List.of(Set.of(10, 12)), ShortestCluePlugin.itemsNeeded(
			new ItemRequirement[]{range("Staff", STAFF_FULL, STAFF_EMPTY)}, items(), ITEM_COUNT, id -> id == 11));
	}

	@Test
	public void carriedItemsAreNotAskedFor()
	{
		assertEquals(List.of(), needed(items(HAT), item(HAT)));
	}

	@Test
	public void missingItemsAreAskedFor()
	{
		assertEquals(List.of(Set.of(BOOTS)), needed(items(HAT), item(HAT), item(BOOTS)));
	}

	@Test
	public void anyOfIsOneGroup()
	{
		assertEquals(List.of(Set.of(RING, RING_IMBUED)), needed(items(), any("Ring", item(RING), item(RING_IMBUED))));
		assertEquals(List.of(Set.of(10, 11, 12)), needed(items(), range("Staff", STAFF_FULL, STAFF_EMPTY)));
	}

	@Test
	public void onlyTheMissingPiecesOfASetAreAskedFor()
	{
		assertEquals(List.of(Set.of(TOP)), needed(items(HAT), all(item(HAT), item(TOP))));
	}

	@Test
	public void oneCompleteSetIsAskedForWhenSeveralWillDo()
	{
		ItemRequirement eitherSet = any("Full set", all(item(HAT), item(TOP)), all(item(RED_HAT), item(RED_TOP)));
		Set<Set<Integer>> groups = Set.copyOf(needed(items(), eitherSet));
		assertTrue(groups.toString(), groups.equals(Set.of(Set.of(HAT), Set.of(TOP)))
			|| groups.equals(Set.of(Set.of(RED_HAT), Set.of(RED_TOP))));
		assertEquals(List.of(Set.of(RED_TOP)), needed(items(RED_HAT), eitherSet));
	}

	@Test
	public void eachPieceOfASetKeepsItsVariants()
	{
		ItemRequirement set = all(any("Hat", item(HAT), item(HAT_TRIMMED)), item(TOP));
		assertEquals(Set.of(Set.of(HAT, HAT_TRIMMED), Set.of(TOP)), Set.copyOf(needed(items(), set)));
	}

	@Test
	public void emptySlotsAreNotAskedFor()
	{
		assertEquals(List.of(), needed(items(), emptySlot("Nothing on your head", EquipmentInventorySlot.HEAD)));
	}
}
