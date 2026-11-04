package com.dandimate.durooduroo.chat.service;
import com.dandimate.durooduroo.chat.dto.*;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import com.dandimate.durooduroo.memory.domain.EntryKind;
import com.dandimate.durooduroo.memory.repository.*;
import com.dandimate.durooduroo.photo.repository.PhotoRepository;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.storage.MediaUrls;
import com.dandimate.durooduroo.embedding.service.EmbeddingText;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Same story-unlock rules as circulation: uploader, own ADDON or explicit photo pass. */
@Service
public class ChatRecordAccess {
    private final MemberRepository members;
    private final RoomRepository rooms;
    private final PhotoRepository photos;
    private final MemoryEntryRepository entries;
    private final PhotoPassRepository passes;
    private final MediaUrls media;
    public ChatRecordAccess(MemberRepository members,RoomRepository rooms,PhotoRepository photos,MemoryEntryRepository entries,
                            PhotoPassRepository passes,MediaUrls media) {
        this.members=members;this.rooms=rooms;this.photos=photos;this.entries=entries;this.passes=passes;this.media=media;
    }
    public record Record(String entryId,String hash,ChatSourceView source,ChatSourceView.EntryInfo entry) {}
    @Transactional(readOnly=true)
    public Map<String,Record> load(String userId,boolean includeImageUrls) {
        Map<String,Record> result=new LinkedHashMap<>();
        for(var me:members.findAllByUserIdAndLeftAtIsNull(userId)) {
            var room=rooms.findById(me.getRoomId()).orElse(null);
            if(room==null)continue;
            var authors=new HashMap<String,ChatAuthorView>();
            members.findAllByRoomId(me.getRoomId()).forEach(m->authors.put(m.getId(),ChatAuthorView.from(m)));
            for(var photo:photos.findAllByRoomId(me.getRoomId())) {
                if(!photo.isPublished()||!photo.isReady()||!authors.containsKey(photo.getMemberId()))continue;
                var addons=entries.findAllByPhotoId(photo.getId()).stream().filter(e->e.getKind()==EntryKind.ADDON)
                    .sorted(Comparator.comparing(com.dandimate.durooduroo.memory.domain.MemoryEntry::getCreatedAt)
                    .thenComparing(com.dandimate.durooduroo.memory.domain.MemoryEntry::getId)).toList();
                boolean unlocked=me.getId().equals(photo.getMemberId()) || addons.stream().anyMatch(e->e.getMemberId().equals(me.getId()))
                    || passes.findByPhotoIdAndMemberId(photo.getId(),me.getId()).isPresent();
                if(!unlocked)continue;
                var views=addons.stream().filter(e->authors.containsKey(e.getMemberId()))
                    .map(e->new ChatSourceView.EntryInfo(e.getId(),e.getTranscript(),e.getCreatedAt(),authors.get(e.getMemberId()))).toList();
                var source=new ChatSourceView(new ChatSourceView.RoomInfo(room.getId(),room.getTitle()),
                    new ChatSourceView.PhotoInfo(photo.getId(),includeImageUrls?media.imageUrl(photo):null,photo.getCreatedAt(),authors.get(photo.getMemberId())),views);
                for(var entry:views)result.put(entry.entryId(),new Record(entry.entryId(),EmbeddingText.hash(entry.text()),source,entry));
            }
        }
        return result;
    }
}
