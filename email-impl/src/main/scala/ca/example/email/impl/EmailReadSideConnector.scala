package ca.example.email.impl

import ca.example.email.api.{EmailResponse, EmailTopics}
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraSession

import scala.concurrent.{ExecutionContext, Future}

class EmailReadSideConnector(session: CassandraSession)(implicit ec: ExecutionContext) {

  def getEmails: Future[Iterable[EmailResponse]] = {
    session.selectAll(
      """
        |SELECT * FROM emails
      """.stripMargin)
      .map(_
        .map(row =>
            EmailResponse(
              row.getString("to"),
              EmailTopics.withName(row.getString("topic")),
              row.getString("content"),
              EmailStatuses.withName(row.getString("status")) == EmailStatuses.DELIVERED,
            Some(row.getString("date")).filter(_.nonEmpty))))

  }
}
