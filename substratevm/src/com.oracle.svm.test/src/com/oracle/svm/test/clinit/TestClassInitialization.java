/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.clinit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.hosted.classinitialization.ClassInitializationOptions;
import com.oracle.svm.hosted.fieldfolding.IsStaticFinalFieldInitializedNode;

import jdk.internal.misc.Unsafe;

/*
 * The suffix of class names indicates the time when the class initializer is determined to be safe
 * for execution/simulation at image build time. The tests are run in two configurations: 1) with
 * the "old" class initialization strategy, which uses the "early" and "late" class initializer
 * analysis to initialize classes at image build time, and 2) with the "new" class initialization
 * strategy where all classes can be used at image build time, but class initializer are simulated
 * to avoid explicit initialization at image build time. This leads to the following 4 suffixes:
 *
 * - MustBeSafeEarly: The early class initializer analysis (before static analysis) finds this class
 * as safe for initialization at image build time. The simulation of class initializer also must
 * succeed, because it is more powerful than the early analysis.
 *
 * - MustBeSafeLate: The late class initializer analysis (ater static analysis) finds this class as
 * safe for initialization at image build time. The * simulation of class initializer also must
 * succeed, because it is more powerful than the late analysis.
 *
 * - MustBeSimulated: Neither the early nor the late analysis finds this class as safe for
 * initialization. But the simulation of class initializer succeeded, i.e., with the "new" class
 * initialization strategy the class starts out as initialized at run time.
 *
 * - MustBeDelayed: The class initializer has side effects, it must be executed at run time.
 *
 * The suffixes are checked in the feature at build time (code in this file), at run time in the
 * main method (code in this file), and by an external script that parses the class initialization
 * log output (code in mx_substratevm.py).
 */

class PureMustBeSafeEarly {
    static int v;
    static {
        v = 1;
        v = 42;
    }
}

class InitializesPureMustBeDelayed {
    static int v;
    static {
        v = PureMustBeSafeEarly.v;
    }
}

/**
 * The class initializer of this type is actually never executed because static final int fields
 * assigned directly at the field definition site are already constant folded by javac at all usage
 * sites.
 */
class NonPureAccessedFinal {
    static final int v = 1;
    static {
        if (alwaysTrue()) {
            throw new RuntimeException("Must not be called at runtime or compile time.");
        }
    }

    static boolean alwaysTrue() {
        return true;
    }
}

class PureCallMustBeSafeEarly {
    static int v;
    static {
        v = TestClassInitialization.pure();
    }
}

class NonPureMustBeDelayed {
    static int v = 1;
    static {
        System.out.println("Delaying NonPureMustBeDelayed");
    }
}

class InitializesNonPureMustBeDelayed {
    static int v = NonPureMustBeDelayed.v;
}

class SystemPropReadMustBeDelayed {
    static int v = 1;
    static {
        System.getProperty("test");
    }
}

class SystemPropWriteMustBeDelayed {
    static int v = 1;
    static {
        System.setProperty("test", "");
    }
}

class StartsAThreadMustBeDelayed {
    static int v = 1;
    static {
        new Thread().start();
    }
}

class CreatesAnExceptionMustBeDelayed {
    static Exception e;
    static {
        e = new Exception("should fire at runtime");
    }
}

class ThrowsAnExceptionUninitializedMustBeDelayed {
    static int v = 1;
    static {
        if (PureMustBeSafeEarly.v == 42) {
            throw new RuntimeException("should fire at runtime");
        }
    }
}

interface PureInterfaceMustBeSafeEarly {
}

class PureSubclassMustBeDelayed extends SuperClassMustBeDelayed {
    static int v = 1;
}

class SuperClassMustBeDelayed implements PureInterfaceMustBeSafeEarly {
    static {
        System.out.println("Delaying SuperClassMustBeDelayed");
    }
}

interface InterfaceNonPureMustBeDelayed {
    int v = B.v;

    class B {
        static int v = 1;
        static {
            System.out.println("Delaying InterfaceNonPureMustBeDelayed");
        }
    }
}

interface InterfaceNonPureDefaultMustBeDelayed {
    int v = B.v;

    class B {
        static int v = 1;
        static {
            System.out.println("Delaying InterfaceNonPureDefaultMustBeDelayed");
        }
    }

    default int m() {
        return v;
    }
}

class PureSubclassInheritsDelayedInterfaceMustBeSafeEarly implements InterfaceNonPureMustBeDelayed {
    static int v = 1;
}

class PureSubclassInheritsDelayedDefaultInterfaceMustBeDelayed implements InterfaceNonPureDefaultMustBeDelayed {
    static int v = 1;
}

class ImplicitExceptionInInitializerUninitializedMustBeDelayed {

    static int a = 10;
    static int b = 0;
    static int res;

    static {
        res = a / b;
    }
}

