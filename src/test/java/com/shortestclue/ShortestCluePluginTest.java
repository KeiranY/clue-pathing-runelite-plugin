package com.shortestclue;

import com.shortestclue.ShortestCluePlugin;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ShortestCluePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ShortestCluePlugin.class);
		RuneLite.main(args);
	}
}