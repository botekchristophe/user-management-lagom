package ca.example.user.impl

import java.util.UUID

import akka.stream.Materializer
import akka.{Done, NotUsed}
import ca.example.user
import ca.example.user.api._
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.transport._
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.scaladsl.server.ServerServiceCall

import scala.concurrent.{ExecutionContext, Future}

class UserServiceImpl(registry: PersistentEntityRegistry,
                      readSideConnector: UserReadSideConnector)
                     (implicit ec: ExecutionContext, mat: Materializer) extends UserService {


  private def refFor(userId: UUID) = registry.refFor[UserEntity](userId.toString)

  private def getUserIdFromHeader(rh: RequestHeader): Future[Either[String, UUID]] =
    rh.getHeader("Authorization")
      .toRight("Missing Authorization header")
      .fold[Future[Either[String, UUID]]](
      e => throw BadRequest(e),
      auth =>
        readSideConnector
          .getUserIdFromAccessToken(UUID.fromString(auth)))

  override def userLogin: ServiceCall[AuthRequest, AuthResponse] =
    ServiceCall(request =>
      readSideConnector
        .getUserIdFromUsername(request.username)
        .flatMap(_.fold[Future[AuthResponse]](
          e => throw NotFound(e),
          userId =>
            refFor(userId)
              .ask(GrantAccessToken(request.password))
              .map(s => AuthResponse(s.access_token, s.expiry, s.refresh_token))
        ))
    )

  override def getUserAuth: ServiceCall[NotUsed, AuthInfo] =
    ServerServiceCall((rh, _) =>
      getUserIdFromHeader(rh)
        .flatMap(_.fold[Future[Either[String, UUID]]](
          e => Future.successful(Left(e)),
          userId =>
            refFor(userId)
              .ask(IsSessionExpired)
              .map(isExpired =>
                if (isExpired) {
                  Left("Session expired")
                } else {
                  Right(userId)
                })))
        .flatMap(_.fold[Future[Either[String, UserResponse]]](
          e => throw Forbidden(e),
          userId => readSideConnector.getUser(userId)))
        .flatMap(_.fold[Future[AuthInfo]](
          e => throw Forbidden(e),
          user => Future.successful(AuthInfo(user))))
        .map(info => (ResponseHeader.Ok, info))
    )

  override def revokeToken: ServiceCall[NotUsed, Done] =
    ServerServiceCall((rh, _) =>
      getUserIdFromHeader(rh)
        .flatMap(_.fold[Future[Done]](
          e => throw Forbidden(e),
          userId => refFor(userId).ask(RevokeAccessToken)))
        .map(done => (ResponseHeader.Ok, done))
    )

  override def refreshToken: ServiceCall[String, AuthResponse] =
    ServiceCall(refresh_token =>
      readSideConnector
        .getUserIdFromRefreshToken(UUID.fromString(refresh_token))
        .flatMap(_.fold[Future[AuthResponse]](
          e => throw NotFound(e),
          userId =>
            refFor(userId)
              .ask(ExtendAccessToken(UUID.fromString(refresh_token)))
              .map(s => AuthResponse(s.access_token, s.expiry, s.refresh_token))
        ))
    )

  override def verifyUser(userId: UUID): ServiceCall[String, Done] =
    ServiceCall(_ =>
      refFor(userId).ask(VerifyUser)
    )

  override def createUser: ServiceCall[user.api.CreateUser, UserResponse] =
    ServiceCall(req => {
      val userId: UUID = UUID.randomUUID()
      refFor(userId)
        .ask(CreateUser(userId, req.username, req.password))
        .map(_ => UserResponse(userId, req.username, verified = false))
    })

  override def getUser(userId: UUID): ServiceCall[NotUsed, UserResponse] =
    ServiceCall(_ =>
      readSideConnector
        .getUser(userId)
        .map(_.fold[UserResponse](
          e => throw NotFound(e),
          identity))
    )

  override def getUsers: ServiceCall[NotUsed, Seq[UserResponse]] =
    ServiceCall(_ =>
      readSideConnector
        .getUsers
        .map(_.toSeq)
    )
}
