package ca.example.user.api

import java.util.UUID

import akka.{Done, NotUsed}
import ca.example.jsonformats.JsonFormats._
import ca.example.user.api.UserEventTypes.UserEventType
import ca.exemple.utils.ErrorResponse
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.deser.DefaultExceptionSerializer
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}
import play.api.{Environment, Mode}
import play.api.libs.json.{Format, Json}

trait UserService extends Service {
  def createUser:                 ServiceCall[CreateUser, Either[ErrorResponse, UserResponse]]
  def getUser(userId: UUID):      ServiceCall[NotUsed, Either[ErrorResponse, UserResponse]]
  def deleteUser(userId: UUID):   ServiceCall[NotUsed, Done]
  def getUsers:                   ServiceCall[NotUsed, Seq[UserResponse]]
  def userLogin:                  ServiceCall[AuthRequest, Either[ErrorResponse, AuthResponse]]
  def getUserAuth:                ServiceCall[String, Either[ErrorResponse, AuthInfo]]
  def revokeToken:                ServiceCall[NotUsed, Done]
  def refreshToken:               ServiceCall[String, Either[ErrorResponse, AuthResponse]]
  def verifyUser(userId: UUID):   ServiceCall[NotUsed, Done]
  def unVerifyUser(userId: UUID): ServiceCall[NotUsed, Done]

  def userEvents: Topic[UserKafkaEvent]

  def descriptor: Descriptor = {
    import Service._
    named("user").withCalls(
      restCall(Method.GET,    "/api/users",              getUsers _),
      restCall(Method.POST,   "/api/users",              createUser _),
      restCall(Method.GET,    "/api/users/:id",          getUser _),
      restCall(Method.DELETE, "/api/users/:id",          deleteUser _),
      restCall(Method.PUT,    "/api/users/:id/verify",   verifyUser _),
      restCall(Method.PUT,    "/api/users/:id/unverify", unVerifyUser _),
      restCall(Method.POST,   "/api/users/auth",         getUserAuth _),
      restCall(Method.POST,   "/api/users/auth/grant",   userLogin _),
      restCall(Method.POST,   "/api/users/auth/revoke",  revokeToken _),
      restCall(Method.POST,   "/api/users/auth/refresh", refreshToken _)
    )
      .withAutoAcl(true)
      .withExceptionSerializer(new DefaultExceptionSerializer(Environment.simple(mode = Mode.Prod)))
      .withTopics(topic("UserEvents", userEvents))
  }
}

case class UserResponse(id: UUID, username: String, email: String, verified: Boolean)

object UserResponse {
  implicit val format: Format[UserResponse] = Json.format
}

case class CreateUser(username: String, email: String, password: String)

object CreateUser {
  implicit val format: Format[CreateUser] = Json.format
}

case class AuthRequest(username: String, password: String)

object AuthRequest {
  implicit val format: Format[AuthRequest] = Json.format
}

case class AuthResponse(access_token: UUID,
                        expiry: Long,
                        refresh_token: UUID)

object AuthResponse {
  implicit val format: Format[AuthResponse] = Json.format
}

case class AuthInfo(user: UserResponse)
object AuthInfo {
  implicit val format: Format[AuthInfo] = Json.format
}

case class UserKafkaEvent(event: UserEventType,
                          id: UUID,
                          data: Map[String, String] = Map.empty[String, String])

object UserKafkaEvent {
  implicit val format: Format[UserKafkaEvent] = Json.format
}

object UserEventTypes extends Enumeration {
  type UserEventType = Value
  val REGISTERED, DELETED, VERIFIED, UNVERIFIED = Value

  implicit val format: Format[UserEventType] = enumFormat(UserEventTypes)
}
