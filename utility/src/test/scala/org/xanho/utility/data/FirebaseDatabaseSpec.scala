package org.xanho.utility.data

import org.scalatest.WordSpec
import play.api.libs.json.{JsObject, JsString, JsValue, Json}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class FirebaseDatabaseSpec extends WordSpec {

  import scala.concurrent.ExecutionContext.Implicits.global

  "Firebase" can {

    val testBucketName =
      "test_bucket_12435624572457625"

    "write a single value" in {
        FirebaseDatabase.write(testBucketName, "item")(Json.obj("foo" -> 645, "bar" -> Seq.empty[String])).await

      assert(true)
    }

    "read a single value" in {

      val result =
        FirebaseDatabase.get(testBucketName, "item", "foo").await

      assert(result.get.as[Int] == 645)

    }

    "push to an array" in {

      FirebaseDatabase.append(testBucketName, "item", "bar")(JsString("item1")).await
      FirebaseDatabase.append(testBucketName, "item", "bar")(JsString("item2")).await

      val result =
        FirebaseDatabase.getCollection(testBucketName, "item", "bar").await

      assert(result.toSeq.map(_.as[String]) == Seq("item1", "item2"))

    }

    "merge an object" in {

      FirebaseDatabase.merge(testBucketName, "item")(Json.obj("baz" -> true)).await

      val result =
        FirebaseDatabase.get(testBucketName, "item").await.get.as[Map[String, JsValue]]

      assert(result("foo").as[Int] == 645)
      assert(result("baz").as[Boolean])

    }

    "observe an array" in {

      import scala.collection.mutable

      val newItems =
        mutable.Buffer.empty[String]

      val id =
        FirebaseDatabase.watchCollection(testBucketName, "item", "bar")((v: JsValue) => newItems += v.as[String])

      Thread.sleep(3000)

      FirebaseDatabase.append(testBucketName, "item", "bar")(JsString("item3")).await
      FirebaseDatabase.append(testBucketName, "item", "bar")(JsString("item4")).await

      Thread.sleep(3000)

      assert(newItems == Seq("item1", "item2", "item3", "item4"))

      FirebaseDatabase.unwatchCollection(id)

      assert(true)

    }

    "delete a value" in {
      FirebaseDatabase.delete(testBucketName, "item", "foo")

      assert(FirebaseDatabase.get(testBucketName, "item", "foo").await.isEmpty)
      assert(FirebaseDatabase.get(testBucketName, "item").await.nonEmpty)

    }

    "cleanup" in {
      FirebaseDatabase.delete(testBucketName)

      assert(true)
    }

  }

  implicit class Awaiter[T](f: Future[T]) {
    def await: T =
      Await.result(f, Duration.Inf)
  }

}
