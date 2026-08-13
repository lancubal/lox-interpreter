package ar.edu.utn.tadp.slox

import ar.edu.utn.tadp.slox.result.EvaluationResult
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class InterpreterSpec extends AnyFunSuite with Matchers {

  private def executeSource(source: String): Either[List[String], EvaluationResult[ar.edu.utn.tadp.slox.environment.Environment]] = {
    Main.run(source, Main.initialGlobalEnvironment)
  }

  test("Arithmetic expressions and string concatenation") {
    val source =
      """
        |var a = 10 + 20 * 2;
        |var b = "Hello " + "World";
        |""".stripMargin
    val res = executeSource(source)
    res.isRight shouldBe true
  }

  test("Control flow with if, while, and for loops") {
    val source =
      """
        |var count = 0;
        |for (var i = 0; i < 10; i = i + 1) {
        |  count = count + 1;
        |}
        |""".stripMargin
    val res = executeSource(source)
    res.isRight shouldBe true
  }

  test("First-class functions and lexical closures") {
    val source =
      """
        |fun makeCounter() {
        |  var i = 0;
        |  fun count() {
        |    i = i + 1;
        |    return i;
        |  }
        |  return count;
        |}
        |var counter = makeCounter();
        |counter();
        |counter();
        |""".stripMargin
    val res = executeSource(source)
    res.isRight shouldBe true
  }

  test("Classes, initializers, dynamic properties, and inheritance with super") {
    val source =
      """
        |class Doughnut {
        |  cook() {
        |    return "Fry until golden brown.";
        |  }
        |}
        |
        |class BostonCream < Doughnut {
        |  init(flavor) {
        |    this.flavor = flavor;
        |  }
        |  cook() {
        |    return super.cook() + " Pipe full of " + this.flavor + " custard.";
        |  }
        |}
        |
        |var pastry = BostonCream("vanilla");
        |pastry.cook();
        |""".stripMargin
    val res = executeSource(source)
    res.isRight shouldBe true
  }

  test("Native clock function") {
    val source = "var t = clock();"
    val res = executeSource(source)
    res.isRight shouldBe true
  }
}
