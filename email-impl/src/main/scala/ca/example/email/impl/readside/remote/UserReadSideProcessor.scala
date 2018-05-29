package ca.example.email.impl.readside.remote

import akka.Done
import ca.example.email.impl.events.{UserAdded, UserEvent}
import com.datastax.driver.core.PreparedStatement
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.scaladsl.persistence.cassandra.{CassandraReadSide, CassandraSession}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEventTag, EventStreamElement, ReadSideProcessor}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class UserReadSideProcessor(readSide: CassandraReadSide,
                            session: CassandraSession)
                           (implicit ec: ExecutionContext)
  extends ReadSideProcessor[UserEvent] {

  private val log = LoggerFactory.getLogger(classOf[UserReadSideProcessor])

  private var insertUserStatement: PreparedStatement = _
  //private var updateUserStatement: PreparedStatement = _

  override def buildHandler: ReadSideHandler[UserEvent] = {
    readSide.builder[UserEvent]("emailUserOffset")
      .setGlobalPrepare(createTable)
      .setPrepare {_ => prepareStatements()}
      .setEventHandler[UserAdded](userAdded)
      .build()
  }

  private def userAdded(e: EventStreamElement[UserAdded]) = {
    Future.successful {
      val u = e.event
      log.info(s"UserReadSideProcessor: userAdded: ${u.id} ${u.username} ${u.email}")
      List(insertUserStatement.bind(
        u.id.toString,
        u.username,
        u.email
      ))
    }
  }

  private def createTable(): Future[Done] = {
    for {
      _ <- session.executeCreateTable(
        """
          |CREATE TABLE IF NOT EXISTS users (
          |id text, username text, email text,
          |PRIMARY KEY (id)
          |)
        """.stripMargin
      )
    } yield Done
  }

  private def prepareStatements(): Future[Done] = {
    for {
      insertUser <- session.prepare(
        """
          |INSERT INTO users
          |(id, username, email)
          |VALUES (?, ?, ?)
        """.stripMargin
      )
    } yield {
      insertUserStatement = insertUser
      Done
    }
  }

  override def aggregateTags: Set[AggregateEventTag[UserEvent]] = UserEvent.Tag.allTags
}
