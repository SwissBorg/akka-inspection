package akka.inspection
import cats.laws.discipline.FunctorTests
import cats.tests.CatsSuite

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class LazyFutureSpec extends CatsSuite {
//  implicit def arbLazyFuture[T: Arbitrary]: Arbitrary[LazyFuture[T]] =
//    Arbitrary(Arbitrary.arbFuture[T].arbitrary.map(f => LazyFuture(() => f)))
//
//  implicit def eqLazyFuture[T]: Eq[LazyFuture[T]] = new Eq[LazyFuture[T]] {
//    override def eqv(x: LazyFuture[T], y: LazyFuture[T]): Boolean =
//      Await.result(x.run, Duration.Inf) == Await.result(y.run, Duration.Inf)
//  }

//  implicit def eqFuture[T]: Eq[Future[T]] = new Eq[Future[T]] {
//    override def eqv(x: Future[T], y: Future[T]): Boolean =
//      Await.result(x, Duration(10, "seconds")) == Await.result(y,  Duration(10, "seconds"))
//  }

//  checkAll("LazyFuture.FunctorLaws", FunctorTests[Future].functor[Int, Int, String])
}
