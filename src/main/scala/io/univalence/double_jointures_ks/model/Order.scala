package io.univalence.double_jointures_ks.model

import com.sksamuel.avro4s.JsonFormat
import com.sksamuel.avro4s.kafka.GenericSerde
import org.apache.kafka.common.serialization.Serde

import java.time.Instant

case class Order(
    product: String,
    quantity: Double
) { self =>
  def toStockAndOrder: StockAndOrder = StockAndOrder(None, Some(self))
}

object Order {
  val valueSerde: Serde[Order]  = new GenericSerde[Order](JsonFormat)
}