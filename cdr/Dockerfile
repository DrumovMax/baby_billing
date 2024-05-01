FROM openjdk:17-jdk

WORKDIR /opt/app

EXPOSE 8080

ARG JAR_FILE=cdr/target/*.jar

COPY ${JAR_FILE} /opt/app/cdr.jar

ENTRYPOINT ["java", "-jar", "cdr.jar"]