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

import com.oracle.graal.pointsto.standalone.features.ClassInitializationAnalyzingFeature;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * This class is safe when loosing the side effect check.
 */
class LooseSideEffectSafe {
    static int v;

    static {
        foo();
    }

    /**
     * this method access the static field that belongs to the clinit's declaring class.
     */
    static void foo() {
        v = 1;
    }
}

class DependSafe extends LooseSideEffectSafe {
    static int v = 1;
}

class LooseSideEffectUnsafe {
    static int v;

    static {
        foo();
        System.out.println("unsafe");
    }

    static void foo() {
        v = 1;
    }
}

/**
 * This class is unsafe because it calls the native method in the class initializer. We configure
 * the actual unsafe method {@link ConfiguredSafe#foo()} as safe in the configuration file to test
 * the effective of option {@code -H:KnownSafeMethodsList=}.
 */
class ConfiguredSafe {
    static int v = 1;

    static {
        foo();
    }

    static void foo() {
        System.currentTimeMillis();
    }
}

/**
 * Test {@link ClassInitializationAnalyzingFeature}.
 */
public class ClassInitializationTimingTest {

    public static void main(String[] args) {
        System.out.println(LooseSideEffectSafe.v);
        System.out.println(DependSafe.v);
        System.out.println(LooseSideEffectUnsafe.v);
        System.out.println(ConfiguredSafe.v);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void runStandAloneTests() throws IOException {
        PointstoAnalyzerTester tester = new PointstoAnalyzerTester(this.getClass());
        Path outPutDirectory = tester.createTestTmpDir();
        Path safeMethodsFilePath = tester.saveFileFromResource("/resources/safeMethods", outPutDirectory.resolve("safeMethods").normalize());
        assertNotNull("Fail to create safeMethods file.", safeMethodsFilePath);
        tester.setAnalysisArguments(tester.getTestClassName(),
                        "-H:AnalysisTargetAppCP=" + tester.getTestClassJar(),
                        "-H:+EnableClassInitializationAnalyze",
                        "-H:PrintClassEagerInitResult=ClassInitializationTimingTest.runStandAloneTests.txt",
                        "-H:KnownSafeMethodsList=" + safeMethodsFilePath.toAbsolutePath().toString());
        Map<String, Optional<String>> result = (Map<String, Optional<String>>) tester.runAnalysisForFeatureResult(ClassInitializationAnalyzingFeature.class);
        String[] canEagerInitializedClasses = new String[]{
                        LooseSideEffectSafe.class.getName(),
                        DependSafe.class.getName(),
                        ConfiguredSafe.class.getName()// Configured to be safe
        };
        for (String canEagerInitializedClass : canEagerInitializedClasses) {
            Optional<String> state = result.get(canEagerInitializedClass);
            assertNotNull(canEagerInitializedClass + " should not be null.", state);
            assertTrue(canEagerInitializedClass + " can be eagerly initialized. " + state.orElse(""), state.isEmpty());
        }

        String[] mustDelayInitializedClasses = new String[]{
                        LooseSideEffectUnsafe.class.getName()
        };
        for (String mustDelayInitializedClass : mustDelayInitializedClasses) {
            Optional<String> state = result.get(mustDelayInitializedClass);
            assertNotNull(mustDelayInitializedClass + " should not be null.", state);
            assertTrue(mustDelayInitializedClass + " must be initialized at runtime.", result.get(mustDelayInitializedClass).isPresent());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void runSVMClassInitializationTests() {
        PointstoAnalyzerTester tester = new PointstoAnalyzerTester("com.oracle.svm.test.clinit.TestClassInitialization");
        tester.setAnalysisArguments(tester.getTestClassName(),
                        "-H:AnalysisTargetAppCP=" + tester.getTestClassJar(),
                        "-H:+EnableClassInitializationAnalyze",
                        "-H:PrintClassEagerInitResult=ClassInitializationTimingTest.runSVMClassInitializationTests.txt");
        Map<String, Optional<String>> result = (Map<String, Optional<String>>) tester.runAnalysisForFeatureResult(ClassInitializationAnalyzingFeature.class);

        String[] canEagerInitializedClasses = new String[]{
                        "com.oracle.svm.test.clinit.PureMustBeSafeEarly",
                        "com.oracle.svm.test.clinit.PureCallMustBeSafeEarly",
                        "com.oracle.svm.test.clinit.PureInterfaceMustBeSafeEarly",
                        "com.oracle.svm.test.clinit.PureSubclassInheritsDelayedInterfaceMustBeSafeEarly",
                        "com.oracle.svm.test.clinit.StaticFieldHolderMustBeSafeEarly",
                        "com.oracle.svm.test.clinit.RecursionInInitializerMustBeSafeLate",
                        "com.oracle.svm.test.clinit.EnumMustBeSafeEarly",
                        "com.oracle.svm.test.clinit.ReferencesOtherPureClassMustBeSafeEarly",
                        "com.oracle.svm.test.clinit.HelperClassMustBeSafeEarly",
                        "com.oracle.svm.test.clinit.CycleMustBeSafeLate",
                        "com.oracle.svm.test.clinit.HelperClassMustBeSafeLate",
                        "com.oracle.svm.test.clinit.ForNameMustBeSafeEarly",
                        "com.oracle.svm.test.clinit.DevirtualizedCallSuperMustBeSafeEarly",
                        "com.oracle.svm.test.clinit.DevirtualizedCallSubMustBeSafeEarly"};
        for (String canEagerInitializedClass : canEagerInitializedClasses) {
            Optional<String> state = result.get(canEagerInitializedClass);
            assertNotNull(canEagerInitializedClass + " should not be null.", state);
            assertTrue(canEagerInitializedClass + " can be eagerly initialized. " + state.orElse(""), state.isEmpty());
        }

        String[] mustDelayInitializedClasses = new String[]{
                        "com.oracle.svm.test.clinit.NonPureMustBeDelayed",
                        "com.oracle.svm.test.clinit.InitializesNonPureMustBeDelayed",
                        "com.oracle.svm.test.clinit.SystemPropReadMustBeDelayed",
                        "com.oracle.svm.test.clinit.SystemPropWriteMustBeDelayed",
                        "com.oracle.svm.test.clinit.StartsAThreadMustBeDelayed",
                        "com.oracle.svm.test.clinit.CreatesAFileMustBeDelayed",
                        "com.oracle.svm.test.clinit.CreatesAnExceptionMustBeDelayed",
                        "com.oracle.svm.test.clinit.ThrowsAnExceptionMustBeDelayed",
                        "com.oracle.svm.test.clinit.PureSubclassMustBeDelayed",
                        "com.oracle.svm.test.clinit.SuperClassMustBeDelayed",
                        "com.oracle.svm.test.clinit.InterfaceNonPureMustBeDelayed",
                        "com.oracle.svm.test.clinit.InterfaceNonPureDefaultMustBeDelayed",
                        "com.oracle.svm.test.clinit.PureSubclassInheritsDelayedDefaultInterfaceMustBeDelayed",
                        "com.oracle.svm.test.clinit.ImplicitExceptionInInitializerMustBeDelayed",
                        "com.oracle.svm.test.clinit.PureDependsOnImplicitExceptionMustBeDelayed",
                        "com.oracle.svm.test.clinit.StaticFieldModifer1MustBeDelayed",
                        "com.oracle.svm.test.clinit.StaticFieldModifer2MustBeDelayed",
                        "com.oracle.svm.test.clinit.NativeMethodMustBeDelayed",
                        // Reflection calls must be delayed in non-native image cases
                        "com.oracle.svm.test.clinit.ReflectionMustBeSafeEarly",
                        // Not safe in HotSpot
                        "com.oracle.svm.test.clinit.UnsafeAccessMustBeSafeLate",
                        "com.oracle.svm.test.clinit.ForNameMustBeDelayed",
                        "com.oracle.svm.test.clinit.DevirtualizedCallMustBeDelayed",
                        "com.oracle.svm.test.clinit.DevirtualizedCallUsageMustBeDelayed",
        };
        for (String mustDelayInitializedClass : mustDelayInitializedClasses) {
            Optional<String> state = result.get(mustDelayInitializedClass);
            assertNotNull(mustDelayInitializedClass + " should not be null.", state);
            assertTrue(mustDelayInitializedClass + " must be initialized at runtime.", result.get(mustDelayInitializedClass).isPresent());
        }
    }

}
