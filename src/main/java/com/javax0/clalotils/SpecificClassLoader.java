package com.javax0.clalotils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Load a class from a specific URL file.
 * 
 * @author Peter Verhas
 *
 */
public class SpecificClassLoader extends ClassLoader {
	private static final Logger LOG = LoggerFactory
			.getLogger(SpecificClassLoader.class);
	private final URL url;
	private final String myClass;
	private final String myClassName;

	public SpecificClassLoader(ClassLoader parent, URL url, String myClass) {
		super(parent);
		this.url = url;
		this.myClass = myClass;
		myClassName = myClass.replaceAll("/", ".").replaceAll("\\\\", ".")
				.replaceAll(".class$", "");
	}

	private Class<?> getMyClass() throws ClassNotFoundException,
			URISyntaxException {
		File location = new File(url.getFile());
		final byte[] b;
		if (location.isFile()) {
			File jarFileFile = new File(url.toURI());
			try (JarFile jarFile = new JarFile(jarFileFile)) {
				JarEntry entry = jarFile.getJarEntry(myClass);
				b = new byte[(int) entry.getSize()];
				try (InputStream is = jarFile.getInputStream(entry)) {
					is.read(b);
				}
			} catch (IOException e) {
				LOG.error("Clould not read the class {} from jar {}", myClass,
						jarFileFile.getAbsoluteFile());
				return null;
			}
		} else {
			File classFile = new File(url.getFile() + File.separator + myClass);
			b = new byte[(int) classFile.length()];
			try (FileInputStream is = new FileInputStream(classFile)) {
				is.read(b);
			} catch (IOException e) {
				LOG.error("Clould not read the class file {}",
						classFile.getAbsoluteFile());
				return null;
			}
		}

		Class<?> c = defineClass(myClassName, b, 0, b.length);
		resolveClass(c);
		return c;
	}

	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		if (name.equals(myClass)) {
			try {
				return getMyClass();
			} catch (URISyntaxException e) {
				throw new ClassNotFoundException(
						"Class not found for some reason.", e);
			}
		}
		return super.loadClass(name);
	}
}
