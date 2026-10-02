#!/bin/sh
# Builds build/jmx2gatling.jar with nothing but a JDK (version 17 or newer).
# No build tool, no network, no third-party libraries.
set -eu
cd "$(dirname "$0")"

rm -rf build/jar-classes
mkdir -p build/jar-classes

# javac follows the source path from Main and compiles every class it uses.
javac --release 17 -encoding UTF-8 -implicit:class \
    -sourcepath src/main/java \
    -d build/jar-classes \
    src/main/java/jmx2gatling/Main.java

jar --create --file build/jmx2gatling.jar --main-class jmx2gatling.Main -C build/jar-classes .

echo "Built build/jmx2gatling.jar"
echo "Run:  java -jar build/jmx2gatling.jar --help"
