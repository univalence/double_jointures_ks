package io.univalence.double_jointures_ks.model

import com.sksamuel.avro4s.JsonFormat
import com.sksamuel.avro4s.kafka.GenericSerde

case class StockAndOrder(
                          stock: Option[Stock],
                          order: Option[Order]
                        )
object StockAndOrder {
  val valueSerde = new GenericSerde[StockAndOrder](JsonFormat)
  val empty = StockAndOrder(None, None)
}
