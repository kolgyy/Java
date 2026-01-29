package com.moro;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class MusicPlayer {

    @Value("${musicPlayer.name}")
    private String name;
    @Value("${musicPlayer.volume}")
    private int volume;

    public String getName() {
        return name;
    }

    public int getVolume() {
        return volume;
    }

    private List<Music> list;

    public MusicPlayer(List<Music> list) {
        this.list = list;
    }

    public String playMusic() {
        Random random = new Random();
        return "Playing: " + list.get(random.nextInt(list.size())).getSong();
    }
}
