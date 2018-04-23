# IBM Cloud Functions runtime for Kotlin 
WARNING: Work in Progress (WIP) not ready for production

[![Build Status](https://travis-ci.org/ibm-functions/runtime-kotlin.svg?branch=master)](https://travis-ci.org/ibm-functions/runtime-kotlin)

The runtime provides Kotlin running on Java 8u131 b11.

## Hello World Kotlin Action

The runtime for Kotlin supports two types of API for creating actions:  
1. JSON parameter and return type, using the GSON Library.  
2. Data Class parameter and return type.

### JSON parameter based main.kt
The following shows how to build a "HelloWorld" action using JSON parameters:

```kotlin
import com.google.gson.JsonObject

fun main(args: JsonObject) : JsonObject {
    val name = args.getAsJsonPrimitive("name").getAsString();
    val hello = JsonObject();
    hello.addProperty("greeting", "Hello " + name + "!");
    return hello
}
```
The action can then be compiled into a JAR file for use with the runtime for Kotlin. As the action has a dependency on the [Google GSON library](https://github.com/google/gson), it needs to be available on the classpath:
```sh
kotlinc -classpath ./gson-2.6.2.jar main.kt -d myAction.jar
```

### Data Class based main.kt
The following shows how to build a "HelloWorld" action using JSON parameters:

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
The action can then be compiled into a JAR file for use with the runtime for Kotlin as follows:
```sh
kotlinc hello.kt -d myAction.jar
```

### How to use as a docker Action
To use as a docker action

```sh
bx wsk action update myAction myAction.jar --docker ibmfunctions/action-kotlin
```

This works on any deployment of Apache OpenWhisk or IBM Cloud Functions

### Future: IBM Cloud Functions (based on Apache OpenWhisk)
To use as a Kotlin kind action:

```sh
bx wsk action update myAction myAction --kind kotlin
```

Tip: Not available yet in the IBM Cloud

### Working with the local git repo 
Prerequisite: *Export* OPENWHISK_HOME to point to your incubator/openwhisk cloned directory.

```sh
./gradlew core:kotlin:distDocker
```

This will produce the image `whisk/action-kotlin`

Build and Push image:

```sh
docker login 
./gradlew core:kotlin:distDocker -PdockerImagePrefix=$prefix-user -PdockerRegistry=docker.io
```

Deploy OpenWhisk using ansible environment that adds the new kind `kotlin`
Assuming you have OpenWhisk already deploy localy and `OPENWHISK_HOME` pointing to root directory of OpenWhisk core repository.

Set `ROOTDIR` to the root directory of this repository.

Redeploy OpenWhisk

```sh
cd $OPENWHISK_HOME/ansible
ANSIBLE_CMD="ansible-playbook -i ${ROOTDIR}/ansible/environments/local"
$ANSIBLE_CMD setup.yml
$ANSIBLE_CMD couchdb.yml
$ANSIBLE_CMD initdb.yml
$ANSIBLE_CMD wipe.yml
$ANSIBLE_CMD openwhisk.yml
```

To use as docker action push to your own dockerhub account

```sh
docker tag whisk/action-kotlin $user_prefix/action-kotlin
docker push $user_prefix/action-kotlin
```

Then create the action using your the image from dockerhub

```
wsk action update myAction myAction.jar --docker $user_prefix/action-kotlin
```

The `$user_prefix` is usually your dockerhub user id.

### Testing


To run all tests: `./gradlew tests:test` this include tests depending on credentials

To run all tests except those which do not rely on credentials `./gradlew tests:testWithoutCredentials`

To run a single test-class: `./gradlew tests:test --tests <SomeGradleTestFilter>`


# License
[Apache 2.0](LICENSE.txt)


