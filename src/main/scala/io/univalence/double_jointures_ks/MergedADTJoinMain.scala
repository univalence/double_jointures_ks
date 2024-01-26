package io.univalence.double_jointures_ks

import io.univalence.double_jointures_ks.model.{Key, Order, Projection, Stock, StockAndOrder}
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.kstream._
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig, Topology}

import java.util.Properties

object MergedADTJoinMain extends App {
  import scala.jdk.CollectionConverters._
  val applicationName = "kafka-streams-double-partial-join"

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

    val finalStocks = stocks.mapValues(_.toStockAndOrder)

    /* Create a KStream for orders the same way.
     */
    val orders: KStream[Key, Order] =
      builder.stream(OrderTopic)(
        Consumed.`with`(Key.keySerde, Order.valueSerde)
      )

    val finalOrders = orders.mapValues(_.toStockAndOrder)

    val mergedStream = finalOrders.merge(finalStocks)

    /**
     * Aggregate the unique stream to compile information. This allows to join information as both the stock and order
     * stream feeds it with their own normalized messages.
     *
     * If the current value from the state store is the empty one (i.e. StockAndOrder(None, None)), then we simply
     * return the value coming from one of the two streams.
     *
     * If it isn't, then we merge the new and current values.
     *
     * The next step with the flatMapValues is to produce a projection thanks to the aggregated StockAndOrder message.
     */
    val projections =
      mergedStream
        .groupByKey(Grouped.`with`(Key.keySerde, StockAndOrder.valueSerde))
        .aggregate(StockAndOrder.empty){
          (_, newValue, current) =>
            (newValue, current) match {
              case (_, StockAndOrder.empty) => newValue
              case (StockAndOrder(None, _), _) => current.copy(order = newValue.order)
              case (StockAndOrder(_, None), _) => current.copy(stock = newValue.stock)
            }
        }(
          Materialized.as("stock-and-order-agg")(Key.keySerde, StockAndOrder.valueSerde)
        )
        .toStream
        .flatMapValues { v =>
          project(v.stock, v.order) match {
            case Some(v) => Seq(v)
            case None => Seq.empty
          }
        }

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

  val groupId: String = "doublePartialJoinGroupId"

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

  // print topology, paste it in https://zz85.github.io/kafka-streams-viz/
  println(topology.describe())
}
