package ca.example.user.impl

import java.util.UUID

import akka.Done
import akka.stream.scaladsl.Flow
import ca.example.email.api.{EmailEventTypes, EmailKafkaEvent, EmailService}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry

import scala.concurrent.{ExecutionContext, Future}

class EmailEventListener(registry: PersistentEntityRegistry,
                         emailClient: EmailService)(implicit ec: ExecutionContext)  {

  private def refFor(userId: UUID) = registry.refFor[UserEntity](userId.toString)

  emailClient.emailEvents.subscribe.atLeastOnce(Flow[EmailKafkaEvent].mapAsync(1) {

    case EmailKafkaEvent(EmailEventTypes.VERIFIED, userId, _) =>
      refFor(userId).ask(VerifyUser).map(_ => Done)

    case _ => Future.successful(Done)
  })
}

