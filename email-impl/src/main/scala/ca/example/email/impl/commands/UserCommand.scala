package ca.example.email.impl.commands

import java.util.UUID

import akka.Done
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType

import play.api.libs.json.{Format, Json}

trait UserCommand[R] extends ReplyType[R]

case class AddUserCommand(id: UUID,
                          username: String,
                          email: String) extends UserCommand[Done]
object AddUserCommand {
  implicit val format: Format[AddUserCommand] = Json.format[AddUserCommand]
}