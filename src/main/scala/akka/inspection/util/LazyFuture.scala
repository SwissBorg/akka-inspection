package akka.inspection.util
import cats.{Applicative, Functor, Monad}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Wraps a [[Future]] so it isn't evaluated directly.
 */
case class LazyFuture[T](f: () => Future[T]) extends AnyVal {

  /**
   * Start the wrap future `f`.
   */
  def run: Future[T] = f()
}

object LazyFuture {
  implicit def lazyFutureApplicative(implicit ec: ExecutionContext): Applicative[LazyFuture] =
    new Applicative[LazyFuture] {
      override def pure[A](x: A): LazyFuture[A] = LazyFuture(() => Future.successful(x))
      override def ap[A, B](ff: LazyFuture[A => B])(fa: LazyFuture[A]): LazyFuture[B] =
        LazyFuture(
          () =>
            for {
              ff <- ff.run
              fa <- fa.run
            } yield ff(fa)
        )

    }

  implicit def lazyFutureFunctor(implicit ec: ExecutionContext): Functor[LazyFuture] = new Functor[LazyFuture] {
    override def map[A, B](fa: LazyFuture[A])(f: A => B): LazyFuture[B] = LazyFuture(() => fa.run.map(f))
  }

  implicit def lazyFutureMonad(implicit ec: ExecutionContext): Monad[LazyFuture] = new Monad[LazyFuture] {
    override def pure[A](x: A): LazyFuture[A] = LazyFuture(() => Future.successful(x))
    override def flatMap[A, B](fa: LazyFuture[A])(f: A => LazyFuture[B]): LazyFuture[B] =
      LazyFuture(
        () =>
          for {
            fa <- fa.run
            fb <- f(fa).run
          } yield fb
      )

    // TODO not really tail-rec
    override def tailRecM[A, B](a: A)(f: A => LazyFuture[Either[A, B]]): LazyFuture[B] =
      LazyFuture(
        () =>
          f(a).run.flatMap {
            case Right(b) => Future.successful(b)
            case Left(a)  => tailRecM(a)(f).run
        }
      )
  }
}
