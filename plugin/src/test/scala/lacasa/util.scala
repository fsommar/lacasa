package lacasa

import reflect._
import tools.reflect.{ToolBox, ToolBoxError}

object util {

  implicit class objectops(obj: Any) {
    def mustBe(other: Any) = assert(obj == other, obj + " is not " + other)
    def mustEqual(other: Any) = mustBe(other)
  }

  implicit class stringops(text: String) {
    def mustContain(substring: String) = assert(text contains substring, text)
  }

  def intercept[T <: Throwable : ClassTag](body: => Any): T =
    try {
      body
      throw new Exception(s"Exception of type ${classTag[T]} was not thrown")
    } catch {
      case t: Throwable =>
        Box.uncheckedCatchControl
        if (classTag[T].runtimeClass != t.getClass) throw t
        else t.asInstanceOf[T]
    }

  def eval(code: String, compileOptions: String = ""): Any = {
    val tb = mkToolbox(compileOptions)
    tb.eval(tb.parse(code))
  }

  def mkToolbox(compileOptions: String = ""): ToolBox[_ <: scala.reflect.api.Universe] = {
    val m = scala.reflect.runtime.currentMirror
    import scala.tools.reflect.ToolBox
    m.mkToolBox(options = compileOptions)
  }

  def scalaBinaryVersion: String = {
    val Pattern = """(\d+\.\d+)\..*""".r
    scala.util.Properties.versionNumberString match {
      case Pattern(v) => v
      case _          => ""
    }
  }

  def toolboxClasspath: String = {
    val f = new java.io.File(s"core/target/scala-${scalaBinaryVersion}/classes")
    if (!f.exists) sys.error(s"output directory ${f.getAbsolutePath} does not exist.")
    f.getAbsolutePath
  }

  def sporesClasspath: String = {
    val f = new java.io.File(s"lib/spores-core_2.11.jar")
    if (!f.exists) sys.error(s"jar file ${f.getAbsolutePath} does not exist.")
    f.getAbsolutePath
  }

  def pluginPath: String = {
    val path = java.lang.System.getProperty("lacasa.plugin.jar")
    val f = new java.io.File(path)
    val absPath = f.getAbsolutePath
    println(s"LaCasa plugin path: $absPath")
    absPath
  }

  def expectError(errorSnippet: String, compileOptions: String = "",
                  baseCompileOptions: String = s"-cp ${toolboxClasspath}:${sporesClasspath} -Xplugin:${pluginPath} -P:lacasa:enable")(code: String) {
    intercept[ToolBoxError] {
      eval(code, compileOptions + " " + baseCompileOptions)
    }.getMessage mustContain errorSnippet
  }
}
