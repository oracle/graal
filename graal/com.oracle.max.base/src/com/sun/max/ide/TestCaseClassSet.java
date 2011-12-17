/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.max.ide;

import static com.sun.max.lang.Classes.*;

import java.lang.reflect.*;
import java.util.*;

import junit.framework.*;

import com.sun.max.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;

/**
 * A utility for defining and refining a set of {@linkplain #isJUnitTestCaseClass(Class) valid} JUnit test case classes.
 */
public class TestCaseClassSet extends LinkedHashSet<Class<? extends TestCase>> {

    private final String defaultTestSuiteName;

    /**
     * Creates a set of classes whose {@linkplain #toTestSuite() derived} test suite will have a given name.
     *
     * @param defaultTestSuiteName the default name to be used for the test suite derived from this set
     */
    public TestCaseClassSet(String defaultTestSuiteName) {
        this.defaultTestSuiteName = defaultTestSuiteName;
    }

    /**
     * Creates a set of classes by scanning a given package for {@linkplain #isJUnitTestCaseClass(Class) valid} JUnit
     * test case classes.
     *
     * @param maxPackage the package to scan for classes
     * @param scanSubPackages specifies if the sub-packages of {@code maxPackage} should also be scanned
     */
//    public TestCaseClassSet(MaxPackage maxPackage, boolean scanSubPackages) {
//        this(maxPackage.name(), scanSubPackages);
//    }
//
//    /**
//     * Creates a set of classes by scanning a given package (but not its sub-packages) for
//     * {@linkplain #isJUnitTestCaseClass(Class) valid} JUnit test case classes.
//     *
//     * @param maxPackage the package to scan for classes
//     */
//    public TestCaseClassSet(MaxPackage maxPackage) {
//        this(maxPackage, false);
//    }

    public TestCaseClassSet(Class packageRepresentative) {
        this(packageRepresentative, false);
    }

    public TestCaseClassSet(Class packageRepresentative, boolean scanSubPackages) {
        this(getPackageName(packageRepresentative), scanSubPackages);
    }

    public TestCaseClassSet(final String packageName, final boolean scanSubPackages) {
        defaultTestSuiteName = packageName;
        new ClassSearch() {
            @Override
            protected boolean visitClass(boolean isArchiveEntry, String className) {
                if (!className.endsWith("package-info")) {
                    if (scanSubPackages || (Classes.getPackageName(className).equals(packageName))) {
                        Class javaClass = Classes.forName(className, false, getClass().getClassLoader());
                        if (isJUnitTestCaseClass(javaClass)) {
                            final Class<Class<? extends TestCase>> type = null;
                            add(Utils.cast(type, javaClass));
                        }
                    }
                }
                return true;
            }
        }.run(Classpath.fromSystem(), packageName.replace('.', '/'));
    }

    public static boolean isJUnitTestCaseClass(Class javaClass) {
        return javaClass != null && !Modifier.isAbstract(javaClass.getModifiers()) &&  TestCase.class.isAssignableFrom(javaClass);
    }

    /**
     * Adds or moves a given set of classes to this set such that they will be returned after all existing entries
     * when this set is iterated over.
     *
     * @param classes the classes to add
     */
    public TestCaseClassSet addToEnd(Class... classes) {
        for (int i = 0; i < classes.length; ++i) {
            final Class c = classes[i];
            if (isJUnitTestCaseClass(c)) {
                remove(c);
                final Class<Class<? extends TestCase>> type = null;
                add(Utils.cast(type, c));
            } else {
                ProgramWarning.message("Class is not an instantiable subclass of TestCase: " + c);
            }
        }
        return this;
    }

    /**
     * Removes a given set of classes from this set.
     *
     * @param classes the classes to remove
     */
    public TestCaseClassSet removeAll(Class... classes) {
        removeAll(java.util.Arrays.asList(classes));
        return this;
    }

    /**
     * Creates a test suite containing the tests defined by the classes in this set.
     *
     * @param name the name of the suite
     * @return the created suite
     */
    public TestSuite toTestSuite(String name) {
        final TestSuite suite = new TestSuite(name);
        for (Class<? extends TestCase> testClass : this) {
            suite.addTestSuite(testClass);
        }
        return suite;
    }

    public TestSuite toTestSuite() {
        return toTestSuite(defaultTestSuiteName);
    }
}
