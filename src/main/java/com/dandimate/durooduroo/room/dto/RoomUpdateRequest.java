package com.dandimate.durooduroo.room.dto;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
/** Omitted place/coverPhoto preserve values; explicit null removes them. */
public class RoomUpdateRequest {
    @Size(min=1,max=20) public String name;
    public Integer capacity;
    public LocalDate meetingDate;
    public OffsetDateTime deadlineAt;
    private String scrollTemplateId;
    private boolean scrollTemplatePresent;
    @Valid private RoomPlace place;
    @Valid private CoverPhotoRef coverPhoto;
    private boolean placePresent, coverPresent;
    private boolean coverUrlPresent;
    private String coverImageUrl;
    @JsonSetter("coverImageUrl") public void setCoverImageUrl(String value) { coverImageUrl=value; coverUrlPresent=true; }
    @JsonSetter("place") public void setPlace(RoomPlace value) { place=value; placePresent=true; }
    @JsonSetter("coverPhoto") public void setCoverPhoto(CoverPhotoRef value) { coverPhoto=value; coverPresent=true; }
    @JsonSetter("scrollTemplateId") public void setScrollTemplateId(String value) { scrollTemplateId=value; scrollTemplatePresent=true; }
    public RoomPlace place() { return place; }
    public CoverPhotoRef coverPhoto() {
        if (!coverUrlPresent) return coverPhoto;
        CoverPhotoRef fromUrl = CoverPhotoRef.fromUrl(coverImageUrl);
        if (coverPresent && !java.util.Objects.equals(fromUrl, coverPhoto))
            throw new com.dandimate.durooduroo.common.exception.BusinessException(
                com.dandimate.durooduroo.common.exception.ErrorCode.VALIDATION_ERROR, "대표 사진 값이 서로 다릅니다.");
        return fromUrl;
    }
    public boolean placePresent() { return placePresent; }
    public boolean coverPresent() { return coverPresent || coverUrlPresent; }
    public String scrollTemplateId() { return scrollTemplateId; }
    public boolean scrollTemplatePresent() { return scrollTemplatePresent; }
}
