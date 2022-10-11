/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.oracle.graal.pointsto.standalone.test;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.standalone.PointsToAnalyzer;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PointstoAnalyzerTester {
    private Set<String> expectedReachableMethods = new HashSet<>();
    private Set<String> expectedReachableTypes = new HashSet<>();
    private Set<String> expectedReachableFields = new HashSet<>();
    private Set<String> expectedUnreachableMethods = new HashSet<>();
    private Set<String> expectedUnreachableTypes = new HashSet<>();
    private Set<String> expectedUnreachableFields = new HashSet<>();
    private String[] arguments;
    private Path tmpDir;
    private String testClassName;
    private String testClassJar;

    public PointstoAnalyzerTester(Class<?> testClass) {
        testClassJar = testClass.getProtectionDomain().getCodeSource().getLocation().getPath();
        testClassName = testClass.getName();
    }

    public String getTestClassName() {
        return testClassName;
    }

    public String getTestClassJar() {
        return testClassJar;
    }

    public void setAnalysisArguments(String... arguments) {
        this.arguments = arguments;
    }

    public void setExpectedReachableMethods(String... methodNames) {
        expectedReachableMethods = new HashSet<>();
        for (String methodName : methodNames) {
            expectedReachableMethods.add(methodName);
        }
    }

    public void setExpectedUnreachableMethods(String... methodNames) {
        expectedUnreachableMethods = new HashSet<>();
        for (String methodName : methodNames) {
            expectedUnreachableMethods.add(methodName);
        }
    }

    public void setExpectedReachableTypes(String... typeNames) {
        expectedReachableTypes = new HashSet<>();
        for (String typeName : typeNames) {
            expectedReachableTypes.add(typeName);
        }
    }

    public void setExpectedUnreachableTypes(String... typeNames) {
        expectedUnreachableTypes = new HashSet<>();
        for (String typeName : typeNames) {
            expectedUnreachableTypes.add(typeName);
        }
    }

    public void setExpectedReachableFields(String... fieldNames) {
        expectedReachableFields = new HashSet<>();
        for (String fieldName : fieldNames) {
            expectedReachableFields.add(fieldName);
        }
    }

    public void setExpectedUnreachableFields(String... fieldNames) {
        expectedUnreachableFields = new HashSet<>();
        for (String fieldName : fieldNames) {
            expectedUnreachableFields.add(fieldName);
        }
    }

    public void runAnalysisAndAssert() {
        PointsToAnalyzer pointstoAnalyzer = PointsToAnalyzer.createAnalyzer(arguments);
        UnsupportedFeatureException unsupportedFeatureException = null;
        try {
            try {
                int ret = pointstoAnalyzer.run();
                assertEquals("Analysis return code is expected to 0", 0, ret);
            } catch (UnsupportedFeatureException e) {
                unsupportedFeatureException = e;
            }
            AnalysisUniverse universe = pointstoAnalyzer.getResultUniverse();
            assertNotNull(universe);

            elementsIteration("Method", expectedReachableMethods, expectedUnreachableMethods, universe.getMethods(), m -> m.getQualifiedName(), m -> m.getImplementations().length >= 1);
            elementsIteration("Type", expectedReachableTypes, expectedUnreachableTypes, universe.getTypes(), t -> t.getJavaClass().getName(), t -> t.isReachable());
            elementsIteration("Field", expectedReachableFields, expectedUnreachableFields, universe.getFields(), f -> f.getDeclaringClass().getJavaClass().getName() + "." + f.getName(),
                            f -> f.isAccessed() || f.isRead() || f.isWritten());

            if (unsupportedFeatureException != null) {
                throw unsupportedFeatureException;
            }
        } finally {
            if (pointstoAnalyzer != null) {
                pointstoAnalyzer.cleanUp();
            }
        }
    }

    public Path createTestTmpDir() {
        try {
            tmpDir = Files.createTempDirectory("Pointsto-test-");
        } catch (IOException e) {
            e.printStackTrace();
            tmpDir = null;
        }
        return tmpDir;
    }

    public Path saveFileFromResource(String resourceName, Path outputFilePath) throws IOException {
        InputStream fileStream = this.getClass().getResourceAsStream(resourceName);
        if (fileStream == null) {
            return null;
        }

        Path outputDir = outputFilePath.getParent();
        Files.createDirectories(outputDir);

        try (FileOutputStream outputStream = new FileOutputStream(outputFilePath.toFile());) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int numOfRead;
            while ((numOfRead = fileStream.read(buffer)) != -1) {
                baos.write(buffer, 0, numOfRead);
            }
            outputStream.write(baos.toByteArray());
        }
        return outputFilePath;
    }

    public void deleteTestTmpDir() throws IOException {
        Files.walkFileTree(tmpDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static <T> void elementsIteration(String elementType, Set<String> expectedReachables, Set<String> expectedUnreachables, Collection<T> elements,
                    Function<T, String> getElementName, Predicate<T> reachablePredicate) {
        if (expectedReachables != null || expectedUnreachables != null) {
            for (T element : elements) {
                String name = getElementName.apply(element);
                if (expectedReachables != null && expectedReachables.remove(name)) {
                    assertTrue(elementType + " " + name + " should be reachable.", reachablePredicate.test(element));
                    continue;
                }

                if (expectedUnreachables != null && expectedUnreachables.remove(name)) {
                    assertFalse(elementType + " " + name + " should not be reachable.", reachablePredicate.test(element));
                }
            }
        }
        if (expectedReachables != null && !expectedReachables.isEmpty()) {
            StringBuilder sb = new StringBuilder(elementType + " should be reached but not:");
            expectedReachables.forEach(s -> sb.append(" ").append(s));
            assertTrue(sb.toString(), expectedReachables.isEmpty());
        }
    }
}
