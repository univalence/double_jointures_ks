package io.univalence.double_jointures_ks.model

import com.sksamuel.avro4s.JsonFormat
import com.sksamuel.avro4s.kafka.GenericSerde

case class Key(product: String)

object Key {
  val keySerde = new GenericSerde[Key](JsonFormat)
}