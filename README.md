
# Introduction

This is an application to stream twitter status, apply simple data transformation and load it into a database. Following is the high level overview of what is available.
* Step 1: Create Twitter Developer account and create an application to generate credentials to use Twitter Streaming API.
* Step 2: [Docker-compose](https://docs.docker.com/compose/) setup to integrate docker images for [Apache Kafka](https://hub.docker.com/r/bitnami/kafka), [Apache Spark](https://github.com/gettyimages/docker-spark) and [PostgreSQL](https://hub.docker.com/r/bitnami/postgresql).
* Step 3: [Twitter developer](https://developer.twitter.com/en.html) account and streaming listener setup to stream status and a Kafka Producer to send them across ZooKeeper cluster.
* Step 4: [Apache Spark streaming API](https://spark.apache.org/docs/2.1.0/streaming-kafka-integration.html) setup to consume the twitter status produced in previous step.



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

# Design

This is a proof-of-concept created for learning purpose. We will need to go through a set of steps to deploy it into production environment. To get started faster, we intentionally incured a technical debt. Such as, we have reused docker container images for Kafka, Spark and PostgreSQL that are already available. As a result, we are dependent on the version of the frameworks and installation configuration that are available out of the box.

Now that we have made ourselves familiar with goal, motivation and constraints, let's understand more about basic design and principles. Our goal is to build a pub/sub system in an automated fashion that can allow us to collect and distribute streaming data between source and down-stream systems. So, our first principle is to assume that any component could potentially replaced or removed from the architecture. For example, Apache Spark can be replced with Apache Flink, PostgreSQL could be replaced with MongoDB.

Based on the high-level diagram from the following image, it is clear that there are three different layers: Source Sink(Twitter Publisher), Collect and Process(Kafka and Spark) and Store (RDBMS). 

(./HighLevelDesign.png)
We are using Twitter4J library to get data from twitter streaming API. Before we make any decision on the data we are collecting, it is important to notice that we are only collecting a fraction of the total twitter status generated (firehose). Twitter streaming API also has some rate limits but for now they are handled gracefully by Twitter4J library.

Twitter4J has a streaming listener, ```StatusListener```, that will call onStatus method upon receiving new status.
```scala
def onStatus(status: Status): Unit = {
val twitterStatus =
          TwitterStatus(
            ...
          )

        kafkaProducer.send(new ProducerRecord[String, String](KAFKA_TOPIC, twitterStatus.userName, statusJson))
		...
}
```
For now, we are only collecting handful of attributes from the status. We will convert the parameters, translate into JSON format and send it to kafka producer. We could also add more twitter listners to redirect data to multiple kafka brokers if the (non)functional requiements demand it. Since we are using network interface bridge within our ```docker-compose.yml```, we are able to access other containers using their name as host.

Once we have publiced the incoming messages, we are now able to consume these messages through the zookeeper. Now, we can set configuration details on how to consume these messages from Kafka using Spark-Kafka-Streaming client. Spark streaming uses a micro batch architecture, so we will set a batch interval of 20 seconds. It is a choice without performing extensive testing, but keep in mind it has implication on our data transformation logic which we will cover in a moment.
```scala
def startSparkStreaming(): Unit = {
	...
    val kafkaParams = Map[String, Object](
      "bootstrap.servers" -> KAFKA_HOST,
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> "my_group_id",
      "auto.offset.reset" -> "latest",
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )
	...
}
```
There is also a way to directly stream twitter data into Spark streaming without using Kafka but that defeats the purpose of learning the integration between frameworks (the hard way!).

To keep things simple, we are going to create a hypothetical data transformation on twitter status that we are collecting. The goal is to understand at any point in time, for a given period, what is the score of a status based on hashtag popularity. The hashtag_popularity is measured by the ```number of occurances``` and score is calculated based on ```total of (hashtag_popularity_n)```. For example, in last 30 seconds, if there are 5 occurances of #Paris and 2 occurances of #Amsterdam and a status contains hashtags #Paris #Amsterdam #London then the score will be ```5+2+1 = 8```. Of course, experts would argue on whether this is the best way to rank a twitter status or not!

We will also filter the incoming status with language 'en' (ISO 2 code for English).

```scala
...
    val hashtagAndCount =
      statuses
        .flatMap(_.hashTags)
        .map(ele => (ele, 1))
        .reduceByKey(_ + _)
        .cache()


    val usersAndHashtagCount =
      statuses
        .flatMap { status =>
          status
            .hashTags
            .map(hashtag => (hashtag, status))
        }

    usersAndHashtagCount
      .join(hashtagAndCount)
      .map { ele =>
        (ele._2._1, ele._2._2)
      }
      .reduceByKey(_ + _)
      .foreachRDD { rdd =>
        MyJdbcClient.loadTweets(rdd.collect().toList)
      }
...
```
Note the use of cache function on the hashtag rdd. We can change the order of the caching between ```usersAndHashtagCount``` and ```hashtagAndCount``` to improve the performace. Finally, we are collecting all the data from RDD and inserting them into PostgreSQL. For the sake of simplicity, you can think of RDD as chunks of data spread across spark cluster (specifically Yarn cluster in our case) and DStream is a logical collection of all these RDDs.

We can always create multiple mapping on DStream to carry out different analyses like the most trending keywords over different time window or the most trending and hated discussions (sentiment analysis of a status and the  count of retweet status id in reference to the original staus id) etc. For now, let's be happy with one analysis and move on to our next topic, the choice of a storage system.

We could have any of the other available options to store our data like internal storage of Kafka, of course we would need to impplement high availability for kafka cluster or HDFS through Sprak or even better NoSql databases like MongoDB or Cassandra. Given our structure and nature of the source system (twitter) MongoDB (highly flexible schema and adaptive to multiple applications) or HDFS(raw, efficient and cheap) could prove to be optimal choice, but we need to understand what other type of data we want to combine and what will the down-stream systems will look like. To avoid all that headache and to find out we made a wrong choice in chosing the database, we will use widely used PostgreSQL now.

# Next Steps

* Build our own docker image to automate the installation process and manage the frameworks setup. For any new project, finding the right combinations of dependencies could be a huge challenge.
* Replace the storage layer with appropriate storage system MongoDB, HDFS etc.
* Add SSL to network communications.
* Move the database configuration and credentials to environment file. Currently they are hard-coded within source code.
* Add module to manage resources of Yarn/Spark. Add auto commit, data check points and graceful recovery from node failures.
* Update network configurations in docker file. Also, enable remote access to the Spark and Kafka cluster.
* Add jupyter or Zeplin to application to ease the data analysis process.
* Add unit tests and automate integration tests.
* Automate code build and deployment process to avoid losing sleep over production deployments.
* Any suggestions are welcome, use common sense and Keep'em coming!