package ca.example.email.impl

import ca.example.email.impl.commands.{ScheduleEmail, SetEmailDelivered, SetEmailFailed}
import ca.example.email.impl.entities.internal.EmailAggregate
import ca.example.email.impl.events.{EmailDelivered, EmailDeliveryFailed, EmailScheduled, EmailVerified}
import com.lightbend.lagom.scaladsl.playjson.{JsonMigration, JsonSerializer, JsonSerializerRegistry}


object EmailSerializerRegistry extends JsonSerializerRegistry {
  override def serializers = List(
    //models
    JsonSerializer[EmailAggregate],
    //commands
    JsonSerializer[ScheduleEmail],
    JsonSerializer[SetEmailFailed.type],
    JsonSerializer[SetEmailDelivered.type],
    //events
    JsonSerializer[EmailScheduled],
    JsonSerializer[EmailDelivered],
    JsonSerializer[EmailDeliveryFailed],
    JsonSerializer[EmailVerified]
  )

  override def migrations = Map.empty[String, JsonMigration]
}

