package ca.example.email.impl

import java.util.UUID

import akka.Done
import akka.stream.scaladsl.Flow
import ca.example.email.api.EmailTopics
import ca.example.user.api.{UserEventTypes, UserKafkaEvent, UserService}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry

import scala.concurrent.{ExecutionContext, Future}

class UserEventListener(registry: PersistentEntityRegistry,
                        userClient: UserService)(implicit ec: ExecutionContext)  {

  private def refFor(emailId: UUID) = registry.refFor[EmailEntity](emailId.toString)

  userClient.userEvents.subscribe.atLeastOnce(Flow[UserKafkaEvent].mapAsync(1) {

    case UserKafkaEvent(UserEventTypes.REGISTERED, userId, data) =>
      val emailId = UUID.randomUUID()
      refFor(emailId).ask(ScheduleEmail(emailId, userId, data("email"), EmailTopics.WELCOME, "Welcome on our website !"))
        .map(_ => true) //send the actual email here.
        .map(isEmailSent => {
        if (isEmailSent) {
          refFor(emailId).ask(SetEmailDelivered)
        } else {
          refFor(emailId).ask(SetEmailFailed)
        }})
        .map(_ => Done)

    case _ => Future.successful(Done)
  })
}
