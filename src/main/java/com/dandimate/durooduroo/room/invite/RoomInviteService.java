package com.dandimate.durooduroo.room.invite;
import com.dandimate.durooduroo.common.auth.*;
import com.dandimate.durooduroo.common.exception.*;
import com.dandimate.durooduroo.room.domain.Room;
import com.dandimate.durooduroo.room.repository.RoomRepository;
import com.dandimate.durooduroo.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class RoomInviteService {
 private final RoomRepository rooms;private final MemberRepository members;private final InviteTokenCipher cipher;
 public RoomInviteService(RoomRepository rooms,MemberRepository members,InviteTokenCipher cipher){this.rooms=rooms;this.members=members;this.cipher=cipher;}
 public void retainCreated(Room room) {
  if(room.getEncryptedInviteToken()==null && room.getIssuedInviteToken()!=null)retain(room,room.getIssuedInviteToken());
 }
 private void retain(Room room,String token){room.retainInviteToken(cipher.encrypt(room.getId(),token),Tokens.hash(token));}
 @Transactional
 public String get(String roomId,String authorization) {
  var room=rooms.findByIdForUpdate(roomId).orElseThrow(()->new BusinessException("ROOM_NOT_FOUND","방이 없습니다."));
  members.findByRoomIdAndMemberToken(roomId,MemberTokenResolver.resolve(authorization))
   .filter(m->m.isActive()).orElseThrow(()->new BusinessException("FORBIDDEN","참여자만 초대 링크를 조회할 수 있습니다."));
  // Legacy hashes cannot be reversed. Keep their lookup valid while establishing one retrievable link.
  if(room.getEncryptedInviteToken()==null)retain(room,room.getIssuedInviteToken()!=null?room.getIssuedInviteToken():Tokens.generate());
  return cipher.decrypt(room.getId(),room.getEncryptedInviteToken());
 }
 @Transactional
 public void remember(String token) {
  var room=rooms.findByInviteTokenHashForUpdate(Tokens.hash(token)).orElseThrow(()->new BusinessException("INVITE_NOT_FOUND","초대 링크가 없습니다."));
  if(room.getEncryptedInviteToken()==null)retain(room,token);
 }
}
