package examples.akka

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

import scala.collection.mutable.ListBuffer
import scala.util.Random

object ProdConsBoundedBufferConfig {
    val bufferSize = 50
    val numProducers = 40
    val numConsumers = 40
    val numItemsPerProducer = 1000
    val prodCost = 25
    val consCost = 25
    val numMailboxes = 1

    def processItem(curTerm: Double, cost: Int): Double = {
      val random = new Random(cost);
      if (cost > 0) {
        (0 until (cost * 100))
          .map( _ => Math.log(Math.abs(random.nextDouble()) + 0.01))
          .sum
      } else {
        Math.log(Math.abs(random.nextDouble()) + 0.01);
      }
  }
}

object ProdConsBoundedBuffer {

  def main(args: Array[String]) {
    val system = ActorSystem("ProdCons")

    val manager = system.actorOf(Props(
      new ManagerActor(
        ProdConsBoundedBufferConfig.bufferSize,
        ProdConsBoundedBufferConfig.numProducers,
        ProdConsBoundedBufferConfig.numConsumers,
        ProdConsBoundedBufferConfig.numItemsPerProducer)),
      "manager")

    Thread.sleep(6000)
    system.terminate()
  }

  private case class DataItemMessage(data: Double, producer: ActorRef)

  private case class ProduceDataMessage()

  private case class ProducerExitMessage()

  private case class ConsumerAvailableMessage(consumer: ActorRef)

  private class ManagerActor(bufferSize: Int, numProducers: Int, numConsumers: Int, numItemsPerProducer: Int) extends Actor {

    private val adjustedBufferSize: Int = bufferSize - numProducers
    private val availableProducers = new ListBuffer[ActorRef]
    private val availableConsumers = new ListBuffer[ActorRef]
    private val pendingData = new ListBuffer[DataItemMessage]
    private var numTerminatedProducers: Int = 0

    private val producers = Array.tabulate[ActorRef](numProducers)(i =>
      context.system.actorOf(Props(new ProducerActor(i, self, numItemsPerProducer))))
    private val consumers = Array.tabulate[ActorRef](numConsumers)(i =>
      context.system.actorOf(Props(new ConsumerActor(i, self))))

    availableConsumers ++= consumers

    producers foreach { _ ! ProduceDataMessage() }

    override def receive: Receive = {
      case dm @ DataItemMessage(_, producer) =>
        if (availableConsumers.isEmpty) {
          pendingData.append(dm)
        } else {
          availableConsumers.remove(0) ! dm
        }
        if (pendingData.size >= adjustedBufferSize) {
          availableProducers.append(producer)
        } else {
          producer ! ProduceDataMessage()
        }
      case ConsumerAvailableMessage(consumer) =>
        if (pendingData.isEmpty) {
          availableConsumers.append(consumer)
          tryExit()
        } else {
          consumer ! pendingData.remove(0)
          if (!availableProducers.isEmpty) {
            availableProducers.remove(0) ! ProduceDataMessage()
          }
        }
      case ProducerExitMessage() =>
        numTerminatedProducers += 1
        tryExit()
    }

    def tryExit() {
      if (numTerminatedProducers == numProducers && availableConsumers.size == numConsumers) {
        context.stop(self)
      }
    }
  }

  private class ProducerActor(id: Int, manager: ActorRef, numItemsToProduce: Int) extends Actor {

    private var prodItem: Double = 0.0
    private var itemsProduced: Int = 0

    private def produceData() {
      prodItem = ProdConsBoundedBufferConfig.processItem(
        prodItem,
        ProdConsBoundedBufferConfig.prodCost)
      manager ! DataItemMessage(prodItem, self)
      itemsProduced += 1
    }

    override def receive: Receive = {
      case ProduceDataMessage() =>
        if (itemsProduced == numItemsToProduce) {
          manager ! ProducerExitMessage()
          context.stop(self)
        } else {
          produceData()
        }
    }

  }

  private class ConsumerActor(id: Int, manager: ActorRef) extends Actor {

    private val consumerAvailableMessage = ConsumerAvailableMessage(self)
    private var consItem: Double = 0

    protected def consumeDataItem(dataToConsume: Double) {
      consItem = ProdConsBoundedBufferConfig.processItem(
        consItem + dataToConsume,
        ProdConsBoundedBufferConfig.consCost)
    }

    override def receive: Receive = {
      case DataItemMessage(data, _) =>
        consumeDataItem(data)
        manager ! consumerAvailableMessage
    }
  }

}