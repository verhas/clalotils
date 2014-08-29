/*
 * Copyright 2014 Peter Verhas.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.javax0.clalotils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Collect all the classes that can be loaded by the class loaders assuming that
 * the class loaders are instances of URL class loaders and so they can present
 * the URLs from where the classes are loaded. Note that a URL class loader
 * presents the URLs from where the classes are loaded calling the
 * {@link URLClassLoader#getURLs()}
 *
 * @author Peter Verhas
 */
public class ClassCollector {
	private static final Logger LOG = LoggerFactory
			.getLogger(ClassCollector.class);

	public static class ClassLoadingDetails {
		public final ClassLoader classLoader;
		public final URL url;
		public Class<?> klass;

		ClassLoadingDetails(ClassLoader classLoader, URL url) {
			this.classLoader = classLoader;
			this.url = url;
		}
	}

	private final Map<String, List<ClassLoadingDetails>> classLoadedFrom;
	private Map<String, List<ClassLoadingDetails>> redundantClasses;

	public ClassCollector() {
		this.classLoadedFrom = new HashMap<>();
	}

	public void analyze(ClassLoader classLoader) throws URISyntaxException,
			IOException {
		collect(classLoader);
		findRedundant();
		if (redundantClasses.size() > 0) {
			analyzeRedundant(classLoader);
		}
	}

	private void analyzeRedundant(ClassLoader classLoader) {
		for (Entry<String, List<ClassLoadingDetails>> entry : redundantClasses
				.entrySet()) {
			analyzeRedundant(classLoader, entry.getKey(), entry.getValue());
		}
	}

	private void analyzeRedundant(ClassLoader classLoader, String myClass,
			List<ClassLoadingDetails> classLoadingDeatilss) {
		for (ClassLoadingDetails classLoadingDetails : classLoadingDeatilss) {
			SpecificClassLoader cl = new SpecificClassLoader(classLoader,
					classLoadingDetails.url, myClass);
			try {
				classLoadingDetails.klass = cl.loadClass(myClass);
			} catch (ClassNotFoundException e) {
				classLoadingDetails.klass = null;
			}
		}
	}

	public void assertNoRedundantClassPath() throws RedundantClassPathException {
		if (redundantClasses.size() > 0)
			throw new RedundantClassPathException();
	}

	private void findRedundant() {
		redundantClasses = classLoadedFrom
				.entrySet()
				.stream()
				.filter(s -> s.getValue().size() > 1
						&& s.getKey().endsWith(".class"))
				.collect(Collectors.toMap(s -> s.getKey(), s -> s.getValue()));
		if (LOG.isDebugEnabled()) {
			logRedundant();
		}
	}

	private void logRedundant() {
		LOG.debug("There are {} redundantly defined classes.",
				redundantClasses.size());
		for (Entry<String, List<ClassLoadingDetails>> entry : redundantClasses
				.entrySet()) {
			LOG.debug("Class {} is defined {} times:", entry.getKey(), entry
					.getValue().size());
			for (ClassLoadingDetails cld : entry.getValue()) {
				LOG.debug("  {}:{}", cld.classLoader, cld.url);
			}
		}
	}

	private Set<URL> manifestCollectedClassPathEntries;
	private Set<URL> alreadyParsedUrls;

	private void collect(ClassLoader classLoader) throws URISyntaxException,
			IOException {
		manifestCollectedClassPathEntries = new HashSet<>();
		alreadyParsedUrls = new HashSet<>();
		for (ClassLoader cl = classLoader; cl != null; cl = cl.getParent()) {
			if (cl instanceof URLClassLoader) {
				LOG.debug("Collecting from class loader {}", cl.getClass()
						.getCanonicalName());
				URL[] urls = ((URLClassLoader) cl).getURLs();

				for (URL url : urls) {
					handle(url, cl);
				}
				for (URL url : manifestCollectedClassPathEntries) {
					handle(url, cl);
				}
			} else {
				LOG.debug("Class loader {} is not URLClassLoader", cl
						.getClass().getName());
			}
		}
	}

	private void handle(URL url, ClassLoader cl) throws URISyntaxException,
			IOException {
		if (!alreadyParsedUrls.contains(url)) {
			ClassLoadingDetails classLoadingDetails = new ClassLoadingDetails(
					cl, url);
			File location = new File(classLoadingDetails.url.getFile());
			if (location.isFile()) {
				handleJarFile(classLoadingDetails);
			} else {
				handleDirectory(classLoadingDetails);
			}
			alreadyParsedUrls.add(classLoadingDetails.url);
		}
	}

	private void addEntry(String classFileName,
			ClassLoadingDetails classLoadingDetails) {
		if (!classLoadedFrom.containsKey(classFileName)) {
			classLoadedFrom.put(classFileName,
					new ArrayList<ClassLoadingDetails>());
		}
		List<ClassLoadingDetails> urlListForClass = classLoadedFrom
				.get(classFileName);
		urlListForClass.add(classLoadingDetails);
		LOG.debug("Adding the entry {}", classFileName);
	}

	private void handleJarFile(ClassLoadingDetails classLoadingDetails)
			throws URISyntaxException, IOException {

		try (JarFile jarFile = new JarFile(new File(
				classLoadingDetails.url.toURI()))) {
			LOG.debug("Loading from the jar file {}",
					classLoadingDetails.url.getFile());
			collectManifestEntries(jarFile);
			Enumeration<JarEntry> entries = jarFile.entries();
			while (entries.hasMoreElements()) {
				addEntry(entries.nextElement().getName(), classLoadingDetails);
			}
		} catch (Exception e) {
			LOG.debug("Not loading from the non-zip file {}",
					classLoadingDetails.url.getFile());
		}
	}

	private URL toUrl(String s, JarFile jarFile) {
		URL url;
		try {
			url = new URL(s);
		} catch (MalformedURLException e) {
			LOG.error(
					"The URL {} given in the manifest file of {} can not be converted to URL",
					s, jarFile.getName());
			url = null;
		}
		return url;
	}

	private void collectManifestEntries(JarFile jarFile) throws IOException {
		Manifest manifest = jarFile.getManifest();
		if (manifest != null) {
			Attributes cpAttributes = manifest.getMainAttributes();
			if (cpAttributes != null) {
				String cp = cpAttributes.getValue("Class-Path");
				if (cp != null) {
					LOG.debug("Class path from {} is {}", jarFile.getName(), cp);
					manifestCollectedClassPathEntries.addAll(Arrays
							.asList(cp.split("\\s+")).stream()
							.map(s -> toUrl(s, jarFile))
							.collect(Collectors.toList()));
				}
			}
		}
	}

	private void handleDirectory(ClassLoadingDetails classLoadingDetails)
			throws URISyntaxException {
		LOG.debug("Loading from the directory url {}", classLoadingDetails.url);

		File[] entries = new File(classLoadingDetails.url.toURI()).listFiles();
		for (File entry : entries) {
			addEntry(entry.getName(), classLoadingDetails);
		}
	}

}
