package io.univalence.double_jointures_ks.tools

import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.streams
import org.apache.kafka.streams.kstream.{Named, ValueTransformerWithKey}
import org.apache.kafka.streams.processor.ProcessorContext
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.kstream.KStream
import org.apache.kafka.streams.state.{KeyValueStore, Stores}

case class ExtraStreamBuilder(builder: StreamsBuilder) {

  /**
   * Perform a join between two streams.
   *
   * The join type (inner, left, right, outer...) is determined by the
   * joiner function.
   *
   * @param left   left stream
   * @param right  right stream
   * @param name   prefix name for created processors
   * @param joiner apply a transformation on input values. The function
   *               receives two options and may return null.
   */
    def join[K, A, B, R](
                          left: KStream[K, A],
                          right: KStream[K, B],
                          name: String
                        )(
                          joiner: (Option[A], Option[B]) => Option[R]
                        )(keySerde: Serde[K], leftValueSerde: Serde[A], rightValueSerde: Serde[B]): KStream[K, R] = {
      val leftStoreName = s"$name-left-table"
      registerStore(leftStoreName, keySerde, leftValueSerde)
      val rightStoreName = s"$name-right-table"
      registerStore(rightStoreName, keySerde, rightValueSerde)

      val leftRStream =
        left.flatTransformValues(
          () => partialJoin[K, A, B, R](leftStoreName, rightStoreName)(joiner),
          Named.as(s"$name-left-join"),
          leftStoreName,
          rightStoreName
        )

      val rightRStream =
        right.flatTransformValues(
          () => partialJoin[K, B, A, R](rightStoreName, leftStoreName)((b, a) => joiner(a, b)),
          Named.as(s"$name-right-join"),
          leftStoreName,
          rightStoreName
        )

      leftRStream.merge(rightRStream, Named.as(s"$name-merge"))
    }

  private def partialJoin[K, V1, V2, R](v1StoreName: String, v2StoreName: String)(
    joiner: (Option[V1], Option[V2]) => Option[R]
  ): ValueTransformerWithKey[K, V1, Iterable[R]] =
    new ValueTransformerWithKey[K, V1, Iterable[R]] {
      var v1Store: KeyValueStore[K, V1] = _
      var v2Store: KeyValueStore[K, V2] = _

      override def init(context: ProcessorContext): Unit = {
        v1Store = context.getStateStore[KeyValueStore[K, V1]](v1StoreName)
        v2Store = context.getStateStore[KeyValueStore[K, V2]](v2StoreName)
      }

      override def transform(readOnlyKey: K, value: V1): scala.Iterable[R] = {
        val v2Value = Option(v2Store.get(readOnlyKey))

        if (value == null)
          v1Store.delete(readOnlyKey)
        else
          v1Store.put(readOnlyKey, value)

        joiner(Option(value), v2Value)
      }

      override def close(): Unit = ()
    }

  private def registerStore[K, V](
                                   storeName: String,
                                   keySerde: Serde[K],
                                   valueSerde: Serde[V]
                                 ): streams.StreamsBuilder = {
    val storeSupplier = Stores.persistentKeyValueStore(storeName)
    val storeBuilder =
      Stores.keyValueStoreBuilder(storeSupplier, keySerde, valueSerde)
    builder.addStateStore(storeBuilder)
  }
}
