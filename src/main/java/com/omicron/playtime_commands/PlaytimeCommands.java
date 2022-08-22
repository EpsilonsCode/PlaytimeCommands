package com.omicron.playtime_commands;

import com.google.gson.*;

import net.minecraft.entity.player.ServerPlayerEntity;

import net.minecraft.server.dedicated.DedicatedServer;

import net.minecraft.util.text.StringTextComponent;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.server.ServerLifecycleHooks;


import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(PlaytimeCommands.MODID)
public class PlaytimeCommands
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String CONFIG_PATH = FMLPaths.CONFIGDIR.get().toString() + "/playtime_commands_config.json";

    private static final String SAVED_PATH = FMLPaths.CONFIGDIR.get().toString() + "/playtime_commands_saved.json";

    private static HashMap<String, Milestone> milestoneMap = new HashMap<>();
    // Define mod id in a common place for everything to reference
    public static final String MODID = "playtime_commands";
    // Directly reference a slf4j logger
    //private static final Logger LOGGER = LogManager.getLogger();

    public PlaytimeCommands()
    {


        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onServerStarting);
        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onEntityJoin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if(event.getEntity() instanceof ServerPlayerEntity)
        {
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) event.getEntity();
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
                ResultSet rs = stmt.executeQuery("SELECT Playtime FROM player_stats WHERE UUID = " + "'" + serverPlayer.getUUID() + "'");
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

            serverPlayer.sendMessage(new StringTextComponent("You've been playing for: " + String.format("%.02f", playtimeInSeconds / 3600) + " hours!"), serverPlayer.getUUID());
            ArrayList<String> milestones = new ArrayList<>();
            JsonObject json = new JsonObject();
            JsonArray jsonArray = new JsonArray();
            if(new File(SAVED_PATH).exists())
            {
                try {
                    json = GSON.fromJson(new FileReader(SAVED_PATH), JsonObject.class);
                    for (Map.Entry<String, JsonElement> entry : json.entrySet())
                    {
                        if(serverPlayer.getUUID().toString().equals(entry.getKey()))
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
                            json.add(serverPlayer.getUUID().toString(), jsonArray);
                            FileWriter writer = new FileWriter(SAVED_PATH);
                            GSON.toJson(json, writer);
                            writer.flush();
                            writer.close();
                        } catch (IOException e) {
                            System.out.println(e);
                        }
                        if(ServerLifecycleHooks.getCurrentServer() instanceof DedicatedServer)
                        {
                            DedicatedServer dedicatedServer = (DedicatedServer) ServerLifecycleHooks.getCurrentServer();
                            for(String command : entry.getValue().commands)
                            {
                                command = command.replace("{username}", serverPlayer.getName().getString());
                                StringBuffer sb = new StringBuffer(command);
                                sb.deleteCharAt(sb.length() - 1);
                                sb.deleteCharAt(0);
                                dedicatedServer.runCommand(sb.toString());
                                //System.out.println(sb.toString());
                            }
                        }
                    }
                }
            }
        }
    }



    // You can use SubscribeEvent and let the Event Bus discover methods to call
    public void onServerStarting(FMLDedicatedServerSetupEvent event)
    {
        // Do something when the server starts
        System.out.println("HELLO from server starting");
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


}