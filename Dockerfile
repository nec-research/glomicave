########################################################################################
# Docker file to run GLOMICAVE java application and post data to graph database
#

# Get base docker image
FROM eclipse-temurin:17-jdk-jammy

# May be optional if these packages are installed already in the base image
RUN apt update && apt install -y curl unzip groff jq openssh-client vim zip less

#Install awscli
RUN curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip" && \
    unzip awscliv2.zip && \
	./aws/install

ARG WORKDIR=/app
WORKDIR ${WORKDIR}

#Install tools to build Java code 
RUN apt-get update
RUN apt-get install -y maven
RUN mvn wrapper:wrapper
COPY pom.xml ./

RUN ./mvnw dependency:resolve
