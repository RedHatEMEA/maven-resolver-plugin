#!/bin/sh

###############################################################################
#
# Example script to populate a maven repository from an SVN server repo of
# JBoss platforms
#
###############################################################################

REPO_PREFIX="http://caneland2.saleslab.fab.redhat.com/svn/repos/tags/"
REPOS=( "jboss-community/as" "jboss-enterprise/ap" "jboss-enterprise/pp" "jboss-enterprise/soap" "jboss-enterprise/ws" "jboss-enterprise/brmsp" )

SVN_DIR="svn"
MVN_DIR="mvn"
TMP_DIR_TEMPLATE="/tmp/`echo ${0%/} | awk -F/ '{print \$NF}'`.XXXXXXXXXXXXXX"
TMP_DIR=`mktemp -d $TMP_DIR_TEMPLATE`
FILE_LOG=log.txt

for REPO in ${REPOS[@]}
do
	mkdir -p "$TMP_DIR/$SVN_DIR/$REPO" "$TMP_DIR/$MVN_DIR/$REPO"
	echo "Processing [$REPO_PREFIX$REPO]" >> $TMP_DIR/$MVN_DIR/$REPO/$FILE_LOG
	{
		svn export --force "$REPO_PREFIX$REPO" "$TMP_DIR/$SVN_DIR/$REPO" >> $TMP_DIR/$MVN_DIR/$REPO/$FILE_LOG
		mvn -e org.jboss.maven.plugin.resolver:plugin:deploy \
			-Dmaven.resolver.targetDir="$TMP_DIR/$MVN_DIR/$REPO"  \
			-Dmaven.resolver.rootDir="$TMP_DIR/$SVN_DIR/$REPO"  \
			-Dmaven.resolver.versionRegExp="([^/]*/[^/]*)/.*"  \
			-Dmaven.resolver.groupPrefix="$REPO"  \
			-Dmaven.resolver.groupMaskRegExp="[^/]*/[^/]*/(.*)"  \
			-Dmaven.resolver.excludeRegExp="/samples|/examples|/docs|/tmp|/temp|/work"  \
			-Dmaven.resolver.repositoryId="thirdparty"  \
			-Dmaven.resolver.repositoryUrl="http://caneland2.saleslab.fab.redhat.com:8081/nexus/content/repositories/thirdparty"  \
			>> $TMP_DIR/$MVN_DIR/$REPO/$FILE_LOG
	}&
done

wait
rm -rf $TMP_DIR


