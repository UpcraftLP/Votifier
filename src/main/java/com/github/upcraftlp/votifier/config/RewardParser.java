package com.github.upcraftlp.votifier.config;

import com.github.upcraftlp.votifier.ForgeVotifier;
import com.github.upcraftlp.votifier.api.RewardCreatedEvent;
import com.github.upcraftlp.votifier.api.reward.Reward;
import com.github.upcraftlp.votifier.reward.RewardChat;
import com.github.upcraftlp.votifier.reward.RewardCommand;
import com.github.upcraftlp.votifier.reward.RewardItem;
import com.github.upcraftlp.votifier.event.VoteEventHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.command.CommandBase;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

public class RewardParser {

    public static void init(FMLPreInitializationEvent event) {
        File configDir = new File(event.getModConfigurationDirectory(), "votifier/rewards");
        if(!configDir.exists()) {
            setupDefaultRewards(configDir);
        }

        File[] jsonFiles = configDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
        JsonParser parser = new JsonParser();
        int regCount = 0;
        for(File jsonFile : jsonFiles) {
            try {
                JsonObject root = parser.parse(new FileReader(jsonFile)).getAsJsonObject();
                if(!root.has("rewards")) {
                    ForgeVotifier.getLogger().error("cannot parse reward file {}!", jsonFile.getName());
                    continue;
                }
                JsonArray rewardArray = root.get("rewards").getAsJsonArray();
                for(int i = 0; i < rewardArray.size(); i++) {
                    JsonObject object = rewardArray.get(i).getAsJsonObject();
                    String type = object.get("type").getAsString();
                    Reward reward;
                    switch(type) {
                        case "command":
                            String commandRaw = object.get("command").getAsString();
                            reward = new RewardCommand(commandRaw);
                            break;
                        case "chat":
                            String msgRaw = object.get("message").getAsString();
                            boolean broadcast = object.has("broadcast") && object.get("broadcast").getAsBoolean();
                            boolean parseAsTellraw = object.has("tellraw") && object.get("tellraw").getAsBoolean();
                            reward = new RewardChat(msgRaw, broadcast, parseAsTellraw);
                            break;
                        case "item":
                            Item item = CommandBase.getItemByText(null, object.get("name").getAsString());
                            int count = object.get("count").getAsInt();
                            String nbtRaw = object.has("nbt") ? object.get("nbt").getAsString() : null;
                            reward = new RewardItem(item, count, nbtRaw);
                            break;
                        default: //allow for custom rewards from other mods
                            RewardCreatedEvent rewardEvent = new RewardCreatedEvent(type, object);
                            MinecraftForge.EVENT_BUS.post(rewardEvent);
                            reward = rewardEvent.getRewardResult();
                    }
                    if(reward != null) {
                        VoteEventHandler.addReward(reward);
                        regCount++;
                    }
                    else ForgeVotifier.getLogger().warn("ignoring unknown votifier reward type: {}", type);
                }
            }
            catch(Exception e) {
                ForgeVotifier.getLogger().error("error parsing reward file " + jsonFile.getName() + "!", e);
            }
            ForgeVotifier.getLogger().info("Votifier registered a total of {} rewards in {} files!", regCount, jsonFiles.length);
        }
    }

    private static void setupDefaultRewards(File rewardsDir) {
        File defaultConfig = new File(rewardsDir, "default_rewards.json");
        try {
            FileUtils.forceMkdirParent(defaultConfig);
            FileUtils.copyToFile(MinecraftServer.class.getClassLoader().getResourceAsStream("assets/" + ForgeVotifier.MODID +"/reward/default_rewards.json"), defaultConfig);
        } catch(IOException e) {
            ForgeVotifier.getLogger().error("Exception setting up the default reward config!", e);
        }
    }
}