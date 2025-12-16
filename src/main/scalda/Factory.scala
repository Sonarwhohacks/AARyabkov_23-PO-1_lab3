import scala.concurrent.{Future, ExecutionContext}
import scala.util.Random

case class FactoryState(
                         currentSingleton: Singleton = Singleton.Singleton0,
                         counter: Int = 0,
                         history: Vector[Singleton] = Vector.empty
                       )

object FactoryOperations {

  def createSingleton(useSingleton0: Boolean = true)(implicit ec: ExecutionContext): State[FactoryState, Singleton] =
    State { state: FactoryState =>
      Future {
        val singleton = if (useSingleton0) Singleton.Singleton0 else Singleton.Singleton1
        val newState = state.copy(
          currentSingleton = singleton,
          counter = state.counter + 1,
          history = state.history :+ singleton
        )
        (newState, singleton)
      }
    }

  def pressFactoryState0(implicit ec: ExecutionContext): State[FactoryState, Singleton] =
    createSingleton(useSingleton0 = true)

  def pressFactoryState1(implicit ec: ExecutionContext): State[FactoryState, Singleton] =
    createSingleton(useSingleton0 = false)

  def getCurrentCount(implicit ec: ExecutionContext): State[FactoryState, Int] =
    State.get[FactoryState].map(_.counter)

  def resetCounter(implicit ec: ExecutionContext): State[FactoryState, Unit] =
    State.modify[FactoryState](state => state.copy(counter = 0))

  def createRandomSingleton(implicit ec: ExecutionContext): State[FactoryState, Singleton] =
    State { state: FactoryState =>
      Future {
        val useSingleton0 = Random.nextBoolean()
        val singleton = if (useSingleton0) Singleton.Singleton0 else Singleton.Singleton1
        val newState = state.copy(
          currentSingleton = singleton,
          counter = state.counter + 1,
          history = state.history :+ singleton
        )
        (newState, singleton)
      }
    }
}