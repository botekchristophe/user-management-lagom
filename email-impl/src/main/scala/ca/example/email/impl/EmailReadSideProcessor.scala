package ca.example.email.impl

import akka.Done
import com.datastax.driver.core.PreparedStatement
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.scaladsl.persistence.cassandra.{CassandraReadSide, CassandraSession}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEventTag, ReadSideProcessor}

import scala.concurrent.{ExecutionContext, Future}

class EmailReadSideProcessor(readSide: CassandraReadSide, session: CassandraSession)
                            (implicit ec: ExecutionContext)
  extends ReadSideProcessor[EmailEvent] {
  // Cassandra optimization: prepared statement
  private var insertEmailStatement: PreparedStatement = _
  private var updateEmailStatement: PreparedStatement = _


  //TODO set event handler
  def buildHandler: ReadSideHandler[EmailEvent] = {
    readSide.builder[EmailEvent]("emailOffset")
      .setGlobalPrepare(createTable)
      .setPrepare { _ => prepareStatements()}
      .build()
  }

  private def createTable(): Future[Done] = {
    for {
      _ <- session.executeCreateTable(
        """
          |CREATE TABLE IF NOT EXISTS emails (
          |   id text, to text, topic text, content text, status text, date text,
          |   PRIMARY KEY (id)
          |   )
        """.stripMargin)

    } yield Done
  }

  private def prepareStatements(): Future[Done] = {
    for {
      insertEmail <- session.prepare(
        """
          |INSERT INTO emails
          |(id, to, topic, content, status, date)
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
      Done
    }
  }

  override def aggregateTags: Set[AggregateEventTag[EmailEvent]] = EmailEvent.Tag.allTags
}
