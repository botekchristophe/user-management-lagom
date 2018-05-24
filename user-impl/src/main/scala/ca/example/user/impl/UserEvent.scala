package ca.example.user.impl

import java.util.UUID

import ca.example.user.impl.UserStatus.UserStatus
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventShards, AggregateEventTag, AggregateEventTagger}
import play.api.libs.json.{Format, Json}

trait UserEvent extends AggregateEvent[UserEvent] {
  override def aggregateTag: AggregateEventTagger[UserEvent] = UserEvent.Tag
}

object UserEvent {
  val NumShards = 10
  val Tag: AggregateEventShards[UserEvent] = AggregateEventTag.sharded[UserEvent](NumShards)
}

case class AccessTokenGranted(userId: UUID, session: UserSession) extends UserEvent
object AccessTokenGranted {
  implicit val format: Format[AccessTokenGranted] = Json.format[AccessTokenGranted]
}
case class AccessTokenRevoked(access_token: UUID) extends UserEvent
object AccessTokenRevoked {
  implicit val format: Format[AccessTokenRevoked] = Json.format[AccessTokenRevoked]
}

// change name to fit business model
case class UserCreated(userId: UUID, username: String, hash: String, status: UserStatus, email: String) extends UserEvent
object UserCreated {
  implicit val format: Format[UserCreated] = Json.format[UserCreated]
}

case class UserVerified(userId: UUID) extends UserEvent
object UserVerified {
  implicit val format: Format[UserVerified] = Json.format
}

case class UserUnVerified(userId: UUID) extends UserEvent
object UserUnVerified {
  implicit val format: Format[UserUnVerified] = Json.format
}

// change name to fit business model
case class UserDeleted(userId: UUID) extends UserEvent
object UserDeleted {
  implicit val format: Format[UserDeleted] = Json.format
}
