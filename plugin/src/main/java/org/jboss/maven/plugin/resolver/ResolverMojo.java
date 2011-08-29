package org.jboss.maven.plugin.resolver;

import java.io.File;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginManager;
import org.apache.maven.project.MavenProject;
import org.apache.regexp.RE;
import org.apache.regexp.RESyntaxException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.WriterFactory;

/**
 * 
 * @author graham@redhat.com
 * 
 */
public abstract class ResolverMojo extends AbstractMojo {

	public static final char POM_SEP = '.';
	public static final char POM_SEP_ESC = '_';
	public static final char VER_SEP = '-';

	public static final String POM_TYPE = "pom";

	public enum POM_TYPES {
		jar, war, ear
	}

	public static final String REGEXP_VALID_VERSION = "[^//*/(/)]*";

	/**
	 * Resolver root directory to begin processing from
	 * 
	 * @parameter expression="${maven.resolver.rootDir}"
	 * @required
	 */
	private File rootDir;

	/**
	 * Prefix to apply to resolved module groupIds
	 * 
	 * @parameter expression="${maven.resolver.groupPrefix}" default-value=""
	 */
	private String groupPrefix = "";

	/**
	 * Regular expression applying a mask to the &lt;rootDir&gt; path when
	 * determining the module groupId. All parenthesized subexpressions are
	 * concatenated to mask the path from which the groupId is formed. If this
	 * results in an emtpy string the module will be ignored. See
	 * http://jakarta.apache.org/regexp/apidocs/ for regular expression notes
	 * 
	 * @parameter expression="${maven.resolver.groupMaskRegExp}"
	 *            default-value=""
	 */
	private String groupMaskRegExp = "";
	private RE groupMaskRE = null;

	/**
	 * Regular expression matching a derived module version from the
	 * &lt;rootDir&gt; path. All parenthesized subexpressions are concatenated
	 * to form the version. If this results in an emtpy string the version will
	 * the &lt;versionRegExp&gt; is used verbatim. The ultimate version string
	 * must match ResolverMojo.REGEXP_VALID_VERSION, else a build time error
	 * will occur. See http://jakarta.apache.org/regexp/apidocs/ for regular
	 * expression notes
	 * 
	 * @parameter expression="${maven.resolver.versionRegExp}" default-value=""
	 * @required
	 */
	private String versionRegExp = "";
	private RE versionRE = null;

	/**
	 * Regular expression matching recursive &lt;rootDir&gt; paths which should
	 * be excluded from module resolution processing. The standard regular
	 * expression pipe character should be used to OR expressoins together into
	 * a list. See http://jakarta.apache.org/regexp/apidocs/ for regular
	 * expression notes
	 * 
	 * @parameter expression="${maven.resolver.excludeRegExp}" default-value=""
	 */
	private String excludeRegExp = "";
	private RE excludeRE = null;

	/**
	 * Set this to 'true' to bypass module resolve
	 * 
	 * @parameter expression="${maven.resolve.skip}" default-value="false"
	 * 
	 */
	private boolean skip = false;

	/**
	 * Target directory
	 * 
	 * @parameter expression="${maven.resolver.targetDir}"
	 *            default-value="./target/resolver"
	 * 
	 */
	private File targetDir;

	/**
	 * Pointer to dummy jar file
	 */
	private File dummyJar;

	/**
	 * The Maven Project Object
	 * 
	 * @parameter expression="${project}"
	 * @required
	 * @readonly
	 */
	protected MavenProject project;

	/**
	 * The Maven Session Object
	 * 
	 * @parameter expression="${session}"
	 * @required
	 * @readonly
	 */
	protected MavenSession session;

	/**
	 * The Maven PluginManager Object
	 * 
	 * @component
	 * @required
	 */
	protected PluginManager pluginManager;

	/**
	 * Holds module references
	 */
	protected Map<String, ModuleReference> modules = new HashMap<String, ModuleReference>();

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.maven.plugin.Mojo#execute()
	 */
	public void execute() throws MojoExecutionException, MojoFailureException {

		if (!skip) {

			// validate the root directory
			if (rootDir == null || !rootDir.isDirectory())
				throw new MojoFailureException("directory [" + rootDir
						+ "] not found");

			// resolve dummyjar module jar file
			dummyJar = new File(DummyClass.class.getProtectionDomain()
					.getCodeSource().getLocation().getFile());

			// initialise the groupPrefix
			groupPrefix = model2Path(groupPrefix);

			// compile regular expressions
			groupMaskRE = compileRE("groupMaskRegExp", groupMaskRegExp);
			versionRE = compileRE("versionRegExp", versionRegExp);
			excludeRE = compileRE("excludeRegExp", excludeRegExp);

			// resolve the modules
			resolveModules(rootDir);

			// stage the modules
			for (ModuleReference module : modules.values())
				stageModules(module);

		}

	}