class PureDependsOnImplicitExceptionUninitializedMustBeDelayed {

    static int a;

    static {
        a = ImplicitExceptionInInitializerUninitializedMustBeDelayed.res;
    }
}

class StaticFieldHolderMustBeSafeEarly {
    /**
     * Other class initializers that modify {@link #a} must not run at image build time so that the
     * initial value 111 assigned here can be read at run time.
     */
    static int a = 111;

    static void setA(int value) {
        a = value;
    }
}

class StaticFieldModifer1MustBeDelayed {
    static {
        StaticFieldHolderMustBeSafeEarly.a = 222;
    }

    static void triggerInitialization() {
    }
}

class StaticFieldModifer2MustBeDelayed {
    static {
        StaticFieldHolderMustBeSafeEarly.setA(333);
    }

    static void triggerInitialization() {
    }
}

class RecursionInInitializerMustBeSafeLate {
    static int i = compute(200);

    static int compute(int n) {
        if (n <= 1) {
            return 1;
        } else {
            return n + compute(n - 1);
        }
    }
}

class UnsafeAccessMustBeSafeLate {
    static UnsafeAccessMustBeSafeLate value = compute();

    int f01;
    int f02;
    int f03;
    int f04;
    int f05;
    int f06;
    int f07;
    int f08;
    int f09;
    int f10;
    int f11;
    int f12;
    int f13;
    int f14;
    int f15;
    int f16;

    static UnsafeAccessMustBeSafeLate compute() {
        UnsafeAccessMustBeSafeLate result = new UnsafeAccessMustBeSafeLate();
        /*
         * We are writing a random instance field, depending on the header size. But the object is
         * big enough so that the write is one of the fields. The unsafe write is converted to a
         * proper store field node because the offset is constant, so in the static analysis graph
         * there is no unsafe access node.
         */
        Unsafe.getUnsafe().putInt(result, 32L, 1234);
        return result;
    }
}

enum EnumMustBeSafeEarly {
    V1(null),
    V2("Hello"),
    V3(new Object());

    final Object value;

    EnumMustBeSafeEarly(Object value) {
        this.value = value;
    }

    Object getValue() {
        /*
         * Use an assertion, so that the static final field that stores the assertion status is
         * filled in the class initializer. We want to test that using assertions does not impact
         * the class initialization analysis.
         */
        assert value != null;
        return value;
    }
}

class NativeMethodMustBeDelayed {
    static int i = compute();

    static int compute() {
        try {
            nativeMethod();
        } catch (LinkageError ignored) {
            /* Expected since the native method is not implemented. */
        }
        return 42;
    }

    static native void nativeMethod();

    static void foo() {
        /*
         * Even when a class is initialized at run time, the check whether assertions are included
         * must be constant folded at image build time. Otherwise we have a performance problem.
         */
        assert assertionOnlyCode();
    }

    static boolean assertionOnlyCode() {
        AssertionOnlyClassMustBeUnreachable.reference();
        return false;
    }
}

class AssertionOnlyClassMustBeUnreachable {
    static void reference() {
    }
}

/**
 * Class initializer references a helper class that can be initialized early. Since the early class
 * initializer analysis recursviely processes dependent classes, this class is also safe for early
 * initialization.
 */
class ReferencesOtherPureClassMustBeSafeEarly {
    static {
        HelperClassMustBeSafeEarly.foo();
    }

    static void foo() {
    }
}

class HelperClassMustBeSafeEarly {
    static void foo() {
    }
}

/**
 * Cycle between this class and a helper class. Even though both classes could be initialized early,
 * the early analysis bails out because analyzing cycles would be too complicated.
 */
class CycleMustBeSafeLate {
    static {
        HelperClassMustBeSafeLate.foo();
    }

    static void foo() {
    }
}

class HelperClassMustBeSafeLate {
    static {
        CycleMustBeSafeLate.foo();
    }

    static void foo() {
    }
}

/** Various reflection lookup methods are safe for execution at image build time. */
class ReflectionMustBeSafeEarly {
    static Class<?> c1;
    static Class<?> c2;
    static Method m1;
    static Field f2;

    static {
        try {
            Class<?> c1Local = Class.forName("com.oracle.svm.test.clinit.ForNameMustBeSafeEarly", true, ReflectionMustBeSafeEarly.class.getClassLoader());
            c1 = c1Local;

            /**
             * Looking up a class that cannot be initialized at build time is allowed, as long as
             * `initialize` is `false`.
             */
            Class<?> c2Local = Class.forName("com.oracle.svm.test.clinit.ForNameUninitializedMustBeDelayed", false, ReflectionMustBeSafeEarly.class.getClassLoader());
            c2 = c2Local;

            /*
             * Calling getDeclaredMethod on the field c1 instead of the variable c1Local would not
             * work, because the field load cannot be constant folded by the
             * EarlyClassInitializerAnalysis.
             */
            m1 = c1Local.getDeclaredMethod("foo", int.class);
            f2 = c2Local.getDeclaredField("field");

            /*
             * Check that reflective class lookup and the elimination of the class initialization
             * check also works when the class name is not constant yet during bytecode parsing.
             */
            if (c1Local != Class.forName(forNameMustBeSafeEarly(), true, ReflectionMustBeSafeEarly.class.getClassLoader())) {
                throw new Error("wrong class");
            }

        } catch (ReflectiveOperationException ex) {
            throw new Error(ex);
        }
    }

