-- 첫 스키마 (MySQL 8.4). 엔티티에서 Hibernate가 생성한 DDL을 그대로 고정했다.
-- 이후 테이블 변경은 이 파일을 고치지 말고 V2__설명.sql처럼 새 파일로 추가한다.


create table ai_jobs (
    attempts integer not null,
    created_at datetime(6) not null,
    next_run_at datetime(6) not null,
    updated_at datetime(6) not null,
    detail varchar(255),
    error_code varchar(255),
    id varchar(255) not null,
    room_id varchar(255) not null,
    status varchar(255) not null,
    target_id varchar(255),
    type varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create table askbacks (
    created_at datetime(6) not null,
    expires_at datetime(6) not null,
    postponed_at datetime(6),
    resolved_at datetime(6),
    status varchar(20) not null,
    gap_type varchar(30) not null,
    question varchar(200) not null,
    evidence_entry_ids varchar(2000),
    answer_entry_id varchar(255),
    id varchar(255) not null,
    photo_id varchar(255) not null,
    room_id varchar(255) not null,
    target_member_id varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create table event_logs (
    created_at datetime(6) not null,
    name varchar(50) not null,
    properties varchar(1000),
    id varchar(255) not null,
    member_id varchar(255),
    room_id varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create table idempotency_keys (
    response_status integer,
    created_at datetime(6) not null,
    method varchar(10) not null,
    id varchar(36) not null,
    request_hash varchar(64) not null,
    scope varchar(80) not null,
    idempotency_key varchar(128) not null,
    path varchar(255) not null,
    response_content_type varchar(255),
    response_body longblob,
    primary key (id)
) engine=InnoDB;

create table members (
    created_at datetime(6) not null,
    nickname varchar(30) not null,
    id varchar(36) not null,
    room_id varchar(36) not null,
    access_token_hash varchar(64) not null,
    flower_code enum ('AZALEA','CANOLA','FORGET_ME_NOT','FORSYTHIA','LILAC','PURPLE','ROYAL_AZALEA','TEAL') not null,
    role enum ('HOST','MEMBER') not null,
    primary key (id)
) engine=InnoDB;

create table memory_entries (
    confirmed_at datetime(6) not null,
    created_at datetime(6) not null,
    input_mode varchar(10) not null,
    audio_location varchar(300),
    transcript varchar(3000) not null,
    id varchar(255) not null,
    member_id varchar(255) not null,
    photo_id varchar(255) not null,
    room_id varchar(255) not null,
    transcription_id varchar(255),
    kind enum ('ADDON','ASKBACK_ANSWER','FIRST') not null,
    primary key (id)
) engine=InnoDB;

create table notifications (
    created_at datetime(6) not null,
    read_at datetime(6),
    id varchar(255) not null,
    member_id varchar(255) not null,
    resource_id varchar(255) not null,
    resource_type varchar(255) not null,
    type varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create table paragraph_ratings (
    score integer not null,
    updated_at datetime(6) not null,
    id varchar(255) not null,
    member_id varchar(255) not null,
    paragraph_id varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create table paragraph_reactions (
    created_at datetime(6) not null,
    id varchar(255) not null,
    member_id varchar(255) not null,
    paragraph_id varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create table paragraph_sources (
    sort_order integer not null,
    quote varchar(3000) not null,
    entry_id varchar(255) not null,
    id varchar(255) not null,
    member_id varchar(255) not null,
    paragraph_id varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create table paragraph_versions (
    version integer not null,
    created_at datetime(6) not null,
    reason varchar(20) not null,
    sources varchar(4000),
    id varchar(255) not null,
    paragraph_id varchar(255) not null,
    body mediumtext not null,
    primary key (id)
) engine=InnoDB;

create table paragraphs (
    ah_matda_count integer not null,
    display_order integer not null,
    version integer not null,
    created_at datetime(6) not null,
    id varchar(255) not null,
    photo_id varchar(255) not null,
    room_id varchar(255) not null,
    scroll_id varchar(255) not null,
    body mediumtext not null,
    primary key (id)
) engine=InnoDB;

create table photo_passes (
    created_at datetime(6) not null,
    id varchar(255) not null,
    member_id varchar(255) not null,
    photo_id varchar(255) not null,
    room_id varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create table photos (
    display_order integer not null,
    source_revision integer not null,
    created_at datetime(6) not null,
    published_at datetime(6),
    status varchar(20) not null,
    id varchar(36) not null,
    content_type varchar(50) not null,
    file_hash varchar(128),
    derived_url varchar(300),
    storage_key varchar(300) not null,
    file_url varchar(255) not null,
    member_id varchar(255) not null,
    room_id varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create table replies (
    created_at datetime(6) not null,
    resolved_at datetime(6),
    input_mode varchar(10) not null,
    classification varchar(20),
    status varchar(20) not null,
    audio_location varchar(300),
    transcript varchar(1000) not null,
    id varchar(255) not null,
    member_id varchar(255) not null,
    paragraph_id varchar(255) not null,
    photo_id varchar(255) not null,
    room_id varchar(255) not null,
    transcription_id varchar(255),
    primary key (id)
) engine=InnoDB;

create table rooms (
    deadline_changed_by_host bit not null,
    deadline_extended bit not null,
    meeting_date date,
    regeneration_count integer not null,
    created_at datetime(6) not null,
    deadline_at datetime(6) not null,
    id varchar(36) not null,
    invite_token_hash varchar(64) not null,
    title varchar(100) not null,
    status enum ('CIRCULATING','COLLECTING','GROWING','WEAVING') not null,
    primary key (id)
) engine=InnoDB;

create table scrolls (
    version integer not null,
    created_at datetime(6) not null,
    id varchar(255) not null,
    room_id varchar(255) not null,
    body mediumtext not null,
    primary key (id)
) engine=InnoDB;

create table transcription_drafts (
    consumed_at datetime(6),
    created_at datetime(6) not null,
    duration_ms bigint,
    expires_at datetime(6) not null,
    context_type varchar(20) not null,
    kind varchar(20) not null,
    audio_location varchar(300) not null,
    transcript varchar(3000) not null,
    audio_asset_id varchar(255) not null,
    context_id varchar(255) not null,
    id varchar(255) not null,
    member_id varchar(255) not null,
    model varchar(255),
    openai_request_id varchar(255),
    room_id varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create index idx_ai_jobs_runnable
   on ai_jobs (status, next_run_at);

create index idx_askbacks_target_status
   on askbacks (target_member_id, status);

create index idx_event_logs_room_name
   on event_logs (room_id, name);

alter table idempotency_keys
   add constraint uk_idempotency_scope_key unique (scope, method, path, idempotency_key);

alter table members
   add constraint uk_members_room_nickname unique (room_id, nickname);

alter table members
   add constraint uk_members_room_flower unique (room_id, flower_code);

alter table members
   add constraint UK1hkx78b076mxioeunyu3r10f9 unique (access_token_hash);

create index idx_entries_photo
   on memory_entries (photo_id);

alter table paragraph_ratings
   add constraint uk_paragraph_ratings_member unique (paragraph_id, member_id);

alter table paragraph_reactions
   add constraint uk_paragraph_reactions_member unique (paragraph_id, member_id);

create index idx_paragraph_sources_paragraph
   on paragraph_sources (paragraph_id);

alter table paragraph_versions
   add constraint uk_paragraph_versions unique (paragraph_id, version);

alter table photo_passes
   add constraint uk_photo_passes_photo_member unique (photo_id, member_id);

create index idx_replies_paragraph
   on replies (paragraph_id);

alter table rooms
   add constraint uk_rooms_invite_token_hash unique (invite_token_hash);
