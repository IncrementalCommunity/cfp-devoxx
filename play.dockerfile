FROM openjdk:8

ENV PLAY_VERSION 2.3.10

RUN wget -q https://downloads.typesafe.com/play/2.3.10/play-2.3.10.zip \
    && unzip -q play-2.3.10.zip \
    && rm play-2.3.10.zip \
    && ln -s /play-2.3.10/play /usr/local/bin/
