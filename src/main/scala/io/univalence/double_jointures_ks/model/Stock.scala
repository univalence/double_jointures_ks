package io.univalence.double_jointures_ks.model

import com.sksamuel.avro4s.JsonFormat
import com.sksamuel.avro4s.kafka.GenericSerde
import org.apache.kafka.common.serialization.Serde

import java.time.Instant


case class Stock(
    product: String,
    checkedAt: Instant,
    quantity: Double
) { self =>
  def toStockAndOrder: StockAndOrder = StockAndOrder(Some(self), None)
}

object Stock {
  val valueSerde: Serde[Stock]  = new GenericSerde[Stock](JsonFormat)
}
