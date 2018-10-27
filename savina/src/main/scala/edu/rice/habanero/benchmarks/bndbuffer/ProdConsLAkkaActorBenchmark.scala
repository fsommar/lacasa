package edu.rice.habanero.benchmarks.bndbuffer

import akka.lacasa.actor.{ActorRef, Props}
import edu.rice.habanero.actors.{LAkkaActor => Actor, LAkkaActorState => ActorState}
import edu.rice.habanero.benchmarks.bndbuffer.ProdConsBoundedBufferConfig._
import edu.rice.habanero.benchmarks.{Benchmark, BenchmarkRunner}

import scala.collection.mutable.ListBuffer

/**
 * @author <a href="http://shams.web.rice.edu/">Shams Imam</a> (shams@rice.edu)
 */
object ProdConsLAkkaActorBenchmark {
  def main(args: Array[String]) {
    BenchmarkRunner.runBenchmark(args, new ProdConsLAkkaActorBenchmark)
  }

  private final class ProdConsLAkkaActorBenchmark extends Benchmark {
    def initialize(args: Array[String]) {
      ProdConsBoundedBufferConfig.parseArgs(args)
    }

    def printArgInfo() {
      ProdConsBoundedBufferConfig.printArgs()
    }

    def runIteration() {

      val system = ActorState.newActorSystem("ProdCons")

      val manager = system.actorOf(Props(
        new ManagerActor(
          ProdConsBoundedBufferConfig.bufferSize,
          ProdConsBoundedBufferConfig.numProducers,
          ProdConsBoundedBufferConfig.numConsumers,
          ProdConsBoundedBufferConfig.numItemsPerProducer)),
        name = "manager")

      ActorState.startActor(manager)

      ActorState.awaitTermination(system)
    }

    def cleanupIteration(lastIteration: Boolean, execTimeMillis: Double) {
    }

    private class ManagerActor(bufferSize: Int, numProducers: Int, numConsumers: Int, numItemsPerProducer: Int) extends Actor[Message] {


      private val adjustedBufferSize: Int = bufferSize - numProducers
      private val availableProducers = new ListBuffer[ActorRef]
      private val availableConsumers = new ListBuffer[ActorRef]
      private val pendingData = new ListBuffer[DataItemMessage]
      private var numTerminatedProducers: Int = 0

      private val producers = Array.tabulate[ActorRef](numProducers)(i =>
        context.system.actorOf(Props(new ProducerActor(i, self, numItemsPerProducer))))
      private val consumers = Array.tabulate[ActorRef](numConsumers)(i =>
        context.system.actorOf(Props(new ConsumerActor(i, self))))

      override def onPostStart() {
        consumers.foreach(loopConsumer => {
          availableConsumers.append(loopConsumer)
          ActorState.startActor(loopConsumer)
        })

        producers.foreach(loopProducer => {
          ActorState.startActor(loopProducer)
        })

        producers.foreach(loopProducer => {
          loopProducer ! ProduceDataMessage()
        })
      }

      override def onPreExit() {
        consumers.foreach(loopConsumer => {
          loopConsumer ! ConsumerExitMessage()
        })
      }

      override def process(theMsg: Message) {
        theMsg match {
          case dm: DataItemMessage =>
            val producer: ActorRef = dm.producer.asInstanceOf[ActorRef]
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
          case cm: ConsumerAvailableMessage =>
            val consumer: ActorRef = cm.consumer.asInstanceOf[ActorRef]
            if (pendingData.isEmpty) {
              availableConsumers.append(consumer)
              tryExit()
            } else {
              consumer ! pendingData.remove(0)
              if (!availableProducers.isEmpty) {
                availableProducers.remove(0) ! ProduceDataMessage()
              }
            }
          case _: ProducerExitMessage =>
            numTerminatedProducers += 1
            tryExit()
        }
      }

      def tryExit() {
        if (numTerminatedProducers == numProducers && availableConsumers.size == numConsumers) {
          exit()
        }
      }
    }

    private class ProducerActor(id: Int, manager: ActorRef, numItemsToProduce: Int) extends Actor[Message] {

      private var prodItem: Double = 0.0
      private var itemsProduced: Int = 0

      private def produceData() {
        prodItem = processItem(prodItem, prodCost)
        manager ! DataItemMessage(prodItem, self)
        itemsProduced += 1
      }

      override def process(theMsg: Message) {
        if (theMsg.isInstanceOf[ProduceDataMessage]) {
          if (itemsProduced == numItemsToProduce) {
            exit()
          } else {
            produceData()
          }
        }
      }

      override def onPreExit() {
        manager ! ProducerExitMessage()
      }
    }

    private class ConsumerActor(id: Int, manager: ActorRef) extends Actor[Message] {

      private val consumerAvailableMessage = ConsumerAvailableMessage(self)
      private var consItem: Double = 0

      protected def consumeDataItem(dataToConsume: Double) {
        consItem = processItem(consItem + dataToConsume, consCost)
      }

      override def process(theMsg: Message) {
        theMsg match {
          case dm: DataItemMessage =>
            consumeDataItem(dm.data)
            manager ! consumerAvailableMessage
          case _: ConsumerExitMessage =>
            exit()
        }
      }
    }

  }

}
