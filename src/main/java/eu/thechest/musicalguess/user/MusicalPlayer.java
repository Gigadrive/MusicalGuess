package eu.thechest.musicalguess.user;

import com.google.common.collect.Iterables;
import eu.thechest.chestapi.ChestAPI;
import eu.thechest.chestapi.mysql.MySQLManager;
import eu.thechest.chestapi.server.GameState;
import eu.thechest.chestapi.server.ServerSettingsManager;
import eu.thechest.chestapi.server.ServerUtil;
import eu.thechest.chestapi.user.ChestUser;
import eu.thechest.chestapi.user.Rank;
import eu.thechest.chestapi.util.StringUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.*;

/**
 * Created by zeryt on 26.02.2017.
 */
public class MusicalPlayer {
    public static HashMap<Player,MusicalPlayer> STORAGE = new HashMap<Player,MusicalPlayer>();

    public static MusicalPlayer get(Player p){
        if(STORAGE.containsKey(p)){
            return STORAGE.get(p);
        } else {
            new MusicalPlayer(p);

            if(STORAGE.containsKey(p)){
                return STORAGE.get(p);
            } else {
                return null;
            }
        }
    }

    public static void unregister(Player p){
        if(STORAGE.containsKey(p)){
            STORAGE.get(p).saveData();
            STORAGE.remove(p);
        }
    }

    private Player p;
    private int startPoints;
    private int points;
    private int startPlayedGames;
    private int playedGames;
    private int startVictories;
    private int victories;
    private Timestamp firstJoin;

    public boolean allowSaveData = true;