    private static String forNameMustBeSafeEarly() {
        return "com.oracle.svm.test.clinit.ForNameMustBeSafeEarly";
    }
}

@SuppressWarnings("unused")
class ForNameMustBeSafeEarly {
    static void foo(int arg) {
    }
}

class ForNameUninitializedMustBeDelayed {
    static {
        System.out.println("Delaying ForNameUninitializedMustBeDelayed");
    }

    int field;
}

class DevirtualizedCallMustBeDelayed {
    static {
        System.out.println("Delaying DevirtualizedCallMustBeDelayed");
    }

    static final Object value = 42;
}

class DevirtualizedCallSuperMustBeSafeEarly {
    Object foo() {
        return -1;
    }
}

class DevirtualizedCallSubMustBeSafeEarly extends DevirtualizedCallSuperMustBeSafeEarly {
    @Override
    Object foo() {
        return DevirtualizedCallMustBeDelayed.value;
    }
}

class DevirtualizedCallUsageMustBeDelayed {
    static final Object value = computeValue();

    private static Object computeValue() {
        DevirtualizedCallSuperMustBeSafeEarly provider = createProvider();

        /*
         * The static analysis can prove that DevirtualizedCallSubMustBeDelayed.foo is the only
         * callee and de-virtualize this call. So the original target method of the call site and
         * the actually invoked method are different - and the analysis that automatically
         * initializes classes must properly pick up this dependency.
         */
        return provider.foo();
    }

    private static DevirtualizedCallSuperMustBeSafeEarly createProvider() {
        return new DevirtualizedCallSubMustBeSafeEarly();
    }
}

class LargeAllocation1MustBeDelayed {
    static final Object value = computeValue();

    private static Object computeValue() {
        return new Object[200_000];
    }
}

class LargeAllocation2MustBeDelayed {
    static final Object value = computeValue();

    private static Object computeValue() {
        return new int[1][200_000];
    }
}

enum ComplexEnumMustBeSimulated {
    V1 {
        @Override
        int virtualMethod() {
            return 41;
        }
    },
    V2 {
        @Override
        int virtualMethod() {
            return 42;
        }
    };

    abstract int virtualMethod();

    static final Map<String, ComplexEnumMustBeSimulated> lookup;

    static {
        lookup = new HashMap<>();
        for (var v : values()) {
            lookup.put(v.name(), v);
        }
    }
}

class StaticFinalFieldFoldingMustBeSafeEarly {

    Object f1;
    Object f2;
    Object f3;

    StaticFinalFieldFoldingMustBeSafeEarly() {
        this.f1 = F1;
        this.f2 = F2;
        this.f3 = F3;
    }

    static final StaticFinalFieldFoldingMustBeSafeEarly before = new StaticFinalFieldFoldingMustBeSafeEarly();

    /**
     * Field value is stored in the class file attribute, so it is available even before this
     * assignment.
     */
    static final String F1 = "abc";
    /**
     * Field is optimized by our {@link IsStaticFinalFieldInitializedNode static final field folding
     * feature}.
     */
    static final Object F2 = "abc";
    /** Just a regular field. */
    static final Object F3 = new String[]{"abc"};

    static final StaticFinalFieldFoldingMustBeSafeEarly after = new StaticFinalFieldFoldingMustBeSafeEarly();
}

class LambdaMustBeSafeLate {
    private static final Predicate<String> IS_AUTOMATIC = s -> s.equals("Hello");

    static boolean matches(List<String> l) {
        return l.stream().anyMatch(IS_AUTOMATIC);
    }
}

@SuppressWarnings("deprecation")
class BoxingMustBeSimulated {
    static Integer i1 = 41;
    static Integer i2 = new Integer(42);

    static int sum = i1 + i2;

    static final Map<Class<?>, Object> defaultValues = new HashMap<>();

    static {
        defaultValues.put(boolean.class, Boolean.FALSE);
        defaultValues.put(byte.class, (byte) 0);
        defaultValues.put(short.class, (short) 0);
        defaultValues.put(int.class, 0);
        defaultValues.put(long.class, 0L);
        defaultValues.put(char.class, '\0');
        defaultValues.put(float.class, 0.0F);
        defaultValues.put(double.class, 0.0);
    }

