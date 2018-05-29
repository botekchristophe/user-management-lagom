package ca.example.email.impl.entities.remote

import java.util.UUID
import play.api.libs.json.{Format, Json}

case class UserAggregate(id: UUID,
                         username: String,
                         email: String)
object UserAggregate {
  implicit val format: Format[UserAggregate] = Json.format[UserAggregate]
}
