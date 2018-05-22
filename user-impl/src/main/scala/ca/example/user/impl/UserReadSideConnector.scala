package ca.example.user.impl

import java.util.UUID

import ca.example.user.api.UserResponse
import ca.exemple.utils.ErrorResponse
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraSession
import ca.exemple.utils.{ErrorResponses => ER}

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
              row.getString("email"),
              row.getString("status") == UserStatus.VERIFIED.toString)).toOption)
        .filter {
          case Some(_) => true
          case _ => false }
        .map(_.get))
  }

  def getUserIdFromAccessToken(access_token: UUID): Future[Either[ErrorResponse, UUID]] = {
    session
      .selectAll(
          s"""
            |SELECT userid FROM sessions WHERE access_token = '$access_token'
          """.stripMargin)
      .map(_.headOption.toRight(ER.NotFound("Access token missing")).map(row => UUID.fromString(row.getString("userid"))))
  }

  def getUserIdFromRefreshToken(refresh_token: UUID): Future[Either[ErrorResponse, UUID]] = {
    session
      .selectAll(
        s"""
           |SELECT userid FROM sessions WHERE refresh_token = '$refresh_token' ALLOW FILTERING
          """.stripMargin)
      .map(_.headOption.toRight(ER.NotFound("Refresh token not found")).map(row => UUID.fromString(row.getString("userid"))))
  }

  def getUserIdFromUsername(username: String): Future[Either[ErrorResponse, UUID]] = {
    session
      .selectAll(
        s"""
           |SELECT id FROM users WHERE username = '$username' ALLOW FILTERING
          """.stripMargin)
      .map(_.headOption.toRight(ER.NotFound("Username not found")).map(row => UUID.fromString(row.getString("id"))))
  }

  def getUser(id: UUID): Future[Either[ErrorResponse, UserResponse]] = {
    session
      .selectAll(
        s"""
           |SELECT * FROM users WHERE id = '${id.toString}'
          """.stripMargin)
      .map(_.headOption
        .toRight(ER.NotFound("User not found with id"))
        .fold(
          e => Left(e),
          row => Try(
            UserResponse(
              UUID.fromString(row.getString("id")),
              row.getString("username"),
              row.getString("email"),
              row.getString("status") == UserStatus.VERIFIED.toString))
            .toOption
            .toRight(ER.InternalServerError("Data corrupted"))))
  }
}
