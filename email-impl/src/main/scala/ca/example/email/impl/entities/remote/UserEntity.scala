package ca.example.email.impl.entities.remote

import akka.Done
import ca.example.email.impl.commands.{AddUserCommand, UserCommand}
import ca.example.email.impl.events.{UserAdded, UserEvent}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity
import org.slf4j.LoggerFactory

class UserEntity extends PersistentEntity {

  private val log = LoggerFactory.getLogger(classOf[UserEntity])

  override type Command = UserCommand[_]
  override type Event = UserEvent
  override type State = Option[UserAggregate]

  override def initialState: Option[UserAggregate] = None

  override def behavior: Behavior = {
    case _ => addUser
  }

  // Persist kafka event inside local UserEntity
  private def addUser: Actions = {
    Actions().onCommand[AddUserCommand, Done] {
      case (AddUserCommand(id, username, email), ctx, _) =>
        log.info("AddUserCommand received")
        ctx.thenPersist(
          UserAdded(
            id,
            username,
            email
          )
        )(_ => ctx.reply(Done))
    }
  }

}
