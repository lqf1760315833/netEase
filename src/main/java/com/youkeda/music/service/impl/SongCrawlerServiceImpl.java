package com.youkeda.music.service.impl;

import com.alibaba.fastjson.JSON;
import com.youkeda.music.model.*;
import com.youkeda.music.service.SongCrawlerService;
import com.youkeda.music.util.WordCloudUtil;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.IOException;
import java.util.*;

/**
 * 音乐抓取服务的实现
 */
public class SongCrawlerServiceImpl implements SongCrawlerService {

    private static final String ARTIST_API_PREFIX = "http://neteaseapi.youkeda.com:3000/artists?id=";
    private static final String S_D_API_PREFIX = "http://neteaseapi.youkeda.com:3000/song/detail?ids=";
    private static final String S_C_API_PREFIX = "http://neteaseapi.youkeda.com:3000/comment/music?id=";
    private static final String S_U_API_PREFIX = "http://neteaseapi.youkeda.com:3000/song/url?id=";

    private OkHttpClient okHttpClient;
    // 歌单数据仓库
    private Map<String, Artist> artists;
    private Object Map;

    private void init() {
        artists = new HashMap<>();
        okHttpClient = new OkHttpClient();
    }

    @Override
    public void start(String artistId) {

        if (artistId == null || artistId.equals("")) {
            return;
        }

        init();

        initArtistHotSongs(artistId);
        assembleSongDetail(artistId);
        assembleSongComment(artistId);
        assembleSongUrl(artistId);
        generateWordCloud(artistId);

    }
    @Override
    public Artist getArtist(String artistId) {
        return artists.get(artistId);
    }

    @Override
    public Song getSong(String artistId, String songId) {
        Artist artist = artists.get(artistId);
        List<Song> songs =  artist.getSongList();
        for (Song song : songs) {
            if (song.getId().equals(songId)) {
                return song;
            }
        }
        return null;
    }

    //获取整体数据
    public Map getSourceDataObj(String PREFIX, String POSTFIX) {
        String url = PREFIX + POSTFIX;
        String content = getContent(url);
        Map returnData = JSON.parseObject(content, Map.class);
        return returnData;
    }

    //构建歌单Artist实例
    private Artist buildArtist(Map returnData) {
        Map artistObj = (Map)returnData.get("artist");
        Artist artist = new Artist();
        artist.setId(artistObj.get("id").toString());
        artist.setAlias((List<String>) artistObj.get("alias"));
        artist.setName((String)artistObj.get("name"));
        artist.setBriefDesc((String)artistObj.get("briefDesc"));
        artist.setPicUrl((String)artistObj.get("picUrl"));
        artist.setImg1v1Url((String)artistObj.get("img1V1Url"));
        return artist;
    }

    //构建Song实例
    private List<Song> buildSongs(Map returnData) {
        List hotSongs = (List)returnData.get("hotSongs");
        List<Song> songs = new ArrayList<>();
        for (int i = 0; i < hotSongs.size(); i++) {
            Map songObj = (Map)hotSongs.get(i);
            Song song = new Song();
            song.setId(songObj.get("id").toString());
            song.setName((String)songObj.get("name"));
            songs.add(song);
        }
        return songs;
    }