	/**
	 * 
	 * @param root
	 * @throws MojoExecutionException
	 */
	public void resolveModules(File root) throws MojoExecutionException {

		if (root.isDirectory()) {
			for (File child : root.listFiles()) {
				resolveModules(child);
			}
		} else {
			for (POM_TYPES suffix : POM_TYPES.values())
				if (root.getName().endsWith(POM_SEP + suffix.toString())) {

					try {
						ModuleReference moduleReference = new ModuleReference(
								root);
						modules.put(moduleReference.getId(), moduleReference);
					} catch (ModuleReferenceExcluded moduleReferenceExcluded) {
						if (getLog().isWarnEnabled())
							getLog().warn(
									"Resolved module ["
											+ root.getAbsolutePath()
											+ "] ignored becuase ["
											+ moduleReferenceExcluded
													.getMessage() + "]");
					}

					if (getLog().isInfoEnabled())
						getLog().info(
								"Resolved module [" + root.getAbsolutePath()
										+ "]");

					File parent = root.getParentFile();
					while (parent != null
							&& parent.getAbsolutePath().length() >= rootDir
									.getAbsolutePath().length()) {

						try {
							ModuleReference module = modules
									.get(new ModuleReference(parent).getModel()
											.getId());
							if (module == null) {
								module = new ModuleReference(parent);
								modules.put(module.getId(), module);
							}
							module.addDependency(root);
							if (getLog().isInfoEnabled())
								getLog().info(
										"Resolved module dependency ["
												+ root.getAbsolutePath()
												+ "] for module ["
												+ parent.getAbsolutePath()
												+ "]");
						} catch (ModuleReferenceExcluded moduleReferenceExcluded) {
							if (getLog().isWarnEnabled())
								getLog().warn(
										"Resolved module dependency ["
												+ root.getAbsolutePath()
												+ "] for module ["
												+ parent.getAbsolutePath()
												+ "] ignored becuase ["
												+ moduleReferenceExcluded
														.getMessage() + "]");
						} finally {
							root = parent;
							parent = root.getParentFile();
						}
					}
				}
		}
	}

	/**
	 * 
	 * @param moduleReference
	 * @throws MojoExecutionException
	 */
	private void stageModules(ModuleReference moduleReference)
			throws MojoExecutionException {

		Writer pomWriter = null;
		try {

			File pomFile = moduleReference.getDestinationPom();
			if (pomFile.exists())
				pomFile.delete();
			pomFile.getParentFile().mkdirs();
			pomWriter = WriterFactory.newXmlWriter(pomFile);
			new MavenXpp3Writer().write(pomWriter, moduleReference.getModel());

			if (getLog().isInfoEnabled())
				getLog().info(
						"Staged POM for module [" + moduleReference.getId()
								+ "]");

			if (moduleReference.getDestinationArtifact().exists())
				moduleReference.getDestinationArtifact().delete();

			FileUtils.copyFile(moduleReference.getSource(), moduleReference
					.getDestinationArtifact());

			if (getLog().isInfoEnabled())
				getLog().info(
						"Staged artifact for module ["
								+ moduleReference.getId() + "]");

		} catch (Exception e) {
			throw new MojoExecutionException("Error staging module file ["
					+ moduleReference.getId() + "]", e);
		} finally {
			IOUtil.close(pomWriter);
		}
	}

	/**
	 * 
	 * @param src
	 * @return
	 */
	public static String model2Path(String src) {
		if (src == null)
			return "";
		src = src.endsWith("" + POM_SEP) ? src.substring(0, src.length() - 1)
				: src;
		src = src.startsWith("" + POM_SEP) ? src.substring(1, src.length())
				: src;
		return src.replace(POM_SEP, File.separatorChar);
	}

	/**
	 * 
	 * @param src
	 * @return
	 */
	public static String path2Model(String src) {
		if (src == null)
			return "";
		src = src.endsWith(File.separator) ? src.substring(0, src.length() - 1)
				: src;
		src = src.startsWith(File.separator) ? src.substring(1, src.length())
				: src;
		return src.replace(POM_SEP, POM_SEP_ESC).replace(File.separatorChar,
				POM_SEP);
	}

