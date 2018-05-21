package ca.example.jsonformats

import java.time.Duration
import java.util.UUID

import play.api.libs.json.JsonValidationError
import play.api.libs.json._

import scala.util.{Failure, Success, Try}

object JsonFormats {

  def enumReads[E <: Enumeration](enum: E): Reads[E#Value] = Reads {
    case JsString(s) =>
      try {
        JsSuccess(enum.withName(s).asInstanceOf[E#Value])
      } catch {
        case _: NoSuchElementException =>
          JsError(s"Enumeration expected of type: '${enum.getClass}', but it does not contain '$s'")
      }
    case _ => JsError("String value expected")
  }
  def enumWrites[E <: Enumeration]: Writes[E#Value] = Writes(v => JsString(v.toString))
  def enumFormat[E <: Enumeration](enum: E): Format[E#Value] = {
    Format(enumReads(enum), enumWrites)
  }

  def singletonReads[O](singleton: O): Reads[O] = {
    (__ \ "value").read[String].collect(
      JsonValidationError(s"Expected a JSON object with a single field with key 'value' and value '${singleton.getClass.getSimpleName}'")
    ) {
      case s if s == singleton.getClass.getSimpleName => singleton
    }
  }
  def singletonWrites[O]: Writes[O] = Writes { singleton =>
    Json.obj("value" -> singleton.getClass.getSimpleName)
  }
  def singletonFormat[O](singleton: O): Format[O] = {
    Format(singletonReads(singleton), singletonWrites)
  }

  implicit val uuidReads: Reads[UUID] = implicitly[Reads[String]]
    .collect(JsonValidationError("Invalid UUID"))(Function.unlift { str =>
      Try(UUID.fromString(str)).toOption
    })
  implicit val uuidWrites: Writes[UUID] = Writes { uuid =>
    JsString(uuid.toString)
  }

  implicit val durationReads: Reads[Duration] = implicitly[Reads[String]]
      .collect(JsonValidationError("Invalid duration"))(Function.unlift { str =>
        Try(Duration.parse(str)).toOption
      })
  implicit val durationWrites: Writes[Duration] = Writes { duration =>
    JsString(duration.toString)
  }

  implicit def mapWrite[E, T](valueOf: (String => E))(implicit format: Format[T]): Writes[Map[E, T]] =
    Writes[Map[E, T]] { o => JsObject(o.map { case (a, b) => (a.toString, Json.toJson(b)) })}

  implicit def mapReads[E, T](valueOf: (String => E))(implicit format: Format[T]): Reads[Map[E, T]] =
    Reads[Map[E, T]] { json =>
      val result = Try(json.as[Map[String, T]].map {
        case (key, value) =>
          valueOf(key) -> value
      })

      result match {
        case Success(status) =>
          JsSuccess(status)
        case Failure(th) =>
          JsError(JsonValidationError(s"Error while serializing $json: $th"))
      }
    }

  implicit def mapFormat[E, T](valueOf: (String => E))(implicit format: Format[T]): Format[Map[E, T]] =
    Format(mapReads(valueOf), mapWrite(valueOf))
}

