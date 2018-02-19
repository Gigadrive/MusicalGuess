package eu.thechest.musicalguess.listener;

import eu.thechest.chestapi.ChestAPI;
import eu.thechest.chestapi.event.NickChangeEvent;
import eu.thechest.chestapi.event.PlayerDataLoadedEvent;
import eu.thechest.chestapi.event.PlayerLocaleChangeEvent;
import eu.thechest.chestapi.game.GameManager;
import eu.thechest.chestapi.items.ItemUtil;
import eu.thechest.chestapi.server.GameState;
import eu.thechest.chestapi.server.ServerSettingsManager;
import eu.thechest.chestapi.user.ChestUser;
import eu.thechest.chestapi.util.StringUtils;
import eu.thechest.musicalguess.MusicalGuess;
import eu.thechest.musicalguess.MusicalSong;
import eu.thechest.musicalguess.cmd.MainExecutor;
import eu.thechest.musicalguess.user.MusicalPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.Inventory;

/**
 * Created by zeryt on 26.02.2017.
 */
public class MainListener implements Listener {
    @EventHandler
    public void onLogin(PlayerLoginEvent e){
        Player p = e.getPlayer();

        if(ServerSettingsManager.CURRENT_GAMESTATE != GameState.LOBBY){
            e.setResult(PlayerLoginEvent.Result.KICK_OTHER);
            e.setKickMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + "The game has already started.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e){
        Player p = e.getPlayer();

        e.setJoinMessage(null);

        p.teleport(MusicalGuess.getInstance().spawnLocation);

        if(MusicalGuess.lobbyCountdown == null && Bukkit.getOnlinePlayers().size() >= MusicalGuess.MIN_PLAYERS){
            MusicalGuess.getInstance().startCountdown();
        }
    }

    @EventHandler
    public void onLoaded(PlayerDataLoadedEvent e){
        Player p = e.getPlayer();

        ChestAPI.async(() -> {
            MusicalPlayer m = MusicalPlayer.get(p);
            ChestUser u = m.getUser();

            StringUtils.sendJoinMessage(p);

            if(m.getVictories() >= 10) u.achieve(5);
            if(m.getVictories() >= 25) u.achieve(6);
            if(m.getVictories() >= 50) u.achieve(7);

            p.setHealth(p.getMaxHealth());
            p.setFoodLevel(20);
            p.setGameMode(GameMode.ADVENTURE);
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            p.getInventory().setItem(8, ItemUtil.namedItem(Material.CHEST, org.bukkit.ChatColor.RED + u.getTranslatedMessage("Back to Lobby"), null));
            p.setExp((float) ((double) MusicalGuess.countdown / 40D));
            p.setLevel(MusicalGuess.countdown);

            for(Player all : Bukkit.getOnlinePlayers()){
                if(ChestUser.isLoaded(all)) ChestAPI.sync(() -> MusicalPlayer.get(all).updateScoreboard());
            }
        });
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e){
        Player p = e.getPlayer();
        ChestUser u = ChestUser.getUser(p);

        if(p.getItemInHand() != null && p.getItemInHand().getItemMeta() != null && p.getItemInHand().getItemMeta().getDisplayName() != null){
            if(p.getItemInHand().getItemMeta().getDisplayName().equals(org.bukkit.ChatColor.RED + u.getTranslatedMessage("Back to Lobby"))){
                u.connectToLobby();
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e){
        e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e){
        Player p = e.getPlayer();
        MusicalPlayer m = MusicalPlayer.get(p);
        ChestUser u = m.getUser();

        e.setQuitMessage(null);

        MusicalPlayer.unregister(p);

        if(ServerSettingsManager.CURRENT_GAMESTATE == GameState.LOBBY){
            StringUtils.sendQuitMessage(p);

            if(Bukkit.getOnlinePlayers().size()-1 < MusicalGuess.MIN_PLAYERS){
                MusicalGuess.getInstance().cancelCountdown();
            }

            for(Player all : Bukkit.getOnlinePlayers()){
                if(ChestUser.isLoaded(all)) MusicalPlayer.get(all).updateScoreboard(1);
            }
        } else if(ServerSettingsManager.CURRENT_GAMESTATE == GameState.INGAME){
            if(Bukkit.getOnlinePlayers().size()-1 == 1){
                /*Player winner = null;

                for(Player a : Bukkit.getOnlinePlayers()){
                    if(a != p){
                        winner = a;
                        break;
                    }
                }

                if(winner != null){
                    MusicalPlayer.get(winner).winGame();
                } else {
                    for(Player a : Bukkit.getOnlinePlayers()){
                        ChestUser.getUser(a).connectToLobby();
                    }
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"restart");
                }*/
                MusicalGuess.getInstance().chooseWinner(p);
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e){
        Player p = e.getPlayer();

        if(p.getLocation().getY() < 0){
            p.teleport(MusicalGuess.getInstance().spawnLocation);
            ChestUser.getUser(p).achieve(48);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e){
        if(e.getEntity() instanceof Player){
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent e){
        if(e.getEntity() instanceof Player){
            Player p = (Player)e.getEntity();
            e.setCancelled(true);
            p.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e){
        if(e.getPlayer() instanceof Player){
            if(e.getInventory().getName().equals("Choose a song")){
                if(!MusicalGuess.VOTED.contains(((Player)e.getPlayer()))){
                    if(MusicalGuess.MAY_GUESS == true){
                        Bukkit.getScheduler().scheduleSyncDelayedTask(MusicalGuess.getInstance(), new Runnable(){
                            public void run(){
                                MusicalGuess.openInv(((Player)e.getPlayer()));
                            }
                        }, 5);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onLocaleChange(PlayerLocaleChangeEvent e){
        e.getUser().clearScoreboard();
        MusicalPlayer.get(e.getPlayer()).updateScoreboard();
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent e){
        e.setCancelled(true);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e){
        Player p = e.getPlayer();
        ChestUser u = ChestUser.getUser(p);
        String msg = e.getMessage();

        if(!e.isCancelled() && ServerSettingsManager.ENABLE_CHAT == true && ServerSettingsManager.CURRENT_GAMESTATE != GameState.LOBBY){
            GameManager.getCurrentGames().get(0).addPlayerChatEvent(p,msg);
        }
    }

    @EventHandler
    public void onChange(NickChangeEvent e){
        if(ServerSettingsManager.CURRENT_GAMESTATE != GameState.LOBBY){
            for(Player all : Bukkit.getOnlinePlayers()){
                MusicalPlayer.get(all).updateScoreboard();
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e){
        e.setCancelled(true);
        if(e.getWhoClicked() instanceof Player){
            Player p = (Player)e.getWhoClicked();
            MusicalPlayer m = MusicalPlayer.get(p);
            ChestUser u = m.getUser();
            Inventory inv = e.getInventory();

            if(inv.getName().equals("Choose a song")){
                e.setCancelled(true);

                if(e.getCurrentItem() != null && e.getCurrentItem().getItemMeta() != null && e.getCurrentItem().getItemMeta().getDisplayName() != null){
                    /*String dis = e.getCurrentItem().getItemMeta().getDisplayName();
                    dis = ChatColor.stripColor(dis);

                    if(dis.startsWith("#")){
                        dis = dis.substring(1, dis.length());
                        String i = dis.split(" ")[0];
                        if(StringUtils.isValidInteger(i)){
                            int songID = Integer.parseInt(i);

                            p.closeInventory();
                            MusicalSong s = null;
                            for(MusicalSong ms : MusicalGuess.POTENTIAL_SONGS){
                                if(ms.id == songID) s = ms;
                            }

                            if(s != null){
                                p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("You've voted for the song %s!").replace("%s",ChatColor.YELLOW + s.title + ChatColor.GREEN));

                                if(MusicalGuess.CURRENT_SONG == s){
                                    MusicalGuess.CHOSE_RIGHT.add(p);
                                }
                            }
                        }
                    }*/

                    MusicalSong s = MusicalGuess.CURRENT_VOTE_SONGS.get(e.getRawSlot());
                    if(s != null){
                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + u.getTranslatedMessage("You've voted for the song %s by %a!").replace("%s",ChatColor.AQUA + s.title + ChatColor.GOLD).replace("%a",ChatColor.AQUA + s.artist + ChatColor.GOLD));

                        MusicalGuess.VOTED.add(p);
                        if(MusicalGuess.CURRENT_SONG.title.equals(s.title) && MusicalGuess.CURRENT_SONG.artist.equals(s.artist)){
                            MusicalGuess.CHOSE_RIGHT.add(p);
                        }
                    }
                    p.closeInventory();
                }
            }
        }
    }
}
