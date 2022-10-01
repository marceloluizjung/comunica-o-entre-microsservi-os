FROM gradle:7.0.0-jdk11
WORKDIR .
COPY --chown=gradle:gradle . .
RUN gradle build -x test --no-daemon
EXPOSE 8081
CMD ["gradle", "bootRun"]