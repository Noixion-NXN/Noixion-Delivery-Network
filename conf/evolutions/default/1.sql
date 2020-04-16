# --- Created by Ebean DDL
# To stop Ebean DDL generation, remove this comment and start using Evolutions

# --- !Ups

create table kad_block (
  id                            varchar(255) not null,
  size_bytes                    bigint not null,
  constraint pk_kad_block primary key (id)
);

create table video_processing_job (
  id                            bigint auto_increment not null,
  job_id                        varchar(255),
  original_file                 varchar(255),
  audio_files                   varchar(255),
  last_checked                  bigint not null,
  task                          varchar(255),
  progress                      double not null,
  remaining_time                double not null,
  constraint pk_video_processing_job primary key (id)
);

create table video_processing_status (
  id                            bigint auto_increment not null,
  kad_key                       varchar(255),
  first_part_key                varchar(255),
  vid_options                   integer,
  in_progress                   boolean default false not null,
  start_timestamp               bigint not null,
  duration                      double not null,
  processed                     boolean default false not null,
  ready                         boolean default false not null,
  error                         boolean default false not null,
  error_message                 varchar(255),
  index_chunk                   varchar(255),
  extra_audio                   boolean,
  constraint ck_video_processing_status_vid_options check ( vid_options in (0,1)),
  constraint uq_video_processing_status_kad_key unique (kad_key),
  constraint uq_video_processing_status_first_part_key unique (first_part_key),
  constraint pk_video_processing_status primary key (id)
);

create table video_upload_status (
  id                            bigint auto_increment not null,
  token                         varchar(255),
  timestamp                     bigint not null,
  uploaded                      boolean default false not null,
  options                       integer,
  constraint ck_video_upload_status_options check ( options in (0,1)),
  constraint uq_video_upload_status_token unique (token),
  constraint pk_video_upload_status primary key (id)
);

create index ix_video_processing_status_kad_key on video_processing_status (kad_key);
create index ix_video_processing_status_first_part_key on video_processing_status (first_part_key);
create index ix_video_upload_status_token on video_upload_status (token);

# --- !Downs

drop table if exists kad_block;

drop table if exists video_processing_job;

drop table if exists video_processing_status;

drop table if exists video_upload_status;

drop index if exists ix_video_processing_status_kad_key;
drop index if exists ix_video_processing_status_first_part_key;
drop index if exists ix_video_upload_status_token;
