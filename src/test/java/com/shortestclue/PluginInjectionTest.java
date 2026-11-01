package com.shortestclue;

import static org.junit.Assert.assertFalse;

import java.lang.reflect.Field;
import javax.inject.Inject;
import net.runelite.client.plugins.Plugin;
import org.junit.Test;

public class PluginInjectionTest
{
	/**
	 * Since RuneLite 1.13.1 a plugin dependency only provides its services, so injecting another plugin stops this
	 * one from loading.
	 */
	@Test
	public void noOtherPluginIsInjected()
	{
		for (Field field : ShortestCluePlugin.class.getDeclaredFields())
		{
			if (field.isAnnotationPresent(Inject.class))
			{
				assertFalse(field.getName(), Plugin.class.isAssignableFrom(field.getType()));
			}
		}
	}
}
