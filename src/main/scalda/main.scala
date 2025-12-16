import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}
import scala.io.StdIn

object Main {

  def readPositiveInt(prompt: String): Int = {
    def loop: Int = {
      println(prompt)

      Try(StdIn.readInt()) match {
        case Success(n) if n >= 2 => n
        case Success(_) =>
          println("Число должно быть не меньше 2. Попробуйте снова.")
          loop
        case Failure(_) =>
          println("Некорректный ввод. Введите целое число.")
          loop
      }
    }

    loop
  }

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global

    val seats = readPositiveInt("Введите количество мест в барбершопе (не менее 2):")
    val customers = readPositiveInt("Введите количество клиентов:")

    println(s"\nЗапуск симуляции: $seats мест, $customers клиентов...")

    val simulationService = new SimulationService()

    val program = for {
      stats <- simulationService.runSimulation(seats, customers)
    } yield stats

    try {
      val stats = Await.result(program, 60.seconds)

      println("\n" + "="*50)
      println("РЕЗУЛЬТАТЫ СИМУЛЯЦИИ")
      println("="*50)
      println(s"Всего клиентов: ${stats.totalCustomers}")
      println(s"Обслужено: ${stats.servedCustomers}")
      println(s"Отказано: ${stats.rejectedCustomers}")
      println(f"Среднее время ожидания: ${stats.averageWaitTime / 1000}%.2f сек")
      println("\nСтатистика кэш-системы:")
      println(s"  L1 кэш: ${stats.cacheStats.l1.size} элементов")
      println(s"  L2 кэш: ${stats.cacheStats.l2.size} элементов")
      println(s"  L3 кэш: ${stats.cacheStats.l3.size} элементов")
      println(s"  Всего операций: ${stats.cacheStats.totalOperations}")
      println("="*50)

    } catch {
      case _: TimeoutException =>
        println("\nСимуляция заняла слишком много времени. Прерываем...")
      case e: Exception =>
        println(s"\nОшибка при выполнении симуляции: ${e.getMessage}")
        e.printStackTrace()
    }

    println("\nПрограмма завершена.")
  }
}