package eu.thechest.musicalguess;

import com.xxmicloxx.NoteBlockAPI.Song;

import java.util.ArrayList;

/**
 * Created by zeryt on 26.02.2017.
 */
public class MusicalSong {
    public int id;
    public String title;
    public String artist;
    public Song song;

    public MusicalSong(int id, String title, String artist, Song song){
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.song = song;
    }

    public static boolean isIn(MusicalSong s, ArrayList<MusicalSong> a){
        boolean b = false;

        for(MusicalSong ms : a){
            if(s.title.equals(ms.title) && s.artist.equals(ms.artist)) b = true;
        }

        return b;
    }
}
