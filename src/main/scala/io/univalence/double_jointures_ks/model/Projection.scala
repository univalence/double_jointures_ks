package io.univalence.double_jointures_ks.model

import com.sksamuel.avro4s.JsonFormat
import com.sksamuel.avro4s.kafka.GenericSerde

import java.time.Instant

case class Projection(product: String, quantity: Double)

object Projection {
  val valueSerde = new GenericSerde[Projection](JsonFormat)
}
