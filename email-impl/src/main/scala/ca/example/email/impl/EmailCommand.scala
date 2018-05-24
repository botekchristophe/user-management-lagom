package ca.example.email.impl

import java.util.UUID

import akka.Done
import ca.example.email.api.EmailTopics.EmailTopic
import ca.example.jsonformats.JsonFormats.singletonFormat
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import play.api.libs.json.{Format, Json}


trait EmailCommand[R] extends ReplyType[R]

case class ScheduleEmail(id: UUID,
                         recipientId: UUID,
                         recipientAddress: String,
                         topic: EmailTopic,
                         content: String) extends EmailCommand[Done]
object ScheduleEmail {
  implicit val format: Format[ScheduleEmail] = Json.format[ScheduleEmail]
}

case object SetEmailDelivered extends EmailCommand[Done] {
  implicit val format: Format[SetEmailDelivered.type] = singletonFormat(SetEmailDelivered)
}

case object SetEmailFailed extends EmailCommand[Done] {
  implicit val format: Format[SetEmailFailed.type] = singletonFormat(SetEmailFailed)
}
