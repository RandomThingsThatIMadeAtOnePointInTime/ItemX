package com.scarsz.itemx;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@SuppressWarnings({"deprecation", "unused", "ConstantConditions"})
public class ItemX extends JavaPlugin {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;

        List<String> argsList = new LinkedList<>();
        Collections.addAll(argsList, args);

        Operation operation = null;
        if (argsList.contains("find")) operation = Operation.FIND;
        if (argsList.contains("delete")) operation = Operation.DELETE;

        if (operation == null) {
            sender.sendMessage(String.format("%sCouldn't determine if you wanted to %sfind%s or %sdelete", ChatColor.RED, ChatColor.WHITE, ChatColor.RED, ChatColor.WHITE));
            return false;
        }

        if (operation == Operation.DELETE && sender instanceof Player && (!sender.isOp() && !sender.hasPermission("itemx.delete"))) {
            sender.sendMessage(ChatColor.RED + "You're not allowed to delete shit. Sorry.");
            return true;
        }

        List<String> idsToFind = new LinkedList<>();
        argsList.forEach(s -> {
            if (s.matches("\\d*:?\\d+")) idsToFind.add(s);
        });

        for (String id : idsToFind) {
            // get current time
            long startMs = System.currentTimeMillis();
            // create new thread-safe queue for all chunk queues to report matches to
            Queue<Location> matches = new ConcurrentLinkedQueue<>();

            sender.sendMessage(ChatColor.DARK_RED + "STARTING SEARCH FOR " + id + " IN LOADED CHUNKS");

            for (World world : Bukkit.getWorlds()) {
                List<Chunk> chunksToSearch = Arrays.asList(world.getLoadedChunks());
                sender.sendMessage(String.format("%sSearching world %s%s%s for %s%s%s (%s%s%s chunks)", ChatColor.RED, ChatColor.WHITE, world.getName(), ChatColor.RED, ChatColor.WHITE, id, ChatColor.RED, ChatColor.WHITE, chunksToSearch.size(), ChatColor.RED));

                int partitionSize = chunksToSearch.size() / 4;
                List<Queue<Chunk>> chunkQueues = new LinkedList<>();
                for (int i = 0; i < chunksToSearch.size(); i += partitionSize)
                    chunkQueues.add(new ArrayDeque<>(chunksToSearch.subList(i, Math.min(i + partitionSize, chunksToSearch.size()))));
                //chunkQueues.add(chunksToSearch.subList(i, Math.min(i + partitionSize, chunksToSearch.size())));

                ArrayList<Thread> threads = new ArrayList<>();
                final int searchLimit = 1000;
                boolean force = argsList.contains("force") && (sender.isOp() || sender.hasPermission("itemx.force"));
                boolean delete = operation == Operation.DELETE;

                // create new thread for each queue of chunks
                chunkQueues.forEach(chunks -> threads.add(new Thread(() -> blockSearchWork(chunks, matches, delete, force, searchLimit, sender, id))));

                // start new threads
                threads.forEach(Thread::start);

                // join threads to wait for all threads to finish
                threads.forEach(thread -> {
                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });

                if (delete) Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
                    for (Location match : matches) match.getWorld().getBlockAt(match).setType(Material.AIR);
                });
            }

            sender.sendMessage(ChatColor.DARK_RED + "Finished search for " + ChatColor.WHITE + id + ChatColor.DARK_RED + ". Found " + ChatColor.WHITE + matches.size() + ChatColor.DARK_RED + " matches in " + ChatColor.WHITE + (System.currentTimeMillis() - startMs) + "ms");
        }

        return true;
    }

    private void blockSearchWork(Queue<Chunk> chunks, Queue<Location> matches, Boolean delete, boolean force, int searchLimit, CommandSender sender, String id) {
        String[] split = id.split(":");
        Integer itemId = Integer.valueOf(split[0]);
        Integer dataId = split.length == 2 ? Integer.valueOf(split[1]) : 0;

        masterLoop:
        for (Chunk chunk : chunks) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < chunk.getWorld().getMaxHeight() - 1; y++) {
            if (!force && matches.size() > searchLimit && !delete) break masterLoop;

            Block block = chunk.getBlock(x, y, z);
            if (block.getTypeId() == itemId && Byte.toUnsignedInt(block.getData()) == dataId) {
                sender.sendMessage(ChatColor.RED + "Chunk " + ChatColor.WHITE + chunk.getX() + ChatColor.RED + "x" + ChatColor.WHITE + chunk.getZ() + ChatColor.RED + " found match at X" + ChatColor.WHITE + block.getX() + ChatColor.RED + " Y" + ChatColor.WHITE + block.getY() + ChatColor.RED + " Z" + ChatColor.WHITE + block.getZ());
                matches.add(block.getLocation());
            }
        }
    }

    private enum Operation {
        DELETE, FIND
    }

}
