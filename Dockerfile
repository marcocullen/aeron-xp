FROM amazoncorretto:23-jdk

RUN yum update -y && \
    yum install -y procps && \
    yum clean all

ARG JAR_DATE=unknown
COPY build/libs/aeron-app-0.0.1-all.jar /app/application.jar
