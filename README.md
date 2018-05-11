# IBM Cloud Functions (OpenWhisk) runtime for Kotlin 

:warning:  The Runtime for Kotlin is currently experimental. Feedback is welcome.  :warning:

[![Build Status](https://travis-ci.org/ibm-functions/runtime-kotlin.svg?branch=master)](https://travis-ci.org/ibm-functions/runtime-kotlin)

This runtime provides Kotlin running on the following OpenJDK/OpenJ9 image from [AdoptOpenJDK](https://adoptopenjdk.net/?variant=openjdk8-openj9):

 *  [adoptopenjdk/openjdk8-openj9:x86_64-ubuntu-jdk8u162-b12_openj9-0.8.0](https://hub.docker.com/r/adoptopenjdk/openjdk8-openj9/)

## Creating a Kotlin Action

The Runtime for Kotlin supports two APIs for creating actions:  
1. JSON based.  
The action receives parameters as a JSON object and returns a JSON object, using the [Google GSON library](https://github.com/google/gson) library.  
2. Data Class based.  
The action receives parameters as a user defined data Class, and returns a user defined data Class.  

### "Hello World" using the JSON API
The following shows how to build a "HelloWorld" action using the JSON API:

1. Create a file called `main.kt` containing:

	```kotlin
	import com.google.gson.JsonObject
	
	fun main(args: JsonObject) : JsonObject {
	    val name = args.getAsJsonPrimitive("name").getAsString();
	    val hello = JsonObject();
	    hello.addProperty("greeting", "Hello " + name + "!");
	    return hello
	}
	```
2. Compile the action into a JAR file:

	```sh
	kotlinc -classpath ./gson-2.6.2.jar main.kt -d myAction.jar
	```
	
This provides the action contained in myAction.jar, ready to be deployed and run.

### "Hello World" using the Data Class API
The following shows how to build a "HelloWorld" action using the Data Class API:

1. Create a file called `main.kt` containing:

	```kotlin
	data class User (
	    val name: String
	)
	
	data class Hello (
	    val greeting: String
	)
	
	fun main(user: User) : Hello {
	    val hello = Hello("Hello " + user.name + "!")
	    return hello
	}
	```
	
2. Compile the action into a JAR file:

	```sh
	kotlinc main.kt -d myAction.jar
	```
This provides the action contained in myAction.jar, ready to be deployed and run.

### Deploying the Kotlin Action
The Kotlin action can be deployed for use as a Docker action using the following, works on any deployment of Apache OpenWhisk or IBM Cloud Functions"

```sh
bx wsk action update myAction myAction.jar --docker ibmfunctions/action-kotlin
```

This assumes that you have used the default file name of `main.kt` and the default main function name of `main`.

You can specify alternative package, file and main function names using the `--main` option to `wsk action update`. For example:  

* Using a file name of `hello.kt`:  

	```sh
	bx wsk action update myAction myAction.jar --main "hello" --docker ibmfunctions/action-kotlin
	```
* Using a main function name of `action`:  

	```sh
	bx wsk action update myAction myAction.jar --main "#action" --docker ibmfunctions/action-kotlin
	```

* Using a file name of `hello.kt` and a main function name of `action`:  

	```sh
	bx wsk action update myAction myAction.jar --main "hello#action" --docker ibmfunctions/action-kotlin
	```
	
* Using a package of `myfunctions` with file name of `hello.kt` and a main function name of `action`:  

	```sh
	bx wsk action update myAction myAction.jar --main "myfunctions.hello#action" --docker ibmfunctions/action-kotlin
	```

### Running the Kotlin Action
The Kotlin action can be run in the same way as any other action. The following will execute the Hello World example with a parameter of `Cloud Functions`:

```sh
bx wsk action invoke myAction -b -p name "Cloud Functions"
```

This should return the following in the `response` section of the output:

```json
    "response": {
        "result": {
            "greeting": "Hello Cloud Functions!"
        },
        "status": "success",
        "success": true
    }
```

## Future Work:
Areas of future work for the Runtime for Kotlin include:

1. Async APIs.  
The Runtime for Kotlin currently only provides synchronous, blocking APIs, however it is well suited for asynchronous programming, and is used extensively in that way on Android.
2. Client SDK.  
The implementation of the data Class API makes it easy to share data type definitions with Kotlin clients. Having a client SDK that accepts data Classes when calling the action, particular for Android, would make adoption and usage much easier.



## Developing for the Runtime for Kotlin 
The following information describes how to build and deploy the Runtime for Kotlin from a local Git repository.

### Building an image from the repository
The following builds an image from the project:

```sh
./gradlew core:kotlin:distDocker
```

This will produce the image `ibmfunctions/action-kotlin`

### Building and Pushing an image to Dockerhub:
The following builds an image, and pushes it to Dockerhub for use by OpenWhisk or IBM Cloud Functions:

```sh
docker login 
./gradlew kotlin:distDocker -PdockerImagePrefix=$prefix-user -PdockerRegistry=docker.io
```

You can then create actions using your the image from dockerhub

```
wsk action update myAction myAction.jar --docker $user_prefix/action-kotlin
```

The `$user_prefix` is usually your dockerhub user id.

### Testing
Install dependencies from the root directory on $OPENWHISK_HOME repository
```
./gradlew install
```

Using gradle to run all tests
```
./gradlew :tests:test
```
Using gradle to run some tests
```
./gradlew :tests:test --tests *ActionContainerTests*
```
Using IntelliJ:
- Import project as gradle project.
- Make sure working directory is root of the project/repo

# License
[Apache 2.0](LICENSE.txt)