    public MusicalPlayer(Player p){
        this.p = p;

        try {
            PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("SELECT * FROM `mg_stats` WHERE `uuid` = ?");
            ps.setString(1,p.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();

            if(rs.first()){
                startPoints = rs.getInt("points");
                points = 0;
                startPlayedGames = rs.getInt("playedGames");
                playedGames = 0;
                startVictories = rs.getInt("victories");
                victories = 0;
                firstJoin = rs.getTimestamp("firstJoin");

                STORAGE.put(p,this);
            } else {
                PreparedStatement insert = MySQLManager.getInstance().getConnection().prepareStatement("INSERT INTO `mg_stats` (`uuid`) VALUES(?)");
                insert.setString(1,p.getUniqueId().toString());
                insert.execute();
                insert.close();

                new MusicalPlayer(p);
            }

            MySQLManager.getInstance().closeResources(rs,ps);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public Player getPlayer(){
        return this.p;
    }

    public ChestUser getUser(){
        return ChestUser.getUser(this.p);
    }

    public void addPoints(int p){
        this.points += p;
    }

    public int getCurrentPoints(){
        return this.points;
    }

    public int getPoints(){
        return this.startPoints+this.points;
    }

    public void addPlayedGames(int g){
        this.playedGames += g;
    }

    public int getPlayedGames(){
        return this.startPlayedGames+this.playedGames;
    }

    public void addVictories(int v){
        this.victories += v;
    }

    public int getVictories(){
        return this.startVictories+this.victories;
    }

    public void updateScoreboard(){
        updateScoreboard(0);
    }

    public void updateScoreboard(int reducePlayerAmount){
        Scoreboard b = getUser().getScoreboard();

        Objective ob = null;

        if(b.getObjective(DisplaySlot.SIDEBAR) != null){
            b.getObjective(DisplaySlot.SIDEBAR).unregister();
        }

        ob = b.registerNewObjective("side","dummy");

        if(ServerSettingsManager.CURRENT_GAMESTATE == GameState.INGAME){
            ob.setDisplayName(ChatColor.BLUE + "Musical Guess");
            ob.setDisplaySlot(DisplaySlot.SIDEBAR);

            ArrayList<Player> a = new ArrayList<Player>();
            a.addAll(Bukkit.getOnlinePlayers());

            Collections.sort(a, new Comparator<Player>() {
                public int compare(Player p1, Player p2) {
                    Integer points1 = MusicalPlayer.get(p1).points;
                    Integer points2 = MusicalPlayer.get(p2).points;

                    return points2.compareTo(points1);
                }
            });

            ob.getScore(" ").setScore(11);
            ob.getScore(ChatColor.AQUA.toString() + ChatColor.BOLD.toString() + getUser().getTranslatedMessage("Your score") + ":").setScore(10);
            ob.getScore(ChatColor.YELLOW + String.valueOf(points)).setScore(9);
            ob.getScore("  ").setScore(8);
            ob.getScore(ChatColor.AQUA.toString() + ChatColor.BOLD.toString() + getUser().getTranslatedMessage("Leading") + ":").setScore(7);
            int score = 6;
            int i = 1;

            for(Player s : a){
                if(i > 3) break;
                ChestUser ss = ChestUser.getUser(s);
                String st = null;
                if(getUser().hasPermission(Rank.VIP)){
                    st = StringUtils.limitString(ss.getRank().getColor() + s.getName(), 16);
                } else {
                    st = StringUtils.limitString(ss.getRank().getColor() + s.getName(), 16);
                }
                if(st == null) continue;
                int points = MusicalPlayer.get(s).points;
                ob.getScore(st).setScore(score);
                getUser().setPlayerPrefix(st,i + ". ");
                getUser().setPlayerSuffix(st,ChatColor.WHITE + ": " + points);

                i++;
                score--;
            }

            String c = "";

            while(i <= 3){
                String st = ChatColor.DARK_GRAY + "???" + c;
                ob.getScore(st).setScore(score);
                getUser().setPlayerPrefix(st,i + ". ");
                getUser().setPlayerSuffix(st,ChatColor.WHITE + ": " + 0);
                c = c + " ";

                score--;
                i++;
            }

            ob.getScore("   ").setScore(3);
            ob.getScore(StringUtils.SCOREBOARD_LINE_SEPERATOR).setScore(2);
            ob.getScore(StringUtils.SCOREBOARD_FOOTER_IP).setScore(1);
        } else if(ServerSettingsManager.CURRENT_GAMESTATE == GameState.LOBBY){
            ob.setDisplayName(ChatColor.BLUE + "Musical Guess");
            ob.setDisplaySlot(DisplaySlot.SIDEBAR);
            ob.getScore("   ").setScore(10);
            ob.getScore(org.bukkit.ChatColor.AQUA.toString() + org.bukkit.ChatColor.BOLD.toString() + getUser().getTranslatedMessage("Players") + ": " + org.bukkit.ChatColor.YELLOW.toString() + (Bukkit.getOnlinePlayers().size()-reducePlayerAmount)).setScore(9);
            ob.getScore(org.bukkit.ChatColor.AQUA.toString() + org.bukkit.ChatColor.BOLD.toString() + getUser().getTranslatedMessage("Min. Players") + ": " + org.bukkit.ChatColor.YELLOW.toString() + ServerSettingsManager.MIN_PLAYERS).setScore(8);
            ob.getScore(org.bukkit.ChatColor.AQUA.toString() + org.bukkit.ChatColor.BOLD.toString() + getUser().getTranslatedMessage("Max. Players") + ": " + org.bukkit.ChatColor.YELLOW.toString() + ServerSettingsManager.MAX_PLAYERS).setScore(7);
            ob.getScore("    ").setScore(6);
            ob.getScore(org.bukkit.ChatColor.AQUA.toString() + org.bukkit.ChatColor.BOLD.toString() + getUser().getTranslatedMessage("Server") + ": ").setScore(5);
            String s = StringUtils.limitString(ServerUtil.getServerName(),16);
            ob.getScore(s).setScore(4);
            getUser().setPlayerPrefix(s,ChatColor.YELLOW.toString());
            ob.getScore("  ").setScore(3);
            ob.getScore(StringUtils.SCOREBOARD_LINE_SEPERATOR).setScore(2);
            ob.getScore(StringUtils.SCOREBOARD_FOOTER_IP).setScore(1);
        }
    }

    public void saveData(){
        if(allowSaveData == false) return;

        ChestAPI.async(() -> {
            try {
                PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("UPDATE `mg_stats` SET `points`=`points`+?, `monthlyPoints`=`monthlyPoints`+?, `playedGames`=`playedGames`+?, `victories`=`victories`+? WHERE `uuid`=?");
                ps.setInt(1,this.points);
                ps.setInt(2,this.points);
                ps.setInt(3,this.playedGames);
                ps.setInt(4,this.victories);
                ps.setString(5,getPlayer().getUniqueId().toString());
                ps.executeUpdate();
                ps.close();
            } catch(Exception e){
                e.printStackTrace();
            }
        });
    }
}
