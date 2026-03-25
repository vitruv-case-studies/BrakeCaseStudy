##
## Dockerfile for reproducing evaluation results of:
## "Directed Replay-Based Merge for Multi-Model Consistency Networks"
## (MODELS 2026)
##
## Build from workspace root:
##   docker build -f BrakeCaseStudy/Dockerfile -t vitruv-merge-ae .
## Run:
##   docker run --rm vitruv-merge-ae
##   docker run --rm vitruv-merge-ae --performance
##

FROM eclipse-temurin:17-jdk AS builder

RUN apt-get update && apt-get install -y git && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

# Copy all project sources
COPY Vitruv-Change/ Vitruv-Change/
COPY Vitruv/ Vitruv/
COPY BrakeCaseStudy/ BrakeCaseStudy/
COPY mobstr-vsum/ mobstr-vsum/

# Build dependencies (cached layer)
RUN cd Vitruv-Change && ./mvnw clean install -Dmaven.test.skip=true -q
RUN cd Vitruv && ./mvnw clean install -Dmaven.test.skip=true -q
RUN cd mobstr-vsum && ./mvnw clean install -DskipTests -q
RUN cd BrakeCaseStudy && ./mvnw clean install -DskipTests -q

# Runtime stage (same image, keeps Maven cache)
FROM builder

WORKDIR /workspace

ENTRYPOINT ["bash", "BrakeCaseStudy/reproduce.sh"]
CMD []
