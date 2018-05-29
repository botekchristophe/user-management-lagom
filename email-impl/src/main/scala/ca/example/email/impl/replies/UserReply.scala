package ca.example.email.impl.replies

import java.util.UUID
import play.api.libs.json.{Format, Json}

case class UserReply(id: UUID, username: String, email: String)
object UserReply {
  implicit val format: Format[UserReply] = Json.format[UserReply]
}