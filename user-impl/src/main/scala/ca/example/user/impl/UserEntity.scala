package ca.example.user.impl

import java.util.UUID

import akka.Done
import ca.example.jsonformats.JsonFormats._
import ca.example.user.impl.UserStatus.UserStatus
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import com.lightbend.lagom.scaladsl.persistence._
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import org.mindrot.jbcrypt.BCrypt
import play.api.libs.json.{Format, Json}

class UserEntity extends PersistentEntity {

  //Command
  override type Command = UserCommand[_]

  //Event
  override type Event = UserEvent

  //Aggregate
  override type State = Option[UserAggregate]
  override def initialState: Option[UserAggregate] = None

  override def behavior: Behavior = {
    case None => unRegistered
    case Some(UserAggregate(UserStatus.UNVERIFIED, _, _, _, _)) => unVerified
    case Some(UserAggregate(UserStatus.VERIFIED, _, _, _, _)) => verified

  }

  private def unRegistered: Actions =
    Actions()
      .onCommand[CreateUser, Done] {
      case (CreateUser(id, username, password), ctx, _) =>
        if (password.length >= 8) {
          ctx.thenPersist(UserCreated(
            id,
            username,
            BCrypt.hashpw(password, BCrypt.gensalt),
            UserStatus.UNVERIFIED))(_ => ctx.reply(Done))
        } else {
          ctx.invalidCommand("password too short.")
          ctx.done
        }
    }
      .onCommand[DeleteUser.type, Done] { case (DeleteUser, ctx, _) => ctx.reply(Done); ctx.done }
      .onCommand[VerifyUser.type, Done] { case (VerifyUser, ctx, _) => ctx.reply(Done); ctx.done }
      .onCommand[UnVerifyUser.type, Done] { case (UnVerifyUser, ctx, _) => ctx.reply(Done); ctx.done }
      .onEvent {
        case (UserCreated(id, username, hash, status), _) =>
          Some(UserAggregate(
            status,
            id,
            username,
            hash,
            None))
      }

  private def unVerified: Actions =
    Actions()
      .onCommand[VerifyUser.type, Done] {
      case (VerifyUser, ctx, state) =>
          ctx.thenPersist(UserVerified(state.get.id))(_ => ctx.reply(Done))
    }.onCommand[DeleteUser.type , Done] {
      case (DeleteUser, ctx, state) =>
        state match {
          case Some(UserAggregate(_, id, _, _, None)) =>
            ctx.thenPersist(UserDeleted(id))(_ => ctx.reply(Done))
          case Some(UserAggregate(_, id, _, _, Some(session))) =>
            ctx.thenPersistAll(
              AccessTokenRevoked(session.access_token),
              UserDeleted(id)
            ){() => ctx.reply(Done) }
        }
    }
      .onEvent {
        case (UserVerified(_), state) =>
          state.map(user => user.copy(status = UserStatus.VERIFIED))
        case (AccessTokenRevoked(_), state) =>
          state.map(user => user.copy(currentSession = None))
        case (UserDeleted(_), _) =>
          None
      }

