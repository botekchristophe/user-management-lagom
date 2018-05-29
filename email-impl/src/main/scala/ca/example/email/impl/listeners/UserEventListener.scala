package ca.example.email.impl.listeners

import java.util.UUID

import akka.Done
import akka.stream.scaladsl.Flow
import ca.example.email.api.EmailTopics
import ca.example.email.impl.commands.{AddUserCommand, ScheduleEmail, SetEmailDelivered, SetEmailFailed}
import ca.example.email.impl.entities.internal.EmailEntity
import ca.example.email.impl.entities.remote.UserEntity
import ca.example.user.api.{UserEventTypes, UserKafkaEvent, UserService}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class UserEventListener(registry: PersistentEntityRegistry,
                        userClient: UserService)(implicit ec: ExecutionContext)  {

  private val log = LoggerFactory.getLogger(classOf[UserEntity])

  private def refForEmailEntity(emailId: UUID) = registry.refFor[EmailEntity](emailId.toString)
  private def refForUserEntity(userId: UUID) = registry.refFor[UserEntity](userId.toString)

  userClient.userEvents.subscribe.atLeastOnce(Flow[UserKafkaEvent].mapAsync(1) {

    case UserKafkaEvent(UserEventTypes.REGISTERED, userId, data) =>
      log.info("UserEventListener received a kafka event.")
      val emailId = UUID.randomUUID()
      refForEmailEntity(emailId).ask(ScheduleEmail(emailId, userId, data("email"), EmailTopics.WELCOME, "Welcome on our website !"))
        .map(_ => true) //send the actual email here.
        .map(isEmailSent => {
        if (isEmailSent) {
          refForEmailEntity(emailId).ask(SetEmailDelivered)
        } else {
          refForEmailEntity(emailId).ask(SetEmailFailed)
        }})
        //.map(_ => Done)
      refForUserEntity(userId).ask(AddUserCommand(userId, data.getOrElse("username", "bogus"), data.getOrElse("email", "bogusemail")))
    case _ => Future.successful(Done)
  })
}
