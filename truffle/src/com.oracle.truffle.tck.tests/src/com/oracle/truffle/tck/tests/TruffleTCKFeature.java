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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    @SuppressWarnings("try")
    private static Collection<String> findTCKTests() {
        try {
            URL resource = TruffleTCKFeature.class.getResource("TruffleTCKFeature.class");
            try (FileSystem fileSystem = "jar".equals(resource.getProtocol()) ? FileSystems.newFileSystem(resource.toURI(), Map.of()) : null) {
                Path path = Path.of(resource.toURI());
                try (Stream<Path> siblingResources = Files.list(path.getParent())) {
                    String packageName = TruffleTCKFeature.class.getPackageName();
                    String suffix = ".class";
                    return siblingResources.map(Path::getFileName).map(Path::toString).filter(name -> name.endsWith("Test" + suffix)).map(
                                    name -> packageName + '.' + name.substring(0, name.length() - suffix.length())).collect(Collectors.toList());
                }
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot access TruffleTCKFeature class as resource");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot list TruffleTCKFeature class sibling resources");
        }
    }
}
