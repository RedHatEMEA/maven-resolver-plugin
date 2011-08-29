#!/bin/sh

###############################################################################
#
# Example script to populate a maven repository from an SVN server repo of a
# single JBoss platform
#
###############################################################################

REPO_PREFIX="http://caneland2.saleslab.fab.redhat.com/svn/repos/tags/"
REPO="jboss-enterprise/ap"
REPO_VERSION="5.0/1"

SVN_DIR="svn"
MVN_DIR="mvn"
TMP_DIR_TEMPLATE="/tmp/`echo ${0%/} | awk -F/ '{print \$NF}'`.XXXXXXXXXXXXXX"
TMP_DIR=`mktemp -d $TMP_DIR_TEMPLATE`

mkdir -p "$TMP_DIR/$SVN_DIR/$REPO" "$TMP_DIR/$MVN_DIR/$REPO"
echo "Processing [$REPO_PREFIX$REPO]"

svn export --force "$REPO_PREFIX$REPO/$REPO_VERSION" "$TMP_DIR/$SVN_DIR/$REPO"
mvn -e org.jboss.maven.plugin.resolver:plugin:deploy \
	-Dmaven.resolver.targetDir="$TMP_DIR/$MVN_DIR/$REPO"  \
	-Dmaven.resolver.rootDir="$TMP_DIR/$SVN_DIR/$REPO"  \
	-Dmaven.resolver.versionRegExp="$REPO_VERSION"  \
	-Dmaven.resolver.groupPrefix="$REPO"  \
	-Dmaven.resolver.groupMaskRegExp="(.*)"  \
	-Dmaven.resolver.excludeRegExp="/samples|/examples|/docs|/tmp|/temp|/work"  \
	-Dmaven.resolver.repositoryId="thirdparty"  \
	-Dmaven.resolver.repositoryUrl="http://caneland2.saleslab.fab.redhat.com:8081/nexus/content/repositories/thirdparty" 

rm -rf $TMP_DIR


