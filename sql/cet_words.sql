create table cet_words
(
    id   serial
        constraint cet_words_pk
            primary key,
    data jsonb not null
);

comment on table cet_words is '英语四级单词';

alter table cet_words
    owner to asher;

INSERT INTO public.cet_words (id, data) VALUES (1, '{"mean": "n.附加语;标签", "type": "cet4", "word": "﻿tag", "addDate": "2024-09-16 15:26:36", "initial": "T", "phonetic_symbol": "/tæg/"}');
