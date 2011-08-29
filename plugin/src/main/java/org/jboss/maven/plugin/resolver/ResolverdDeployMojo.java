package org.jboss.maven.plugin.resolver;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.twdata.maven.mojoexecutor.MojoExecutor;

/**
 * 
 * Resolve modules from a directory tree, deploying them to a remote repository
 * 
 * @author graham@redhat.com
 * 
 * @goal deploy
 * @phase deploy
 * @requiresProject false
 * 
 */
public class ResolverdDeployMojo extends ResolverMojo {

	/**
	 * Target repository ID
	 * 
	 * @parameter expression="${maven.resolver.repositoryId}"
	 * @required
	 */
	private String repositoryId;

	/**
	 * Target repository URL
	 * 
	 * @parameter expression="${maven.resolver.repositoryUrl}"
	 * @required
	 */
	private String repositoryUrl;

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
					.artifactId("maven-deploy-plugin"), MojoExecutor
					.version("2.4")), MojoExecutor.goal("deploy-file"),
					MojoExecutor.configuration(MojoExecutor.element(
							MojoExecutor.name("file"), module.getSource()
									.getAbsolutePath()), MojoExecutor.element(
							MojoExecutor.name("repositoryId"), repositoryId),
							MojoExecutor.element(MojoExecutor.name("url"),
									repositoryUrl), MojoExecutor.element(
									MojoExecutor.name("pomFile"), module
											.getDestinationPom()
											.getAbsolutePath())), MojoExecutor
							.executionEnvironment(project, session,
									pluginManager));

	}

}
