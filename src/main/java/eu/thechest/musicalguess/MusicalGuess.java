package eu.thechest.musicalguess;

import com.xxmicloxx.NoteBlockAPI.NBSDecoder;
import com.xxmicloxx.NoteBlockAPI.RadioSongPlayer;
import com.xxmicloxx.NoteBlockAPI.Song;
import com.xxmicloxx.NoteBlockAPI.SongPlayer;
import eu.thechest.chestapi.ChestAPI;
import eu.thechest.chestapi.game.GameManager;
import eu.thechest.chestapi.items.ItemUtil;
import eu.thechest.chestapi.mysql.MySQLManager;
import eu.thechest.chestapi.server.GameState;
import eu.thechest.chestapi.server.GameType;
import eu.thechest.chestapi.server.ServerSettingsManager;
import eu.thechest.chestapi.server.ServerUtil;
import eu.thechest.chestapi.user.ChestUser;
import eu.thechest.chestapi.user.Rank;
import eu.thechest.chestapi.util.BountifulAPI;
import eu.thechest.chestapi.util.StringUtils;
import eu.thechest.musicalguess.cmd.MainExecutor;
import eu.thechest.musicalguess.listener.MainListener;
import eu.thechest.musicalguess.user.MusicalPlayer;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by zeryt on 26.02.2017.
 */
public class MusicalGuess extends JavaPlugin {
    public static final int MIN_PLAYERS = 3;
    public static BukkitTask lobbyCountdown;
    public static int countdown = 40;

    public Location spawnLocation;

    public int roundCountdown = 30;
    public BukkitTask roundCountdownTask;

    public static ArrayList<MusicalSong> SONGS = new ArrayList<MusicalSong>();
    public static ArrayList<MusicalSong> POTENTIAL_SONGS = new ArrayList<MusicalSong>();
    public static ArrayList<Player> CHOSE_RIGHT = new ArrayList<Player>();
    public static ArrayList<Player> VOTED = new ArrayList<Player>();
    public static MusicalSong CURRENT_SONG;
    public static int CURRENT_ROUND = 0;
    public static int MAX_ROUNDS = 0;
    public static BukkitTask ACTIONBAR_TASK;
    public static BukkitTask GUESSING_TIME;
    public static int GUESS_TIME_LEFT = 10;
    public static ArrayList<MusicalSong> CURRENT_VOTE_SONGS;
    public static boolean MAY_GUESS = false;

    public void onEnable(){
        saveDefaultConfig();
        instance = this;

        ServerSettingsManager.updateGameState(GameState.LOBBY);
        ServerSettingsManager.setMaxPlayers(16);
        ServerSettingsManager.RUNNING_GAME = GameType.MUSICAL_GUESS;
        ServerSettingsManager.PROTECT_ITEM_FRAMES = true;
        ServerSettingsManager.AUTO_OP = true;
        ServerSettingsManager.ADJUST_CHAT_FORMAT = true;
        ServerSettingsManager.VIP_JOIN = true;
        ServerSettingsManager.UPDATE_TAB_NAME_WITH_SCOREBOARD = true;
        ServerSettingsManager.ENABLE_CHAT = true;
        ServerSettingsManager.ENABLE_NICK = true;
        ServerSettingsManager.SHOW_FAME_TITLE_ABOVE_HEAD = true;
        ServerSettingsManager.MIN_PLAYERS = MIN_PLAYERS;
        ServerUtil.updateMapName("MusicalGuess");

        Bukkit.getPluginManager().registerEvents(new MainListener(), this);

        MainExecutor exec = new MainExecutor();
        getCommand("setspawn").setExecutor(exec);
        getCommand("start").setExecutor(exec);

        spawnLocation = new Location(Bukkit.getWorld(getConfig().getString("locations.spawn.world")),getConfig().getDouble("locations.spawn.x"),getConfig().getDouble("locations.spawn.y"),getConfig().getDouble("locations.spawn.z"),getConfig().getInt("locations.spawn.yaw"),getConfig().getInt("locations.spawn.pitch"));

        loadSongs();
    }

