# IBM Cloud Functions runtime for Kotlin 
WARNING: Work in Progress (WIP) not ready for production

[![Build Status](https://travis-ci.org/ibm-functions/runtime-kotlin.svg?branch=master)](https://travis-ci.org/ibm-functions/runtime-kotlin)

The runtime provides Kotlin running on Java 8u131 b11.

### How to use as a docker Action
To use as a docker action

```sh
bx wsk action update myAction myAction.py --docker ibmfunctions/action-kotlin
```

This works on any deployment of Apache OpenWhisk or IBM Cloud Functions

### Future: IBM Cloud Functions (based on Apache OpenWhisk)
To use as a Kotlin kind action

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


