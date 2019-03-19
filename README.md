
# Introduction

This is an application to stream twitter statuses, apply simple data transformation and load it into a database. Following is the high level overview of what is available.
* Step 1: Create Twitter Developer account and create a application to generate credentials to use Twitter Streaming API.
* Step 2: [Docker-compose](https://docs.docker.com/compose/) setup to integrate docker images for [Apache Kafka](https://hub.docker.com/r/bitnami/kafka), [Apache Spark](https://github.com/gettyimages/docker-spark) and [PostgreSQL](https://hub.docker.com/r/bitnami/postgresql).
* Step 3: [Twitter developer](https://developer.twitter.com/en.html) account and streaming listener setup to stream statuses and a Kafka Producer to send them across ZooKeeper cluster.
* Step 4: [Apache Spark streaming API](https://spark.apache.org/docs/2.1.0/streaming-kafka-integration.html) setup to consume the twitter statuses produced in previous step.



# Prerequisites

* This document will provide instructions based on WinPutty installed on Windows 10 or any unix distribution.
* Docker-machine or Docker desktop (Windows 10) installed.
* Java (1.8) and Scala (2.11.8) installed.


# How to install?

Download the Docker_Kafka_Spark/docker-compose.yml.
Download the Docker_Kafka_Spark/Docker_Kafka_Spark/out/artifacts/Docker_Kafka_Spark.jar.
Start the docker containers.
```bash
docker-compose up
```
Get the docker container id of the spark master and spark worker nodes.
```bash
docker ps
```
Copy the jar file Docker_Kafka_Spark/Docker_Kafka_Spark/out/artifacts/Docker_Kafka_Spark.jar to spark containers.
NOTE: Later, we will build the docker image to automate the file transfer and start the streaming application.
```bash
docker cp ./Docker_Kafka_Spark/out/artifacts/Docker_Kafka_Spark.jar <Spark container id>:/opt/Docker_Kafka_Spark.jar
```
Start the spark container shell and execute the spark application.
```bash
winpty docker exec -it kpmg_master_1 bash
spark-submit --class MyExecutor --deploy-mode client --driver-memory 1g --executor-memory 1g --executor-cores 1 /opt/Docker_Kafka_Spark.jar <Consumer Key> <Consumer Secret> <Access Token> <Access Secret>
```

# How do I verify the installation?
Create a new kafka topic. (Ignore the winpty command prefix if you are on unix.)
```bash
winpty docker exec -it kpmg_kafka_1 kafka-topics.sh --create --zookeeper kpmg_zookeeper_1:2181 --replication-factor 1 --partitions 1 --topic mytopic
```
List and verify the newly created kafka topic.
```bash
winpty docker exec -it kpmg_kafka_1 kafka-topics.sh --list --zookeeper kpmg_zookeeper_1
```
Create a kafka producer (Preferably in a new command shell).
```bash
winpty docker exec -it kpmg_kafka_1 kafka-console-producer.sh --broker-list kpmg_kafka_1:9092 --topic mytopic
```
Create a kafka consumer.
```bash
winpty docker exec -it kpmg_kafka_1 kafka-console-consumer.sh --bootstrap-server kpmg_kafka_1:9092 --topic mytopic --from-beginning
```
Give some text inputs in the producer shell and see if you can verify them in the consumer shell.
Connect to postgresql container and verify the access using psql.
```bash
winpty docker exec -it kpmg_postgresql_1 bash
psql -U postgresql
\c my_database
\dt
```

#Design


# Next Steps