    public static Object defaultValue(Class<?> clazz) {
        return defaultValues.get(clazz);
    }

    static Object S1;
    static Object O1;
    static Object O2;

    static {
        short[] shorts = {42, 43, 44, 45, 46, 47, 48};
        S1 = new short[12];
        System.arraycopy(shorts, 1, S1, 2, 5);
        System.arraycopy(S1, 3, S1, 5, 5);

        Object[] objects = {"42", null, "44", "45", null, "47", "48"};
        O1 = Arrays.copyOf(objects, 3);
        O2 = Arrays.copyOfRange(objects, 3, 6, String[].class);
    }
}

class SingleByteFieldMustBeSafeEarly {
    static SingleByteFieldMustBeSafeEarly instance1 = new SingleByteFieldMustBeSafeEarly((byte) 42);
    static SingleByteFieldMustBeSafeEarly instance2 = new SingleByteFieldMustBeSafeEarly((byte) -42);

    byte b;

    SingleByteFieldMustBeSafeEarly(byte b) {
        this.b = b;
    }
}

class SynchronizedMustBeSimulated {

    static Vector<String> vector;

    static {
        /*
         * Using the normally disallowed "old" synchronized collection classes is the easiest way to
         * test what we want to test.
         */
        // Checkstyle: stop
        vector = new Vector<>();
        // Checkstyle: resume
        for (int i = 0; i < 42; i++) {
            vector.add(String.valueOf(i));
        }
    }
}

class SynchronizedMustBeDelayed {
    static {
        synchronizedMethod();
    }

    /**
     * This method synchronizes on an object that exists before class initialization is started: the
     * class object itself. So we cannot determine at image build time if the class initializer
     * would ever finish execution at image run time. Another thread could hold the lock
     * indefinitely.
     */
    static synchronized int synchronizedMethod() {
        return 42;
    }
}

class InitializationOrder {
    static final List<Class<?>> initializationOrder = Collections.synchronizedList(new ArrayList<>());
}

interface Test1_I1 {
    default void defaultI1() {
    }

    int order = add();

    static int add() {
        InitializationOrder.initializationOrder.add(Test1_I1.class);
        return 42;
    }
}

interface Test1_I2 extends Test1_I1 {
    int order = add();

    static int add() {
        InitializationOrder.initializationOrder.add(Test1_I2.class);
        return 42;
    }
}

interface Test1_I3 extends Test1_I2 {
    default void defaultI3() {
    }

    int order = add();

    static int add() {
        InitializationOrder.initializationOrder.add(Test1_I3.class);
        return 42;
    }
}

interface Test1_I4 extends Test1_I3 {
    int order = add();

    static int add() {
        InitializationOrder.initializationOrder.add(Test1_I4.class);
        return 42;
    }
}

class Test1_A implements Test1_I4 {
    static {
        InitializationOrder.initializationOrder.add(Test1_A.class);
    }
}

interface Test2_I1 {
    default void defaultI1() {
    }

    int order = add();

    static int add() {
        InitializationOrder.initializationOrder.add(Test2_I1.class);
        return 42;
    }
}

interface Test2_I2 extends Test2_I1 {
    int order = add();

    static int add() {
        InitializationOrder.initializationOrder.add(Test2_I2.class);
        return 42;
    }
}

interface Test2_I3 extends Test2_I2 {
    default void defaultI3() {
    }

    int order = add();

    static int add() {
        InitializationOrder.initializationOrder.add(Test2_I3.class);
        return 42;
    }
}

interface Test2_I4 extends Test2_I3 {
    int order = add();

    static int add() {
        InitializationOrder.initializationOrder.add(Test2_I4.class);
        return 42;
    }
}

abstract class TestClassInitializationFeature implements Feature {

    private void checkClasses(boolean checkSafeEarly, boolean checkSafeLate) {
        System.out.println("=== Checking initialization state of classes: checkSafeEarly=" + checkSafeEarly + ", checkSafeLate=" + checkSafeLate);

        List<String> errors = new ArrayList<>();
        for (Class<?> checkedClass : TestClassInitialization.checkedClasses) {
            boolean nameHasSafeEarly = checkedClass.getName().contains("MustBeSafeEarly");
            boolean nameHasSafeLate = checkedClass.getName().contains("MustBeSafeLate");
            boolean nameHasSimulated = checkedClass.getName().contains("MustBeSimulated");
            boolean nameHasDelayed = checkedClass.getName().contains("MustBeDelayed");

            if ((nameHasSafeEarly ? 1 : 0) + (nameHasSafeLate ? 1 : 0) + (nameHasSimulated ? 1 : 0) + (nameHasDelayed ? 1 : 0) != 1) {
                errors.add(checkedClass.getName() + ": Wrongly named class: nameHasSafeEarly=" + nameHasSafeEarly + ", nameHasSafeLate=" + nameHasSafeLate +
                                ", nameHasSimulated=" + nameHasSimulated + ", nameHasDelayed=" + nameHasDelayed);
            } else {
                checkClass(checkedClass, checkSafeEarly, checkSafeLate, errors, nameHasSafeEarly, nameHasSafeLate, nameHasSimulated, nameHasDelayed);
            }
        }

        if (!errors.isEmpty()) {
            throw new Error(errors.stream().collect(Collectors.joining(System.lineSeparator())));
        }
    }

