package ca.example.email.api

import java.util.UUID

import akka.NotUsed
import ca.example.email.api.EmailEventTypes.EmailEventType
import ca.example.email.api.EmailTopics.EmailTopic
import ca.example.jsonformats.JsonFormats._
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.deser.DefaultExceptionSerializer
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}
import play.api.libs.json.{Format, Json}
import play.api.{Environment, Mode}

trait EmailService extends Service {
  def getEmails: ServiceCall[NotUsed, List[EmailResponse]]

  def emailEvents: Topic[EmailKafkaEvent]

  def descriptor: Descriptor = {
    import Service._
    named("email").withCalls(
      restCall(Method.GET, "/api/emails", getEmails _)
    )
      .withAutoAcl(true)
      .withExceptionSerializer(new DefaultExceptionSerializer(Environment.simple(mode = Mode.Prod)))
      .withTopics(topic("EmailEvents", emailEvents))
  }
}

case class EmailResponse(to: String,
                         topic: EmailTopic,
                         content: String,
                         delivered: Boolean,
                         deliveredOn: Option[String] = None)
object EmailResponse {
  implicit val format: Format[EmailResponse] = Json.format[EmailResponse]
}

object EmailTopics extends Enumeration {
  type EmailTopic = Value
  val WELCOME, PASSWORD_RECOVERY = Value

  implicit val format: Format[EmailTopic] = enumFormat(EmailTopics)
}

case class EmailKafkaEvent(event: EmailEventType,
                           id: UUID,
                           data: Map[String, String] = Map.empty[String, String])
object EmailKafkaEvent {
  implicit val format: Format[EmailKafkaEvent] = Json.format
}

object EmailEventTypes extends Enumeration {
  type EmailEventType = Value
  val SCHEDULED, DELIVERED, DELIVERYFAILED, VERIFIED = Value

  implicit val format: Format[EmailEventType] = enumFormat(EmailEventTypes)
}

