package com.dandimate.durooduroo.chat.dto;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
public record ChatSourceView(RoomInfo room, PhotoInfo photo, List<EntryInfo> entries, EntryInfo firstEntry) {
 public ChatSourceView { entries=List.copyOf(entries); }
 public ChatSourceView(RoomInfo room,PhotoInfo photo,List<EntryInfo> entries){this(room,photo,entries,null);}
 public record RoomInfo(String roomId,String name,LocalDate meetingDate) {
  public RoomInfo(String roomId,String name){this(roomId,name,null);}
 }
 public record PhotoInfo(String photoId,String imageUrl,LocalDateTime createdAt,ChatAuthorView uploader) {}
 public record EntryInfo(String entryId,String text,LocalDateTime createdAt,ChatAuthorView author) {}
}
