package ca.example.user.impl

import ca.example.jsonformats.JsonFormats._
import ca.example.user.impl.UserStatus.UserStatus
import com.lightbend.lagom.scaladsl.playjson.{JsonMigration, JsonSerializer, JsonSerializerRegistry}
import play.api.libs.json.{JsObject, JsString}


object UserSerializerRegistry extends JsonSerializerRegistry {
  override def serializers = List(
    //models
    JsonSerializer[UserAggregate],
    JsonSerializer[UserSession],
    JsonSerializer[UserStatus],
    //commands
    JsonSerializer[CreateUser],
    JsonSerializer[VerifyUser.type],
    JsonSerializer[GrantAccessToken],
    JsonSerializer[ExtendAccessToken],
    JsonSerializer[RevokeAccessToken.type],
    JsonSerializer[IsSessionExpired.type],
    JsonSerializer[UnVerifyUser.type],
    JsonSerializer[DeleteUser.type],
    //events
    JsonSerializer[UserVerified],
    JsonSerializer[UserUnVerified],
    JsonSerializer[AccessTokenGranted],
    JsonSerializer[AccessTokenRevoked],
    JsonSerializer[UserCreated],
    JsonSerializer[UserDeleted]
  )

  private val emailAdded = new JsonMigration(2) {
    override def transform(fromVersion: Int, json: JsObject): JsObject = {
      if (fromVersion < 2) {
        json + ("email" -> JsString("example@company.ca"))
      } else {
        json
      }
    }
  }

  override def migrations = Map[String, JsonMigration](
    classOf[CreateUser].getName -> emailAdded,
    classOf[UserCreated].getName -> emailAdded,
    classOf[UserAggregate].getName -> emailAdded
  )
}
