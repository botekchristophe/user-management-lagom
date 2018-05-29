package ca.example.email.impl.readside.remote

import java.util.UUID

import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraSession
import ca.example.email.impl.replies.UserReply

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class UserReadSideConnector(session: CassandraSession)
                           (implicit ec: ExecutionContext) {

  def getUsers: Future[Iterable[UserReply]] = {
    session.selectAll(
      """
        |SELECT * FROM users
      """.stripMargin)
      .map(_.map(row =>
        Try(
          UserReply(
            UUID.fromString(row.getString("id")),
            row.getString("username"),
            row.getString("email")
          )
        ).toOption)
        .filter {
          case Some(_) => true
          case None    => false}
        .map(_.get)
      )
  }

}
