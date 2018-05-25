package ca.example.user.impl

import akka.Done
import ca.exemple.utils.{ErrorResponse, ErrorResponses => ER}
import com.lightbend.lagom.scaladsl.persistence._
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory

class UserEntity extends PersistentEntity {

  //Command
  override type Command = UserCommand[_]

  //Event
  override type Event = UserEvent

  //Aggregate
  override type State = Option[UserAggregate]
  override def initialState: Option[UserAggregate] = None

  // Check FSM
  override def behavior: Behavior = {
    case None => unRegistered
    case Some(UserAggregate(UserStatus.UNVERIFIED, _, _, _, _, _)) => unVerified
    case Some(UserAggregate(UserStatus.VERIFIED, _, _, _, _, _)) => verified
  }

  private val log = LoggerFactory.getLogger(classOf[UserEntity])

  private def unRegistered: Actions =
    Actions()
      .onCommand[CreateUser, Either[ErrorResponse, String]] {
      // Validate command
      case (CreateUser(id, username, password, email), ctx, _) =>
        log.info(s"Entity $username: CreateUser Command.")
        if (password.length >= 8) {
          // Persist Event and Publish Internal Event
          ctx.thenPersist(UserCreated(
            id,
            username,
            BCrypt.hashpw(password, BCrypt.gensalt),
            UserStatus.UNVERIFIED,
            email))(_ => ctx.reply(Right("Created")))
        } else {
          ctx.reply(Left(ER.BadRequest("password too short")))
          ctx.done
        }
    }
      .onCommand[DeleteUser.type, Done] { case (DeleteUser, ctx, _) => ctx.reply(Done); ctx.done }
      .onCommand[VerifyUser.type, Done] { case (VerifyUser, ctx, _) => ctx.reply(Done); ctx.done }
      .onCommand[UnVerifyUser.type, Done] { case (UnVerifyUser, ctx, _) => ctx.reply(Done); ctx.done }
      .onCommand[GrantAccessToken, Either[ErrorResponse, UserSession]] {
      case (GrantAccessToken(_), ctx, _) =>
        log.info("Entity: GrantAccessToken Command.")
        ctx.reply(Left(ER.BadRequest("User not verified")))
        ctx.done
    }
      .onEvent {
        case (UserCreated(id, username, hash, status, email), _) =>
          log.info(s"Entity $username: UserCreated Event.")
          Some(UserAggregate(
            status,
            id,
            username,
            email,
            hash,
            None))
      }

  private def unVerified: Actions =
    Actions()
      .onCommand[VerifyUser.type, Done] {
      case (VerifyUser, ctx, Some(u)) =>
        log.info(s"Entity ${u.username}: VerifyUser Command.")
        ctx.thenPersist(UserVerified(u.id))(_ => ctx.reply(Done))
    }.onCommand[GrantAccessToken, Either[ErrorResponse, UserSession]] {
      case (GrantAccessToken(_), ctx, _) =>
        ctx.reply(Left(ER.BadRequest("User not verified")))
        ctx.done
    }.onCommand[ExtendAccessToken, Either[ErrorResponse, UserSession]] {
      case (ExtendAccessToken(_), ctx, _) =>
        ctx.reply(Left(ER.BadRequest("User not verified")))
        ctx.done
    }
      .onCommand[DeleteUser.type , Done] {
      case (DeleteUser, ctx, Some(u)) =>
        log.info(s"Entity ${u.username}: DeleteUser Command.")
        u match {
          case UserAggregate(_, id, _, _, _, None) =>
            ctx.thenPersist(UserDeleted(id))(_ => ctx.reply(Done))
          case UserAggregate(_, id, _, _, _, Some(session)) =>
            ctx.thenPersistAll(
              AccessTokenRevoked(session.access_token),
              UserDeleted(id)
            ){() => ctx.reply(Done) }
        }
    }
      .onEvent {
        case (UserVerified(_), state @ Some(u)) =>
          log.info(s"Entity ${u.username}: UserVerified Event.")
          state.map(user => user.copy(status = UserStatus.VERIFIED))
        case (AccessTokenRevoked(_), state @ Some(u)) =>
          log.info(s"Entity ${u.username}: AccessTokenRevoked Event.")
          state.map(user => user.copy(currentSession = None))
        case (UserDeleted(_), Some(u)) =>
          log.info(s"Entity ${u.username}: UserDeleted Event.")
          None
      }

