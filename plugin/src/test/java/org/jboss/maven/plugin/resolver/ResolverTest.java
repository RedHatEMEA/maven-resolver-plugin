package org.jboss.maven.plugin.resolver;

import java.io.File;
import java.io.FileNotFoundException;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;

/**
 * 
 * @author graham@redhat.com
 * 
 */
public class ResolverTest extends AbstractMojoTestCase {

	public void testModelMetaData2Path() throws FileNotFoundException {

		assertEquals("", ResolverMojo.model2Path(""));
		assertEquals("", ResolverMojo.model2Path("."));
		assertEquals("org", ResolverMojo.model2Path("org"));
		assertEquals("org" + File.separator + "jboss", ResolverMojo
				.model2Path("org.jboss"));
		assertEquals("org" + File.separator + "jboss", ResolverMojo
				.model2Path(".org.jboss"));
		assertEquals("org" + File.separator + "jboss", ResolverMojo
				.model2Path(".org.jboss."));
	}

	public void testPath2ModelMetaData() {

		assertEquals("", ResolverMojo.path2Model(""));
		assertEquals("", ResolverMojo.path2Model(File.separator));
		assertEquals("org", ResolverMojo.path2Model("org"));
		assertEquals("org.jboss", ResolverMojo.path2Model("org"
				+ File.separator + "jboss"));
		assertEquals("org.jboss.4_5", ResolverMojo.path2Model("org"
				+ File.separator + "jboss" + File.separator + "4.5"));
		assertEquals("org.jboss.4_5", ResolverMojo.path2Model(""
				+ File.separator + "org" + File.separator + "jboss"
				+ File.separator + "4.5"));
		assertEquals("org.jboss.4_5", ResolverMojo.path2Model("org"
				+ File.separator + "jboss" + File.separator + "4.5"
				+ File.separator + ""));

	}

}