	/**
	 * 
	 * @param src
	 * @return
	 */
	public static String path2Version(String src) {
		if (src == null)
			return "";
		src = src.endsWith(File.separator) ? src.substring(0, src.length() - 1)
				: src;
		src = src.startsWith(File.separator) ? src.substring(1, src.length())
				: src;
		return src.replace(File.separatorChar, VER_SEP);
	}

	public static RE compileRE(String name, String regExp)
			throws MojoFailureException {
		try {
			return (regExp == null || regExp.equals("")) ? null
					: new RE(regExp);
		} catch (RESyntaxException reSyntaxException) {
			throw new MojoFailureException(
					"Could not compile regular expression (" + name
							+ ") with value [" + regExp + "] becuase ["
							+ reSyntaxException.getMessage() + "]");
		}
	}

	/**
	 * 
	 * @param regexp
	 * @param string
	 * @return
	 */
	public static String concatParens(RE regexp, String string) {
		StringBuffer stringMasked = new StringBuffer();
		if (regexp != null) {
			if (regexp.match(string)) {
				for (int i = 1; i < regexp.getParenCount(); i++)
					stringMasked.append(regexp.getParen(i));
			}
		}
		return stringMasked.toString();
	}

	/**
	 * 
	 * Holds a reference to a maven module
	 * 
	 * @author graham@redhat.com
	 * 
	 */
	protected class ModuleReference {

		private Model mod;
		private Set<String> deps;
		private String srcAbsPath;
		private String modRelPath;

		/**
		 * 
		 * @param src
		 */
		public ModuleReference(File src) throws ModuleReferenceExcluded {
			addModel(src);
		}

		/**
		 * 
		 * @return
		 */
		protected String getId() {
			return getModel().getId();
		}

		/**
		 * 
		 * @return
		 */
		protected File getSource() {
			return new File(srcAbsPath);
		}

		/**
		 * 
		 * @return
		 */
		protected File getDestinationPom() {
			return new File(targetDir + File.separator + modRelPath + POM_SEP
					+ POM_TYPE);
		}

		/**
		 * 
		 * @return
		 * @throws ModuleReferenceExcluded
		 */
		protected File getDestinationArtifact() throws ModuleReferenceExcluded {
			return new File(targetDir + File.separator + modRelPath + POM_SEP
					+ getType(srcAbsPath));
		}

		/**
		 * 
		 * @return
		 */
		protected Model getModel() {
			return mod;
		}

		/**
		 * 
		 * @param src
		 */
		protected void addModel(File src) throws ModuleReferenceExcluded {

			if (src == null
					|| !src.getAbsolutePath().startsWith(
							rootDir.getAbsolutePath()))
				throw new RuntimeException("invalid module reference path ["
						+ src + "]");

			String dstRelPath = getRelativePath(srcAbsPath = src
					.getAbsolutePath());
			modRelPath = getGroupIdPath(dstRelPath) + File.separator
					+ getArtifactIdPath(dstRelPath) + POM_SEP_ESC
					+ getVersion(dstRelPath);

			if (src.isDirectory()) {
				srcAbsPath = dummyJar.getAbsolutePath();
			}

			deps = new HashSet<String>();

			mod = new Model();
			mod.setModelVersion("4.0.0");
			mod.setGroupId(path2Model(getGroupIdPath(dstRelPath)));
			mod.setArtifactId(path2Model(getArtifactIdPath(dstRelPath)));
			mod.setVersion(getVersion(dstRelPath));
			mod.setPackaging(getType(srcAbsPath, src.isDirectory()));
			mod.setDescription("POM was created from resolver plugin");
		}

		/**
		 * 
		 * @param src
		 */
		protected void addDependency(File src) throws ModuleReferenceExcluded {

			String srcAbsPath = src.getAbsolutePath();
			String dstRelPath = getRelativePath(srcAbsPath);
			String type = getType(srcAbsPath, src.isDirectory());
			if (!deps.contains(srcAbsPath)
					&& type.equals(POM_TYPES.jar.toString())) {
				Dependency dep = new Dependency();
				dep.setGroupId(path2Model(getGroupIdPath(dstRelPath)));
				dep.setArtifactId(path2Model(getArtifactIdPath(dstRelPath)));
				dep.setVersion(getVersion(dstRelPath));
				dep.setType(type);
				mod.addDependency(dep);
				deps.add(srcAbsPath);
			}
		}

