package io.univalence.double_jointures_ks

import io.univalence.double_jointures_ks.model.{Key, Order, Projection, Stock}
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.kstream._
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig, Topology}

import java.util.Properties

object KTableJoinMain extends App {
  import scala.jdk.CollectionConverters._
  val applicationName = "kafka-streams-ktable-join"

  // input topics
  val StockTopic = "stock-stream"
  val OrderTopic = "order-stream"
  // output topic
  val ProjectionTopic = "projection-stream"

  /** Create the topology for ATP simplified.
   *
   * @param builder
   *   Kafka Streams topology/stream builder
   */
  def createTopology(builder: StreamsBuilder): Unit = {
    /* Create the stock KStream from the stock topic.
     */
    val stocks: KStream[Key, Stock] =
      builder.stream(StockTopic)(
        Consumed.`with`(Key.keySerde, Stock.valueSerde)
      )

    /* Create a KStream for orders the same way.
     */
    val orders: KStream[Key, Order] =
      builder.stream(OrderTopic)(
        Consumed.`with`(Key.keySerde, Order.valueSerde)
      )

    val orderTable: KTable[Key, Order] =
      orders
        .groupByKey(Grouped.`with`(Key.keySerde, Order.valueSerde))
        .reduce((order1, order2) => mergeOrders(order1, order2))(
          Materialized.as("order-table")(Key.keySerde, Order.valueSerde)
        )

    val stockTable: KTable[Key, Stock] =
      stocks
        .groupByKey(Grouped.`with`(Key.keySerde, Stock.valueSerde))
        .reduce((stock1, stock2) => moreRecentOf(stock1, stock2))(
          Materialized.as("stock-table")(Key.keySerde, Stock.valueSerde)
        )

    val projections: KStream[Key, Projection] =
      stockTable
        .leftJoin(orderTable)((stock, order) =>
          project(Option(stock), Option(order))
        )
        .toStream
        .flatMapValues(_.toList)


    projections.to(ProjectionTopic)(
      Produced.`with`(Key.keySerde, Projection.valueSerde)
    )
  }

  def getTopology: Topology = {
    val builder = new StreamsBuilder()
    createTopology(builder)

    builder.build()
  }

  def project(
               stock: Option[Stock],
               order: Option[Order]
             ): Option[Projection] = {
    stock.map(s => Projection(s.product, s.quantity - order.map(_.quantity).getOrElse(0.0)))
  }

  def moreRecentOf(stock1: Stock, stock2: Stock): Stock =
    if (stock1.checkedAt.isAfter(stock2.checkedAt))
      stock1
    else
      stock2

  def mergeOrders(
                   order1: Order,
                   order2: Order
                 ): Order =
    order2.copy(quantity = order1.quantity + order2.quantity)

  val numThreads = java.lang.Runtime.getRuntime.availableProcessors().toString

  val groupId: String = "ktableJoinGroupId"

  val ksProperties: Properties = new Properties()
  ksProperties.putAll(
    Map[String, AnyRef](
      StreamsConfig.BOOTSTRAP_SERVERS_CONFIG -> "localhost:9092",
      /* The application.id will determine the group.id but also
       * the directory name where the KTable data will be stored.
       *
       * If you modify application.id, you will create a new
       * consumer group that will consume input topics from the very
       * beginning and you will create a new directory structure for
       * your data storages. Thus, it is like deploying a brand new
       * application.
       */
      StreamsConfig.APPLICATION_ID_CONFIG -> s"$applicationName-$groupId",
      /* Kafka Streams can parallelize the work if you have multiple
       * cores.
       *
       * By default the value is one. Fix the value to the maximum
       * number of cores available in scalable environment.
       */
      StreamsConfig.NUM_STREAM_THREADS_CONFIG -> numThreads,
      /* This parameter allows Kafka Streams to perform optimization
       * on your topology.
       *
       * The parameter is disable by default.
       */
      StreamsConfig.TOPOLOGY_OPTIMIZATION_CONFIG -> StreamsConfig.OPTIMIZE
    ).asJava
  )

  val topology: Topology = getTopology

  val kafkaStreams = new KafkaStreams(topology, ksProperties)
  sys.addShutdownHook { kafkaStreams.close() }
  kafkaStreams.start()

  println(topology.describe())
}
