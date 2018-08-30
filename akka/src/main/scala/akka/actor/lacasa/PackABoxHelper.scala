package lacasa

object PackABoxHelper {
  final class NoReturnControl extends scala.util.control.ControlThrowable {
    // do not fill in stack trace for efficiency
    final override def fillInStackTrace(): Throwable = this
  }

  def pack[T](box: Box[T])(implicit access: box.Access): lacasa.Packed[T] =
    box.pack()

}