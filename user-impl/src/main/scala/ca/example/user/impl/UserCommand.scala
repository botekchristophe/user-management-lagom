package ca.example.user.impl

import java.util.UUID

import akka.Done
import ca.example.jsonformats.JsonFormats.singletonFormat
import ca.exemple.utils.ErrorResponse
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import play.api.libs.json.{Format, Json}


trait UserCommand[R] extends ReplyType[R]

case class GrantAccessToken(password: String) extends UserCommand[Either[ErrorResponse, UserSession]]
object GrantAccessToken {
  implicit val format: Format[GrantAccessToken] = Json.format[GrantAccessToken]
}
case object RevokeAccessToken extends UserCommand[Done] {
  implicit val format: Format[RevokeAccessToken.type] = singletonFormat(RevokeAccessToken)
}
case class ExtendAccessToken(refresh_token: UUID) extends UserCommand[Either[ErrorResponse, UserSession]]
object ExtendAccessToken {
  implicit val format: Format[ExtendAccessToken] = Json.format[ExtendAccessToken]
}
case class CreateUser(id: UUID, username: String, password: String, email: String) extends UserCommand[Either[ErrorResponse, String]]
object CreateUser {
  implicit val format: Format[CreateUser] = Json.format[CreateUser]
}
case object VerifyUser extends UserCommand[Done] {
  implicit val format: Format[VerifyUser.type] = singletonFormat(VerifyUser)
}
case object UnVerifyUser extends UserCommand[Done] {
  implicit val format: Format[UnVerifyUser.type] = singletonFormat(UnVerifyUser)
}
case object IsSessionExpired extends UserCommand[Boolean] {
  implicit val format: Format[IsSessionExpired.type] = singletonFormat(IsSessionExpired)
}
case object DeleteUser extends UserCommand[Done] {
  implicit val format: Format[DeleteUser.type] = singletonFormat(DeleteUser)
}
