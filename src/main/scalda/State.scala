import scala.concurrent.{Future, ExecutionContext}

case class State[S, +A](run: S => Future[(S, A)]) {
  def map[B](f: A => B)(implicit ec: ExecutionContext): State[S, B] =
    State { s =>
      run(s).map { case (s1, a) => (s1, f(a)) }
    }

  def flatMap[B](f: A => State[S, B])(implicit ec: ExecutionContext): State[S, B] =
    State { s =>
      run(s).flatMap { case (s1, a) => f(a).run(s1) }
    }
}

object State {
  def pure[S, A](a: A): State[S, A] =
    State(s => Future.successful((s, a)))

  def get[S]: State[S, S] =
    State(s => Future.successful((s, s)))

  def set[S](s: S): State[S, Unit] =
    State(_ => Future.successful((s, ())))

  def modify[S](f: S => S): State[S, Unit] =
    State(s => Future.successful((f(s), ())))

  def modifyAsync[S](f: S => Future[S])(implicit ec: ExecutionContext): State[S, Unit] =
    State(s => f(s).map(s1 => (s1, ())))
}