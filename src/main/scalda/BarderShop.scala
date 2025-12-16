import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Random
import scala.collection.immutable.Queue

case class Customer(id: Int, name: String, arrivalTime: Long = System.currentTimeMillis())

case class ShopStats(
                      totalCustomers: Int = 0,
                      servedCustomers: Int = 0,
                      rejectedCustomers: Int = 0,
                      averageWaitTime: Double = 0.0,
                      cacheStats: CacheSystemState = CacheSystemState()
                    )

class AsyncQueue[A](maxSize: Int)(implicit ec: ExecutionContext) {
  private var queue = Queue.empty[A]
  private val lock = new AnyRef

  def enqueue(item: A): Boolean = lock.synchronized {
    if (queue.size < maxSize) {
      queue = queue.enqueue(item)
      true
    } else {
      false
    }
  }

  def dequeue(): Option[A] = lock.synchronized {
    if (queue.nonEmpty) {
      val (element, newQueue) = queue.dequeue
      queue = newQueue
      Some(element)
    } else {
      None
    }
  }

  def size: Int = lock.synchronized {
    queue.size
  }

  def isEmpty: Boolean = lock.synchronized {
    queue.isEmpty
  }
}

class BarberShopService(seats: Int) {
  implicit val ec: ExecutionContext = ExecutionContext.global

  private val waitingRoom = new AsyncQueue[Customer](seats)
  private var isOpen = true
  private var totalServed = 0
  private var totalRejected = 0
  private var waitTimes = Vector.empty[Long]
  private val allCustomersProcessed = Promise[Unit]()

  private var cacheState = CacheSystemState()
  private var factoryState = FactoryState()

  def waitForCompletion(): Future[Unit] = allCustomersProcessed.future

  def customerArrives(customer: Customer): Boolean = {
    if (!isOpen) {
      false
    } else {
      val success = waitingRoom.enqueue(customer)
      if (!success) {
        totalRejected += 1
      }
      success
    }
  }

  def startBarber(): Future[Unit] = Future {
    var continue = true

    while (continue && (isOpen || !waitingRoom.isEmpty)) {
      waitingRoom.dequeue() match {
        case Some(customer) =>
          println(s"Парикмахер начал стричь ${customer.name}")

          serveCustomer(customer)

          totalServed += 1
          val waitTime = System.currentTimeMillis() - customer.arrivalTime
          waitTimes = waitTimes :+ waitTime

          println(s"Парикмахер закончил стричь ${customer.name}")

          if (totalServed + totalRejected >= seats && waitingRoom.isEmpty) {
            println("Все клиенты обслужены, завершаем работу...")
            continue = false
            allCustomersProcessed.success(())
          }

        case None =>
          if (isOpen) {
            Thread.sleep(1000)
          } else {
            continue = false
            allCustomersProcessed.success(())
          }
      }
    }

    if (!allCustomersProcessed.isCompleted) {
      allCustomersProcessed.success(())
    }

    println("Парикмахер завершил рабочий день.")
  }

  private def serveCustomer(customer: Customer): Unit = {
    val random = new Random(customer.id)
    val operationsCount = random.nextInt(19)

    val operations = (1 to operationsCount).map { i =>
      val useSingleton0 = random.nextBoolean()
      val count = random.nextInt(15) + 1
      (useSingleton0, count)
    }.toList

    operations.foreach { case (useSingleton0, count) =>
      val factoryResult = FactoryOperations
        .createSingleton(useSingleton0)
        .run(factoryState)

      val (newFactoryState, singleton) = Await.result(factoryResult, 10.seconds)

      factoryState = newFactoryState

      val cacheResult = CachePipeline
        .processWithDecorators(
          singleton,
          count,
          List(CacheDecorator.countDecorator)
        )
        .run(cacheState)

      val (newCacheState, messages) = Await.result(cacheResult, 10.seconds)

      cacheState = newCacheState

      messages.foreach { msg =>
        println(s"  [${customer.name}] [${msg.level}] ${msg.message}")
      }
    }

    Thread.sleep(500 + random.nextInt(500))
  }

  def generateCustomers(total: Int, intervalMillis: Int): Future[Unit] = Future {
    (1 to total).foreach { i =>
      val customer = Customer(i, s"Клиент-$i")
      val success = customerArrives(customer)

      if (success) {
        println(s"${customer.name} вошел и сел ждать")
      } else {
        println(s"${customer.name} ушел - нет мест")
      }

      Thread.sleep(intervalMillis)
    }

    println("Все клиенты пришли в барбершоп.")
  }

  def getStats(): ShopStats = {
    val avgWaitTime = if (waitTimes.nonEmpty) {
      waitTimes.sum.toDouble / waitTimes.size
    } else 0.0

    ShopStats(
      totalCustomers = totalServed + totalRejected,
      servedCustomers = totalServed,
      rejectedCustomers = totalRejected,
      averageWaitTime = avgWaitTime,
      cacheStats = cacheState
    )
  }

  def close(): Unit = {
    isOpen = false
    println("Барбершоп закрывается...")
  }
}

class SimulationService {
  implicit val ec: ExecutionContext = ExecutionContext.global

  def runSimulation(seats: Int, totalCustomers: Int): Future[ShopStats] = {
    val shop = new BarberShopService(seats)

    val barberFuture = shop.startBarber()

    val customersFuture = shop.generateCustomers(totalCustomers, 500)

    for {
      _ <- customersFuture
      _ <- {
        println("Ожидаем завершения обслуживания клиентов...")
        shop.waitForCompletion()
      }
      _ = shop.close()
      _ <- barberFuture
      stats = shop.getStats()
    } yield stats
  }
}