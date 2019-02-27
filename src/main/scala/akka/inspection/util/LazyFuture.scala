package akka.inspection.util
import cats.{Applicative, Functor, Monad}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Wraps a [[Future]] so it isn't evaluated directly.
 */
final class LazyFuture[T](f: () => Future[T]) {
  def value: Future[T] = f()
}

object LazyFuture {
  def apply[T](f: => Future[T]): LazyFuture[T] = new LazyFuture[T](f _)

  def pure[T](t: T): LazyFuture[T] = LazyFuture(Future.successful(t))

  implicit def lazyFutureApplicative(implicit ec: ExecutionContext): Applicative[LazyFuture] =
    new Applicative[LazyFuture] {
      override def pure[A](x: A): LazyFuture[A] = LazyFuture.pure(x)
      override def ap[A, B](ff: LazyFuture[A => B])(fa: LazyFuture[A]): LazyFuture[B] =
        LazyFuture(
          for {
            ff <- ff.value
            fa <- fa.value
          } yield ff(fa)
        )

    }

  implicit def lazyFutureFunctor(implicit ec: ExecutionContext): Functor[LazyFuture] = new Functor[LazyFuture] {
    override def map[A, B](fa: LazyFuture[A])(f: A => B): LazyFuture[B] = LazyFuture(fa.value.map(f))
  }

  implicit def lazyFutureMonad(implicit ec: ExecutionContext): Monad[LazyFuture] = new Monad[LazyFuture] {
    override def pure[A](x: A): LazyFuture[A] = LazyFuture.pure(x)
    override def flatMap[A, B](fa: LazyFuture[A])(f: A => LazyFuture[B]): LazyFuture[B] =
      LazyFuture(
        for {
          fa <- fa.value
          fb <- f(fa).value
        } yield fb
      )

    // TODO not really tail-rec
    override def tailRecM[A, B](a: A)(f: A => LazyFuture[Either[A, B]]): LazyFuture[B] =
      LazyFuture(
        f(a).value.flatMap {
          case Right(b) => Future.successful(b)
          case Left(a)  => tailRecM(a)(f).value
        }
      )
  }
}
