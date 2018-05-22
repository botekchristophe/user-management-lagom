package ca.exemple.utils

import play.api.libs.json.{Format, Json}

case class ErrorResponse(code: Int, error: String, message: String)

object ErrorResponse {
  implicit val format: Format[ErrorResponse] = Json.format[ErrorResponse]
}

object ErrorResponses {
  val BadRequest: String => ErrorResponse = { message => ErrorResponse(400, "BadRequest", message) }
  val UnAuthorized: String => ErrorResponse = { message => ErrorResponse(401, "UnAuthorized", message) }
  val PaymentRequired: String => ErrorResponse = { message => ErrorResponse(402, "Payment Required", message) }
  val Forbidden: String => ErrorResponse = { message => ErrorResponse(403, "Forbidden", message) }
  val NotFound: String => ErrorResponse = { message => ErrorResponse(404, "Not Found", message) }
  val Conflict: String => ErrorResponse = { message => ErrorResponse(409, "Conflict", message) }
  val UnProcessable: String => ErrorResponse = { message => ErrorResponse(422, "UnProcessable", message) }
  val InternalServerError: String => ErrorResponse = { message => ErrorResponse(500, "Internal Server Error", message) }
  val NotImplemented: String => ErrorResponse = { message => ErrorResponse(501, "Not implemented", message) }
  val ServiceUnavailableError: String => ErrorResponse = { message => ErrorResponse(503, "Service Unavailable", message) }
}
