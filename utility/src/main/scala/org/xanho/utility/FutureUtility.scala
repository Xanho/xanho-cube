package org.xanho.utility

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

object FutureUtility {

  implicit class FutureHelper[T](future: Future[T]) {

    def await(implicit duration: Duration): T =
      Await.result(future, duration)

  }

}
