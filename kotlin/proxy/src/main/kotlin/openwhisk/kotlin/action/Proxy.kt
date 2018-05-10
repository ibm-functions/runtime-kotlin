/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package openwhisk.kotlin.action

import io.ktor.application.*
import io.ktor.content.readText
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.lang.reflect.Method
import java.net.URLClassLoader
import java.util.*
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import com.squareup.moshi.*
import java.util.HashMap
import java.util.Base64
import java.io.BufferedReader
import java.io.InputStreamReader

val moshi = Moshi.Builder().build()

data class Init (
        val value: InitValue
)
data class InitValue (
        val name: String,
        val binary: Boolean,
        val main: String,
        val code: String
)

// Utility function to workaround limitations with Kotlin's generics
inline fun <reified T> returnIt(returnObj: T): String {
    val adapter = moshi.adapter(T::class.java)
    return adapter.toJson(returnObj)
}

fun augmentEnv(newEnv: Map<String, String>): Unit {
    try {
        for (cl in java.util.Collections::class.java.getDeclaredClasses()) {
            if ("java.util.Collections\$UnmodifiableMap".equals(cl.getName())) {
                val field = cl.getDeclaredField("m")
                field.setAccessible(true)
                val obj = field.get(System.getenv())
                val map: Map<String, String> = obj as Map<String, String>
                field.set(System.getenv(), map.plus(newEnv))
            }
        }
    } catch (e: Exception) {}
}

