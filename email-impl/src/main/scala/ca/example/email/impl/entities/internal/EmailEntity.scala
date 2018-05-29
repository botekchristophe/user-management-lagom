package ca.example.email.impl.entities.internal

import akka.Done
import ca.example.email.impl.commands.{EmailCommand, ScheduleEmail, SetEmailDelivered, SetEmailFailed}
import ca.example.email.impl.events._
import com.lightbend.lagom.scaladsl.persistence._
import org.slf4j.LoggerFactory

class EmailEntity extends PersistentEntity {

  //Command
  override type Command = EmailCommand[_]

  //Event
  override type Event = EmailEvent

  //Aggregate
  override type State = Option[EmailAggregate]
  override def initialState: Option[EmailAggregate] = None

  // Check FSM
  override def behavior: Behavior = {
    case None => unScheduled
    case Some(EmailAggregate(EmailStatuses.SCHEDULED, _, _, _, _, _, _)) => scheduled
    case Some(EmailAggregate(EmailStatuses.DELIVERED, _, _, _, _, _, _)) => delivered
    case Some(EmailAggregate(EmailStatuses.FAILED, _, _, _, _, _, _)) => failed
  }

  private val log = LoggerFactory.getLogger(classOf[EmailEntity])

  private def unScheduled: Actions =
    Actions()
      .onCommand[ScheduleEmail, Done] {
      // Validate command
      case (ScheduleEmail(id, recipientId, recipientAddress, topic, content), ctx, _) =>
        log.info("Entity: ScheduleEmail Command.")
        ctx.thenPersist(
          EmailScheduled(
            id,
            recipientId,
            recipientAddress,
            topic,
            content))(_ => ctx.reply(Done))
    }
      .onCommand[SetEmailDelivered.type, Done] { case (SetEmailDelivered, ctx, _) => ctx.reply(Done); ctx.done }
      .onCommand[SetEmailFailed.type, Done] { case (SetEmailFailed, ctx, _) => ctx.reply(Done); ctx.done }
      .onEvent {
        case (EmailScheduled(id, recipientId, recipientAddress, topic, content), _) =>
          log.info("Entity: EmailScheduled Event.")
          Some(
            EmailAggregate(
              EmailStatuses.SCHEDULED,
              id,
              recipientId,
              recipientAddress,
              topic,
              content))
      }

  private def scheduled: Actions =
    Actions()
      .onCommand[SetEmailDelivered.type, Done] {
      case (SetEmailDelivered, ctx, Some(email)) =>
        log.info("Entity: SetEmailDelivered Command.")
        ctx.thenPersistAll(
          EmailDelivered(email.id, System.currentTimeMillis()),
          EmailVerified(email.recipientId))(() => ctx.reply(Done))
    }.onCommand[SetEmailFailed.type, Done] {
      case (SetEmailFailed, ctx, Some(email)) =>
        log.info("Entity: SetEmailFailed Command.")
        ctx.thenPersist(EmailDeliveryFailed(email.id, System.currentTimeMillis()))(_ => ctx.reply(Done))
    }
      .onCommand[ScheduleEmail, Done] { case (ScheduleEmail(_, _, _, _, _), ctx, _) => ctx.reply(Done); ctx.done }
      .onEvent {
        case (EmailDeliveryFailed(_, date), state) =>
          log.info("Entity: EmailDeliveryFailed Event.")
          state.map(email => email.copy(status = EmailStatuses.FAILED, deliveredOn = Some(date)))
        case (EmailDelivered(_, date), state) =>
          log.info("Entity: EmailDelivered Event.")
          state.map(email => email.copy(status = EmailStatuses.DELIVERED, deliveredOn = Some(date)))
        case (EmailVerified(_), state) =>
          log.info("Entity: EmailVerified Event.")
          state
      }
  private def delivered: Actions =
    Actions()
      .onCommand[SetEmailDelivered.type, Done] { case (SetEmailDelivered, ctx, _) => ctx.reply(Done); ctx.done }
      .onCommand[SetEmailFailed.type, Done] { case (SetEmailFailed, ctx, _) => ctx.reply(Done); ctx.done }
      .onCommand[ScheduleEmail, Done] { case (ScheduleEmail(_, _, _, _, _), ctx, _) => ctx.reply(Done); ctx.done }
      .onEvent {
        case (EmailDeliveryFailed(_, date), state) =>
          log.info("Entity: EmailDeliveryFailed Event.")
          state.map(email => email.copy(status = EmailStatuses.FAILED, deliveredOn = Some(date)))
        case (EmailDelivered(_, date), state) =>
          log.info("Entity: EmailDelivered Event.")
          state.map(email => email.copy(status = EmailStatuses.DELIVERED, deliveredOn = Some(date)))
        case (EmailVerified(_), state) =>
          log.info("Entity: EmailVerified Event.")
          state
      }

  private def failed: Actions =
    Actions()
      .onCommand[SetEmailDelivered.type, Done] { case (SetEmailDelivered, ctx, _) => ctx.reply(Done); ctx.done }
      .onCommand[SetEmailFailed.type, Done] { case (SetEmailFailed, ctx, _) => ctx.reply(Done); ctx.done }
      .onCommand[ScheduleEmail, Done] { case (ScheduleEmail(_, _, _, _, _), ctx, _) => ctx.reply(Done); ctx.done }
      .onEvent {
        case (EmailDeliveryFailed(_, date), state) =>
          log.info("Entity: EmailDeliveryFailed Event.")
          state.map(email => email.copy(status = EmailStatuses.FAILED, deliveredOn = Some(date)))
        case (EmailDelivered(_, date), state) =>
          log.info("Entity: EmailDelivered Event.")
          state.map(email => email.copy(status = EmailStatuses.DELIVERED, deliveredOn = Some(date)))
        case (EmailVerified(_), state) =>
          log.info("Entity: EmailVerified Event.")
          state
      }
}

