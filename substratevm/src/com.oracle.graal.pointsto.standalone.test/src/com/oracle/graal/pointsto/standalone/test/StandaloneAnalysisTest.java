/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisElement;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.PointsToAnalysisField;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.standalone.PointsToAnalyzer;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Shared standalone-analysis test harness.
 *
 * Subclasses configure entry points and options, execute the standalone analyzer, and query the
 * resulting analysis universe through convenience lookup and assertion helpers.
 */
public abstract class StandaloneAnalysisTest {

    private static final String ANALYSIS_ENTRY_POINTS_FILE_OPTION = "-H:AnalysisEntryPointsFile=";
    private static final String ANALYSIS_TARGET_APP_CP_OPTION = "-H:AnalysisTargetAppCP=";
    private static final String[] NO_OPTIONS = new String[0];

    private final List<String> configuredOptions = new ArrayList<>();
    private final List<Path> temporaryDirectories = new ArrayList<>();

    private PointsToAnalyzer analyzer;
    private PointsToAnalysis analysis;
    private AnalysisUniverse universe;
    private MetaAccessProvider originalMetaAccess;

    /**
     * Clears per-test configuration and resets any analysis state left from a previous run.
     */
    @Before
    public void beforeStandaloneAnalysisTest() {
        configuredOptions.clear();
        resetAnalysisState();
    }

    /**
     * Cleans analysis state and removes temporary directories created by the test.
     */
    @After
    public void afterStandaloneAnalysisTest() throws IOException {
        resetAnalysisState();
        deleteTemporaryDirectories();
        configuredOptions.clear();
    }

    /**
     * Returns default analyzer options for the current test class.
     */
    protected String[] extraOptions() {
        return NO_OPTIONS;
    }

    /**
     * Appends an analyzer option that should apply to the next run started by this test instance.
     */
    protected final void addOption(String option) {
        configuredOptions.add(option);
    }

    /**
     * Runs standalone analysis using the supplied entry class as the main entry point.
     */
    protected final void runAnalysis(Class<?> entryClass, String... extraOptions) {
        runAnalysisWithArguments(createArguments(entryClass, extraOptions));
    }

    /**
     * Runs standalone analysis using a specific entry method selected by name and parameter types.
     */
    protected final void runAnalysisMethod(Class<?> entryClass, String entryMethodName, Class<?>... parameterTypes) {
        runAnalysisMethod(entryClass, entryMethodName, parameterTypes, NO_OPTIONS);
    }

    /**
     * Runs standalone analysis using one specific reflective entry method as the root.
     */
    protected final void runAnalysisMethod(Class<?> entryClass, String entryMethodName, Class<?>[] parameterTypes, String... extraOptions) {
        Method entryMethod;
        try {
            entryMethod = entryClass.getDeclaredMethod(entryMethodName, parameterTypes);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new RuntimeException(e);
        }
        runAnalysisWithArguments(createCommonArguments(entryClass, extraOptions).toArray(String[]::new), entryMethod);
    }

    /**
     * Runs standalone analysis using an explicit entry-points file and the default target
     * application root class.
     */
    protected final void runAnalysisWithEntryPointsFile(Path entryPointsFile, String... extraOptions) {
        runAnalysisWithEntryPointsFile(getTargetApplicationRootClass(), entryPointsFile, extraOptions);
    }

    /**
     * Runs standalone analysis using an explicit entry-points file and class-path root.
     */
    protected final void runAnalysisWithEntryPointsFile(Class<?> classPathRoot, Path entryPointsFile, String... extraOptions) {
        runAnalysisWithEntryPointsFile(classPathRoot, entryPointsFile.toString(), extraOptions);
    }

    /**
     * Runs standalone analysis using an explicit entry-points resource or filesystem path and the
     * default target application root class.
     */
    protected final void runAnalysisWithEntryPointsFile(String entryPointsFile, String... extraOptions) {
        runAnalysisWithEntryPointsFile(getTargetApplicationRootClass(), entryPointsFile, extraOptions);
    }

    /**
     * Runs standalone analysis using an explicit entry-points resource or filesystem path and
     * class-path root.
     */
    protected final void runAnalysisWithEntryPointsFile(Class<?> classPathRoot, String entryPointsFile, String... extraOptions) {
        List<String> arguments = createCommonArguments(classPathRoot, extraOptions);
        arguments.add(ANALYSIS_ENTRY_POINTS_FILE_OPTION + entryPointsFile);
        runAnalysisWithArguments(arguments.toArray(String[]::new));
    }

    /**
     * Returns the class whose code source should seed the analyzed application class path.
     */
    protected Class<?> getTargetApplicationRootClass() {
        return getClass();
    }

