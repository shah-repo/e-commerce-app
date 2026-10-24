create table if not exists app_user
(
    id                 integer      not null primary key,
    firstname          varchar(255),
    lastname           varchar(255),
    email              varchar(255) not null
        constraint uk_app_user_email unique,
    password           varchar(255) not null,
    role               varchar(255) not null,
    created_date       timestamp(6) not null,
    last_modified_date timestamp(6)
);

create sequence if not exists app_user_seq increment by 50;
