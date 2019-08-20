/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.tck.tests;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

public class TruffleTCKFeature implements Feature {

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        try {
            return Collections.singletonList(Class.forName("com.oracle.svm.reflect.hosted.ReflectionFeature").asSubclass(Feature.class));
        } catch (ClassNotFoundException cnf) {
            throw new RuntimeException(cnf);
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        try {
            registerParameterizedRunner();
            registerJavaHostLanguageProvider();

            for (String testClass : findTCKTests()) {
                registerTCKTest(access, testClass);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void registerParameterizedRunner() throws NoSuchMethodException {
        RuntimeReflection.register(Parameterized.class.getDeclaredConstructor(Class.class));
    }

    private static void registerJavaHostLanguageProvider() throws NoSuchMethodException {
        RuntimeReflection.register(Supplier.class.getDeclaredMethod("get"));
        RuntimeReflection.register(Function.class.getDeclaredMethod("apply", Object.class));
        RuntimeReflection.register(Object.class.getDeclaredConstructor());
    }

    private static void registerTCKTest(BeforeAnalysisAccess access, String testClassFqn) {
        Class<?> testClass = access.findClassByName(testClassFqn);
        access.registerAsInHeap(testClass);
        RuntimeReflection.register(testClass);
        for (Constructor<?> constructor : testClass.getDeclaredConstructors()) {
            RuntimeReflection.register(constructor);
        }
        for (Method method : testClass.getDeclaredMethods()) {
            if (isJUnitEntryPoint(method)) {
                RuntimeReflection.register(method);
            }
        }
    }

    private static boolean isJUnitEntryPoint(Method method) {
        return method.isAnnotationPresent(After.class) || method.isAnnotationPresent(AfterClass.class) || method.isAnnotationPresent(Before.class) || method.isAnnotationPresent(BeforeClass.class) ||
                        method.isAnnotationPresent(Parameters.class) || method.isAnnotationPresent(Test.class);
    }

    private static Collection<String> findTCKTests() {
        Set<File> todo = new HashSet<>();
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (!(contextClassLoader instanceof URLClassLoader)) {
            throw new IllegalStateException("Context ClassLoader must be URLClassLoader");
        }
        for (URL url : ((URLClassLoader) contextClassLoader).getURLs()) {
            try {
                final File file = new File(url.toURI());
                todo.add(file);
            } catch (URISyntaxException | IllegalArgumentException e) {
                throw new IllegalStateException(String.format("Unable to handle image class path element %s.", url));
            }
        }
        Collection<String> result = new ArrayList<>();
        for (File cpEntry : todo) {
            if (cpEntry.isDirectory()) {
                result.addAll(findTCKTestsInDirectory(cpEntry));
            } else {
                result.addAll(findTCKTestsInArchive(cpEntry));
            }
        }
        return result;
    }

    private static Collection<String> findTCKTestsInDirectory(File root) {
        String pkgFqn = TruffleTCKFeature.class.getPackage().getName();
        String folderResourceName = pkgFqn.replace('.', File.separatorChar) + File.separatorChar;
        File folder = new File(root, folderResourceName);
        String[] names = folder.list();
        if (names == null) {
            return Collections.emptySet();
        }
        Collection<String> result = new ArrayList<>();
        for (String name : names) {
            if (name.endsWith("Test.class")) {
                String className = pkgFqn + '.' + name.substring(0, name.length() - 6);
                result.add(className);
            }
        }
        return result;
    }

    private static Collection<String> findTCKTestsInArchive(File archive) {
        String folderResourceName = TruffleTCKFeature.class.getPackage().getName().replace('.', '/') + '/';
        Collection<String> result = new ArrayList<>();
        try (JarFile jf = new JarFile(archive)) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.startsWith(folderResourceName) && entryName.endsWith("Test.class")) {
                    String className = entryName.substring(0, entryName.length() - 6).replace('/', '.');
                    result.add(className);
                }
            }
        } catch (IOException ioe) {
            // Ignore non existent image classpath entry
        }
        return result;
    }
}
