package ca.example.user.api

import java.util.UUID

import akka.{Done, NotUsed}
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}
import play.api.libs.json.{Format, Json}

trait UserService extends Service {
  def createUser: ServiceCall[CreateUser, UserResponse]
  def getUser(userId: UUID): ServiceCall[NotUsed, UserResponse]
  def deleteUser(userId: UUID): ServiceCall[NotUsed, Done]
  def getUsers: ServiceCall[NotUsed, Seq[UserResponse]]
  def userLogin: ServiceCall[AuthRequest, AuthResponse]
  def getUserAuth: ServiceCall[String, AuthInfo]
  def revokeToken: ServiceCall[NotUsed, Done]
  def refreshToken: ServiceCall[String, AuthResponse]
  def verifyUser(userId: UUID): ServiceCall[NotUsed, Done]
  def unVerifyUser(userId: UUID): ServiceCall[NotUsed, Done]

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