  private def verified: Actions =
    Actions()
      .onCommand[VerifyUser.type, Done] {
      case (VerifyUser, ctx, Some(u)) =>
        log.info(s"Entity ${u.username}: VerifyUser Command.")
        ctx.reply(Done)
        ctx.done
    }
      .onCommand[GrantAccessToken, Either[ErrorResponse, UserSession]] {
      case (GrantAccessToken(password), ctx, state @ Some(u)) =>
        log.info(s"Entity ${u.username}: GrantAccessToken Command.")
        state match {
          case Some(UserAggregate(_, userId, _, _, hash, Some(session))) if BCrypt.checkpw(password, hash) =>
            if (session.createdOn + UserSession.EXPIRY < System.currentTimeMillis()) {
              val newSession = UserSession()
              ctx.thenPersistAll(
                AccessTokenRevoked(session.access_token),
                AccessTokenGranted(userId, newSession)){() => ctx.reply(Right(newSession)) }
              } else {
              ctx.reply(Right(session.copy(expiry = session.createdOn + session.expiry - System.currentTimeMillis())))
              ctx.done
            }

          case Some(UserAggregate(_, userId, _, _, hash, None)) if BCrypt.checkpw(password, hash) =>
            val newSession = UserSession()
            ctx.thenPersist(AccessTokenGranted(userId, newSession))(_ => ctx.reply(Right(newSession)))

          case _ =>
            ctx.invalidCommand("Authentication failed")
            ctx.done
      }
    }.onCommand[ExtendAccessToken, Either[ErrorResponse, UserSession]] {
      case (ExtendAccessToken(refresh_token), ctx, state @ Some(u)) =>
        log.info(s"Entity ${u.username}: ExtendAccessToken Command.")
        state match {
          case Some(UserAggregate(_, userId, _, _, _, Some(session))) if session.refresh_token == refresh_token =>
            val newSession = UserSession()
            ctx.thenPersistAll(
              AccessTokenRevoked(session.access_token),
              AccessTokenGranted(userId, newSession)){() => ctx.reply(Right(newSession)) }

          case _ =>
            ctx.reply(Left(ER.BadRequest("Cannot refresh session")))
            ctx.done
        }
    }.onCommand[RevokeAccessToken.type , Done] {
      case (RevokeAccessToken, ctx, state @ Some(u)) =>
        log.info(s"Entity ${u.username}: RevokeAccessToken Command.")
        state match {
          case Some(UserAggregate(_, _, _, _, _, None)) =>
            ctx.reply(Done)
            ctx.done

          case Some(UserAggregate(_, _, _, _, _, Some(session))) =>
            ctx.thenPersist(AccessTokenRevoked(session.access_token))(_ => ctx.reply(Done))

          case _ =>
            ctx.reply(Done)
            ctx.done
        }
    }.onCommand[IsSessionExpired.type , Boolean] {
      case (IsSessionExpired, ctx, state) =>
        state match {
          case Some(UserAggregate(_, _, _, _, _, None)) =>
            ctx.reply(true)
            ctx.done
          case Some(UserAggregate(_, _, _, _, _, Some(session))) if session.createdOn + session.expiry >= System.currentTimeMillis() =>
            ctx.reply(false)
            ctx.done
          case Some(UserAggregate(_, _, _, _, _, Some(session))) if session.createdOn + session.expiry < System.currentTimeMillis() =>
            ctx.thenPersist(AccessTokenRevoked(session.access_token))(_ => ctx.reply(true))
        }
    }.onCommand[DeleteUser.type , Done] {
      case (DeleteUser, ctx, state @ Some(u)) =>
        log.info(s"Entity ${u.username}: DeleteUser Command.")
        state match {
          case Some(UserAggregate(_, id, _, _, _, None)) =>
            ctx.thenPersist(UserDeleted(id))(_ => ctx.reply(Done))
          case Some(UserAggregate(_, id, _, _, _, Some(session))) =>
            ctx.thenPersistAll(
              AccessTokenRevoked(session.access_token),
              UserDeleted(id)
            ){() => ctx.reply(Done) }
        }
    }.onCommand[UnVerifyUser.type , Done] {
      case (UnVerifyUser, ctx, state @ Some(u)) =>
        log.info(s"Entity ${u.username}: UnVerifyUser Command.")
        state match {
          case Some(UserAggregate(_, id, _, _, _, _)) =>
            ctx.thenPersist(UserUnVerified(id))(_ => ctx.reply(Done))
          case _ =>
            ctx.reply(Done)
            ctx.done
        }
    }
      .onEvent {
        case (AccessTokenGranted(_, session), state @ Some(u)) =>
          log.info(s"Entity ${u.username}: AccessTokenGranted Event.")
          state.map(user => user.copy(currentSession = Some(session)))
        case (AccessTokenRevoked(_), state @ Some(u)) =>
          log.info(s"Entity ${u.username}: AccessTokenRevoked Event.")
          state.map(user => user.copy(currentSession = None))
        case (UserDeleted(_), state @ Some(u)) =>
          log.info(s"Entity ${u.username}: UserDeleted Event.")
          None
        case (UserUnVerified(_), state @ Some(u)) =>
          log.info(s"Entity ${u.username}: UserUnVerified Event.")
          state.map(user => user.copy(status = UserStatus.UNVERIFIED))
      }
}
