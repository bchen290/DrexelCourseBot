FROM openjdk:8

# Downloading and installing Gradle
# 1- Define a constant with the version of gradle you want to install
ARG GRADLE_VERSION=6.7.1

# 2- Define the URL where gradle can be downloaded from
ARG GRADLE_BASE_URL=https://services.gradle.org/distributions

# 3- Define the SHA key to validate the gradle download
#    obtained from here https://gradle.org/release-checksums/
ARG GRADLE_SHA=3239b5ed86c3838a37d983ac100573f64c1f3fd8e1eb6c89fa5f9529b5ec091d

# 4- Create the directories, download gradle, validate the download, install it, remove downloaded file and set links
RUN mkdir -p /usr/share/gradle /usr/share/gradle/ref \
  && echo "Downloading gradle hash" \
  && curl -fsSL -o /tmp/gradle.zip ${GRADLE_BASE_URL}/gradle-${GRADLE_VERSION}-bin.zip \
  \
  && echo "Checking download hash" \
  && echo "${GRADLE_SHA}  /tmp/gradle.zip" | sha256sum -c - \
  \
  && echo "Unzipping gradle" \
  && unzip -d /usr/share/gradle /tmp/gradle.zip \
   \
  && echo "Cleaning and setting links" \
  && rm -f /tmp/gradle.zip \
  && ln -s /usr/share/gradle/gradle-${GRADLE_VERSION} /usr/bin/gradle

# 5- Define environmental variables required by gradle
ENV GRADLE_VERSION 6.7.1
ENV GRADLE_HOME /usr/bin/gradle
ENV GRADLE_USER_HOME /cache

ENV PATH $PATH:$GRADLE_HOME/bin

VOLUME $GRADLE_USER_HOME

EXPOSE 80
COPY . .
CMD ["gradle", "bootRun"]