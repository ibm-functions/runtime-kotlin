/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package runtime.actionContainers

import actionContainers.{ActionContainer, ActionProxyContainerTestUtils}
import actionContainers.ActionContainer.withContainer
import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner
import spray.json.DefaultJsonProtocol._
import spray.json._

import ResourceHelpers.JarBuilder

import common.WskActorSystem

@RunWith(classOf[JUnitRunner])
class KotlinActionContainerTests extends FlatSpec with Matchers with WskActorSystem with ActionProxyContainerTestUtils {

  // Helpers specific to java actions
  def withKotlinContainer(code: ActionContainer => Unit, env: Map[String, String] = Map.empty) =
    withContainer("action-kotlin", env)(code)

  override def initPayload(mainClass: String, jar64: String) =
    JsObject(
      "value" -> JsObject("name" -> JsString("dummyAction"), "main" -> JsString(mainClass), "code" -> JsString(jar64)))

  behavior of "Kotlin action"

  it should s"run a kotlin snippet and confirm expected environment variables" in {
    val props = Seq(
      "api_host" -> "xyz",
      "api_key" -> "abc",
      "namespace" -> "zzz",
      "action_name" -> "xxx",
      "activation_id" -> "iii",
      "deadline" -> "123")
    val env = props.map { case (k, v) => s"__OW_${k.toUpperCase}" -> v }
    val (out, err) =
      withKotlinContainer(
        { c =>
          val jar = JarBuilder.compileToJar(
            Seq("example", "HelloWhisk.kt") ->
              """
                | package example
                |
                | import com.google.gson.JsonObject
                |
                |
                | fun main(args: JsonObject) : JsonObject {
                |     var response = JsonObject()
                |     response.addProperty("api_host", System.getenv("__OW_API_HOST"))
                |     response.addProperty("api_key", System.getenv("__OW_API_KEY"))
                |     response.addProperty("namespace", System.getenv("__OW_NAMESPACE"))
                |     response.addProperty("action_name", System.getenv("__OW_ACTION_NAME"))
                |     response.addProperty("activation_id", System.getenv("__OW_ACTIVATION_ID"))
                |     response.addProperty("deadline", System.getenv("__OW_DEADLINE"))
                |     return response
                | }
              """.stripMargin.trim)

          val (initCode, _) = c.init(initPayload("example.HelloWhisk", jar))
          initCode should be(200)

          val (runCode, out) = c.run(runPayload(JsObject(), Some(props.toMap.toJson.asJsObject)))
          runCode should be(200)
          props.map {
            case (k, v) => out.get.fields(k) shouldBe JsString(v)

          }
        },
        env.take(1).toMap)

    //out.trim shouldBe empty
    //err.trim shouldBe empty
  }

  it should "support valid flows" in {
    val (out, err) = withKotlinContainer { c =>
      val jar = JarBuilder.compileToJar(
        Seq("example", "HelloWhisk.kt") ->
          """
            | package example
            |
            | import com.google.gson.JsonObject
            |
            | fun main(args: JsonObject) : JsonObject {
            |     val name = args.getAsJsonPrimitive("name").getAsString()
            |     val response = JsonObject()
            |     val greeting = "Hello " + name + "!"
            |     response.addProperty("greeting", greeting)
            |     return response
            | }
          """.stripMargin.trim)

      val (initCode, _) = c.init(initPayload("example.HelloWhisk", jar))
      initCode should be(200)

      val (runCode1, out1) = c.run(runPayload(JsObject("name" -> JsString("Whisk"))))
      runCode1 should be(200)
      out1 should be(Some(JsObject("greeting" -> JsString("Hello Whisk!"))))

      val (runCode2, out2) = c.run(runPayload(JsObject("name" -> JsString("ksihW"))))
      runCode2 should be(200)
      out2 should be(Some(JsObject("greeting" -> JsString("Hello ksihW!"))))
    }

    //out.trim shouldBe empty
    //err.trim shouldBe empty
  }

  it should "support valid actions with non 'main' names" in {
    val (out, err) = withKotlinContainer { c =>
      val jar = JarBuilder.compileToJar(
        Seq("example", "HelloWhisk.kt") ->
          """
             | package example
             |
             | import com.google.gson.JsonObject
             |
             | fun hello(args: JsonObject) : JsonObject {
             |     val name = args.getAsJsonPrimitive("name").getAsString()
             |     val response = JsonObject()
             |     val greeting = "Hello " + name + "!"
             |     response.addProperty("greeting", greeting)
             |     return response
             | }
          """.stripMargin.trim)

      val (initCode, _) = c.init(initPayload("example.HelloWhisk#hello", jar))
      initCode should be(200)

      val (runCode, out) = c.run(runPayload(JsObject("name" -> JsString("Whisk"))))
      runCode should be(200)
      out should be(Some(JsObject("greeting" -> JsString("Hello Whisk!"))))
    }

    //out.trim shouldBe empty
    //err.trim shouldBe empty
  }

