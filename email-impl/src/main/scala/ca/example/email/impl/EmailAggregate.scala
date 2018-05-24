package ca.example.email.impl

import java.util.UUID

import ca.example.email.api.EmailTopics.EmailTopic
import ca.example.email.impl.EmailStatuses.EmailStatus
import ca.example.jsonformats.JsonFormats.enumFormat
import play.api.libs.json.Format

case class EmailAggregate(status: EmailStatus,
                          id: UUID,
                          recipientId: UUID,
                          recipientAddress: String,
                          topic: EmailTopic,
                          content: String,
                          deliveredOn: Option[Long] = None)

object EmailStatuses extends Enumeration {
  type EmailStatus = Value
  val SCHEDULED, FAILED, DELIVERED = Value

  implicit val format: Format[EmailStatus] = enumFormat(EmailStatuses)
}


