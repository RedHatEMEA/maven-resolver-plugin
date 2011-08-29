#!/bin/sh

# Update eclipse with maven repo reference
mvn -Declipse.workspace=/home/ggear/opt/jboss/trunk/jboss-apps/workspace eclipse:add-maven-repo

# Build project
cd ./build
mvn -Dmaven.test.skip=false -P run-its eclipse:clean eclipse:eclipse clean install