  it should "report an error if explicit 'main' is not found" in {
    val (out, err) = withKotlinContainer { c =>
      val jar = JarBuilder.compileToJar(
        Seq("example", "HelloWhisk.kt") ->
          """
            | package example
            |
            | import com.google.gson.JsonObject
            |
            | fun hello(args: JsonObject) : JsonObject {
            |     val name = args.getAsJsonPrimitive("name").getAsString()
            |     val response = JsonObject()
            |     val greeting = "Hello " + name + "!"
            |     response.addProperty("greeting", greeting)
            |     return response
            | }
          """.stripMargin.trim)

      Seq("", "x", "!", "#", "#main", "#bogus").foreach { m =>
        val (initCode, out) = c.init(initPayload(s"example.HelloWhisk$m", jar))
        initCode shouldBe 502

        out shouldBe {
          val error = m match {
            case c if c == "x" || c == "!" =>
              "Failed to find specified class: example.HelloWhisk" + c + "Kt in provided jar file"
            case "#bogus" => "Failed to find specified method: bogus in example.HelloWhiskKt"
            case _        => "Failed to find specified method: main in example.HelloWhiskKt"
          }
          Some(JsObject("error" -> error.toJson))
        }
      }
    }

    //out.trim shouldBe empty
    //err.trim should not be empty
  }

  it should "handle unicode in source, input params, logs, and result" in {
    val (out, err) = withKotlinContainer { c =>
      val jar = JarBuilder.compileToJar(
        Seq("example", "HelloWhisk.kt") ->
          """
            | package example
            |
            | import com.google.gson.JsonObject
            |
            | fun main(args: JsonObject) : JsonObject {
            |     val delimiter = args.getAsJsonPrimitive("delimiter").getAsString()
            |     val response = JsonObject()
            |     val str = delimiter + " ☃ " + delimiter
            |     println(str)
            |     response.addProperty("winter", str)
            |     return response
            | }
          """.stripMargin.trim)

      val (initCode, _) = c.init(initPayload("example.HelloWhisk", jar))
      val (runCode, runRes) = c.run(runPayload(JsObject("delimiter" -> JsString("❄"))))
      runRes.get.fields.get("winter") shouldBe Some(JsString("❄ ☃ ❄"))
    }

    out should include("❄ ☃ ❄")
    err.trim shouldBe empty
  }

  it should "fail to initialize with bad code" in {
    val (out, err) = withKotlinContainer { c =>
      // This is valid zip file containing a single file, but not a valid
      // jar file.
      val brokenJar = ("UEsDBAoAAAAAAPxYbkhT4iFbCgAAAAoAAAANABwAbm90YWNsYXNzZmlsZVV" +
        "UCQADzNPmVszT5lZ1eAsAAQT1AQAABAAAAABzYXVjaXNzb24KUEsBAh4DCg" +
        "AAAAAA/FhuSFPiIVsKAAAACgAAAA0AGAAAAAAAAQAAAKSBAAAAAG5vdGFjb" +
        "GFzc2ZpbGVVVAUAA8zT5lZ1eAsAAQT1AQAABAAAAABQSwUGAAAAAAEAAQBT" +
        "AAAAUQAAAAAA")

      Thread.sleep(500)
      val (initCode, out) = c.init(initPayload("example.Broken", brokenJar))
      initCode should not be (200)
      out should be(
        Some(JsObject("error" -> JsString("Failed to find specified class: example.BrokenKt in provided jar file"))))
    }

    // Somewhere, the logs should contain an exception.
    //val combined = out + err
    //combined.toLowerCase should include("exception")
  }

  it should "return some error on action error" in {
    val (out, err) = withKotlinContainer { c =>
      val jar = JarBuilder.compileToJar(
        Seq("example", "HelloWhisk.kt") ->
          """
            | package example
            |
            | import com.google.gson.JsonObject
            |
            | fun main(args: JsonObject) : Nothing {
            |     throw Exception("noooooooo")
            | }
          """.stripMargin.trim)

      val (initCode, _) = c.init(initPayload("example.HelloWhisk", jar))
      initCode should be(200)

      val (runCode, runRes) = c.run(runPayload(JsObject()))
      runCode should not be (200)

      runRes shouldBe defined
      runRes.get.fields.get("error") shouldBe defined
    }

    //val combined = out + err
    //combined.toLowerCase should include("exception")
  }

  it should "support application errors" in {
    val (out, err) = withKotlinContainer { c =>
      val jar = JarBuilder.compileToJar(
        Seq("example", "Error.kt") ->
          """
            | package example
            |
            | import com.google.gson.JsonObject
            |
            | fun main(args: JsonObject) : JsonObject {
            |     val error = JsonObject()
            |     error.addProperty("error", "This action is unhappy.")
            |     return error
            | }
          """.stripMargin.trim)

      val (initCode, _) = c.init(initPayload("example.Error", jar))
      initCode should be(200)

      val (runCode, runRes) = c.run(runPayload(JsObject()))
      runCode should be(200) // action writer returning an error is OK

      runRes shouldBe defined
      runRes.get.fields.get("error") shouldBe defined
    }

    //val combined = out + err
    //combined.trim shouldBe empty
  }

