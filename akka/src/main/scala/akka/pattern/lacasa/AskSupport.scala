package akka.lacasa.pattern

import akka.lacasa.actor.{ActorRef, ActorRefAdapter, SafeWrapper}
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

trait AskSupport {
  implicit def ask(actorRef: ActorRef): AskableActorRef =
    new AskableActorRef(actorRef.asInstanceOf[ActorRefAdapter].unsafe)
}

// private[akka] AskWrapper[T](value: T)

final class AskableActorRef(val actorRef: akka.actor.ActorRef) extends AnyVal {
  
  def ask[Req: lacasa.Safe, Res: lacasa.Safe]
    (msg: Req, sender: ActorRef)
    (implicit timeout: Timeout, ec: ExecutionContext): Future[Res] = {
    akka.pattern.ask(actorRef, new SafeWrapper(msg), sender.asInstanceOf[ActorRefAdapter].unsafe)(timeout)
      .map {
        case x: SafeWrapper[Res @unchecked] =>
          x.value
      }
  }

  def ?[Req, Res](msg: Req)
                 (implicit timeout: Timeout, ec: ExecutionContext,
                  sender: ActorRef,
                  req: lacasa.Safe[Req], res: lacasa.Safe[Res]): Future[Res] =
      ask(msg, sender)(req, res, timeout, ec)

}