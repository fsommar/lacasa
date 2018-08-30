package akka.lacasa.actor

object Props {
  val empty: Props = PropsImpl(akka.actor.Props.empty)

  // TODO: Create `T` via reflection
  // def apply[T <: Actor: ClassTag](): Props =
  //   PropsImpl(akka.actor.Props[ActorAdapter])

  def apply[T <: Actor](creator: ⇒ T): Props =
    PropsImpl(akka.actor.Props(new ActorAdapter(creator)))
}

sealed trait Props {
  def unsafe: akka.actor.Props
}

private case class PropsImpl(unsafe: akka.actor.Props) extends Props

private object PropsAdapter {
  def apply(props: Props = Props.empty): akka.actor.Props = props.unsafe
}
