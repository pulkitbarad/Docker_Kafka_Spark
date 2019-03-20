import MyTwitterClient.TwitterStatus
import net.liftweb.json.DefaultFormats
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.SparkConf
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010._
import org.apache.spark.streaming.{Seconds, StreamingContext}

/**
  *
  * Created by Pulkit on 17Mar2019
  *
  * Content of this file is proprietary and confidential.
  * It shall not be reused or disclosed without prior consent
  * of distributor
  **/

object MyDataTransformer {

  val KAFKA_HOST = if (MyExecutor.isTestRun) "172.21.0.2:9092" else "kpmg_kafka_1:9092"
  val KAFKA_TOPIC = "mytopic"

  def startSparkStreaming(): Unit = {

    // Create a local StreamingContext with two working thread and batch interval of 1 second.

    val conf = new SparkConf().setMaster("local[2]").setAppName("Docker_Kafka_Spark")
    val streamingContext = new StreamingContext(conf, Seconds(10))


    val kafkaParams = Map[String, Object](
      "bootstrap.servers" -> KAFKA_HOST,
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> "my_group_id",
      "auto.offset.reset" -> "latest",
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )

    val topics = Array(KAFKA_TOPIC)
    val inputStream = KafkaUtils.createDirectStream[String, String](
      streamingContext,
      PreferConsistent,
      Subscribe[String, String](topics, kafkaParams)
    )


    val statuses =
      inputStream
        .map { ele =>
          implicit val formats = DefaultFormats
          val twitterStatusAST = net.liftweb.json.parse(ele.value())
          twitterStatusAST.extract[TwitterStatus]
          //          TwitterStatus("a", "b", "c", 0, List())
        }
        .filter(_.language.equalsIgnoreCase("en"))
        .window(Seconds(30), Seconds(30))

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


    streamingContext.sparkContext.getConf.set("spark.streaming.kafka.consumer.cache.enabled", "false")
    streamingContext.sparkContext.setLogLevel("ERROR")
    streamingContext.start()
    streamingContext.awaitTermination()
  }
}
