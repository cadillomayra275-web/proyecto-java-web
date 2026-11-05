FROM eclipse-temurin:17-jre
WORKDIR /opt/siscom
COPY lib lib
COPY bin/siscom.jar bin/siscom.jar
COPY src/main/webapp src/main/webapp
COPY sql sql
COPY config config
RUN useradd -u 10001 -r siscom && mkdir -p datos respaldos && chown -R siscom:siscom /opt/siscom
USER siscom
ENV SISCOM_HOST=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["java","-cp","bin/siscom.jar:lib/*","pe.edu.siscom.Inicio"]
