import akka.actor.{ActorSystem, ActorRef, Props, Actor}
object App {
  case class Data(var counter: Int)
  class A(n: Int) extends Actor {
    def receive: Receive = {
      case d: Data =>
        d.counter += 1
        println(s"actor$n: counter is ${d.counter}")
    }
  }
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("app")
    val actor1 = system.actorOf(Props(new A(1)))
    val actor2 = system.actorOf(Props(new A(2)))
    val data = Data(0)
    actor1 ! data
    actor2 ! data
  }
}