package ca.example.email.impl.readside.internal

import akka.Done
import ca.example.email.impl.entities.internal.EmailStatuses
import ca.example.email.impl.events.{EmailDelivered, EmailDeliveryFailed, EmailEvent, EmailScheduled}
import com.datastax.driver.core.PreparedStatement
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.scaladsl.persistence.cassandra.{CassandraReadSide, CassandraSession}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEventTag, EventStreamElement, ReadSideProcessor}

import scala.concurrent.{ExecutionContext, Future}

class EmailReadSideProcessor(readSide: CassandraReadSide,
                             session: CassandraSession)
                            (implicit ec: ExecutionContext)
  extends ReadSideProcessor[EmailEvent] {

  // Cassandra optimization: prepared statement
  private var insertEmailStatement: PreparedStatement = _
  private var updateEmailStatement: PreparedStatement = _

  def buildHandler: ReadSideHandler[EmailEvent] = {
    readSide.builder[EmailEvent]("emailOffset")
      .setGlobalPrepare(createTable)
      .setPrepare { _ => prepareStatements()}
      .setEventHandler[EmailDelivered](emailDelivered)
      .setEventHandler[EmailDeliveryFailed](emailDeliveryFailed)
      .setEventHandler[EmailScheduled](emailScheduled)
      .build()
  }

  private def createTable(): Future[Done] = {
    for {
      _ <- session.executeCreateTable(
        """
          |CREATE TABLE IF NOT EXISTS emails (
          |id text,recipient text,topic text,content text,status text,date text,
          |PRIMARY KEY (id))
        """.stripMargin)

    } yield Done
  }

  private def prepareStatements(): Future[Done] = {
    for {
      insertEmail <- session.prepare(
        """
          |INSERT INTO emails
          |(id, recipient, topic, content, status, date)
          |VALUES (?, ?, ?, ?, ?, ?)
        """.stripMargin
      )
      updateStatusAndDate <- session.prepare(
        """
          |UPDATE emails
          |SET status = ?, date = ?
          |WHERE id = ?
        """.stripMargin
      )
    } yield {
      insertEmailStatement = insertEmail
      updateEmailStatement = updateStatusAndDate
      Done
    }
  }

  private def emailScheduled(e: EventStreamElement[EmailScheduled]) = {
    Future.successful {
      val u = e.event
      List(insertEmailStatement.bind(
        u.id.toString,
        u.recipientAddress,
        u.topic.toString,
        u.content,
        EmailStatuses.SCHEDULED.toString,
        ""
      ))
    }
  }

  private def emailDelivered(e: EventStreamElement[EmailDelivered]) = {
    Future.successful {
      val u = e.event
      List(updateEmailStatement.bind(
        EmailStatuses.DELIVERED.toString,
        u.deliveredOn.toString,
        u.id.toString
      ))
    }
  }

  private def emailDeliveryFailed(e: EventStreamElement[EmailDeliveryFailed]) = {
    Future.successful {
      val u = e.event
      List(updateEmailStatement.bind(
        EmailStatuses.FAILED.toString,
        u.deliveredOn.toString,
        u.id.toString
      ))
    }
  }

  override def aggregateTags: Set[AggregateEventTag[EmailEvent]] = EmailEvent.Tag.allTags
}
