import scala.concurrent.{Future, ExecutionContext}

case class Timestamp(millis: Long = System.currentTimeMillis()) {
  override def toString: String = {
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    f"$hours%02d:$minutes%02d:$seconds%02d"
  }
}

case class CacheMessage(level: String, message: String, timestamp: Timestamp = Timestamp())

case class CacheSystemState(
                             l1: Map[String, String] = Map.empty,
                             l2: Map[String, String] = Map.empty,
                             l3: Map[String, String] = Map.empty,
                             proxyCount: Int = 0,
                             totalOperations: Int = 0,
                             messages: Vector[CacheMessage] = Vector.empty
                           )

object CacheOperations {

  private def createMessage(level: String, message: String): CacheMessage =
    CacheMessage(level, message)

  def processProxy(singleton: Singleton, count: Int)(implicit ec: ExecutionContext): State[CacheSystemState, Unit] =
    State.modify[CacheSystemState] { state: CacheSystemState =>
      if (count != 0) {
        val message = createMessage("Proxy", s"Processing: ${singleton.name}")
        state.copy(
          proxyCount = state.proxyCount + 1,
          totalOperations = state.totalOperations + 1,
          messages = state.messages :+ message
        )
      } else {
        state.copy(totalOperations = state.totalOperations + 1)
      }
    }

  def processL3(singleton: Singleton, count: Int)(implicit ec: ExecutionContext): State[CacheSystemState, Option[String]] =
    State { state: CacheSystemState =>
      Future {
        if (count <= 3) {
          state.l3.get(singleton.name) match {
            case Some(data) =>
              val message = createMessage("L3", s"Cache hit for ${singleton.name}: $data")
              val newState = state.copy(messages = state.messages :+ message)
              (newState, Some(data))

            case None =>
              val newL3 = state.l3 + (singleton.name -> singleton.value)
              val message = createMessage("L3", s"Cache miss for ${singleton.name}, added to L3")
              val newState = state.copy(
                l3 = newL3,
                messages = state.messages :+ message
              )
              (newState, None)
          }
        } else {
          val newL3 = state.l3 - singleton.name
          val message = createMessage("L3", s"Removed ${singleton.name} from L3, count: $count")
          val newState = state.copy(
            l3 = newL3,
            messages = state.messages :+ message
          )
          (newState, None)
        }
      }
    }

  def processL2(singleton: Singleton, count: Int)(implicit ec: ExecutionContext): State[CacheSystemState, Option[String]] =
    State { state: CacheSystemState =>
      Future {
        if (count > 3 && count <= 8) {
          state.l2.get(singleton.name) match {
            case Some(data) =>
              val message = createMessage("L2", s"Cache hit for ${singleton.name}: $data")
              val newState = state.copy(messages = state.messages :+ message)
              (newState, Some(data))

            case None =>
              val newL2 = state.l2 + (singleton.name -> singleton.value)
              val message = createMessage("L2", s"Cache miss for ${singleton.name}, added to L2")
              val newState = state.copy(
                l2 = newL2,
                messages = state.messages :+ message
              )
              (newState, None)
          }
        } else {
          val newL2 = state.l2 - singleton.name
          (state.copy(l2 = newL2), None)
        }
      }
    }

  def processL1(singleton: Singleton, count: Int)(implicit ec: ExecutionContext): State[CacheSystemState, Option[String]] =
    State { state: CacheSystemState =>
      Future {
        if (count > 8) {
          state.l1.get(singleton.name) match {
            case Some(data) =>
              val message = createMessage("L1", s"Cache hit for ${singleton.name}: $data")
              val newState = state.copy(messages = state.messages :+ message)
              (newState, Some(data))

            case None =>
              val newL1 = state.l1 + (singleton.name -> singleton.value)
              val message = createMessage("L1", s"Cache miss for ${singleton.name}, added to L1")
              val newState = state.copy(
                l1 = newL1,
                messages = state.messages :+ message
              )
              (newState, None)
          }
        } else {
          val newL1 = state.l1 - singleton.name
          (state.copy(l1 = newL1), None)
        }
      }
    }

  def processThroughAllLevels(singleton: Singleton, count: Int)(implicit ec: ExecutionContext): State[CacheSystemState, Vector[CacheMessage]] = {
    for {
      _ <- processProxy(singleton, count)
      _ <- processL3(singleton, count)
      _ <- processL2(singleton, count)
      _ <- processL1(singleton, count)
      finalState <- State.get[CacheSystemState]
    } yield finalState.messages
  }
}

object CacheDecorator {

  type Decorator = (Singleton, Int, Vector[CacheMessage]) => Future[Vector[CacheMessage]]

  val countDecorator: Decorator = (singleton, count, messages) =>
    Future.successful(messages :+ CacheMessage("Decorator", s"Count: $count"))

  val timestampDecorator: Decorator = (singleton, count, messages) =>
    Future.successful(messages :+ CacheMessage("Decorator", s"Timestamp: ${Timestamp()}"))

  def applyDecorators(
                       singleton: Singleton,
                       count: Int,
                       messages: Vector[CacheMessage],
                       decorators: List[Decorator]
                     )(implicit ec: ExecutionContext): Future[Vector[CacheMessage]] = {
    decorators.foldLeft(Future.successful(messages)) { (messagesFuture, decorator) =>
      messagesFuture.flatMap(msgs => decorator(singleton, count, msgs))
    }
  }
}

object CachePipeline {

  def processWithDecorators(
                             singleton: Singleton,
                             count: Int,
                             decorators: List[CacheDecorator.Decorator] = Nil
                           )(implicit ec: ExecutionContext): State[CacheSystemState, Vector[CacheMessage]] = {
    State { initialState: CacheSystemState =>
      for {
        (stateAfterProcessing, messages) <- CacheOperations
          .processThroughAllLevels(singleton, count)
          .run(initialState)

        decoratedMessages <- CacheDecorator.applyDecorators(
          singleton,
          count,
          messages,
          decorators
        )
      } yield (stateAfterProcessing, decoratedMessages)
    }
  }
}