FROM azul/zulu-openjdk:25

ARG JAR_NAME
ENV PROJECT_NAME ${JAR_NAME}
ENV PROJECT_HOME /usr/local/${PROJECT_NAME}

# 设置字符编码环境变量
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
RUN echo 'Asia/Shanghai' >/etc/timezone
RUN mkdir -p $PROJECT_HOME && mkdir -p $PROJECT_HOME/logs

ARG JAR_FILE
COPY ${JAR_FILE} $PROJECT_HOME/${JAR_NAME}.jar


# exec 使 java 成为 PID 1（支持 jcmd attach 与正确的信号处理）
ENTRYPOINT exec java  \
    -Xmx500M -Xms100M \
    -jar $PROJECT_HOME/$PROJECT_NAME.jar