    abstract void checkClass(Class<?> checkedClass, boolean checkSafeEarly, boolean checkSafeLate, List<String> errors,
                    boolean nameHasSafeEarly, boolean nameHasSafeLate, boolean nameHasSimulated, boolean nameHasDelayed);

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        /* We need to access the checkedClasses array both at image build time and run time. */
        RuntimeClassInitialization.initializeAtBuildTime(TestClassInitialization.class);

        /*
         * Initialization of a class first triggers initialization of all superinterfaces that
         * declared default methods.
         */
        InitializationOrder.initializationOrder.clear();
        assertNotInitialized(Test1_I1.class, Test1_I2.class, Test1_I3.class, Test1_I4.class, Test1_A.class);
        RuntimeClassInitialization.initializeAtBuildTime(Test1_A.class);
        assertNotInitialized(Test1_I2.class, Test1_I4.class);
        assertInitialized(Test1_I1.class, Test1_I3.class, Test1_A.class);
        assertArraysEqual(new Object[]{Test1_I1.class, Test1_I3.class, Test1_A.class}, InitializationOrder.initializationOrder.toArray());

        /*
         * The old class initialization policy is wrong regarding interfaces, but we do not want to
         * change that now because it will be deleted soon.
         */
        if (TestClassInitialization.simulationEnabled) {

            /*
             * Initialization of an interface does not trigger initialization of superinterfaces.
             * Regardless whether any of the involved interfaces declare default methods.
             */
            InitializationOrder.initializationOrder.clear();
            assertNotInitialized(Test2_I1.class, Test2_I2.class, Test2_I3.class, Test2_I4.class);
            RuntimeClassInitialization.initializeAtBuildTime(Test2_I4.class);
            assertNotInitialized(Test2_I1.class, Test2_I2.class, Test2_I3.class);
            assertInitialized(Test2_I4.class);
            assertArraysEqual(new Object[]{Test2_I4.class}, InitializationOrder.initializationOrder.toArray());
            RuntimeClassInitialization.initializeAtBuildTime(Test2_I3.class);
            assertNotInitialized(Test2_I1.class, Test2_I2.class);
            assertInitialized(Test2_I3.class, Test2_I4.class);
            assertArraysEqual(new Object[]{Test2_I4.class, Test2_I3.class}, InitializationOrder.initializationOrder.toArray());
            RuntimeClassInitialization.initializeAtBuildTime(Test2_I2.class);
            assertNotInitialized(Test2_I1.class);
            assertInitialized(Test2_I2.class, Test2_I3.class, Test2_I4.class);
            assertArraysEqual(new Object[]{Test2_I4.class, Test2_I3.class, Test2_I2.class}, InitializationOrder.initializationOrder.toArray());
        }
    }

    private static void assertNotInitialized(Class<?>... classes) {
        for (var clazz : classes) {
            if (!Unsafe.getUnsafe().shouldBeInitialized(clazz)) {
                throw new AssertionError("Already initialized: " + clazz);
            }
        }
    }

    private static void assertInitialized(Class<?>... classes) {
        for (var clazz : classes) {
            if (Unsafe.getUnsafe().shouldBeInitialized(clazz)) {
                throw new AssertionError("Not initialized: " + clazz);
            }
        }
    }

    private static void assertArraysEqual(Object[] expected, Object[] actual) {
        if (!Arrays.equals(expected, actual)) {
            throw new RuntimeException("expected " + Arrays.toString(expected) + " but found " + Arrays.toString(actual));
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        checkClasses(false, false);
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        checkClasses(true, false);
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        if (access.isReachable(AssertionOnlyClassMustBeUnreachable.class)) {
            throw new Error("Assertion check was not constant folded for a class that is initialized at run time. " +
                            "We assume here that the image is built with assertions disabled, which is the case for the gate check.");
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        checkClasses(true, true);
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        checkClasses(true, true);
    }
}

/**
 * For testing with {@link ClassInitializationOptions#StrictImageHeap} set to false and simulation
 * of class initializer disabled.
 */
class TestClassInitializationFeatureOldPolicyFeature extends TestClassInitializationFeature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        super.afterRegistration(access);

        TestClassInitialization.simulationEnabled = false;
    }

    @Override
    void checkClass(Class<?> checkedClass, boolean checkSafeEarly, boolean checkSafeLate, List<String> errors,
                    boolean nameHasSafeEarly, boolean nameHasSafeLate, boolean nameHasSimulated, boolean nameHasDelayed) {

        boolean initialized = !Unsafe.getUnsafe().shouldBeInitialized(checkedClass);

        if (nameHasSafeEarly && initialized != checkSafeEarly) {
            errors.add(checkedClass.getName() + ": Check for MustBeSafeEarly failed");
        }
        if (nameHasSafeLate && initialized != checkSafeLate) {
            errors.add(checkedClass.getName() + ": Check for MustBeSafeLate failed");
        }
        if (nameHasSimulated && initialized) {
            /*
             * Class initializer simulation is disabled in this configuration, so these classes must
             * be initialized at run time.
             */
            errors.add(checkedClass.getName() + ": Check for MustBeSimulated failed");
        }
        if (nameHasDelayed && initialized) {
            errors.add(checkedClass.getName() + ": Check for MustBeDelayed failed");
        }
    }
}

