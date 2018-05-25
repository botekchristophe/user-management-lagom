package ca.example.email.impl

import java.util.UUID

import akka.Done
import ca.example.email.api.EmailTopics.EmailTopic
import ca.example.jsonformats.JsonFormats.singletonFormat
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventShards, AggregateEventTag, AggregateEventTagger}
import play.api.libs.json.{Format, Json}

trait EmailEvent extends AggregateEvent[EmailEvent] {
  override def aggregateTag: AggregateEventTagger[EmailEvent] = EmailEvent.Tag
}

object EmailEvent {
  val NumShards = 10
  val Tag: AggregateEventShards[EmailEvent] = AggregateEventTag.sharded[EmailEvent](NumShards)
}

case class EmailScheduled(id: UUID,
                          recipientId: UUID,
                          recipientAddress: String,
                          topic: EmailTopic,
                          content: String) extends EmailEvent
object EmailScheduled {
  implicit val format: Format[EmailScheduled] = Json.format[EmailScheduled]
}

case class EmailDelivered(id: UUID,
                          deliveredOn: Long) extends EmailEvent
object EmailDelivered {
  implicit val format: Format[EmailDelivered] = Json.format
}

case class EmailDeliveryFailed(id: UUID,
                               deliveredOn: Long) extends EmailEvent
object EmailDeliveryFailed {
  implicit val format: Format[EmailDeliveryFailed] = Json.format
}

case class EmailVerified(userId: UUID) extends EmailEvent
object EmailVerified {
  implicit val format: Format[EmailVerified] = Json.format
}