  it should "survive System.exit" in {
    val (out, err) = withKotlinContainer { c =>
      val jar = JarBuilder.compileToJar(
        Seq("example", "Quitter.kt") ->
          """
            | package example
            |
            | import com.google.gson.*
            |
            | fun main(args: JsonObject) : JsonObject {
            |     System.exit(1)
            |     return JsonObject()
            | }
          """.stripMargin.trim)

      val (initCode, _) = c.init(initPayload("example.Quitter", jar))
      initCode should be(200)

      val (runCode, runRes) = c.run(runPayload(JsObject()))
      runCode should not be (200)

      runRes shouldBe defined
      runRes.get.fields.get("error") shouldBe defined
    }

    //val combined = out + err
    //combined.toLowerCase should include("system.exit")
  }

  it should "enforce that the user returns an object" in {
    withKotlinContainer { c =>
      val jar = JarBuilder.compileToJar(
        Seq("example", "Nuller.kt") ->
          """
            | package example
            |
            | import com.google.gson.*
            |
            | fun main(args: JsonObject) : JsonObject? {
            |     return null;
            | }
          """.stripMargin.trim)

      val (initCode, _) = c.init(initPayload("example.Nuller", jar))
      initCode should be(200)

      val (runCode, runRes) = c.run(runPayload(JsObject()))
      runCode should not be (200)

      runRes shouldBe defined
      runRes.get.fields.get("error") shouldBe defined
    }
  }

  val dynamicLoadingJar = JarBuilder.compileToJar(
    Seq(
      Seq("example", "EntryPoint.kt") ->
        """
          | package example
          |
          | import com.google.gson.*
          | import java.lang.reflect.*
          |
          | val CLASS_NAME = "example.DynamicClass";
          |
          | fun main(args: JsonObject) : JsonObject {
          |     val cl = args.getAsJsonPrimitive("classLoader").getAsString()
          |
          |     var dynamicClass: Class<*>? = null
          |     if("local".equals(cl)) {
          |         dynamicClass = Class.forName(CLASS_NAME)
          |     } else if("thread".equals(cl)) {
          |         dynamicClass = Thread.currentThread().getContextClassLoader().loadClass(CLASS_NAME)
          |     }
          |     val response = JsonObject()
          |     if (dynamicClass == null) {
          |       response.addProperty("error", "dynamicClass is null")
          |       return response
          |     }
          |     val d = dynamicClass!!
          |
          |     val o = d.newInstance()
          |     val m = o::class.java.getMethod("getMessage")
          |     val msg: String = m.invoke(o) as String
          |
          |     //val response = JsonObject()
          |     response.addProperty("message", msg)
          |     return response
          | }
        """.stripMargin.trim,
      Seq("example", "DynamicClass.kt") ->
        """
          | package example
          |
          | class DynamicClass {
          |     fun getMessage() : String {
          |         return "dynamic!"
          |     }
          | }
        """.stripMargin.trim))

  def classLoaderTest(param: String) = {
    val (out, err) = withKotlinContainer { c =>
      Thread.sleep(700)
      val (initCode, _) = c.init(initPayload("example.EntryPoint", dynamicLoadingJar))
      initCode should be(200)

      val (runCode, runRes) = c.run(runPayload(JsObject("classLoader" -> JsString(param))))
      runCode should be(200)

      runRes shouldBe defined
      runRes.get.fields.get("message") shouldBe Some(JsString("dynamic!"))
    }
    //(out ++ err).trim shouldBe empty
  }

  it should "support loading classes from the current classloader" in {
    classLoaderTest("local")
  }

  it should "support loading classes from the Thread classloader" in {
    classLoaderTest("thread")
  }

  it should "support class in/out flows" in {
    val (out, err) = withKotlinContainer { c =>
      val jar = JarBuilder.compileToJar(
        Seq("example", "HelloWhisk.kt") ->
          """
            | package example
            |
            | import com.google.gson.JsonObject
            |
            | data class User(
            |     val id: String,
            |     val username: String
            | )
            |
            | fun main(user: User) : User {
            |     return user
            | }
          """.stripMargin.trim)

      val (initCode, _) = c.init(initPayload("example.HelloWhisk", jar))
      initCode should be(200)

      val props = Seq("id" -> "UsersID", "username" -> "UsersUsername")

      val (runCode, runRes) = c.run(runPayload(props.toMap.toJson.asJsObject))
      runCode should be(200)

      runRes shouldBe defined
      runRes.get.fields.get("id") shouldBe Some(JsString("UsersID"))
      runRes.get.fields.get("username") shouldBe Some(JsString("UsersUsername"))
    }
  }
}