/**
 * For testing with {@link ClassInitializationOptions#StrictImageHeap} set to true and simulation of
 * class initializer enabled.
 */
class TestClassInitializationFeatureNewPolicyFeature extends TestClassInitializationFeature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        super.afterRegistration(access);

        TestClassInitialization.simulationEnabled = true;
    }

    @Override
    void checkClass(Class<?> checkedClass, boolean checkSafeEarly, boolean checkSafeLate, List<String> errors,
                    boolean nameHasSafeEarly, boolean nameHasSafeLate, boolean nameHasSimulated, boolean nameHasDelayed) {
        if (!Unsafe.getUnsafe().shouldBeInitialized(checkedClass)) {
            errors.add(checkedClass.getName() + ": Class already initialized at image build time");
        }
    }
}

public class TestClassInitialization {

    static boolean simulationEnabled;

    static final Class<?>[] checkedClasses = new Class<?>[]{
                    PureMustBeSafeEarly.class,
                    NonPureMustBeDelayed.class,
                    PureCallMustBeSafeEarly.class,
                    InitializesNonPureMustBeDelayed.class,
                    SystemPropReadMustBeDelayed.class,
                    SystemPropWriteMustBeDelayed.class,
                    StartsAThreadMustBeDelayed.class,
                    CreatesAnExceptionMustBeDelayed.class,
                    ThrowsAnExceptionUninitializedMustBeDelayed.class,
                    PureInterfaceMustBeSafeEarly.class,
                    PureSubclassMustBeDelayed.class,
                    SuperClassMustBeDelayed.class,
                    InterfaceNonPureMustBeDelayed.class,
                    InterfaceNonPureDefaultMustBeDelayed.class,
                    PureSubclassInheritsDelayedInterfaceMustBeSafeEarly.class,
                    PureSubclassInheritsDelayedDefaultInterfaceMustBeDelayed.class,
                    ImplicitExceptionInInitializerUninitializedMustBeDelayed.class,
                    PureDependsOnImplicitExceptionUninitializedMustBeDelayed.class,
                    StaticFieldHolderMustBeSafeEarly.class,
                    StaticFieldModifer1MustBeDelayed.class,
                    StaticFieldModifer2MustBeDelayed.class,
                    RecursionInInitializerMustBeSafeLate.class,
                    UnsafeAccessMustBeSafeLate.class,
                    EnumMustBeSafeEarly.class,
                    NativeMethodMustBeDelayed.class,
                    ReferencesOtherPureClassMustBeSafeEarly.class, HelperClassMustBeSafeEarly.class,
                    CycleMustBeSafeLate.class, HelperClassMustBeSafeLate.class,
                    ReflectionMustBeSafeEarly.class, ForNameMustBeSafeEarly.class, ForNameUninitializedMustBeDelayed.class,
                    DevirtualizedCallMustBeDelayed.class, DevirtualizedCallSuperMustBeSafeEarly.class, DevirtualizedCallSubMustBeSafeEarly.class, DevirtualizedCallUsageMustBeDelayed.class,
                    LargeAllocation1MustBeDelayed.class, LargeAllocation2MustBeDelayed.class,
                    ComplexEnumMustBeSimulated.class,
                    StaticFinalFieldFoldingMustBeSafeEarly.class,
                    LambdaMustBeSafeLate.class,
                    BoxingMustBeSimulated.class,
                    SingleByteFieldMustBeSafeEarly.class,
                    SynchronizedMustBeSimulated.class, SynchronizedMustBeDelayed.class,
    };

    static int pure() {
        return transitivelyPure() + 42;
    }

    private static int transitivelyPure() {
        return 42;
    }

