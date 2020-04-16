FROM debian

RUN apt update

# Install dependencies

RUN apt install -y wget zip unzip git default-jdk ffmpeg golang

# Set gopath

RUN mkdir /usr/share/gopath
ENV GOPATH=/usr/share/gopath

# Install and build MSE utils

RUN go get github.com/acolwell/mse-tools/mse_webm_remuxer
RUN go get github.com/acolwell/mse-tools/mse_json_manifest

# Download and install Bento Mp4 tools

WORKDIR /tmp/
RUN wget "http://zebulon.bok.net/Bento4/binaries/Bento4-SDK-1-5-1-627.x86_64-unknown-linux.zip"
RUN unzip "Bento4-SDK-1-5-1-627.x86_64-unknown-linux.zip"
RUN mkdir /usr/share/bento
RUN mv "Bento4-SDK-1-5-1-627.x86_64-unknown-linux/bin" "/usr/share/bento/bin"

# Copy the project binaries

RUN mkdir /usr/share/noixion_storage
WORKDIR /usr/share/noixion_storage
COPY "target/universal/noixion_delivery_network-1.0.zip" "/usr/share/noixion_storage/storage.zip"
RUN unzip /usr/share/noixion_storage/storage.zip

# Copy noixion public key
RUN mkdir /root/.noixion
RUN mkdir /root/.noixion/security
COPY "conf/noixion.public.key" "/root/.noixion/security/noixion.public.key"

# Set the docker configuration

ARG config=docker.conf

COPY ${config} "/usr/share/noixion_storage/docker.conf"
RUN cp -rf "/usr/share/noixion_storage/docker.conf" "/usr/share/noixion_storage/noixion_delivery_network-1.0/conf/application.conf"

RUN chmod 755 /usr/share/noixion_storage/noixion_delivery_network-1.0/

EXPOSE 8080/tcp
EXPOSE 7800/tcp
EXPOSE 7800/udp

WORKDIR "/usr/share/noixion_storage/noixion_delivery_network-1.0/"

CMD ["/usr/share/noixion_storage/noixion_delivery_network-1.0/bin/noixion_delivery_network", "-Dplay.evolutions.db.default.autoApply=true", "-Dhttp.port=8080"]
