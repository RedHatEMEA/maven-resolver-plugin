#!/bin/sh

###############################################################################
#
# Example script to populate a maven repository from a local server install of a
# single JBoss platform
#
###############################################################################

REPO="/home/ggear/run/pp"
REPO_VERSION="5.0"

MVN_DIR="mvn"
TMP_DIR_TEMPLATE="/tmp/`echo ${0%/} | awk -F/ '{print \$NF}'`.XXXXXXXXXXXXXX"
TMP_DIR=`mktemp -d $TMP_DIR_TEMPLATE`

mkdir -p "$TMP_DIR/$MVN_DIR/$REPO"
echo "Processing [$REPO]"

mvn -e org.jboss.maven.plugin.resolver:plugin:install \
	-Dmaven.resolver.targetDir="$TMP_DIR/$MVN_DIR/$REPO"  \
	-Dmaven.resolver.rootDir="$REPO/$REPO_VERSION"  \
	-Dmaven.resolver.versionRegExp="$REPO_VERSION"  \
	-Dmaven.resolver.groupPrefix="$REPO"  \
	-Dmaven.resolver.groupMaskRegExp="(.*)"  \
	-Dmaven.resolver.excludeRegExp="/samples|/examples|/docs|/tmp|/temp|/work"  \
	-Dmaven.resolver.repositoryId="thirdparty"  \
	-Dmaven.resolver.repositoryUrl="http://caneland2.saleslab.fab.redhat.com:8081/nexus/content/repositories/thirdparty" 

rm -rf $TMP_DIR