    /**
     * Returns the analyzed universe from the most recent run.
     */
    protected final AnalysisUniverse universe() {
        assertAnalysisWasRun();
        return universe;
    }

    /**
     * Looks up an analyzed type for the supplied Java class, or {@code null} if it is absent.
     */
    protected final AnalysisType findClass(Class<?> clazz) {
        try {
            return universe().optionalLookup(originalMetaAccess().lookupJavaType(clazz));
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Looks up an analyzed method by reflective signature, or {@code null} if it is absent.
     */
    protected final AnalysisMethod findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            ResolvedJavaMethod method = originalMetaAccess().lookupJavaMethod(clazz.getDeclaredMethod(methodName, parameterTypes));
            return universe().getMethod(method);
        } catch (NoSuchMethodException | SecurityException e) {
            return null;
        }
    }

    /**
     * Returns the analyzed class initializer for the supplied type, or {@code null} if the type is
     * absent from the universe.
     */
    protected final AnalysisMethod findClassInitializer(Class<?> clazz) {
        AnalysisType type = findClass(clazz);
        return type == null ? null : type.getClassInitializer();
    }

    /**
     * Looks up an analyzed field by reflective name, or {@code null} if it is absent.
     */
    protected final AnalysisField findField(Class<?> clazz, String fieldName) {
        try {
            ResolvedJavaField field = originalMetaAccess().lookupJavaField(clazz.getDeclaredField(fieldName));
            return universe().getField(field);
        } catch (NoSuchFieldException | SecurityException e) {
            return null;
        }
    }

    /**
     * Asserts that the analyzed field state retains null as a possible value.
     */
    protected static void assertFieldCanBeNull(AnalysisField field) {
        PointsToAnalysisField pointsToField = asPointsToField(field);
        var fieldFlow = pointsToField.getSinkFlow();
        assertFalse("Flow " + fieldFlow + " should not be saturated.", fieldFlow.isSaturated());
        assertTrue("Expected field flow to allow null for: " + field, fieldFlow.getState().canBeNull());
    }

    /**
     * Asserts that the analyzed field state contains exactly the supplied concrete types.
     */
    protected final void assertFieldTypes(AnalysisField field, Class<?>... classes) {
        PointsToAnalysisField pointsToField = asPointsToField(field);
        var fieldFlow = pointsToField.getSinkFlow();
        assertFalse("Flow " + fieldFlow + " should not be saturated.", fieldFlow.isSaturated());
        assertExactMatch(fieldFlow.getState(), classes);
    }

    /**
     * Asserts that the analyzed parameter state contains exactly the supplied concrete types.
     */
    protected final void assertParameterTypes(AnalysisMethod method, int idx, Class<?>... classes) {
        assertParameterState(method, idx, typeState -> assertExactMatch(typeState, classes));
    }

    /**
     * Asserts that the analyzed parameter is effectively unused and therefore has no propagated
     * type state.
     */
    protected final void assertParameterNotAnalyzed(AnalysisMethod method, int idx) {
        PointsToAnalysisMethod pointsToMethod = asPointsToMethod(method);
        var methodFlow = pointsToMethod.getTypeFlow();
        var parameterFlow = methodFlow.getParameter(idx);
        if (parameterFlow == null || parameterFlow.getUses().isEmpty()) {
            return;
        }
        assertFalse("Flow " + parameterFlow + " should not be saturated.", methodFlow.isSaturated(analysis(), parameterFlow));
        assertTrue("Expected parameter " + idx + " of " + method + " to have an empty type state.", methodFlow.foldTypeFlow(analysis(), parameterFlow).isEmpty());
    }

    /**
     * Asserts that the analyzed return state contains exactly the supplied concrete types.
     */
    protected final void assertResultTypes(AnalysisMethod method, Class<?>... classes) {
        PointsToAnalysisMethod pointsToMethod = asPointsToMethod(method);
        var methodFlow = pointsToMethod.getTypeFlow();
        var returnFlow = methodFlow.getReturn();
        assertNotNull("Expected return flow for method " + method, returnFlow);
        assertFalse("Flow " + returnFlow + " should not be saturated.", methodFlow.isSaturated(analysis(), returnFlow));
        assertExactMatch(methodFlow.foldTypeFlow(analysis(), returnFlow), classes);
    }

    /**
     * Asserts that the invoke at the given bytecode index resolves to exactly the supplied analyzed
     * callees.
     */
    protected static void assertInvokeCallees(AnalysisMethod method, int bci, AnalysisMethod... methods) {
        var invokeFlow = findInvoke(method, bci);
        assertExactMatch(invokeFlow.getAllCallees(), methods);
    }

