package com.shortestclue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;

public class StashUnitsTest
{
	private static final int SLOT_UNIT = 100;
	private static final int BIT_UNIT = 101;
	private static final int EXTRA_BIT_UNIT = ObjectID.HH_ELITE_EXP9;
	private static final int NOT_A_UNIT = 102;
	private static final int ITEM = 1;

	/**
	 * A packed (x, y) as the game's enums hold it.
	 */
	private static int pack(int x, int y)
	{
		return (x << 14) | y;
	}

	private static final IntUnaryOperator BUILT_ENUM = unit -> unit == NOT_A_UNIT ? -1 : pack(2, 5); // medium, bit 5
	private static final IntUnaryOperator SLOT_ENUM = unit -> unit == SLOT_UNIT || unit == EXTRA_BIT_UNIT ? pack(3, 3) : -1;
	private static final IntUnaryOperator BIT_ENUM = unit -> unit == BIT_UNIT ? pack(2, 7) : -1; // second int, bit 7
	private static final IntUnaryOperator BUILT = varp -> varp == VarPlayerID.HH_CONSTRUCTED_MEDIUM ? 1 << 5 : 0;
	private static final IntUnaryOperator NOT_BUILT = varp -> 0;
	private static final IntUnaryOperator EMPTY_SLOTS = slot -> -1;
	private static final IntUnaryOperator ITEM_IN_SLOT_3 = slot -> slot == 3 ? ITEM : -1;

	private static Boolean chart(int unit, int[] ints, IntUnaryOperator varp, IntUnaryOperator slotItem)
	{
		return StashUnits.chartFilled(unit, ints, BUILT_ENUM, SLOT_ENUM, BIT_ENUM, varp, slotItem);
	}

	@Test
	public void builtIsTheTiersVarpBit()
	{
		assertTrue(StashUnits.built(BIT_UNIT, BUILT_ENUM, BUILT));
		assertFalse(StashUnits.built(BIT_UNIT, BUILT_ENUM, NOT_BUILT));
		assertFalse(StashUnits.built(NOT_A_UNIT, BUILT_ENUM, BUILT));
	}

	@Test
	public void unitsThatArentBuiltHoldNothing()
	{
		assertEquals(false, chart(BIT_UNIT, new int[]{0, 1 << 7, 0}, NOT_BUILT, EMPTY_SLOTS));
		assertNull(chart(NOT_A_UNIT, new int[3], BUILT, EMPTY_SLOTS));
	}

	@Test
	public void bitUnitsAreFilledByTheirBit()
	{
		assertEquals(true, chart(BIT_UNIT, new int[]{0, 1 << 7, 0}, BUILT, EMPTY_SLOTS));
		assertEquals(false, chart(BIT_UNIT, new int[]{1 << 7, 0, 1 << 7}, BUILT, EMPTY_SLOTS));
	}

	@Test
	public void slotUnitsAreFilledByAnItemInTheirSlot()
	{
		assertEquals(true, chart(SLOT_UNIT, new int[3], BUILT, ITEM_IN_SLOT_3));
		assertEquals(false, chart(SLOT_UNIT, new int[3], BUILT, EMPTY_SLOTS));
		assertNull("the slots never arrived", chart(SLOT_UNIT, new int[3], BUILT, null));
	}

	@Test
	public void someSlotUnitsAreAlsoFilledByABit()
	{
		assertEquals(true, chart(EXTRA_BIT_UNIT, new int[]{0, 1 << 31, 0}, BUILT, null));
		assertEquals(false, chart(EXTRA_BIT_UNIT, new int[3], BUILT, EMPTY_SLOTS));
	}

	@Test
	public void searchingPutsInOrTakesOut()
	{
		Map<Integer, Integer> outfit = Map.of(1, 1, 2, 1, 3, 5);
		Map<Integer, Integer> rest = Map.of(3, 5);
		assertEquals(true, StashUnits.putIn(outfit, rest));
		assertEquals(false, StashUnits.putIn(rest, outfit));
		assertNull("no change", StashUnits.putIn(rest, rest));
		assertNull("something else", StashUnits.putIn(Map.of(1, 1), Map.of(2, 1)));
	}

	@Test
	public void unitsAreSavedAsText()
	{
		Map<Integer, Boolean> units = Map.of(28959, false, 28958, true);
		assertEquals("28958:1,28959:0", StashUnits.encode(units));
		assertEquals(units, StashUnits.decode("28958:1,28959:0"));
		assertEquals(Map.of(28958, true), StashUnits.decode("28958:1,x:0,28960:2,28961"));
		assertEquals(Map.of(), StashUnits.decode(null));
	}
}
