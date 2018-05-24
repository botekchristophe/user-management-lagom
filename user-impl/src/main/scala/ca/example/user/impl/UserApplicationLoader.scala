package ca.example.user.impl

import ca.example.email.api.EmailService
import ca.example.user.api.UserService
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.server._
import com.softwaremill.macwire._
import play.api.libs.ws.ahc.AhcWSComponents

class UserApplicationLoader extends LagomApplicationLoader {
  override def load(context: LagomApplicationContext): UserApplication =
    new UserApplication(context) {
      override def serviceLocator: ServiceLocator = ServiceLocator.NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext) =
    new UserApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[UserService])
}

abstract class UserApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with AhcWSComponents
    with CassandraPersistenceComponents {

  override lazy val lagomServer: LagomServer = serverFor[UserService](wire[UserServiceImpl])
  override lazy val jsonSerializerRegistry: JsonSerializerRegistry = UserSerializerRegistry

  readSide.register(wire[UserReadSideProcessor])
  persistentEntityRegistry.register(wire[UserEntity])
  lazy val readSideConnector: UserReadSideConnector = wire[UserReadSideConnector]
  lazy val emailClient: EmailService = serviceClient.implement[EmailService]
  wire[EmailEventListener]
}
