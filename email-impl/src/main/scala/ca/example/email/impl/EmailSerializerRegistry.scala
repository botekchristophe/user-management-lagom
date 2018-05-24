package ca.example.email.impl

import ca.example.jsonformats.JsonFormats._
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
    JsonSerializer[EmailDeliveryFailed]
  )

  override def migrations = Map.empty[String, JsonMigration]
}

