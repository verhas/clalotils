package com.javax0.clalotils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLClassLoader;

import org.junit.Test;

import com.javax0.clalotils.ClassCollector;



public class ClassCollectorTest {

	@Test
	public void collectClasses() throws URISyntaxException, IOException{
		new ClassCollector().analyze((URLClassLoader) this.getClass().getClassLoader());
	}
}
