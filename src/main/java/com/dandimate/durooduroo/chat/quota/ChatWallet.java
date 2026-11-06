package com.dandimate.durooduroo.chat.quota;
import jakarta.persistence.*;
import java.time.LocalDate;
@Entity @Table(name="chat_wallets")
public class ChatWallet {
 @Id @Column(length=36) String userId;
 LocalDate freeDate;
 int freeUsed;
 long credits;
 protected ChatWallet() {}
 ChatWallet(String userId) { this.userId=userId; }
 void reset(LocalDate day) { if(!day.equals(freeDate)) { freeDate=day; freeUsed=0; } }
}
