package org.jboss.maven.plugin.resolver;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.twdata.maven.mojoexecutor.MojoExecutor;

/**
 * 
 * Resolve modules from a directory tree, installing them to a local repository
 * 
 * @author graham@redhat.com
 * 
 * @goal install
 * @phase install
 * @requiresProject false
 * 
 */
public class ResolverInstallMojo extends ResolverMojo {

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.maven.plugin.Mojo#execute()
	 */
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		super.execute();

		for (ModuleReference module : modules.values())
			MojoExecutor.executeMojo(MojoExecutor.plugin(MojoExecutor
					.groupId("org.apache.maven.plugins"), MojoExecutor
					.artifactId("maven-install-plugin"), MojoExecutor
					.version("2.2")), MojoExecutor.goal("install-file"),
					MojoExecutor.configuration(MojoExecutor.element(
							MojoExecutor.name("file"), module.getSource()
									.getAbsolutePath()), MojoExecutor.element(
							MojoExecutor.name("pomFile"), module
									.getDestinationPom().getAbsolutePath())),
					MojoExecutor.executionEnvironment(project, session,
							pluginManager));

	}

}
