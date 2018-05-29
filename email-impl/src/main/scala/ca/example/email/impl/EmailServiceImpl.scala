package ca.example.email.impl

import java.util.UUID

import akka.NotUsed
import akka.stream.Materializer
import ca.example.email.api.{EmailEventTypes, EmailKafkaEvent, EmailResponse, EmailService}
import ca.example.email.impl.entities.internal.EmailEntity
import ca.example.email.impl.events.{EmailEvent, EmailVerified}
import ca.example.email.impl.readside.internal.EmailReadSideConnector
import ca.example.email.impl.readside.remote.UserReadSideConnector
import ca.exemple.utils.Marshaller
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import org.slf4j.LoggerFactory

import scala.collection.immutable
import scala.concurrent.ExecutionContext

class EmailServiceImpl(registry: PersistentEntityRegistry,
                       readSideConnector: EmailReadSideConnector,
                       userReadSideConnector: UserReadSideConnector)
                      (implicit ec: ExecutionContext, mat: Materializer) extends EmailService with Marshaller {

  private val log = LoggerFactory.getLogger(classOf[EmailServiceImpl])

  private def refFor(emailId: UUID) = registry.refFor[EmailEntity](emailId.toString)

  override def getEmails: ServiceCall[NotUsed, List[EmailResponse]] = {
    val users = userReadSideConnector.getUsers.map(_.toList)
    log.info(s"Get Emails found theses users: ${users.foreach(println)}")
    ServiceCall(_ => readSideConnector.getEmails.map(_.toList))
  }


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

