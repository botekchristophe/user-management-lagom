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
  private var insertSessionStatement: PreparedStatement = _
  private var deleteSessionStatement: PreparedStatement = _
  private var verifyUserStatement: PreparedStatement = _


  def buildHandler: ReadSideHandler[UserEvent] = {
    readSide.builder[UserEvent]("userOffset")
      .setGlobalPrepare(createTable)
      .setPrepare { tag => prepareStatements()}
      .setEventHandler[UserCreated](userCreated)
      .setEventHandler[AccessTokenGranted](accessTokenGranted)
      .setEventHandler[AccessTokenRevoked](accessTokenRevoked)
      .setEventHandler[UserVerified](userVerified)
      .build()
  }

  private def createTable(): Future[Done] = {
    for {
      _ <- session.executeCreateTable(
        """
          |CREATE TABLE IF NOT EXISTS users (
          |   id text, username text, status text,
          |   PRIMARY KEY (id)
          |   )
        """.stripMargin)
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
          |(id, username, status)
          |VALUES (?, ?, ?)
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
    } yield {
      insertUserStatement = insertUser
      insertSessionStatement = insertSession
      deleteSessionStatement = deleteSession
      verifyUserStatement = verifyUser
      Done
    }
  }

  private def userCreated(e: EventStreamElement[UserCreated]) = {
    Future.successful {
      val u = e.event
      List(insertUserStatement.bind(
        u.userId.toString,
        u.username,
        u.status.toString
      ))
    }
  }

  private def accessTokenGranted(e: EventStreamElement[AccessTokenGranted]) = {
    Future.successful {
      val s = e.event.session
      List(insertSessionStatement.bind(
        s.access_token.toString,
        s.refresh_token,
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
      List(verifyUserStatement.bind(
        UserStatus.VERIFIED.toString,
        u.userId.toString
      ))
    }
  }

  override def aggregateTags: Set[AggregateEventTag[UserEvent]] = UserEvent.Tag.allTags
}