fun main(args: Array<String>) {

    var mainMethod: Method? = null
    var loader: URLClassLoader? = null

    val server = embeddedServer(Netty, 8080) {
        routing {
            post("/init") {
                // Check whether we have already been initialized
                if (mainMethod != null) {
                    val message = "Init failed, Cannot initialize the action more than once."
                    call.respondText("{\"error\":\"" + message + "\"}", ContentType.Application.Json, HttpStatusCode.BadGateway)
                    return@post
                }
                // Parse body data to an instance of Init
                val adapter = moshi.adapter(Init::class.java)
                val content = call.request.receiveContent()
                val text = content.readText()

                val initData: Init?
                try {
                    initData = adapter.fromJson(text)
                } catch (ex: Exception) {
                    val message = "Initialization failed, unable to parse input: " + text
                    call.respondText("{\"error\":\"" + message + "\"}", ContentType.Application.Json, HttpStatusCode.BadGateway)
                    return@post
                }
                val init = initData!!

                // Read init.value.code and write it to a temporary file called useractionXXX.jar
                val value = init.value
                val base64 = value.code.toByteArray(Charsets.UTF_8)
                val file = createTempFile("useraction", ".jar")   // this contains a random name, so we need to get the URL from the file
                val decoder = Base64.getDecoder()
                val decoded = decoder.decode(base64)
                file.writeBytes(decoded)
                val url = file.toURL()

                // Load the main class passed in from init.value.main
                // Kotlin classes have Kt appended, so we'll default to MainKt, and a method of main
                val splittedEntrypoint = value.main.split("#")

                var className = "MainKt"
                if (splittedEntrypoint.size > 0 && splittedEntrypoint.get(0) != "") {
                    className = splittedEntrypoint.get(0)
                    // If the className doesn't end in Kt, add it
                    if (className.takeLast(2) != "Kt") {
                        className = className + "Kt"
                    }
                    // Input files can be lowercase, but are converted to uppercase when compiled
                    // If we have a package name, capitalize first letter after last package separator
                    val classIndex = className.lastIndexOf('.')
                    if ( classIndex > 0) {
                        val mainInitialIndex = classIndex + 1
                        className = className.replaceRange(mainInitialIndex, mainInitialIndex + 1, className[mainInitialIndex].toUpperCase().toString())
                    } else {
                        // If there's no package name, capitalize the first letter
                        className = className.capitalize()
                    }
                }
                var methodName = "main"
                if (splittedEntrypoint.size > 1 && splittedEntrypoint.get(1) != "") {
                    methodName = splittedEntrypoint.get(1)
                }

                loader = URLClassLoader(arrayOf(url))
                val theClass: Class<*>?

                try {
                    theClass = loader!!.loadClass(className)
                } catch (e: Exception) {
                    val message = "Failed to find specified class: " + className + " in provided jar file"
                    call.respondText("{\"error\":\"" + message + "\"}", ContentType.Application.Json, HttpStatusCode.BadGateway)
                    return@post
                }

                // Validate that the class contains the provided method and assign it a global variable
                val theMethods = theClass.declaredMethods
                theMethods.iterator().forEach { method ->
                    if (method.name.startsWith(methodName)) {
                        mainMethod = method
                        return@forEach
                    }
                }
                if (mainMethod != null) {
                    call.respondText("OK", ContentType.Text.Html, HttpStatusCode.OK)
                    return@post
                } else {
                    val message = "Failed to find specified method: " + methodName + " in " + className
                    call.respondText("{\"error\":\"" + message + "\"}", ContentType.Application.Json, HttpStatusCode.BadGateway)
                    return@post
                }
            }
            post("/run") {
                // Check that we have been initialized
                if (mainMethod == null) {
                    val message = "Run failed, cannot invoke an uninitialized action"
                    call.respondText("{\"error\":\"" + message + "\"}", ContentType.Application.Json, HttpStatusCode.BadGateway)
                    return@post
                }

                val content = call.request.receiveContent()
                val inStream = content.inputStream()

                // Convert parameters using GSON. We cannot use Moshi here because we need the contents of "value" to
                // be available as stringified JSON so we can parse it according to what the main function expects
                val parser = JsonParser()
                val runObj = parser.parse(BufferedReader(InputStreamReader(inStream, Charsets.UTF_8)))
                if (runObj == null) {
                    val message = "Run failed, unable to parse input"
                    call.respondText("{\"error\":\"" + message + "\"}", ContentType.Application.Json, HttpStatusCode.BadGateway)
                    return@post
                }

                // Store "value" as a JSON ready for the default "JSON in JSON out" case
                val inputObj = runObj.getAsJsonObject().getAsJsonObject("value")
                if (inputObj == null) {
                    val message = "Run failed, unable to find value entry in input"
                    call.respondText("{\"error\":\"" + message + "\"}", ContentType.Application.Json, HttpStatusCode.BadGateway)
                    return@post
                }

                // Parse the other values and store then in the environment
                val env = HashMap<String, String>()
                for (p in arrayOf("api_key", "namespace", "action_name", "activation_id", "deadline")) {
                    try {
                        val value = runObj.getAsJsonObject().getAsJsonPrimitive(p).getAsString()
                        env.put(String.format("__OW_%s", p.toUpperCase()), value)
                    } catch (e: Exception) {
                    }
                }
                augmentEnv(env)

                // Replace security manager to catch anything that would cause the process to exist, and set the
                // content class loader to allow dynamic loading. These are reverted in finally blocks.
                val sm = System.getSecurityManager()
                val cl = Thread.currentThread().getContextClassLoader()
                Thread.currentThread().setContextClassLoader(loader)
                System.setSecurityManager(WhiskSecurityManager())

                // Get the parameter type for the main function. This tell us whether we're doing
                // JSON in / JSON out, or if we're doing Type in / Type out
                val params = mainMethod!!.getParameterTypes()

                // "Classic" handling, eg. JSON in / JSON out
                if (params[0].name == JsonObject::class.qualifiedName) {
                    try {
                        val returnVal = mainMethod!!.invoke(null, inputObj) as JsonObject
                        if (returnVal == null) {
                            val message = "Run failed, provided function failed to return valid JSON Object"
                            call.respondText("{\"error\":\"" + message + "\"}", ContentType.Application.Json, HttpStatusCode.BadGateway)
                            return@post
                        }
                        val returnString = returnVal.toString()
                        call.respondText(returnString, ContentType.Application.Json, HttpStatusCode.OK)
                        return@post
                    } catch (ex: Exception) {
                        call.respondText("{\"error\":\"" + ex + "\"}", ContentType.Application.Json, HttpStatusCode.BadGateway)
                        return@post
                    } finally {
                        System.setSecurityManager(sm)
                        Thread.currentThread().setContextClassLoader(cl)
                    }
                }
                // "Typed" handling, so convert to type going in, and convert back to JSON coming out
                else {
                    try {
                        val adapter = moshi.adapter(params[0])
                        val inObj = adapter.fromJson(inputObj.toString())
                        if (inObj == null) {
                            val message = "Provided arguments: " + inputObj.toString() + " do not match the functions arguments"
                            call.respondText("{\"error\":\"" + message + "\"}", ContentType.Application.Json, HttpStatusCode.BadGateway)
                            return@post
                        }
                        val returnVal = mainMethod!!.invoke(null, inObj)
                        val returnType = mainMethod!!.returnType
                        val casted = returnType.cast(returnVal)
                        if (casted == null) {
                            val message = "Provided functions did not provide a valid response"
                            call.respondText("{\"error\":\"" + message + "\"}", ContentType.Application.Json, HttpStatusCode.BadGateway)
                            return@post
                        }
                        val returnString = returnIt(casted)
                        if (returnString == null) {
                            val message = "Provided functions response could not be converted to JSON"
                            call.respondText("{\"error\":\"" + message + "\"}", ContentType.Application.Json, HttpStatusCode.BadGateway)
                            return@post
                        }
                        call.respondText(returnString, ContentType.Application.Json, HttpStatusCode.OK)
                        return@post
                    } catch (ex: Exception) {
                        call.respondText("{\"error\":\"" + ex + "\"}", ContentType.Application.Json, HttpStatusCode.BadGateway)
                    } finally {
                        System.setSecurityManager(sm)
                        Thread.currentThread().setContextClassLoader(cl)
                    }
                }
            }
        }
    }
    server.start(wait = true)
}
