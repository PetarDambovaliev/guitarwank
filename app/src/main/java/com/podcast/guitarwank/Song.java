package com.podcast.guitarwank;

public class Song {
    private String link;
    private String title;

    public Song(String link, String title) {
        this.link = link;
        this.title = title;
    }

    public String getLink() {
        return link;
    }
    public String getTitle() {
        return title;
    }
}