    public static void openInv(Player p){
        ArrayList<Material> discs = new ArrayList<Material>();
        discs.add(Material.RECORD_3);
        discs.add(Material.RECORD_4);
        discs.add(Material.RECORD_5);
        discs.add(Material.RECORD_6);
        discs.add(Material.RECORD_7);
        discs.add(Material.RECORD_8);
        discs.add(Material.RECORD_9);
        discs.add(Material.RECORD_10);
        discs.add(Material.RECORD_11);
        discs.add(Material.RECORD_12);
        discs.add(Material.GOLD_RECORD);

        Inventory inv = Bukkit.createInventory(null,9,"Choose a song");
        for(MusicalSong s : CURRENT_VOTE_SONGS){
            Collections.shuffle(discs);
            inv.addItem(ItemUtil.hideFlags(ItemUtil.namedItem(discs.get(0),ChatColor.WHITE.toString() + "#" + s.id + " " + ChatColor.GREEN + s.title,new String[]{ChatColor.GRAY + ChestUser.getUser(p).getTranslatedMessage("by ") + ChatColor.WHITE + s.artist})));
        }
        p.openInventory(inv);
    }

    public void startGuessingTime(MusicalSong currentSong){
        CURRENT_SONG = currentSong;
        MAY_GUESS = true;

        GUESSING_TIME = new BukkitRunnable(){
            @Override
            public void run() {
                if(GUESS_TIME_LEFT == 0){
                    MAY_GUESS = false;
                    cancel();
                    GUESS_TIME_LEFT = 10;
                    GUESSING_TIME = null;

                    for(Player all : Bukkit.getOnlinePlayers()){
                        ChestUser a = ChestUser.getUser(all);
                        all.closeInventory();
                        all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("The correct song was %s by %a!").replace("%s",ChatColor.AQUA + currentSong.title + ChatColor.GOLD).replace("%a",ChatColor.AQUA + currentSong.artist + ChatColor.GOLD));
                        if(CHOSE_RIGHT.size() == 0){
                            all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("Nobody chose the correct song!"));
                        } else if(CHOSE_RIGHT.size() == 1){
                            all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("%p was the only player to know the correct song!").replace("%p",CHOSE_RIGHT.get(0).getDisplayName() + ChatColor.GOLD));
                            if(CHOSE_RIGHT.contains(all)){
                                all.playSound(all.getEyeLocation(), Sound.LEVEL_UP,1f,1f);
                                MusicalPlayer.get(all).addPoints(2);
                                a.giveExp(4);
                            }
                        } else {
                            String t = "";
                            for(Player w : CHOSE_RIGHT){
                                if(t.equals("")){
                                    t = t + w.getDisplayName();
                                } else {
                                    t = t + ChatColor.GOLD + ", " + w.getDisplayName();
                                }

                                ChestUser.getUser(w).giveExp(2);
                            }

                            all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("The players %t knew the correct song!").replace("%t",t + ChatColor.GOLD));
                            if(CHOSE_RIGHT.contains(all)){
                                all.playSound(all.getEyeLocation(), Sound.LEVEL_UP,1f,1f);
                                MusicalPlayer.get(all).addPoints(1);
                            }
                        }
                    }

                    for(Player all : CHOSE_RIGHT){
                        GameManager.getCurrentGames().get(0).addMusicalGuessSongGuessEvent(all,currentSong.id);
                    }

                    for(Player all : Bukkit.getOnlinePlayers()) MusicalPlayer.get(all).updateScoreboard();

