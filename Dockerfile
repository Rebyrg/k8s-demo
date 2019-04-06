FROM java:8

VOLUME /tmp
COPY target/k8s-demo-currency-provider-0.0.1-SNAPSHOT.jar app.jar
RUN bash -c 'touch /app.jar'

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTIONS -Djava.security.egd=file:/dev/./urandom -jar /app.jar"]