    /**
     * Returns the bytecode index of the only invoke in the analyzed method.
     */
    protected static int findOnlyInvokeBci(AnalysisMethod method) {
        PointsToAnalysisMethod pointsToMethod = asPointsToMethod(method);
        List<InvokeTypeFlow> invokes = pointsToMethod.getTypeFlow().getInvokes();
        assertEquals("Expected exactly one invoke in " + method, 1, invokes.size());
        return invokes.get(0).getBci();
    }

    /**
     * Asserts that the supplied analysis element is present and reachable.
     */
    protected static void assertReachable(AnalysisElement element) {
        assertNotNull("Expected analysis element to be present in the universe.", element);
        assertTrue("Expected analysis element to be reachable: " + element, element.isReachable());
    }

    /**
     * Asserts that the supplied analysis element is absent or not reachable.
     */
    protected static void assertNotReachable(AnalysisElement element) {
        if (element != null) {
            assertFalse("Expected analysis element to be unreachable: " + element, element.isReachable());
        }
    }

    /**
     * Creates and tracks a temporary directory that will be deleted in
     * {@link #afterStandaloneAnalysisTest()}.
     */
    protected final Path createTestTmpDir() {
        try {
            Path temporaryDirectory = Files.createTempDirectory("Pointsto-test-");
            temporaryDirectories.add(temporaryDirectory);
            return temporaryDirectory;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Copies a bundled test resource into the requested output path and returns that path.
     */
    protected final Path saveFileFromResource(String resourceName, Path outputFilePath) throws IOException {
        InputStream fileStream = getClass().getResourceAsStream(resourceName);
        if (fileStream == null) {
            return null;
        }

        Path outputDir = outputFilePath.getParent();
        if (outputDir != null) {
            Files.createDirectories(outputDir);
        }

        try (InputStream input = fileStream; FileOutputStream outputStream = new FileOutputStream(outputFilePath.toFile())) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int numOfRead;
            while ((numOfRead = input.read(buffer)) != -1) {
                baos.write(buffer, 0, numOfRead);
            }
            outputStream.write(baos.toByteArray());
        }
        return outputFilePath;
    }

    /**
     * Creates, runs, and stores the analyzer state for the supplied command-line arguments and
     * direct entry methods.
     */
    private void runAnalysisWithArguments(String[] arguments, Executable... entryMethods) {
        resetAnalysisState();
        analyzer = PointsToAnalyzer.createAnalyzer(arguments, entryMethods);
        try {
            int returnCode = analyzer.run();
            assertEquals("Analysis return code is expected to be 0", 0, returnCode);
        } catch (UnsupportedFeatureException e) {
            throw e;
        }

        analysis = analyzer.getResultAnalysis();
        universe = analyzer.getResultUniverse();
        assertNotNull(analysis);
        assertNotNull(universe);
        originalMetaAccess = universe.getOriginalMetaAccess();
    }

    /**
     * Builds analyzer arguments for a main-entry analysis run.
     */
    private String[] createArguments(Class<?> entryClass, String... extraOptions) {
        List<String> arguments = createCommonArguments(entryClass, extraOptions);
        arguments.add(0, entryClass.getName());
        return arguments.toArray(String[]::new);
    }

    /**
     * Builds analyzer arguments shared by main-entry and entry-points-file runs.
     */
    private List<String> createCommonArguments(Class<?> classPathRoot, String... extraOptions) {
        List<String> arguments = new ArrayList<>();
        arguments.add(ANALYSIS_TARGET_APP_CP_OPTION + getClassPath(classPathRoot));
        arguments.addAll(Arrays.asList(extraOptions()));
        arguments.addAll(configuredOptions);
        arguments.addAll(Arrays.asList(extraOptions));
        return arguments;
    }

    /**
     * Returns the class-path root for the analyzed fixture class.
     */
    private static String getClassPath(Class<?> classPathRoot) {
        return classPathRoot.getProtectionDomain().getCodeSource().getLocation().getPath();
    }

    /**
     * Returns the original meta-access provider for the current analysis run.
     */
    private MetaAccessProvider originalMetaAccess() {
        assertAnalysisWasRun();
        return originalMetaAccess;
    }

    /**
     * Returns the current standalone points-to analysis instance.
     */
    private PointsToAnalysis analysis() {
        assertAnalysisWasRun();
        return analysis;
    }

    /**
     * Verifies that a standalone analysis run has populated the cached result state.
     */
    private void assertAnalysisWasRun() {
        assertNotNull("Standalone analysis object is not available.", analysis);
        assertNotNull("Standalone analysis has not been run yet.", universe);
        assertNotNull("Standalone analysis meta access is not available.", originalMetaAccess);
    }

    /**
     * Cleans any previous analyzer instance and clears the cached analysis state.
     */
    private void resetAnalysisState() {
        if (analyzer != null) {
            analyzer.cleanUp();
        }
        analyzer = null;
        analysis = null;
        universe = null;
        originalMetaAccess = null;
    }

    /**
     * Looks up a parameter flow and delegates detailed validation of its propagated type state.
     */
    private void assertParameterState(AnalysisMethod method, int idx, java.util.function.Consumer<TypeState> parameterStateChecker) {
        PointsToAnalysisMethod pointsToMethod = asPointsToMethod(method);
        var methodFlow = pointsToMethod.getTypeFlow();
        var parameterFlow = methodFlow.getParameter(idx);
        assertNotNull("Expected parameter flow " + idx + " for method " + method, parameterFlow);
        assertFalse("Flow " + parameterFlow + " should not be saturated.", methodFlow.isSaturated(analysis(), parameterFlow));
        parameterStateChecker.accept(methodFlow.foldTypeFlow(analysis(), parameterFlow));
    }

    /**
     * Asserts that the given type state contains exactly the supplied concrete classes.
     */
    private void assertExactMatch(TypeState typeState, Class<?>... classes) {
        assertExactMatch(typeState.typesStream(analysis()).collect(Collectors.toSet()), classes);
    }

    /**
     * Asserts that the analyzed type collection matches the supplied Java classes exactly.
     */
    private void assertExactMatch(Set<AnalysisType> actualTypes, Class<?>... classes) {
        assertEquals("\n Expected types: " + asString(classes) + "\n Actual types: " + asString(actualTypes) + "\n", classes.length, actualTypes.size());
        for (Class<?> clazz : classes) {
            assertTrue(actualTypes.contains(findClass(clazz)));
        }
    }

    /**
     * Formats Java classes for assertion failure messages.
     */
    private static String asString(Class<?>... classes) {
        return Arrays.stream(classes).map(Class::getTypeName).collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Formats analyzed types for assertion failure messages.
     */
    private static String asString(Collection<AnalysisType> types) {
        return types.stream().map(type -> type == null ? "null" : type.toJavaName(true)).collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Formats analyzed methods for assertion failure messages.
     */
    private static String toString(Stream<AnalysisMethod> methods, String separator) {
        return methods.map(method -> method == null ? "null" : method.format("%H.%n(%p)")).collect(Collectors.joining(separator));
    }

    /**
     * Asserts that the actual callee set matches the expected analyzed methods exactly.
     */
    private static void assertExactMatch(Collection<AnalysisMethod> actual, AnalysisMethod... expected) {
        if (actual.size() != expected.length || !actual.containsAll(Arrays.asList(expected))) {
            String separator = "\n ";
            throw new AssertionError(String.format("Methods mismatch:%nExpect:%s%s%n !=%nActual:%s%s",
                            separator, toString(Arrays.stream(expected), separator),
                            separator, toString(actual.stream(), separator)));
        }
    }

    /**
     * Finds the invoke flow at a specific bytecode index within an analyzed method.
     */
    private static InvokeTypeFlow findInvoke(AnalysisMethod method, int bci) {
        PointsToAnalysisMethod pointsToMethod = asPointsToMethod(method);
        for (InvokeTypeFlow invokeFlow : pointsToMethod.getTypeFlow().getInvokes()) {
            if (invokeFlow.getBci() == bci) {
                return invokeFlow;
            }
        }
        fail("Did not find invoke at bci " + bci + " in " + method.getQualifiedName());
        return null;
    }

    /**
     * Narrows an analyzed method to the points-to-specific implementation used by the shared
     * assertions.
     */
    private static PointsToAnalysisMethod asPointsToMethod(AnalysisMethod method) {
        assertTrue("Expected points-to analysis method but found: " + method, method instanceof PointsToAnalysisMethod);
        return (PointsToAnalysisMethod) method;
    }

    /**
     * Narrows an analyzed field to the points-to-specific implementation used by the shared
     * assertions.
     */
    private static PointsToAnalysisField asPointsToField(AnalysisField field) {
        assertTrue("Expected points-to analysis field but found: " + field, field instanceof PointsToAnalysisField);
        return (PointsToAnalysisField) field;
    }

    /**
     * Deletes all tracked temporary directories created by the current test instance.
     */
    private void deleteTemporaryDirectories() throws IOException {
        IOException firstFailure = null;
        for (Path temporaryDirectory : temporaryDirectories) {
            try {
                Files.walkFileTree(temporaryDirectory, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                        Files.deleteIfExists(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.deleteIfExists(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        temporaryDirectories.clear();
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

}