    //获取网络请求
    private String getContent(String url) {
        Request request = new Request.Builder().url(url).build();
        Call call = okHttpClient.newCall(request);
        String result = null;
        try {
            result = call.execute().body().string();
        }catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private void initArtistHotSongs(String artistId) {

        Map returnData = getSourceDataObj(ARTIST_API_PREFIX, artistId);
        Artist artist = buildArtist(returnData);
        List<Song> songs = buildSongs(returnData);
        artist.setSongList(songs);
        artists.put(artistId, artist);

    }

    //歌曲详情
    private void assembleSongDetail(String artistId) {
        Artist artist = getArtist(artistId);
        List<Song> songs = artist.getSongList();
        String sId = buildManyIdParam(songs);
        Map returnData = getSourceDataObj(S_D_API_PREFIX, sId);

        List<Map> sourceSongs = (List<Map>) returnData.get("songs");
        Map<String, Map> sourceSongsMap = new HashMap<>();
        for (Map sourceSongMap : sourceSongs) {
            String id = sourceSongMap.get("id").toString();
            sourceSongsMap.put(id, sourceSongMap);
        }
        for (Song song : songs) {
            Map songData = sourceSongsMap.get(song.getId());

            List<Map> singersData = (List<Map>) songData.get("ar");
            List<User> singers = new ArrayList<>();
            for (Map singerData :singersData) {
                User singer = new User();
                singer.setId(singerData.get("id").toString());
                singer.setNickName(singerData.get("name").toString());
                singers.add(singer);
            }
            song.setSingers(singers);

            Map albumData = (Map) songData.get("al");
            Album album = new Album();
            album.setId(albumData.get("id").toString());
            album.setName(albumData.get("name").toString());
            if (albumData.get("picUrl") != null) {
                album.setPicUrl(albumData.get("picUrl").toString());
            }
            song.setAlbum(album);
        }

    }

    //歌曲评论
    private void assembleSongComment(String artistId) {
        Artist artist = getArtist(artistId);
        List<Song> songs =  artist.getSongList();
        for (Song song : songs) {
            String sId = song.getId() + "&limit=5";
            Map returnData = getSourceDataObj(S_C_API_PREFIX, sId);
            List<Map> hotComments = (List<Map>)returnData.get("hotComments");
            List<Map> comments = (List<Map>)returnData.get("comments");
            song.setHotComments(buildComments(hotComments));
            song.setComments(buildComments(comments));
        }
    }

    //音乐文件
    private void assembleSongUrl(String artistId) {
        Artist artist = getArtist(artistId);
        List<Song> songs = artist.getSongList();
        String sId= buildManyIdParam(songs);
        Map returnData = getSourceDataObj(S_U_API_PREFIX, sId);

        List<Map> songUrlData = (List<Map>) returnData.get("data");

        Map<String ,Map> songUrlMap = new HashMap<>();
        for (Map songUrl : songUrlData) {
            String id = songUrl.get("id").toString();
            songUrlMap.put(id, songUrl);
        }
        for (Song song : songs) {
            Map songUrl = (Map)songUrlMap.get(song.getId());
            if (songUrl != null && songUrl.get("url") != null) {
                String url = songUrl.get("url").toString();
                song.setSourceUrl(url);
            }
        }
    }

    private void generateWordCloud(String artistId) {
        Artist artist = getArtist(artistId);
        List<Song> songs = artist.getSongList();
        List<String> contents = new ArrayList<>();

        for (Song song : songs) {
            collectContent(song.getHotComments(), contents);
            collectContent(song.getComments(), contents);
        }
        WordCloudUtil.generate(artistId, contents);
    }

    private void collectContent(List<Comment> comments, List<String> contents) {
        for (Comment comment : comments) {
            contents.add(comment.getContent());
        }
    }

    private List<Comment> buildComments(List<Map> commonts) {
        List<Comment> comments = new ArrayList<>();

        for (Map comment : commonts) {
            Comment comment1 = new Comment();
            comment1.setId(comment.get("commentId").toString());
            comment1.setTime(comment.get("time").toString());
            comment1.setContent(comment.get("content").toString());
            comment1.setLikedCount(comment.get("likedCount").toString());

            User user = new User();
            Map userObj = (Map) comment.get("user");
            user.setId(userObj.get("userId").toString());
            user.setNickName(userObj.get("nickname").toString());
            user.setAvatar(userObj.get("avatarUrl").toString());
            comment1.setCommentUser(user);

            comments.add(comment1);
        }
        return comments;
    }

    private String buildManyIdParam(List<Song> songs) {
        List<String> songIds = new ArrayList<>();
        for (Song song : songs) {
            songIds.add(song.getId());
        }
        String sId = String.join(",",songIds);
        return sId;
    }

}







