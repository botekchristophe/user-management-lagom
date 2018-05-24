package ca.example.email.impl

import java.util.UUID

import akka.NotUsed
import akka.stream.Materializer
import ca.example.email.api.{Email, EmailEventTypes, EmailKafkaEvent, EmailService}
import ca.exemple.utils.{Marshaller, ErrorResponses => ER}
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.scaladsl.server.ServerServiceCall

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

class EmailServiceImpl(registry: PersistentEntityRegistry,
                       readSideConnector: EmailReadSideConnector)
                      (implicit ec: ExecutionContext, mat: Materializer) extends EmailService with Marshaller {

  private def refFor(emailId: UUID) = registry.refFor[EmailEntity](emailId.toString)

  override def getEmails: ServiceCall[NotUsed, List[Email]] =
    ServerServiceCall((_, _) =>
      Future.successful(List.empty[Email])
        .map(_.marshall)
    )

  override def emailEvents: Topic[EmailKafkaEvent] =
    TopicProducer.taggedStreamWithOffset(EmailEvent.Tag.allTags.to[immutable.Seq]) { (tag, offset) =>
      registry.eventStream(tag, offset)
        .filter { evt =>
          evt.event match {
            case _: EmailVerified => true
            case _ => false
          }
        }
        .map(ev => ev.event match {
          case EmailVerified(userId) =>
            (EmailKafkaEvent(EmailEventTypes.VERIFIED, userId, Map.empty[String, String]), ev.offset)
        })
    }
}

