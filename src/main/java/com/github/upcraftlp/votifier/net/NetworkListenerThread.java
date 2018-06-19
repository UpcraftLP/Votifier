package com.github.upcraftlp.votifier.net;

import com.github.upcraftlp.votifier.ForgeVotifier;
import com.github.upcraftlp.votifier.api.VoteReceivedEvent;
import com.github.upcraftlp.votifier.util.RSAUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.ParseException;
import java.text.SimpleDateFormat;

public class NetworkListenerThread extends Thread {

    private final String host;
    private final int port;
    private boolean isRunning = true;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-DD HH:mm:ss Z");

    public NetworkListenerThread(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void run() {
        this.setName("Vote-Listener");
        this.setPriority(Thread.MIN_PRIORITY);
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(this.host, this.port));
            ForgeVotifier.getLogger().info("votifier running on {}:{}", this.host, this.port);
            while(this.isRunning) {
                try(Socket socket = serverSocket.accept()) {
                    socket.setSoTimeout(5000); //workaround for slow connections
                    try(BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())); InputStream inputStream = socket.getInputStream()) {
                        writer.write("FORGE VOTIFIER running on [" + ForgeVotifier.VERSION + "]");
                        //writer.write("VOTIFIER 1.0.0");
                        writer.newLine();
                        writer.flush();

                        byte[] bytes = new byte[256];
                        inputStream.read(bytes, 0, bytes.length);
                        String[] lines = new String(RSAUtil.decrypt(bytes, RSAUtil.getKeyPair().getPrivate())).split("\n");
                        if(lines.length < 5) {
                            error(lines);
                        } else {
                            String opcode = lines[0];
                            if("VOTE".equals(opcode)) {
                                String service = lines[1];
                                String username = lines[2];
                                String address = lines[3];
                                String timestampString = lines[4];
                                long timestamp;
                                try {
                                    timestamp = DATE_FORMAT.parse(timestampString).getTime();
                                } catch(ParseException e) {
                                    error(lines);
                                    ForgeVotifier.getLogger().error("invalid vote timestamp!", e);
                                    continue; //timestamp is invalid
                                }
                                FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(() -> { //ensure we are not handling the event on the network thread
                                    ForgeVotifier.getLogger().info("received vote from {} at {} from service {}", username, timestamp, service);
                                    EntityPlayerMP player = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUsername(username);
                                    if(player != null) { //TODO store votes that were made offline and delay event?
                                        MinecraftForge.EVENT_BUS.post(new VoteReceivedEvent(player, service, address, timestamp));
                                    }
                                });
                            } else {
                                error(lines);
                            }
                        }
                    } catch(Exception e) {
                        ForgeVotifier.getLogger().error("Error handling socket connection!", e);
                    }
                } catch(Exception e) {
                    ForgeVotifier.getLogger().error("Error handling socket connection!", e);
                }
            }
        }
        catch(Exception e) {
            this.isRunning = false;
            ForgeVotifier.getLogger().error("Votifier network error! Host: " + this.host + ", Port: " + this.port, e);
        }
        ForgeVotifier.getLogger().info("votifier thread stopped!");
    }

    public void shutdown() {
        isRunning = false;
    }

    private static void error(String[] input) {
        StringBuilder builder = new StringBuilder();
        for(String line : input) {
            builder.append(line).append("\n");
        }
        ForgeVotifier.getLogger().error("Votifier: incorrect vote received:\n-----------------------------------------------------\n{}-----------------------------------------------------", builder.toString());
    }
}