package ca.example.user.impl

import java.util.UUID

import ca.example.jsonformats.JsonFormats._
import ca.example.user.impl.UserStatus.UserStatus
import play.api.libs.json.{Format, Json}

case class UserAggregate(status: UserStatus = UserStatus.UNVERIFIED,
                         id: UUID,
                         username: String,
                         email: String,
                         hashed_salted_pwd: String,
                         currentSession: Option[UserSession] = None)
object UserAggregate {
  implicit val format: Format[UserAggregate] = Json.format[UserAggregate]
}


object UserStatus extends Enumeration {
  type UserStatus = Value
  val VERIFIED,
  UNVERIFIED = Value

  implicit val format: Format[UserStatus] = enumFormat(UserStatus)
}

case class UserSession(access_token: UUID,
                       createdOn: Long,
                       expiry: Long,
                       refresh_token: UUID)
object UserSession {
  final val EXPIRY: Long = 3600000
  def apply(): UserSession = UserSession(UUID.randomUUID(), System.currentTimeMillis(), EXPIRY, UUID.randomUUID())
  implicit val format: Format[UserSession] = Json.format[UserSession]
}