                    if(CURRENT_ROUND == MAX_ROUNDS){
                        chooseWinner();
                    } else {
                        startNewRound(true);
                    }
                } else {
                    GUESS_TIME_LEFT--;
                    for(Player all : Bukkit.getOnlinePlayers()){
                        all.setExp((float) ((double) GUESS_TIME_LEFT / 10D));
                        all.setLevel(GUESS_TIME_LEFT);
                    }
                }
            }
        }.runTaskTimer(this,20L,20L);
    }

    public void startActionBar(){
        ACTIONBAR_TASK = new BukkitRunnable(){
            public void run(){
                if(CURRENT_ROUND > 0){
                    for(Player all : Bukkit.getOnlinePlayers()){
                        ChestUser a = ChestUser.getUser(all);

                        BountifulAPI.sendActionBar(all, ChatColor.DARK_PURPLE + a.getTranslatedMessage("Round") + " " + ChatColor.YELLOW + CURRENT_ROUND + ChatColor.GRAY + "/" + ChatColor.YELLOW + MAX_ROUNDS);
                    }
                }
            }
        }.runTaskTimer(this,20L,20L);
    }

    public void startNewRound(boolean delayed){
        if(ServerSettingsManager.CURRENT_GAMESTATE == GameState.INGAME){
            CHOSE_RIGHT.clear();
            VOTED.clear();


            if(delayed){
                ServerSettingsManager.ENABLE_CHAT = true;

                for(Player all : Bukkit.getOnlinePlayers()){
                    ChestUser a = ChestUser.getUser(all);
                    all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + a.getTranslatedMessage("Chat has been enabled."));
                    all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("The next round starts in 5 seconds!"));
                    all.setLevel(0);
                    all.setExp(0);
                    MusicalPlayer.get(all).updateScoreboard();
                }

                Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable(){
                   public void run(){
                       startNewRound(false);
                   }
                }, 5*20);
            } else {
                ServerSettingsManager.ENABLE_CHAT = false;
                for(Player all : Bukkit.getOnlinePlayers()){
                    ChestUser a = ChestUser.getUser(all);
                    all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + a.getTranslatedMessage("Chat has been disabled."));
                }

                MusicalSong nextSong = SONGS.get(0);
                SONGS.remove(nextSong);
                CURRENT_ROUND++;

                SongPlayer sp = new RadioSongPlayer(nextSong.song);
                for(Player all : Bukkit.getOnlinePlayers()) sp.addPlayer(all);
                sp.setAutoDestroy(true);
                sp.setPlaying(true);

                roundCountdown = 30;
                roundCountdownTask = new BukkitRunnable(){
                    @Override
                    public void run() {
                        if(roundCountdown == 0){
                            cancel();
                            roundCountdownTask = null;

                            sp.setPlaying(false);

                            ArrayList<MusicalSong> songs = new ArrayList<MusicalSong>();
                            songs.add(nextSong);
                            while(songs.size() < 5){
                                Collections.shuffle(POTENTIAL_SONGS);
                                if(!MusicalSong.isIn(POTENTIAL_SONGS.get(0),songs)) songs.add(POTENTIAL_SONGS.get(0));
                            }

                            Collections.shuffle(songs);

                            CURRENT_VOTE_SONGS = songs;

                            for(Player all : Bukkit.getOnlinePlayers()){
                                all.setExp(0);
                                all.setLevel(0);
                                sp.removePlayer(all);
                                ChestUser a = ChestUser.getUser(all);

                                openInv(all);
                            }

                            startGuessingTime(nextSong);
                        } else {
                            for(Player all : Bukkit.getOnlinePlayers()){
                                all.setExp((float) ((double) roundCountdown / 30D));
                                all.setLevel(roundCountdown);
                            }

                            roundCountdown--;
                        }
                    }
                }.runTaskTimer(this,20L,20L);
            }
        }
    }

    public void chooseWinner(){
        chooseWinner(null);
    }

    public void chooseWinner(Player toExclude){
        ArrayList<Player> potentials = new ArrayList<Player>();

        int highestPoints = 0;

        for(Player all : Bukkit.getOnlinePlayers()){
            if(toExclude != null && toExclude == all) continue;
            if(MusicalPlayer.get(all).getCurrentPoints() > highestPoints){
                potentials.clear();
                potentials.add(all);
                highestPoints = MusicalPlayer.get(all).getCurrentPoints();
            } else if(MusicalPlayer.get(all).getCurrentPoints() == highestPoints){
                potentials.add(all);
            }
        }

        Collections.shuffle(potentials);
        Player p = potentials.get(0);
        MusicalPlayer m = MusicalPlayer.get(p);
        ChestUser u = m.getUser();
        GameManager.getCurrentGames().get(0).getWinners().add(p.getUniqueId());
        GameManager.getCurrentGames().get(0).setCompleted(true);
        GameManager.getCurrentGames().get(0).saveData();

        m.addVictories(1);
        if(m.getVictories() >= 10) u.achieve(5);
        if(m.getVictories() >= 25) u.achieve(6);
        if(m.getVictories() >= 50) u.achieve(7);

        if(m.getCurrentPoints() >= 16) u.achieve(4);

        for(Player all : Bukkit.getOnlinePlayers()){
            ChestUser.getUser(all).addCoins(MusicalPlayer.get(all).getCurrentPoints());
            ChestUser.getUser(all).giveExp(MusicalPlayer.get(all).getCurrentPoints()*1.5);
            if(ChestUser.getUser(all).hasPermission(Rank.VIP)){
                all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + ChestUser.getUser(all).getTranslatedMessage("The player %p has won the game with %c points! Congratulations!").replace("%p",ChestUser.getUser(p).getRank().getColor() + p.getName() + ChatColor.GOLD).replace("%c",ChatColor.AQUA.toString() + m.getCurrentPoints() + ChatColor.GOLD));
            } else {
                all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + ChestUser.getUser(all).getTranslatedMessage("The player %p has won the game with %c points! Congratulations!").replace("%p",p.getDisplayName() + ChatColor.GOLD).replace("%c",ChatColor.AQUA.toString() + m.getCurrentPoints() + ChatColor.GOLD));
            }
            all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + ChestUser.getUser(all).getTranslatedMessage("Chat has been enabled."));
            if(ChestUser.getUser(all).hasPermission(Rank.VIP)){
                BountifulAPI.sendTitle(all,1*20,5*20,1*20,ChestUser.getUser(p).getRank().getColor() + p.getName(),ChatColor.GRAY + ChestUser.getUser(all).getTranslatedMessage("is the WINNER!"));
            } else {
                BountifulAPI.sendTitle(all,1*20,5*20,1*20,p.getDisplayName(),ChatColor.GRAY + ChestUser.getUser(all).getTranslatedMessage("is the WINNER!"));
            }
        }

        u.playVictoryEffect();

        ServerSettingsManager.updateGameState(GameState.ENDING);
        ServerSettingsManager.ENABLE_CHAT = true;

        ChestAPI.giveAfterGameCrate(new Player[]{p});

        Bukkit.getScheduler().scheduleSyncDelayedTask(this,new Runnable(){
            public void run(){
                for(Player all : Bukkit.getOnlinePlayers()){
                    ChestUser a = ChestUser.getUser(all);

                    a.sendGameLogMessage(GameManager.getCurrentGames().get(0).getID());
                }
            }
        }, 5*20);

        Bukkit.getScheduler().scheduleSyncDelayedTask(this,new Runnable(){
            public void run(){
                for(Player all : Bukkit.getOnlinePlayers()){
                    ChestUser a = ChestUser.getUser(all);

                    a.sendAfterGamePremiumAd();
                    a.connectToLobby();
                }
            }
        }, 10*20);

        Bukkit.getScheduler().scheduleSyncDelayedTask(this,new Runnable(){
            public void run(){
                //Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"restart");
                ChestAPI.stopServer();
            }
        }, 15*20);
    }

    private void loadSongs(){
        String songsFolder = getDataFolder().getAbsolutePath() + "/Songs/";
        new File(songsFolder).mkdirs();

        try {
            PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("SELECT * FROM `mg_songs` ORDER BY RAND() DESC LIMIT 8");
            ResultSet rs = ps.executeQuery();
            rs.beforeFirst();

            while(rs.next()){
                int id = rs.getInt("id");
                System.out.println("Loading Song: #" + id);
                String title = rs.getString("title");
                String artist = rs.getString("artist");

                InputStream inputStream = rs.getBlob("songFile").getBinaryStream();
                OutputStream outputStream = new FileOutputStream(songsFolder + "song" + id + ".nbs");

                int bytesRead = -1;
                byte[] buffer = new byte[4096];
                while((bytesRead = inputStream.read(buffer)) != -1){
                    outputStream.write(buffer, 0, bytesRead);
                }

                inputStream.close();
                outputStream.close();

                File f = new File(songsFolder + "song" + id + ".nbs");

                if(f.exists()){
                    Song s = NBSDecoder.parse(f);

                    SONGS.add(new MusicalSong(id,title,artist,s));
                } else {
                    System.err.println("Failed to load Song #" + id + ": No such file or directory.");
                }
            }

            MySQLManager.getInstance().closeResources(rs,ps);

            MAX_ROUNDS = SONGS.size();
        } catch(Exception e){
            System.err.println("Unhandled exception whilst loading song:");
            e.printStackTrace();
        }

        try {
            PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("SELECT * FROM `mg_songs`");
            ResultSet rs = ps.executeQuery();
            rs.beforeFirst();
            while(rs.next()){
                POTENTIAL_SONGS.add(new MusicalSong(rs.getInt("id"),rs.getString("title"),rs.getString("artist"),null));
            }
            MySQLManager.getInstance().closeResources(rs,ps);
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public void onDisable(){
        for(Player all : Bukkit.getOnlinePlayers()){
            MusicalPlayer.get(all);
            MusicalPlayer.unregister(all);
        }
    }

    public void setSpawn(Location loc){
        spawnLocation = loc;

        getConfig().set("locations.spawn.world",loc.getWorld().getName());
        getConfig().set("locations.spawn.x",loc.getX());
        getConfig().set("locations.spawn.y",loc.getY());
        getConfig().set("locations.spawn.z",loc.getZ());
        getConfig().set("locations.spawn.yaw",loc.getYaw());
        getConfig().set("locations.spawn.pitch",loc.getPitch());
        saveConfig();
    }

    public void startCountdown(){
        if(lobbyCountdown == null){
            lobbyCountdown = new BukkitRunnable() {
                @Override
                public void run() {
                    if(countdown > 0){
                        for(Player all : Bukkit.getOnlinePlayers()){
                            all.setExp((float) ((double) countdown / 40D));
                            all.setLevel(countdown);
                        }
                    } else {
                        for(Player all : Bukkit.getOnlinePlayers()){
                            all.setExp(0);
                            all.setLevel(0);
                        }
                    }

                    if(countdown == 60 || countdown == 30 || countdown == 20 || countdown == 10 || countdown == 5 || countdown == 4 || countdown == 3 || countdown == 2 || countdown == 1){
                        for(Player all : Bukkit.getOnlinePlayers()){
                            if(!ChestUser.isLoaded(all)) return;
                            ChestUser a = ChestUser.getUser(all);
                            //all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("The game starts in %ts!").replace("%t",ChatColor.GREEN + String.valueOf(countdown) + ChatColor.GOLD));
                            all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("The lobby phase ends in %s seconds!").replace("%s",ChatColor.AQUA.toString() + countdown + ChatColor.GOLD.toString()));
                        }
                    } else if(countdown == 0){
                        cancel();
                        lobbyCountdown = null;

                        ServerSettingsManager.updateGameState(GameState.INGAME);
                        ServerSettingsManager.VIP_JOIN = false;
                        ServerSettingsManager.ENABLE_CHAT = false;
                        GameManager.initializeNewGame(GameType.MUSICAL_GUESS,null);

                        for(Player all : Bukkit.getOnlinePlayers()){
                            if(!ChestUser.isLoaded(all)) return;
                            MusicalPlayer m = MusicalPlayer.get(all);
                            ChestUser a = ChestUser.getUser(all);
                            all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("The game starts NOW!"));
                            all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + a.getTranslatedMessage("Chat has been disabled."));
                            a.clearScoreboard();
                            m.updateScoreboard();
                            m.addPlayedGames(1);
                            GameManager.getCurrentGames().get(0).getParticipants().add(all.getUniqueId());
                            all.getInventory().clear();
                            all.getInventory().setArmorContents(null);
                        }

                        startNewRound(true);
                        startActionBar();
                        startDiscoArmorTask();
                    }

                    countdown--;
                }
            }.runTaskTimer(this,20L,20L);
        }
    }

    public static void startDiscoArmorTask(){
        Bukkit.getScheduler().scheduleSyncRepeatingTask(MusicalGuess.getInstance(),new Runnable(){
            public void run(){
                for(Player p : Bukkit.getOnlinePlayers()){
                    ChestUser u = ChestUser.getUser(p);

                    if(u.hasGamePerk(2)){
                        Color color = Color.fromRGB(StringUtils.randomInteger(0,255),StringUtils.randomInteger(0,255),StringUtils.randomInteger(0,255));

                        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
                        LeatherArmorMeta helmetM = (LeatherArmorMeta)helmet.getItemMeta();
                        helmetM.setColor(color);
                        helmet.setItemMeta(helmetM);

                        ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
                        LeatherArmorMeta chestplateM = (LeatherArmorMeta)helmet.getItemMeta();
                        chestplateM.setColor(color);
                        chestplate.setItemMeta(chestplateM);

                        ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);
                        LeatherArmorMeta leggingsM = (LeatherArmorMeta)leggings.getItemMeta();
                        leggingsM.setColor(color);
                        leggings.setItemMeta(leggingsM);

                        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
                        LeatherArmorMeta bootsM = (LeatherArmorMeta)boots.getItemMeta();
                        bootsM.setColor(color);
                        boots.setItemMeta(bootsM);

                        p.getInventory().setHelmet(helmet);
                        p.getInventory().setChestplate(chestplate);
                        p.getInventory().setLeggings(leggings);
                        p.getInventory().setBoots(boots);
                    }
                }
            }
        }, 20l,20l);
    }

    public void cancelCountdown(){
        if(lobbyCountdown != null){
            lobbyCountdown.cancel();
            lobbyCountdown = null;
            countdown = 40;

            for(Player all : Bukkit.getOnlinePlayers()){
                all.setExp((float) ((double) countdown / 40D));
                all.setLevel(countdown);

                ChestUser a = ChestUser.getUser(all);
                all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + a.getTranslatedMessage("The countdown has been cancelled."));
            }
        }
    }

    private static MusicalGuess instance;
    public static MusicalGuess getInstance(){
        return instance;
    }
}
