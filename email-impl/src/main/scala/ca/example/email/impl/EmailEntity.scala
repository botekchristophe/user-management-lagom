package ca.example.email.impl

import akka.Done
import ca.exemple.utils.{ErrorResponses => ER}
import com.lightbend.lagom.scaladsl.persistence._

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

  private def unScheduled: Actions =
    Actions()
      .onCommand[ScheduleEmail, Done] {
      // Validate command
      case (ScheduleEmail(id, recipientId, recipientAddress, topic, content), ctx, _) =>
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
          Some(
            EmailAggregate(
              EmailStatuses.SCHEDULED,
              id,
              recipientId,
              recipientAddress,
              topic,
              content))
      }

  private def scheduled: Actions = ??? //TODO
  private def delivered: Actions = ??? //TODO
  private def failed: Actions = ??? //TODO
}

