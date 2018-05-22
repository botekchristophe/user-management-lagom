package ca.example.user.impl

import akka.Done
import com.datastax.driver.core.PreparedStatement
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.scaladsl.persistence.cassandra.{CassandraReadSide, CassandraSession}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEventTag, EventStreamElement, ReadSideProcessor}

import scala.concurrent.{ExecutionContext, Future}

class UserReadSideProcessor(readSide: CassandraReadSide, session: CassandraSession)
                           (implicit ec: ExecutionContext)
  extends ReadSideProcessor[UserEvent] {

  private var insertUserStatement: PreparedStatement = _
  private var deleteUserStatement: PreparedStatement = _
  private var insertSessionStatement: PreparedStatement = _
  private var deleteSessionStatement: PreparedStatement = _
  private var updateUserStatusStatement: PreparedStatement = _


  def buildHandler: ReadSideHandler[UserEvent] = {
    readSide.builder[UserEvent]("userOffset")
      .setGlobalPrepare(createTable)
      .setPrepare { tag => prepareStatements()}
      .setEventHandler[UserCreated](userCreated)
      .setEventHandler[UserDeleted](userDeleted)
      .setEventHandler[AccessTokenGranted](accessTokenGranted)
      .setEventHandler[AccessTokenRevoked](accessTokenRevoked)
      .setEventHandler[UserVerified](userVerified)
      .setEventHandler[UserUnVerified](userUnverified)
      .build()
  }

  private def createTable(): Future[Done] = {
    for {
      _ <- session.executeCreateTable(
        """
          |CREATE TABLE IF NOT EXISTS users (
          |   id text, username text, email text, status text,
          |   PRIMARY KEY (id)
          |   )
        """.stripMargin)

      //_ <- session.executeWrite(
      //  """
      //    |ALTER TABLE users ADD email text
      //  """.stripMargin)

      _ <- session.executeCreateTable(
        """
          |CREATE TABLE IF NOT EXISTS sessions (
          |   access_token text, refresh_token text, userid text,
          |   PRIMARY KEY (access_token)
          |   )
        """.stripMargin
      )
    } yield Done
  }

  private def prepareStatements(): Future[Done] = {
    for {
      insertUser <- session.prepare(
        """
          |INSERT INTO users
          |(id, username, email, status)
          |VALUES (?, ?, ?, ?)
        """.stripMargin
      )
      verifyUser <- session.prepare(
        """
          |UPDATE users
          |SET status = ?
          |WHERE id = ?
        """.stripMargin
      )
      insertSession <- session.prepare(
        """
          |INSERT INTO sessions
          |(access_token, refresh_token, userid)
          |VALUES (?, ?, ?)
        """.stripMargin
      )
      deleteSession <- session.prepare(
        """
          |DELETE
          |FROM sessions
          |WHERE access_token = ?
        """.stripMargin
      )
      deleteUser <- session.prepare(
        """
          |DELETE
          |FROM users
          |WHERE id = ?
        """.stripMargin
      )
    } yield {
      insertUserStatement = insertUser
      deleteUserStatement = deleteUser
      insertSessionStatement = insertSession
      deleteSessionStatement = deleteSession
      updateUserStatusStatement = verifyUser
      Done
    }
  }

  private def userCreated(e: EventStreamElement[UserCreated]) = {
    Future.successful {
      val u = e.event
      List(insertUserStatement.bind(
        u.userId.toString,
        u.username,
        u.email,
        u.status.toString
      ))
    }
  }

  private def accessTokenGranted(e: EventStreamElement[AccessTokenGranted]) = {
    Future.successful {
      val s = e.event.session
      List(insertSessionStatement.bind(
        s.access_token.toString,
        s.refresh_token.toString,
        e.event.userId.toString
      ))
    }
  }

  private def accessTokenRevoked(e: EventStreamElement[AccessTokenRevoked]) = {
    Future.successful {
      val u = e.event
      List(deleteSessionStatement.bind(
        u.access_token.toString
      ))
    }
  }

  private def userVerified(e: EventStreamElement[UserVerified]) = {
    Future.successful {
      val u = e.event
      List(updateUserStatusStatement.bind(
        UserStatus.VERIFIED.toString,
        u.userId.toString
      ))
    }
  }

  private def userUnverified(e: EventStreamElement[UserUnVerified]) = {
    Future.successful {
      val u = e.event
      List(updateUserStatusStatement.bind(
        UserStatus.UNVERIFIED.toString,
        u.userId.toString
      ))
    }
  }

  private def userDeleted(e: EventStreamElement[UserDeleted]) = {
    Future.successful {
      val u = e.event
      List(deleteUserStatement.bind(
        u.userId.toString
      ))
    }
  }

  override def aggregateTags: Set[AggregateEventTag[UserEvent]] = UserEvent.Tag.allTags
}