		/**
		 * 
		 * @param src
		 * @return
		 */
		private String getRelativePath(String src)
				throws ModuleReferenceExcluded {
			return (src == null || src.equals("") || src.length() == rootDir
					.getAbsolutePath().length()) ? "" : src.substring(rootDir
					.getAbsolutePath().length() + 1);
		}

		/**
		 * 
		 * @param src
		 * @return
		 * @throws MojoExecutionException
		 */
		private String getVersion(String src) throws ModuleReferenceExcluded {

			// match version regexp
			String version = concatParens(versionRE, src);

			// default to regexp itself
			version = version.equals("") ? versionRegExp : version;

			// escape version string
			version = path2Version(version);
			
			if (version.equals("") || !version.matches(REGEXP_VALID_VERSION))
				throw new ModuleReferenceExcluded(
						"version regular expression (versionRegExp) value ["
								+ versionRegExp
								+ "] did not produce any matching parenthesized "
								+ "subexpressions for path [" + src
								+ " ] nor is a valid static version");

			return version;
		}

		/**
		 * 
		 * @param src
		 * @return
		 * @throws ModuleReferenceExcluded
		 */
		private String getType(String src, boolean useDefault)
				throws ModuleReferenceExcluded {
			return useDefault ? POM_TYPES.jar.toString() : getType(src);
		}

		/**
		 * 
		 * @param src
		 * @return
		 * @throws ModuleReferenceExcluded
		 */
		private String getType(String src) throws ModuleReferenceExcluded {

			// protect against nulls
			String type = src == null ? "" : src;

			// treat the file suffix as type
			int start = src.lastIndexOf(POM_SEP);
			type = start == -1 || start == src.length() - 1 ? POM_TYPES.jar
					.toString() : src.substring(start + 1, src.length());

			// check that the type is supported
			try {
				if (POM_TYPES.valueOf(type) == null)
					throw new IllegalArgumentException();
			} catch (IllegalArgumentException e) {
				throw new ModuleReferenceExcluded(
						"resolved module type value [" + type
								+ "] is not in the supported list "
								+ Arrays.asList(POM_TYPES.values()));
			}

			return type;
		}

		/**
		 * 
		 * @param src
		 * @return
		 * @throws MojoExecutionException
		 */
		private String getGroupIdPath(String src)
				throws ModuleReferenceExcluded {

			String groupId = src;

			// apply exclude regexp
			if (excludeRE != null && excludeRE.match(groupId))
				throw new ModuleReferenceExcluded(
						"exclude regular expression (excludeRegExp) value ["
								+ excludeRegExp + "] produced matched path ["
								+ src + "]");

			// apply group regexp mask
			if (groupMaskRE != null
					&& (groupId = concatParens(groupMaskRE, groupId))
							.equals(""))
				throw new ModuleReferenceExcluded(
						"group ID mask regular expression (groupMaskRegExp) value ["
								+ groupMaskRegExp
								+ "] did not produce any matching parenthesized "
								+ "subexpression for path [" + src + "]");

			// add group prefix
			groupId = ((groupPrefix == null || groupPrefix.equals("")) ? ""
					: (groupPrefix + File.separator))
					+ ((groupId == null || groupId.equals("") || groupId
							.equals(File.separator)) ? "" : groupId);

			// subtract artifactId
			int end = groupId.lastIndexOf(File.separatorChar);
			groupId = groupId.substring(0, end == -1 ? groupId.length() : end);

			// ensure groupId always has a value
			groupId = (groupId == null || groupId.equals("")) ? rootDir
					.getName() : groupId;

			return groupId;
		}

		/**
		 * 
		 * @param src
		 * @return
		 */
		private String getArtifactIdPath(String src)
				throws ModuleReferenceExcluded {

			// subtract groupId
			String artifactId = src == null ? "" : src.substring(src
					.lastIndexOf(File.separatorChar) + 1, src.length());

			// move up a level if no natural artifactId
			artifactId = artifactId.equals("") ? getArtifactIdPath(getGroupIdPath(src))
					: artifactId;

			return artifactId;
		}
	}

	/**
	 * 
	 * @author graham@redhat.com
	 * 
	 */
	private class ModuleReferenceExcluded extends Exception {

		/**
		 * 
		 */
		private static final long serialVersionUID = 3735423276268006956L;

		/**
		 * 
		 * @param message
		 */
		public ModuleReferenceExcluded(String message) {
			super(message);
		}

	}
}
