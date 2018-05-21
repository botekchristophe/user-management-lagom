package ca.example.user.impl

import java.util.UUID

import ca.example.user.api.UserResponse
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraSession

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class UserReadSideConnector(session: CassandraSession)(implicit ec: ExecutionContext) {

  def getUsers: Future[Iterable[UserResponse]] = {
    session.selectAll(
      """
        |SELECT * FROM users
      """.stripMargin)
      .map(_
        .map(row =>
          Try(
            UserResponse(
              UUID.fromString(row.getString("id")),
              row.getString("username"),
              row.getString("status") == UserStatus.VERIFIED.toString)).toOption)
        .filter {
          case Some(_) => true
          case _ => false }
        .map(_.get))
  }

  def getUserIdFromAccessToken(access_token: UUID): Future[Either[String, UUID]] = {
    session
      .selectAll(
          s"""
            |SELECT userid FROM sessions WHERE access_token = $access_token
          """.stripMargin)
      .map(_.headOption.toRight("Not found").map(row => UUID.fromString(row.getString("userid"))))
  }

  def getUserIdFromRefreshToken(refresh_token: UUID): Future[Either[String, UUID]] = {
    session
      .selectAll(
        s"""
           |SELECT userid FROM sessions WHERE refresh_token = $refresh_token
          """.stripMargin)
      .map(_.headOption.toRight("Not found").map(row => UUID.fromString(row.getString("userid"))))
  }

  def getUserIdFromUsername(username: String): Future[Either[String, UUID]] = {
    session
      .selectAll(
        s"""
           |SELECT userid FROM users WHERE username = $username
          """.stripMargin)
      .map(_.headOption.toRight("Not found").map(row => UUID.fromString(row.getString("userid"))))
  }

  def getUser(id: UUID): Future[Either[String, UserResponse]] = {
    session
      .selectAll(
        s"""
           |SELECT * FROM users WHERE id = $id
          """.stripMargin)
      .map(_.headOption
        .toRight("User not found with id")
        .fold(
          e => Left(e),
          row => Try(
            UserResponse(
              UUID.fromString(row.getString("id")),
              row.getString("username"),
              row.getString("status") == UserStatus.VERIFIED.toString)).toOption.toRight("User not recoverable")))
  }
}
