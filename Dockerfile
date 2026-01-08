FROM ibm-semeru-runtimes:open-17-jre

ARG JAR_NAME
ENV PROJECT_NAME ${JAR_NAME}
ENV PROJECT_HOME /usr/local/${PROJECT_NAME}

# 设置字符编码环境变量
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8


RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
RUN echo 'Asia/Shanghai' >/etc/timezone
RUN mkdir -p $PROJECT_HOME && mkdir -p $PROJECT_HOME/logs

ARG JAR_FILE
COPY ${JAR_FILE} $PROJECT_HOME/${JAR_NAME}.jar


ENTRYPOINT java  \
    -Xmx500M -Xms100M \
    -jar $PROJECT_HOME/$PROJECT_NAME.jar
