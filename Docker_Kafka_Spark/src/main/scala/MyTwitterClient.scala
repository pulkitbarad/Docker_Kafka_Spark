import java.text.SimpleDateFormat
import java.util.Properties

import net.liftweb.json.DefaultFormats
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import twitter4j._
import twitter4j.conf.ConfigurationBuilder

/**
  *
  *
  * Created by Pulkit on 16MAR2019
  *
  * Content of this file is proprietary and confidential.
  * It shall not be reused or disclosed without prior consent
  * of distributor
  **/


object MyTwitterClient {

  val KAFKA_HOST = if (MyExecutor.isTestRun) "172.21.0.2:9092" else "kpmg_kafka_1:9092"
  val KAFKA_TOPIC = "mytopic"

  case class TwitterStatus(
                            statusId: Long,
                            userId: Long,
                            userName: String,
                            createdAt: String,
                            language: String,
                            favoriteCount: Int,
                            hashTags: List[String]
                          )

  def startTwitterStreaming(consumerKey: String,
                            consumerSecret: String,
                            accessToken: String,
                            accessTokenSecret: String): Unit = {

    val kafkaConfig = new Properties()

    kafkaConfig.put("bootstrap.servers", KAFKA_HOST)
    kafkaConfig.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    kafkaConfig.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")

    val kafkaProducer = new KafkaProducer[String, String](kafkaConfig)

    val listener: StatusListener = new StatusListener() {
      def onStatus(status: Status): Unit = {

        val twitterStatus =
          TwitterStatus(
            statusId = status.getId,
            userId = status.getUser.getId,
            userName = status.getUser.getName,
            createdAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(status.getCreatedAt),
            language = status.getLang,
            favoriteCount = status.getFavoriteCount,
            hashTags = status.getHashtagEntities.map(_.getText).toList
          )

        implicit val formats = DefaultFormats

        val statusJson = net.liftweb.json.Serialization.write(twitterStatus)

        kafkaProducer.send(new ProducerRecord[String, String](KAFKA_TOPIC, twitterStatus.userName, statusJson))
      }

      override def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice): Unit = {
      }

      override def onTrackLimitationNotice(numberOfLimitedStatuses: Int): Unit = {
      }

      override def onException(ex: Exception): Unit = {
        ex.printStackTrace()
      }

      override def onScrubGeo(l: Long, l1: Long): Unit = {

      }

      override def onStallWarning(stallWarning: StallWarning): Unit = {

      }
    }

    val cb = new ConfigurationBuilder()
    cb.setDebugEnabled(true)
      .setOAuthConsumerKey(consumerKey)
      .setOAuthConsumerSecret(consumerSecret)
      .setOAuthAccessToken(accessToken)
      .setOAuthAccessTokenSecret(accessTokenSecret)
      .setDebugEnabled(false)

    val twitterStream: TwitterStream = new TwitterStreamFactory(cb.build()).getInstance
    twitterStream.addListener(listener)

    //    twitterStream.filter(new FilterQuery().track(Array("Trump")))
    twitterStream.sample()
  }
}