  private def verified: Actions =
    Actions()
      .onCommand[GrantAccessToken, UserSession] {
      case (GrantAccessToken(password), ctx, state) =>
      state match {
        case Some(UserAggregate(_, userId, _, hash, Some(session))) if BCrypt.checkpw(password, hash) =>
          if (session.createdOn + UserSession.EXPIRY < System.currentTimeMillis()) {
            val newSession = UserSession()
            ctx.thenPersistAll(
              AccessTokenRevoked(session.access_token),
              AccessTokenGranted(userId, newSession)){() => ctx.reply(newSession) }
            } else {
            ctx.reply(session.copy(expiry = session.createdOn + session.expiry - System.currentTimeMillis()))
            ctx.done
          }

        case Some(UserAggregate(_, userId, _, hash, None)) if BCrypt.checkpw(password, hash) =>
          val newSession = UserSession()
          ctx.thenPersist(AccessTokenGranted(userId, newSession))(_ => ctx.reply(newSession))

        case _ =>
          ctx.invalidCommand("Authentication failed")
          ctx.done
      }
    }.onCommand[ExtendAccessToken, UserSession] {
      case (ExtendAccessToken(refresh_token), ctx, state) =>
        state match {
          case Some(UserAggregate(_, userId, _, _, Some(session))) if session.refresh_token == refresh_token =>
            val newSession = UserSession()
            ctx.thenPersistAll(
              AccessTokenRevoked(session.access_token),
              AccessTokenGranted(userId, newSession)){() => ctx.reply(newSession) }

          case _ =>
            ctx.invalidCommand("Refresh token not recognized")
            ctx.done
        }
    }.onCommand[RevokeAccessToken.type , Done] {
      case (RevokeAccessToken, ctx, state) =>
        state match {
          case Some(UserAggregate(_, _, _, _, None)) =>
            ctx.reply(Done)
            ctx.done
          case Some(UserAggregate(_, _, _, _, Some(session))) =>
            ctx.thenPersist(AccessTokenRevoked(session.access_token))(_ => ctx.reply(Done))

          case _ =>
            ctx.reply(Done)
            ctx.done
        }
    }.onCommand[IsSessionExpired.type , Boolean] {
      case (IsSessionExpired, ctx, state) =>
        state match {
          case Some(UserAggregate(_, _, _, _, None)) =>
            ctx.reply(true)
            ctx.done
          case Some(UserAggregate(_, _, _, _, Some(session))) if session.createdOn + session.expiry >= System.currentTimeMillis() =>
            ctx.reply(false)
            ctx.done
          case Some(UserAggregate(_, _, _, _, Some(session))) if session.createdOn + session.expiry < System.currentTimeMillis() =>
            ctx.thenPersist(AccessTokenRevoked(session.access_token))(_ => ctx.reply(true))
        }
    }.onCommand[DeleteUser.type , Done] {
      case (DeleteUser, ctx, state) =>
        state match {
          case Some(UserAggregate(_, id, _, _, None)) =>
            ctx.thenPersist(UserDeleted(id))(_ => ctx.reply(Done))
          case Some(UserAggregate(_, id, _, _, Some(session))) =>
            ctx.thenPersistAll(
              AccessTokenRevoked(session.access_token),
              UserDeleted(id)
            ){() => ctx.reply(Done) }
        }
    }.onCommand[UnVerifyUser.type , Done] {
      case (UnVerifyUser, ctx, state) =>
        state match {
          case Some(UserAggregate(_, id, _, _, _)) =>
            ctx.thenPersist(UserUnVerified(id))(_ => ctx.reply(Done))
          case None =>
            ctx.reply(Done)
            ctx.done
        }
    }
      .onEvent {
        case (AccessTokenGranted(_, session), state) =>
          state.map(user => user.copy(currentSession = Some(session)))
        case (AccessTokenRevoked(_), state) =>
          state.map(user => user.copy(currentSession = None))
        case (UserDeleted(_), _) =>
          None
        case (UserUnVerified(_), state) =>
          state.map(user => user.copy(status = UserStatus.UNVERIFIED))
      }
}


trait UserCommand[R] extends ReplyType[R]

case class GrantAccessToken(password: String) extends UserCommand[UserSession]
object GrantAccessToken {
  implicit val format: Format[GrantAccessToken] = Json.format[GrantAccessToken]
}
case object RevokeAccessToken extends UserCommand[Done] {
  implicit val format: Format[RevokeAccessToken.type] = singletonFormat(RevokeAccessToken)
}
case class ExtendAccessToken(refresh_token: UUID) extends UserCommand[UserSession]
object ExtendAccessToken {
  implicit val format: Format[ExtendAccessToken] = Json.format[ExtendAccessToken]
}
case class CreateUser(id: UUID, username: String, password: String) extends UserCommand[Done]
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


/**
  * A persisted event.
  */
trait UserEvent extends AggregateEvent[UserEvent] {
  override def aggregateTag: AggregateEventTagger[UserEvent] = UserEvent.Tag
}

object UserEvent {
  val NumShards = 10
  val Tag: AggregateEventShards[UserEvent] = AggregateEventTag.sharded[UserEvent](NumShards)
}

case class AccessTokenGranted(userId: UUID, session: UserSession) extends UserEvent
object AccessTokenGranted {
  implicit val format: Format[AccessTokenGranted] = Json.format[AccessTokenGranted]
}
case class AccessTokenRevoked(access_token: UUID) extends UserEvent
object AccessTokenRevoked {
  implicit val format: Format[AccessTokenRevoked] = Json.format[AccessTokenRevoked]
}
case class UserCreated(userId: UUID, username: String, hash: String, status: UserStatus) extends UserEvent
object UserCreated {
  implicit val format: Format[UserCreated] = Json.format[UserCreated]
}

case class UserVerified(userId: UUID) extends UserEvent
object UserVerified {
  implicit val format: Format[UserVerified] = Json.format
}

case class UserUnVerified(userId: UUID) extends UserEvent
object UserUnVerified {
  implicit val format: Format[UserUnVerified] = Json.format
}

case class UserDeleted(userId: UUID) extends UserEvent
object UserDeleted {
  implicit val format: Format[UserDeleted] = Json.format
}

/**
  * Aggregate
  */


case class UserAggregate(status: UserStatus = UserStatus.UNVERIFIED,
                         id: UUID,
                         username: String,
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
}