    /*
     * Since {@link Function} is a core JDK type that is always marked as
     * "initialize at build time", it is allowed to have a lambda for it in the image heap.
     */
    static Function<String, String> buildTimeLambda = TestClassInitialization::duplicate;

    static String duplicate(String s) {
        return s + s;
    }

    public static void main(String[] args) {
        for (var checkedClass : checkedClasses) {
            boolean nameHasSimulated = checkedClass.getName().contains("MustBeSimulated");
            boolean nameHasDelayed = checkedClass.getName().contains("MustBeDelayed");
            boolean initialized = !Unsafe.getUnsafe().shouldBeInitialized(checkedClass);
            if ((nameHasDelayed || (!simulationEnabled && nameHasSimulated)) == initialized) {
                throw new RuntimeException("Class " + checkedClass.getName() + ": nameHasSimulated=" + nameHasSimulated + ", nameHasDelayed=" + nameHasDelayed + ", initialized=" + initialized);
            }
        }

        assertTrue("123123".equals(buildTimeLambda.apply("123")));

        assertSame(42, PureMustBeSafeEarly.v);
        assertSame(84, PureCallMustBeSafeEarly.v);
        assertSame(42, InitializesPureMustBeDelayed.v);
        assertSame(1, NonPureMustBeDelayed.v);
        assertSame(1, NonPureAccessedFinal.v);
        assertSame(1, InitializesNonPureMustBeDelayed.v);
        assertSame(1, SystemPropReadMustBeDelayed.v);
        assertSame(1, SystemPropWriteMustBeDelayed.v);
        assertSame(1, StartsAThreadMustBeDelayed.v);
        assertSame(1, PureSubclassMustBeDelayed.v);
        assertSame(1, PureSubclassInheritsDelayedInterfaceMustBeSafeEarly.v);
        assertSame(1, PureSubclassInheritsDelayedDefaultInterfaceMustBeDelayed.v);
        assertSame(1, InterfaceNonPureMustBeDelayed.v);
        try {
            sink(ThrowsAnExceptionUninitializedMustBeDelayed.v);
            throw new RuntimeException("should not reach here");
        } catch (ExceptionInInitializerError e) {
            assertSame("should fire at runtime", e.getCause().getMessage());
        }
        assertSame("should fire at runtime", CreatesAnExceptionMustBeDelayed.e.getMessage());
        try {
            sink(ImplicitExceptionInInitializerUninitializedMustBeDelayed.res);
            throw new RuntimeException("should not reach here");
        } catch (ExceptionInInitializerError e) {
            assertSame(ArithmeticException.class, e.getCause().getClass());
        }
        try {
            sink(PureDependsOnImplicitExceptionUninitializedMustBeDelayed.a);
            throw new RuntimeException("should not reach here");
        } catch (NoClassDefFoundError e) {
            /* Expected. */
        }

        assertSame(111, StaticFieldHolderMustBeSafeEarly.a);
        StaticFieldModifer1MustBeDelayed.triggerInitialization();
        assertSame(222, StaticFieldHolderMustBeSafeEarly.a);
        StaticFieldModifer2MustBeDelayed.triggerInitialization();
        assertSame(333, StaticFieldHolderMustBeSafeEarly.a);

        assertSame(20100, RecursionInInitializerMustBeSafeLate.i);

        UnsafeAccessMustBeSafeLate value = UnsafeAccessMustBeSafeLate.value;
        assertSame(1234, value.f01 + value.f02 + value.f03 + value.f04 + value.f05 + value.f06 + value.f07 + value.f08 +
                        value.f09 + value.f10 + value.f11 + value.f12 + value.f13 + value.f14 + value.f15 + value.f16);

        EnumMustBeSafeEarly[] values = EnumMustBeSafeEarly.values();
        assertSame(null, values[0].getValue());
        assertSame("Hello", values[1].getValue());
        assertSame(Object.class, values[2].getValue().getClass());
        assertSame(EnumMustBeSafeEarly.V1, stringToEnum("v1"));

        assertSame(42, NativeMethodMustBeDelayed.i);
        NativeMethodMustBeDelayed.foo();
        ReferencesOtherPureClassMustBeSafeEarly.foo();
        CycleMustBeSafeLate.foo();

        assertSame(ForNameMustBeSafeEarly.class, ReflectionMustBeSafeEarly.c1);
        assertSame(ForNameUninitializedMustBeDelayed.class, ReflectionMustBeSafeEarly.c2);
        assertSame("foo", ReflectionMustBeSafeEarly.m1.getName());
        assertSame("field", ReflectionMustBeSafeEarly.f2.getName());

        assertSame(42, DevirtualizedCallUsageMustBeDelayed.value);

        assertSame(200_000, ((Object[]) LargeAllocation1MustBeDelayed.value).length);
        assertSame(1, ((int[][]) LargeAllocation2MustBeDelayed.value).length);
        assertSame(200_000, ((int[][]) LargeAllocation2MustBeDelayed.value)[0].length);

        assertSame(ComplexEnumMustBeSimulated.V1, ComplexEnumMustBeSimulated.lookup.get("V1"));
        assertSame(42, ComplexEnumMustBeSimulated.lookup.get("V2").virtualMethod());

        assertSame("abc", StaticFinalFieldFoldingMustBeSafeEarly.before.f1);
        assertSame(null, StaticFinalFieldFoldingMustBeSafeEarly.before.f2);
        assertSame(null, StaticFinalFieldFoldingMustBeSafeEarly.before.f3);
        assertSame("abc", StaticFinalFieldFoldingMustBeSafeEarly.after.f1);
        assertSame("abc", StaticFinalFieldFoldingMustBeSafeEarly.after.f2);
        assertSame(1, ((Object[]) StaticFinalFieldFoldingMustBeSafeEarly.after.f3).length);

        assertSame(true, LambdaMustBeSafeLate.matches(List.of("1", "2", "3", "Hello", "4")));
        assertSame(false, LambdaMustBeSafeLate.matches(List.of("1", "2", "3", "4")));

        assertSame(83, BoxingMustBeSimulated.sum);
        assertSame(Character.class, BoxingMustBeSimulated.defaultValue(char.class).getClass());
        assertSame(Short.class, BoxingMustBeSimulated.defaultValue(short.class).getClass());
        assertSame(Float.class, BoxingMustBeSimulated.defaultValue(float.class).getClass());
        assertTrue(Arrays.equals((short[]) BoxingMustBeSimulated.S1, new short[]{0, 0, 43, 44, 45, 44, 45, 46, 47, 0, 0, 0}));
        assertTrue(Arrays.equals((Object[]) BoxingMustBeSimulated.O1, new Object[]{"42", null, "44"}));
        assertTrue(Arrays.equals((Object[]) BoxingMustBeSimulated.O2, new String[]{"45", null, "47"}));

        /*
         * The unsafe field offset lookup is constant folded at image build time, which also
         * registers the field as unsafe accessed.
         */
        long bOffset = Unsafe.getUnsafe().objectFieldOffset(SingleByteFieldMustBeSafeEarly.class, "b");
        assertTrue(bOffset % 4 == 0);
        /*
         * Check that for sub-int values, the padding after the value is not touched by the image
         * heap writer.
         */
        assertSame(42, readRawByte(SingleByteFieldMustBeSafeEarly.instance1, bOffset + 0));
        assertSame(0, readRawByte(SingleByteFieldMustBeSafeEarly.instance1, bOffset + 1));
        assertSame(0, readRawByte(SingleByteFieldMustBeSafeEarly.instance1, bOffset + 2));
        assertSame(0, readRawByte(SingleByteFieldMustBeSafeEarly.instance1, bOffset + 3));
        assertSame(-42, readRawByte(SingleByteFieldMustBeSafeEarly.instance2, bOffset + 0));
        assertSame(0, readRawByte(SingleByteFieldMustBeSafeEarly.instance2, bOffset + 1));
        assertSame(0, readRawByte(SingleByteFieldMustBeSafeEarly.instance2, bOffset + 2));
        assertSame(0, readRawByte(SingleByteFieldMustBeSafeEarly.instance2, bOffset + 3));

        assertSame(42, SynchronizedMustBeSimulated.vector.size());
        assertSame(42, SynchronizedMustBeDelayed.synchronizedMethod());

        for (var checkedClass : checkedClasses) {
            boolean initialized = !Unsafe.getUnsafe().shouldBeInitialized(checkedClass);
            boolean expectedUninitialized = checkedClass.getName().contains("Uninitialized");
            if (initialized == expectedUninitialized) {
                throw new RuntimeException("Class " + checkedClass.getName() + ": initialized=" + initialized + ", expectedUninitialized=" + expectedUninitialized);
            }
        }
    }

    @NeverInline("prevent constant folding, we read the raw memory after the last field")
    static int readRawByte(Object o, long offset) {
        return Unsafe.getUnsafe().getByte(o, offset);
    }

    private static EnumMustBeSafeEarly stringToEnum(String name) {
        if (EnumMustBeSafeEarly.V1.name().equalsIgnoreCase(name)) {
            return EnumMustBeSafeEarly.V1;
        } else {
            return EnumMustBeSafeEarly.V2;
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new RuntimeException("condition not true");
        }
    }

    private static void assertSame(long expected, long actual) {
        if (expected != actual) {
            throw new RuntimeException("expected " + expected + " but found " + actual);
        }
    }

    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) {
            throw new RuntimeException("expected " + expected + " but found " + actual);
        }
    }

    private static void sink(@SuppressWarnings("unused") Object o) {
    }
}
