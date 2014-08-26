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
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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

		public ClassLoadingDetails(ClassLoader classLoader, URL url) {
			this.classLoader = classLoader;
			this.url = url;
		}
	}

	private final Map<String, ArrayList<ClassLoadingDetails>> classLoadedFrom;

	public void collect(ClassLoader classLoader) throws URISyntaxException,
			IOException {
		for (ClassLoader cl = classLoader; cl != null; cl = cl
				.getParent()) {
			if (cl instanceof URLClassLoader) {
				LOG.debug("Collecting from class loader {}", cl.getClass()
						.getName());
				LOG.debug("Collecting from class loader {} simple name", cl.getClass()
						.getSimpleName());
				LOG.debug("Collecting from class loader {} canonical", cl.getClass()
						.getCanonicalName());
				URL[] urls = ((URLClassLoader) cl).getURLs();

				for (URL url : urls) {
					ClassLoadingDetails classLoadingDetails = new ClassLoadingDetails(
							cl, url);
					File location = new File(url.getFile());
					if (location.isFile()) {
						handleJarFile(classLoadingDetails);
					} else {
						handleDirectory(classLoadingDetails);
					}
				}
			} else {
				LOG.debug("Class loader {} is not URLClassLoader", cl
						.getClass().getName());
			}
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
	}

	private void handleJarFile(ClassLoadingDetails classLoadingDetails)
			throws URISyntaxException, IOException {
		LOG.debug("Loading from the jar url {}",classLoadingDetails.url);

		try (JarFile jarFile = new JarFile(new File(
				classLoadingDetails.url.toURI()))) {
			Enumeration<JarEntry> entries = jarFile.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				addEntry(entry.getName(), classLoadingDetails);
				LOG.debug("Adding the entry {}",entry.getName());
			}
		}
	}

	private void handleDirectory(ClassLoadingDetails classLoadingDetails)
			throws URISyntaxException {
		LOG.debug("Loading from the directory url {}",classLoadingDetails.url);

		File[] entries = new File(classLoadingDetails.url.toURI()).listFiles();
		for (File entry : entries) {
			addEntry(entry.getName(), classLoadingDetails);
			LOG.debug("Adding the entry {}",entry.getName());
		}
	}

	public ClassCollector() {
		this.classLoadedFrom = new HashMap<>();
	}
}
