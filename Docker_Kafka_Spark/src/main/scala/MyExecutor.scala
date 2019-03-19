import MyJdbcClient.{verifyConnection, verifyTweetsTable}
import MyTwitterClient.TwitterStatus
import net.liftweb.json.DefaultFormats

/**
  *
  * Created by Pulkit on 16Mar2019
  *
  * Content of this file is proprietary and confidential.
  * It shall not be reused or disclosed without prior consent
  * of distributor
  **/

object MyExecutor {

  var isTestRun = false


  def main(args: Array[String]): Unit = {
    if (args.length >= 4) {

      if (args.length == 5) {
        isTestRun = args(4).toBoolean
      }
      verifyConnection()
      verifyTweetsTable()

      MyTwitterClient.startTwitterStreaming(
        consumerKey = args(0),
        consumerSecret = args(1),
        accessToken = args(2),
        accessTokenSecret = args(3)
      )
      MyDataTransformer.startSparkStreaming()
    } else {
      System.out.println("Usage: java -jar /path/to/Docker_Kafka_Spark.jar <Twitter Developer Consumer Key> <Twitter Developer Consumer Secret> <Twitter Developer Access Token> <Twitter Developer Access Secret>")
    }
  }

  def testDatabaseInsert(): Unit = {
    MyJdbcClient.loadTweets(List(
      (
        TwitterStatus(
          statusId = 51654,
          userId = 123,
          userName = "abc",
          createdAt = "2019-03-18 19:50:53.000",
          language = "en",
          favoriteCount = 2,
          hashTags = List("hashtag", "h2")
        ),
        2), (
        TwitterStatus(
          statusId = 321354,
          userId = 456,
          userName = "def",
          createdAt = "2017-03-18 19:42:53.000",
          language = "nl",
          favoriteCount = 0,
          hashTags = List("hashtag", "hashtag2")
        ),
        55)
    ))
  }

}
