package com.shortestclue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cluescrolls.clues.emote.STASHUnit;
import org.junit.Test;

public class StashReturnTest
{
	private static final STASHUnit UNIT = STASHUnit.FINE_CLOTHES_ENTRANCE;
	private static final WorldPoint AT_UNIT = UNIT.getWorldPoints()[0];

	@Test
	public void itemsToPutBackAreSavedAsText()
	{
		StashReturn.Taken taken = new StashReturn.Taken(UNIT.getObjectId(),
			"Cheer at Iffie Nitter. Equip a chef hat and a red cape.", Map.of(1949, 1, 1007, 1));
		String text = taken.encode();
		assertEquals("unit=" + UNIT.getObjectId() + "\nclue=Cheer at Iffie Nitter. Equip a chef hat and a red cape.\nitems=1007:1,1949:1", text);
		assertEquals(taken, StashReturn.Taken.decode(text));
		assertNull(StashReturn.Taken.decode(null));
		assertNull("no items", StashReturn.Taken.decode("unit=1\nclue=x\nitems="));
		assertNull("damaged", StashReturn.Taken.decode("unit=x\nclue=x\nitems=1:1"));
	}

	@Test
	public void allTheItemsMustBeCarried()
	{
		Map<Integer, Integer> outfit = Map.of(1949, 1, 1007, 1);
		assertTrue(StashReturn.carriesAll(Map.of(1949, 1, 1007, 1, 995, 100), outfit));
		assertFalse(StashReturn.carriesAll(Map.of(1949, 1), outfit));
	}

	@Test
	public void goingFarOrTeleportingMovesOn()
	{
		WorldPoint near = AT_UNIT.dx(StashReturn.FAR_TILES);
		WorldPoint far = AT_UNIT.dx(StashReturn.FAR_TILES + 1);
		assertFalse(StashReturn.movedOn(null, near, UNIT));
		assertTrue(StashReturn.movedOn(null, far, UNIT));
		assertFalse("walking", StashReturn.movedOn(AT_UNIT.dx(2), AT_UNIT, UNIT));
		assertTrue("teleporting", StashReturn.movedOn(AT_UNIT.dy(StashReturn.TELEPORT_TILES + 1), AT_UNIT, UNIT));
		assertFalse("a unit upstairs is near", StashReturn.movedOn(null, AT_UNIT.dz(1), UNIT));
	}

	@Test
	public void theItemsThatCameAreCounted()
	{
		assertEquals(Map.of(1949, 1, 995, 5), StashUnits.added(Map.of(995, 10, 1007, 1), Map.of(995, 15, 1949, 1)));
	}
}
