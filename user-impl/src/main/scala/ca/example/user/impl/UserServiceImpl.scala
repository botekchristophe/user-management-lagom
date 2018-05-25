package ca.example.user.impl

import java.util.UUID

import akka.stream.Materializer
import akka.{Done, NotUsed}
import ca.example.user
import ca.example.user.api._
import ca.exemple.utils.{ErrorResponse, Marshaller, ErrorResponses => ER}
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.transport._
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.scaladsl.server.ServerServiceCall

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

class UserServiceImpl(registry: PersistentEntityRegistry,
                      readSideConnector: UserReadSideConnector)
                     (implicit ec: ExecutionContext, mat: Materializer) extends UserService with Marshaller {

  override def userEvents: Topic[UserKafkaEvent] =
    TopicProducer.taggedStreamWithOffset(UserEvent.Tag.allTags.to[immutable.Seq]) { (tag, offset) =>
      registry.eventStream(tag, offset)
        .filter { evt =>
          evt.event match {
            case _: UserCreated => true
            case _: UserVerified => true
            case _: UserUnVerified => true
            case _: UserDeleted => true
            case _ => false
          }
        }
        .map(ev => ev.event match {
          case UserCreated(id, username, _, _, email) =>
            (UserKafkaEvent(UserEventTypes.REGISTERED, id, Map("username" -> username, "email" -> email)), ev.offset)
          case UserDeleted(id) =>
            (UserKafkaEvent(UserEventTypes.DELETED, id), ev.offset)
          case UserVerified(id) =>
            (UserKafkaEvent(UserEventTypes.VERIFIED, id), ev.offset)
          case UserUnVerified(id) =>
            (UserKafkaEvent(UserEventTypes.UNVERIFIED, id), ev.offset)
        })
    }

  private def refFor(userId: UUID) = registry.refFor[UserEntity](userId.toString)

  private def getUserIdFromHeader(rh: RequestHeader): Future[Either[ErrorResponse, UUID]] =
    rh.getHeader("Authorization")
      .toRight(ER.UnAuthorized("Missing Authorization header"))
      .fold[Future[Either[ErrorResponse, UUID]]](
      e => Future.successful(Left(e)),
      auth =>
        readSideConnector
          .getUserIdFromAccessToken(UUID.fromString(auth)))

  override def userLogin: ServiceCall[AuthRequest, Either[ErrorResponse, AuthResponse]] =
    ServerServiceCall((_, request) =>
      readSideConnector
        .getUserIdFromUsername(request.username)
        .flatMap(_.fold[Future[Either[ErrorResponse, AuthResponse]]](
          e => Future.successful(Left(e)),
          userId =>
            refFor(userId)
              .ask(GrantAccessToken(request.password))
              .map(_.map(s => AuthResponse(s.access_token, s.expiry, s.refresh_token)))))
        .map(_.marshall)
    )

  override def getUserAuth: ServiceCall[String, Either[ErrorResponse, AuthInfo]] =
    ServerServiceCall((rh, req) =>
      getUserIdFromHeader(rh)
        .flatMap(_.fold[Future[Either[ErrorResponse, UUID]]](
          e => Future.successful(Left(e)),
          userId =>
            refFor(userId)
              .ask(IsSessionExpired)
              .map(isExpired =>
                if (isExpired) {
                  Left(ER.UnAuthorized("Session expired"))
                } else {
                  Right(userId)
                })))
        .flatMap(_.fold[Future[Either[ErrorResponse, UserResponse]]](
          e => Future.successful(Left(e)),
          userId => readSideConnector.getUser(userId)))
        .map(_.fold[Either[ErrorResponse, AuthInfo]](
          e => Left(e),
          user => Right(AuthInfo(user))))
        .map(_.marshall)
    )

  override def revokeToken: ServiceCall[NotUsed, Done] =
    ServerServiceCall((rh, _) =>
      getUserIdFromHeader(rh)
        .flatMap(_.fold[Future[Done]](
          _ => Future.successful(Done),
          userId => refFor(userId).ask(RevokeAccessToken)))
        .map(done => (ResponseHeader.Ok, done))
    )

  override def refreshToken: ServiceCall[String, Either[ErrorResponse, AuthResponse]] =
    ServerServiceCall((_, refresh_token) =>
      readSideConnector
        .getUserIdFromRefreshToken(UUID.fromString(refresh_token))
        .flatMap(_.fold[Future[Either[ErrorResponse, AuthResponse]]](
          e => Future.successful(Left(e)),
          userId =>
            refFor(userId)
              .ask(ExtendAccessToken(UUID.fromString(refresh_token)))
              .map(_.map(s => AuthResponse(s.access_token, s.expiry, s.refresh_token)))))
        .map(_.marshall)
    )

  override def verifyUser(userId: UUID): ServiceCall[NotUsed, Done] =
    ServiceCall(_ => refFor(userId).ask(VerifyUser))

  override def createUser: ServiceCall[user.api.CreateUser, Either[ErrorResponse, UserResponse]] =
    ServerServiceCall((_, req) =>
      readSideConnector
        .getUserIdFromUsername(req.username)
        .flatMap(_.fold(
          e => {
            val userId: UUID = UUID.randomUUID()
            refFor(userId)
              .ask(CreateUser(userId, req.username, req.password, req.email))
              .map(_ => Right(UserResponse(userId, req.username, req.email, verified = false)))},
          _ => Future.successful(Left(ER.Conflict("Username taken")))))
        .map(_.marshall)
    )

  override def getUser(userId: UUID): ServiceCall[NotUsed, Either[ErrorResponse, UserResponse]] =
    ServerServiceCall((_, _) =>
      readSideConnector
        .getUser(userId)
        .map(_.fold[Either[ErrorResponse, UserResponse]](
          e => Left(e),
          Right(_)))
        .map(_.marshall)
    )

  override def getUsers: ServiceCall[NotUsed, Seq[UserResponse]] =
    ServiceCall(_ =>
      readSideConnector
        .getUsers
        .map(_.toSeq)
    )

  override def deleteUser(userId: UUID): ServiceCall[NotUsed, Done] =
    ServiceCall(_ =>
      refFor(userId).ask(DeleteUser)
    )

  override def unVerifyUser(userId: UUID): ServiceCall[NotUsed, Done] =
    ServiceCall(_ =>
      refFor(userId).ask(UnVerifyUser)
    )
}
