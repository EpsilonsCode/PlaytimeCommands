package com.omicron.playtime_commands;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class PlaytimeCommands implements ModInitializer {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final String CONFIG_PATH = FabricLoader.getInstance().getConfigDir().toString() + "/playtime_commands_config.json";

	private static final String SAVED_PATH = FabricLoader.getInstance().getConfigDir().toString() + "/playtime_commands_saved.json";

	private static HashMap<String, Milestone> milestoneMap = new HashMap<>();
	// Define mod id in a common place for everything to reference
	public static final String MODID = "playtime_commands";
	// Directly reference a slf4j logger
	private static final Logger LOGGER = LogUtils.getLogger();
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");
		ServerLifecycleEvents.SERVER_STARTING.register(new ServerLifecycleEvents.ServerStarting() {
			@Override
			public void onServerStarting(MinecraftServer server) {
				LOGGER.info("HELLO from server starting");
				if(!new File(SAVED_PATH).exists())
					try {
						JsonObject jsonObject = new JsonObject();
						FileWriter writer = new FileWriter(SAVED_PATH);
						GSON.toJson(jsonObject, writer);
						writer.flush();
						writer.close();
					} catch (IOException e) {
						System.out.println(e);
					}

				if(new File(CONFIG_PATH).exists())
					try {
						JsonObject json = GSON.fromJson(new FileReader(CONFIG_PATH), JsonObject.class);
						for(Map.Entry<String, JsonElement> entry : json.entrySet())
						{
							ArrayList<String> commands = new ArrayList<>();
							GsonHelper.getAsJsonArray(entry.getValue().getAsJsonObject(), "commands").forEach((jsonElement -> {
								commands.add(jsonElement.toString());
							}));

							milestoneMap.put(entry.getKey(), new Milestone(GsonHelper.getAsInt(entry.getValue().getAsJsonObject(), "playtime"), commands.toArray(new String[0])));
						}

					} catch (FileNotFoundException e) {
						throw new RuntimeException(e);
					}
			}
		});
	}

	public static void onPlayerConnect(ClientConnection connection, ServerPlayerEntity serverPlayer)
	{
		LOGGER.info("HELLO from player logging in");
		float playtimeInSeconds = 0;
		//System.out.println(event.getEntity());

		try {
			Class.forName("org.mariadb.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			System.out.println(e);
		}
		//create connection for a server installed in localhost, with a user "root" with no password
		try (Connection conn = DriverManager.getConnection("jdbc:mariadb://localhost/player_data", "root", null)) {
			// create a Statement
			//System.out.println("sql2");
			Statement stmt = conn.createStatement();
			//execute query
			//System.out.println("sql1");
			ResultSet rs = stmt.executeQuery("SELECT Playtime FROM player_stats WHERE UUID = " + "'" + serverPlayer.getUuid() + "'");
			//position result to first
			//rs.first();

			while(rs.next())
			{
				playtimeInSeconds = Float.parseFloat(rs.getString(1));
			}

			//System.out.println(rs.getString(1)); //result is "Hello World!"
			//System.out.println("sql");
		} catch (SQLException throwables) {
			throwables.printStackTrace();
		}


		serverPlayer.sendSystemMessage(new LiteralText("You've been playing for: " + String.format("%.02f", playtimeInSeconds / 3600) + " hours!"), serverPlayer.getUuid());
		ArrayList<String> milestones = new ArrayList<>();
		JsonObject json = new JsonObject();
		JsonArray jsonArray = new JsonArray();
		if(new File(SAVED_PATH).exists())
		{
			try {
				json = GSON.fromJson(new FileReader(SAVED_PATH), JsonObject.class);
				for (Map.Entry<String, JsonElement> entry : json.entrySet())
				{
					if(serverPlayer.getUuid().toString().equals(entry.getKey()))
					{
						if(entry.getValue().isJsonArray())
						{
							jsonArray = entry.getValue().getAsJsonArray();
							entry.getValue().getAsJsonArray().forEach(jsonElement -> {
								milestones.add(jsonElement.toString());
							});
						}
					}
				}
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		else {

		}


		for(Map.Entry<String, Milestone> entry : milestoneMap.entrySet())
		{
			//System.out.println("MILESTONE@: " + entry.getKey());
			if(!milestones.contains('\"' + entry.getKey() + '\"'))
			{
				//System.out.println("test" + entry.getKey());
				if(entry.getValue().playtime <= playtimeInSeconds)
				{
					try {
						System.out.println("test2" + entry.getKey());
						jsonArray.add(entry.getKey());
						json.add(serverPlayer.getUuid().toString(), jsonArray);
						FileWriter writer = new FileWriter(SAVED_PATH);
						GSON.toJson(json, writer);
						writer.flush();
						writer.close();
					} catch (IOException e) {
						System.out.println(e);
					}
					if(serverPlayer.server instanceof DedicatedServer dedicatedServer)
					{
						for(String command : entry.getValue().commands)
						{
							command = command.replace("{username}", serverPlayer.getName().getString());
							StringBuffer sb = new StringBuffer(command);
							sb.deleteCharAt(sb.length() - 1);
							sb.deleteCharAt(0);
							dedicatedServer.executeRconCommand(sb.toString());
							//System.out.println(sb.toString());
						}
					}
				}
			}
		}
	}
}
