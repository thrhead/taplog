FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

# Pinned environment variables
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=${PATH}:${JAVA_HOME}/bin:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools

# Install JDK 17, curl, unzip, and prerequisites
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jdk-headless \
    curl \
    unzip \
    ca-certificates \
    git \
    && rm -rf /var/lib/apt/lists/*

# Install Android Command Line Tools and SDK Platform 36 / Build-Tools 36.0.0
ARG CMDLINE_TOOLS_VERSION=11076708
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && curl -sS -o /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip \
    && unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm /tmp/cmdline-tools.zip \
    && yes | ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null 2>&1 \
    && ${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager \
        "platforms;android-36" \
        "build-tools;36.0.0" \
        "platform-tools" \
    && chown -R vscode:vscode ${ANDROID_HOME} 2>/dev/null || true
