package ca.example.email.impl.readside.internal

import akka.http.scaladsl.model.DateTime
import ca.example.email.api.{EmailResponse, EmailTopics}
import ca.example.email.impl.entities.internal.EmailStatuses
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraSession

import scala.concurrent.{ExecutionContext, Future}

class EmailReadSideConnector(session: CassandraSession)(implicit ec: ExecutionContext) {

  def getEmails: Future[Seq[EmailResponse]] = {
    session.selectAll(
      """
        |SELECT * FROM emails
      """.stripMargin)
      .map(_
        .map(row =>
            EmailResponse(
              row.getString("recipient"),
              EmailTopics.withName(row.getString("topic")),
              row.getString("content"),
              EmailStatuses.withName(row.getString("status")) == EmailStatuses.DELIVERED,
              Some(row.getString("date"))
                .filter(_.nonEmpty)
                .map(str => DateTime(str.toLong).toIsoDateTimeString())))
        .sortBy(_.deliveredOn).reverse)

  }
}
