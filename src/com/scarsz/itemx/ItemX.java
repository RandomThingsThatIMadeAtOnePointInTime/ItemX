package com.scarsz.itemx;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings({"deprecation", "unused", "ConstantConditions"})
public class ItemX extends JavaPlugin {

    // uncomment to restrict deleting to specific uuids
    //private List<UUID> uuidsAllowedToDelete = Arrays.asList(UUID.fromString("ba6a6a1d-d69c-4a3b-921b-2ad3ce5211f5"), UUID.fromString("2d1dbb2a-3fa9-4536-bd4d-c9efe125b68f"), UUID.fromString("76aa1167-6da8-43f3-a509-7b20272063b7"));

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

        // uncomment to restrict deleting to specific uuids
        //if (operation == Operation.DELETE && sender instanceof Player && !uuidsAllowedToDelete.contains(((Player) sender).getUniqueId())) {
        //    sender.sendMessage(ChatColor.RED + "You're not allowed to delete shit. Sorry.");
        //    return true;
        //}

        List<String> idsToFind = new LinkedList<>();
        argsList.forEach(s -> {
            if (s.matches("\\d*:?\\d+")) idsToFind.add(s);
        });

        for (String id : idsToFind) {
            // get current time
            long startMs = System.currentTimeMillis();
            // make new atomic integer for counting hits
            AtomicInteger found = new AtomicInteger();

            sender.sendMessage(ChatColor.DARK_RED + "STARTING SEARCH FOR " + id);

            for (World world : Bukkit.getWorlds()) {
                List<Chunk> chunksToSearch = Arrays.asList(world.getLoadedChunks());
                sender.sendMessage(String.format("%sSearching world %s%s%s for %s%s%s (%s%s%s chunks)", ChatColor.RED, ChatColor.WHITE, world.getName(), ChatColor.RED, ChatColor.WHITE, id, ChatColor.RED, ChatColor.WHITE, chunksToSearch.size(), ChatColor.RED));

                int partitionSize = chunksToSearch.size() / 4;
                List<List<Chunk>> chunkQueues = new LinkedList<>();
                for (int i = 0; i < chunksToSearch.size(); i += partitionSize) {
                    chunkQueues.add(chunksToSearch.subList(i, Math.min(i + partitionSize, chunksToSearch.size())));
                }

                ArrayList<Thread> threads = new ArrayList<>();
                final int searchLimit = 1000;
                boolean force = argsList.contains("force") && (sender.isOp() || sender.hasPermission("itemx.force"));
                boolean delete = operation == Operation.DELETE;

                // create new thread for each queue of chunks
                chunkQueues.forEach(chunks -> threads.add(new Thread(() -> blockSearchWork(chunks, force, found, searchLimit, sender, id, delete))));

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
            }

            sender.sendMessage(ChatColor.DARK_RED + "Finished search for " + ChatColor.WHITE + id + ChatColor.DARK_RED + ". Found " + ChatColor.WHITE + found.get() + ChatColor.DARK_RED + " matches in " + ChatColor.WHITE + (System.currentTimeMillis() - startMs) + "ms");
        }

        return true;
    }

    private void blockSearchWork(List<Chunk> chunks, boolean force, AtomicInteger found, int searchLimit, CommandSender sender, String id, Boolean delete) {
        String[] split = id.split(":");
        Integer itemId = Integer.valueOf(split[0]);
        Integer dataId = split.length == 2 ? Integer.valueOf(split[1]) : 0;

        masterLoop:
        for (Chunk chunk : chunks) {
            int x = 0;
            while (x < 16) {
                int y = 0;
                while (y < chunk.getWorld().getMaxHeight() - 1) {
                    int z = 0;
                    while (z < 16) {
                        if (!force && found.intValue() > searchLimit && !delete) break masterLoop;

                        Block block = chunk.getBlock(x, y, z);
                        if (block.getTypeId() == itemId && Byte.toUnsignedInt(block.getData()) == dataId) {
                            if (delete) Bukkit.getScheduler().scheduleSyncDelayedTask(this, new BukkitRunnable() { public void run() { block.setType(Material.AIR); } });

                            String message = ChatColor.RED + "Chunk " + ChatColor.WHITE + chunk.getX() + ChatColor.RED + "x" + ChatColor.WHITE + chunk.getZ() + ChatColor.RED + " found match at X" + ChatColor.WHITE + block.getX() + ChatColor.RED + " Y" + ChatColor.WHITE + block.getY() + ChatColor.RED + " Z" + ChatColor.WHITE + block.getZ() + ChatColor.RED;
                            if (delete) message += ": " + ChatColor.WHITE + "DELETED";
                            sender.sendMessage(message);

                            found.incrementAndGet();
                        }
                        ++z;
                    }
                    ++y;
                }
                ++x;
            }
        }

//        // search online player's inventories
//        for (Player player : Bukkit.getOnlinePlayers()) {
//            Inventory inventory = player.getInventory();
//            List<ItemStack> contents = Arrays.asList(inventory.getContents());
//            List<ItemStack> contentsToRemove = new ArrayList<>();
//            contents.forEach(itemStack -> {
//                if (itemStack.getTypeId() == itemId && Byte.toUnsignedInt(itemStack.getData().getData()) == dataId) {
//                    contentsToRemove.add(itemStack);
//                }
//            });
//            contents.removeAll(contentsToRemove);
//            inventory.setContents((ItemStack[]) contents.toArray());
//        }

    }

    private enum Operation {
        DELETE, FIND
    }

}
