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
import com.oracle.graal.pointsto.meta.AnalysisElement;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.standalone.PointsToAnalyzer;
import com.oracle.graal.pointsto.util.AnalysisError;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.nativeimage.hosted.Feature;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class PointstoAnalyzerTester {
    private Set<Executable> expectedReachableMethods = new HashSet<>();
    private Set<Class<?>> expectedReachableTypes = new HashSet<>();
    private Set<Field> expectedReachableFields = new HashSet<>();
    private Set<Executable> expectedUnreachableMethods = new HashSet<>();
    private Set<Class<?>> expectedUnreachableTypes = new HashSet<>();
    private Set<Field> expectedUnreachableFields = new HashSet<>();
    private Set<Class<?>> expectedReachableClinits = new HashSet<>();
    private Set<Class<?>> expectedUnreachableClinits = new HashSet<>();
    private String[] arguments;
    private Path tmpDir;
    private String testClassName;
    private String testClassJar;
    private Class<?> testClass;

    public PointstoAnalyzerTester(Class<?> testClass) {
        this.testClass = testClass;
        testClassJar = testClass.getProtectionDomain().getCodeSource().getLocation().getPath();
        testClassName = testClass.getName();
    }

    public PointstoAnalyzerTester(String testClassName) {
        try {
            testClass = Class.forName(testClassName);
            testClassJar = testClass.getProtectionDomain().getCodeSource().getLocation().getPath();
            this.testClassName = testClassName;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public Class<?> getTestClass() {
        return testClass;
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

    public void setExpectedReachableMethods(Executable... methods) {
        expectedReachableMethods.addAll(Arrays.asList(methods));
    }

    public void setExpectedUnreachableMethods(Executable... methods) {
        expectedUnreachableMethods.addAll(Arrays.asList(methods));
    }

    public void setExpectedReachableTypes(Class<?>... types) {
        expectedReachableTypes.addAll(Arrays.asList(types));
    }

    public void setExpectedUnreachableTypes(Class<?>... types) {
        expectedUnreachableTypes.addAll(Arrays.asList(types));
    }

    public void setExpectedReachableClinits(Class<?>... types) {
        expectedReachableClinits.addAll(Arrays.asList(types));
    }

    public void setExpectedUnreachableClinits(Class<?>... types) {
        expectedUnreachableClinits.addAll(Arrays.asList(types));
    }

    public void setExpectedReachableFields(Field... fields) {
        expectedReachableFields.addAll(Arrays.asList(fields));
    }

    public void setExpectedUnreachableFields(Field... fields) {
        expectedUnreachableFields.addAll(Arrays.asList(fields));
    }

    public void runAnalysisAndAssert() {
        runAnalysisAndAssert(true);
    }

    /**
     * Run the analysis and check the results.
     *
     * @param expectPass true if the analysis is expected to be successfully finished, false if the
     *            analysis is supposed to fail.
     */
    public void runAnalysisAndAssert(boolean expectPass) {
        PointsToAnalyzer pointstoAnalyzer = PointsToAnalyzer.createAnalyzer(arguments);
        UnsupportedFeatureException unsupportedFeatureException = null;
        try {
            try {
                int ret = pointstoAnalyzer.run();
                assertEquals("Analysis return code is expected to 0", 0, ret);
            } catch (UnsupportedFeatureException e) {
                unsupportedFeatureException = e;
            }
            ClassLoader analysisClassLoader = pointstoAnalyzer.getClassLoader();
            if (!expectPass) {
                return;
            }
            final AnalysisUniverse universe = pointstoAnalyzer.getResultUniverse();
            final MetaAccessProvider originalMetaAccess = universe.getOriginalMetaAccess();
            assertNotNull(universe);

            assertReachable("Method", expectedReachableMethods, expectedUnreachableMethods, reflectionMethod -> {
                try {
                    Executable actualMethod = reflectionMethod;
                    Class<?> declaringClass = reflectionMethod.getDeclaringClass();
                    if (!analysisClassLoader.equals(declaringClass.getClassLoader())) {
                        Class<?> c = Class.forName(declaringClass.getName(), false, analysisClassLoader);
                        actualMethod = c.getDeclaredMethod(reflectionMethod.getName(), reflectionMethod.getParameterTypes());
                    }
                    ResolvedJavaMethod m = universe.getOriginalMetaAccess().lookupJavaMethod(actualMethod);
                    return universe.getMethod(m);
                } catch (ReflectiveOperationException e) {
                    throw AnalysisError.shouldNotReachHere(e);
                }
            },
                            reflectionMethod -> reflectionMethod.getDeclaringClass().getName() + "." + reflectionMethod.getName());
            assertReachable("<clinit>", expectedReachableClinits, expectedUnreachableClinits, clazz -> {
                AnalysisType t = classToAnalysisType(analysisClassLoader, universe, originalMetaAccess, clazz);
                return t.getClassInitializer();
            }, clazz -> clazz.getName() + ".<clinit>");
            assertReachable("Type", expectedReachableTypes, expectedUnreachableTypes, clazz -> classToAnalysisType(analysisClassLoader, universe, originalMetaAccess, clazz),
                            clazz -> clazz.getName());
            assertReachable("Field", expectedReachableFields, expectedUnreachableFields, reflectionField -> {
                try {
                    Field actualField = reflectionField;
                    Class<?> declaringClass = actualField.getDeclaringClass();
                    if (!analysisClassLoader.equals(declaringClass.getClassLoader())) {
                        Class<?> c = Class.forName(declaringClass.getName(), false, analysisClassLoader);
                        actualField = c.getDeclaredField(reflectionField.getName());
                    }
                    ResolvedJavaField resolvedJavaField = originalMetaAccess.lookupJavaField(actualField);
                    return universe.getField(resolvedJavaField);
                } catch (ReflectiveOperationException e) {
                    throw AnalysisError.shouldNotReachHere(e);
                }
            },
                            reflectionField -> reflectionField.getDeclaringClass().getName() + "." + reflectionField.getName());

            if (unsupportedFeatureException != null) {
                throw unsupportedFeatureException;
            }
        } finally {
            if (pointstoAnalyzer != null) {
                pointstoAnalyzer.cleanUp();
            }
        }
    }

    private static AnalysisType classToAnalysisType(ClassLoader analysisClassLoader, AnalysisUniverse universe, MetaAccessProvider originalMetaAccess, Class<?> clazz) {
        try {
            Class<?> actualClass = clazz;
            if (!analysisClassLoader.equals(clazz.getClassLoader())) {
                actualClass = Class.forName(clazz.getName(), false, analysisClassLoader);
            }
            ResolvedJavaType resolvedJavaType = originalMetaAccess.lookupJavaType(actualClass);
            return universe.optionalLookup(resolvedJavaType);
        } catch (ReflectiveOperationException e) {
            throw AnalysisError.shouldNotReachHere(e);
        }
    }

    public Object runAnalysisForFeatureResult(Class<? extends Feature> feature) {
        PointsToAnalyzer pointstoAnalyzer = runAnalysis(true);
        return pointstoAnalyzer.getResultFromFeature(feature);
    }

    private PointsToAnalyzer runAnalysis(boolean expectPass) {
        PointsToAnalyzer pointstoAnalyzer = PointsToAnalyzer.createAnalyzer(arguments);
        try {
            int ret = pointstoAnalyzer.run();
            if (expectPass) {
                assertEquals("Analysis return code is expected to 0", 0, ret);
            } else {
                assertNotEquals("The analysis is expected to fail, but succeeded", 0, ret);
            }
        } catch (UnsupportedFeatureException e) {
            throw e;
        }
        return pointstoAnalyzer;
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

    private static <T, R extends AnalysisElement> void assertReachable(String elementType, Set<T> expectedReachables, Set<T> expectedUnreachables,
                    Function<T, R> getAnalysisElement, Function<T, String> getName) {
        if (expectedReachables != null && !expectedReachables.isEmpty()) {
            // Find all the elements that should be reachable but not
            List<T> shouldReachableButNot = expectedReachables.stream().filter(t -> {
                R element = getAnalysisElement.apply(t);
                return !element.isReachable();
            }).collect(Collectors.toList());

            if (!shouldReachableButNot.isEmpty()) {
                StringBuilder sb = new StringBuilder(elementType + " should be reached but not:");
                shouldReachableButNot.forEach(s -> sb.append(" ").append(getName.apply(s)).append("\n"));
                fail(sb.toString());
            }
        }

        if (expectedUnreachables != null && !expectedUnreachables.isEmpty()) {
            for (T expectedUnreachable : expectedUnreachables) {
                R element = getAnalysisElement.apply(expectedUnreachable);
                // element can be null as the unreachable type is not in the universe
                if (element != null) {
                    assertFalse(elementType + " " + getName.apply(expectedUnreachable) + " should not be reachable.", element.isReachable());
                }
            }
        }
    }
}
