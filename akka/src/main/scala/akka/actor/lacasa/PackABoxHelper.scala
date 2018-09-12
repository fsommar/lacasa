package lacasa

object PackABoxHelper {
  final class NoReturnControl extends lacasa.NoReturnControl

  def pack[T](box: Box[T])(implicit access: box.Access): lacasa.Packed[T] =
    box.pack()

}