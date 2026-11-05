#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
mkdir -p bin/classes
siscom_libs='lib/gson.jar:lib/tomcat-embed-core.jar:lib/jbcrypt.jar:lib/h2.jar:lib/mysql-connector.jar:lib/javax-annotation.jar'
if command -v javac >/dev/null 2>&1; then
 javac -source 8 -target 8 -encoding UTF-8 -cp "$siscom_libs" -d bin/classes src/main/java/pe/edu/siscom/*.java
else
 java -m jdk.compiler/com.sun.tools.javac.Main --release 8 -encoding UTF-8 -cp "$siscom_libs" -d bin/classes src/main/java/pe/edu/siscom/*.java
fi
if command -v jar >/dev/null 2>&1; then
 jar cf bin/siscom.jar -C bin/classes .
else
 java -m jdk.jartool/sun.tools.jar.Main cf bin/siscom.jar -C bin/classes .
fi
