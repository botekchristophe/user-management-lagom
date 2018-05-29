package ca.example.email.impl.events

import java.util.UUID

import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventShards, AggregateEventTag, AggregateEventTagger}
import play.api.libs.json.{Format, Json}

trait UserEvent extends AggregateEvent[UserEvent] {
  override def aggregateTag: AggregateEventTagger[UserEvent] = UserEvent.Tag
}
object UserEvent {
  val NumShards = 10
  val Tag: AggregateEventShards[UserEvent] = AggregateEventTag.sharded[UserEvent](NumShards)
}

case class UserAdded(id: UUID,
                     username: String,
                     email: String) extends UserEvent
object UserAdded {
  implicit val format: Format[UserAdded] = Json.format[UserAdded]
}
