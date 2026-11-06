package com.dandimate.durooduroo.room.service;
import com.dandimate.durooduroo.room.cover.RoomCoverService;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.dto.*;
import org.springframework.stereotype.Service;
@Service
public class RoomDetailsService {
    private final RoomCoverService covers;
    public RoomDetailsService(RoomCoverService covers){this.covers=covers;}
    @org.springframework.beans.factory.annotation.Autowired private com.dandimate.durooduroo.room.invite.RoomInviteService invites;
    public void apply(Room room,RoomPlace place,CoverPhotoRef cover,String userId) {
        if(cover!=null)covers.requireOwned(cover.id(),userId);
        invites.retainCreated(room);
        room.changePlace(place);room.changeCoverPhoto(cover==null?null:cover.id());
    }
    public void validateCover(CoverPhotoRef cover,String userId){if(cover!=null)covers.requireOwned(cover.id(),userId);}
}
