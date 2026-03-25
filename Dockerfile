##
## Dockerfile for reproducing evaluation results of:
## "Directed Replay-Based Merge for Multi-Model Consistency Networks"
## (MODELS 2026)
##
## Build from workspace root:
##   docker build -f BrakeCaseStudy/Dockerfile -t vitruv-merge-ae .
## Run:
##   docker run --rm vitruv-merge-ae                  # comparison tables
##   docker run --rm vitruv-merge-ae --performance    # + performance benchmarks
##   docker run --rm vitruv-merge-ae --all            # + Track B (63 scenarios)
##

FROM eclipse-temurin:17-jdk AS builder

RUN apt-get update && apt-get install -y git && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

# Copy all project sources
COPY Vitruv-Change/ Vitruv-Change/
COPY Vitruv/ Vitruv/
COPY BrakeCaseStudy/ BrakeCaseStudy/
COPY Vitruv-Merge-Tests/ Vitruv-Merge-Tests/
COPY mobstr-vsum/ mobstr-vsum/

# Build dependencies (cached layer)
RUN cd Vitruv-Change && ./mvnw clean install -Dmaven.test.skip=true -q
RUN cd Vitruv && ./mvnw clean install -Dmaven.test.skip=true -q
RUN cd BrakeCaseStudy && ./mvnw clean install -DskipTests -q
RUN cd Vitruv-Merge-Tests && ./mvnw clean install -Dmaven.test.skip=true -q
RUN cd mobstr-vsum && ./mvnw clean install -DskipTests -q

# Runtime stage (same image, keeps Maven cache)
FROM builder

WORKDIR /workspace

ENTRYPOINT ["bash", "BrakeCaseStudy/reproduce.sh"]
CMD []
