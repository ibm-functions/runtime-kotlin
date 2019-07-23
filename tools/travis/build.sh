#!/bin/bash
set -ex

# Build script for Travis-CI.

SCRIPTDIR=$(cd $(dirname "$0") && pwd)
ROOTDIR="$SCRIPTDIR/../.."
WHISKDIR="$ROOTDIR/../openwhisk"

export OPENWHISK_HOME=$WHISKDIR

IMAGE_PREFIX="testing"

# Build OpenWhisk
cd $WHISKDIR

#pull down images
docker pull openwhisk/controller:nightly
docker tag openwhisk/controller:nightly ${IMAGE_PREFIX}/controller
docker pull openwhisk/invoker:nightly
docker tag openwhisk/invoker:nightly ${IMAGE_PREFIX}/invoker
docker pull openwhisk/nodejs6action:nightly
docker tag openwhisk/nodejs6action:nightly ${IMAGE_PREFIX}/nodejs6action

TERM=dumb ./gradlew install

# run scalafmt checks
cd $ROOTDIR
TERM=dumb ./gradlew checkScalafmtAll

# Build runtime
cd $ROOTDIR
TERM=dumb ./gradlew kotlin:distDocker -PdockerImagePrefix=${IMAGE_PREFIX}
