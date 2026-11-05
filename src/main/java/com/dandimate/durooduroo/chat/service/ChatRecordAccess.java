package com.dandimate.durooduroo.chat.service;
import com.dandimate.durooduroo.chat.dto.*;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.memory.domain.*;
import com.dandimate.durooduroo.memory.repository.*;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.storage.MediaUrls;
import com.dandimate.durooduroo.embedding.service.EmbeddingText;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ChatRecordAccess {
 private final MemberRepository members;private final RoomRepository rooms;private final PhotoRepository photos;
 private final MemoryEntryRepository entries;private final PhotoPassRepository passes;private final MediaUrls media;
 public ChatRecordAccess(MemberRepository members,RoomRepository rooms,PhotoRepository photos,MemoryEntryRepository entries,PhotoPassRepository passes,MediaUrls media){
  this.members=members;this.rooms=rooms;this.photos=photos;this.entries=entries;this.passes=passes;this.media=media;
 }
 public record Record(String entryId,String hash,String photoVersion,String kind,ChatSourceView source,ChatSourceView.EntryInfo entry) {}
 @Transactional(readOnly=true)
 public Map<String,Record> load(String userId,boolean imageUrls){
  Map<String,Record> result=new LinkedHashMap<>();
  for(var me:members.findAllByUserIdAndLeftAtIsNull(userId)){
   var room=rooms.findById(me.getRoomId()).orElse(null);if(room==null)continue;
   var authors=new HashMap<String,ChatAuthorView>();
   members.findAllByRoomId(me.getRoomId()).forEach(m->authors.put(m.getId(),ChatAuthorView.from(m)));
   for(var photo:photos.findAllByRoomId(me.getRoomId())){
    if(!photo.isPublished()||!photo.isReady()||!authors.containsKey(photo.getMemberId()))continue;
    var all=entries.findAllByPhotoId(photo.getId()).stream().filter(e->e.getKind()==EntryKind.FIRST||e.getKind()==EntryKind.ADDON)
     .sorted(Comparator.comparing(MemoryEntry::getCreatedAt).thenComparing(MemoryEntry::getId)).toList();
    boolean unlocked=me.getId().equals(photo.getMemberId())||all.stream().anyMatch(e->e.getKind()==EntryKind.ADDON&&e.getMemberId().equals(me.getId()))
     ||passes.findByPhotoIdAndMemberId(photo.getId(),me.getId()).isPresent();
    // FIRST is already public to participants in circulation; locked ADDONs must never enter retrieval or the model.
    var visible=all.stream().filter(e->authors.containsKey(e.getMemberId())&&(e.getKind()==EntryKind.FIRST||unlocked)).toList();
    var views=new LinkedHashMap<String,ChatSourceView.EntryInfo>();
    visible.forEach(e->views.put(e.getId(),new ChatSourceView.EntryInfo(e.getId(),e.getTranscript(),e.getCreatedAt(),authors.get(e.getMemberId()))));
    var first=visible.stream().filter(e->e.getKind()==EntryKind.FIRST).map(e->views.get(e.getId())).findFirst().orElse(null);
    var addons=visible.stream().filter(e->e.getKind()==EntryKind.ADDON).map(e->views.get(e.getId())).toList();
    var source=new ChatSourceView(new ChatSourceView.RoomInfo(room.getId(),room.getTitle(),room.getMeetingDate()),
     new ChatSourceView.PhotoInfo(photo.getId(),imageUrls?media.imageUrl(photo):null,photo.getCreatedAt(),authors.get(photo.getMemberId())),addons,first);
    String version=EmbeddingText.hash(source.room().toString()+authors.get(photo.getMemberId()).toString()+unlocked+views.toString());
    for(var e:visible)result.put(e.getId(),new Record(e.getId(),EmbeddingText.hash(e.getTranscript()),version,e.getKind().name(),source,views.get(e.getId())));
   }
  }
  return result;
 }
}
