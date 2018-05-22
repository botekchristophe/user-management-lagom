package ca.exemple.utils

import com.lightbend.lagom.scaladsl.api.transport.{MessageProtocol, ResponseHeader}

import scala.collection.immutable
import scala.language.{implicitConversions, reflectiveCalls}
import scala.util.{Left, Right}

trait Marshaller {

  // Implicit conversions for rest services

  implicit def errorToResponse[T](error: ErrorResponse): (ResponseHeader, Either[ErrorResponse, T]) =
    (ResponseHeader(error.code, MessageProtocol.empty, immutable.Seq.empty[(String, String)]), Left(error))

  implicit def eitherMarshall[A]: Marshallable[Either[ErrorResponse, A]] = new Marshallable[Either[ErrorResponse, A]] {
    override def marshall(either: Either[ErrorResponse, A]): (ResponseHeader, Either[ErrorResponse, A]) =
      either match {
        case Left(e: ErrorResponse) => e //implicit conversion errorToResponse
        case right @ (Right(_)) => (ResponseHeader.Ok, right)
      }
  }

  implicit class MarshallOps[A](val a: A) {
    def marshall(implicit instance: Marshallable[A]): (ResponseHeader, A) =
      instance.marshall(a)
  }

  trait Marshallable[A] {
    def marshall(a: A): (ResponseHeader, A)
  }
}

