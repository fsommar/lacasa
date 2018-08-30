package akka.pattern.lacasa

import akka.actor.lacasa.{ActorRef, ActorRefAdapter, SafeWrapper}
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

import lacasa.Safe

trait AskSupport {
  implicit def ask(actorRef: ActorRef): AskableActorRef =
    new AskableActorRef(actorRef.asInstanceOf[ActorRefAdapter].unsafe)
}

final class AskableActorRef(val actorRef: akka.actor.ActorRef) extends AnyVal {
  
  def ask[Req: Safe, Res: Safe](msg: Req, sender: ActorRef = ActorRef.noSender)
                               (implicit timeout: Timeout, ec: ExecutionContext): Future[Res] = {
    akka.pattern.ask(actorRef, new SafeWrapper(msg), sender.asInstanceOf[ActorRefAdapter].unsafe)(timeout)
      .map {
        case x: Res @unchecked => x
        case _: SafeWrapper[Res @unchecked] =>
          println("Got SafeWrapper result")
          ???
      }
  }

  def ?[Req, Res](msg: Req)
                 (implicit timeout: Timeout, ec: ExecutionContext,
                  req: Safe[Req], res: Safe[Res]): Future[Res] =
      ask(msg, ActorRef.noSender)(req, res, timeout, ec)

}