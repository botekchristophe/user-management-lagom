package ca.example.email.impl

import ca.example.email.api.EmailService
import ca.example.user.api.UserService
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.server._
import com.softwaremill.macwire._
import play.api.libs.ws.ahc.AhcWSComponents

class EmailApplicationLoader extends LagomApplicationLoader {
  override def load(context: LagomApplicationContext): EmailApplication =
    new EmailApplication(context) {
      override def serviceLocator: ServiceLocator = ServiceLocator.NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext) =
    new EmailApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[EmailService])
}

abstract class EmailApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with AhcWSComponents
    with CassandraPersistenceComponents
    with LagomKafkaComponents {

  override lazy val lagomServer: LagomServer = serverFor[EmailService](wire[EmailServiceImpl])
  override lazy val jsonSerializerRegistry: JsonSerializerRegistry = EmailSerializerRegistry

  readSide.register(wire[EmailReadSideProcessor])
  persistentEntityRegistry.register(wire[EmailEntity])
  lazy val readSideConnector: EmailReadSideConnector = wire[EmailReadSideConnector]
  lazy val userClient: UserService = serviceClient.implement[UserService]
  wire[UserEventListener]
}
