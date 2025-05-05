/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.test.debug.helper;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.c.InvokeJavaFunctionPointer;
import com.oracle.svm.graal.SubstrateGraalUtils;
import com.oracle.svm.graal.hosted.runtimecompilation.RuntimeCompilationFeature;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.test.debug.CStructTests;
import com.oracle.svm.util.ModuleSupport;

import jdk.vm.ci.code.InstalledCode;

class RuntimeCompilations {

    Integer a;
    int b;
    String c;
    Object d;

    static Integer sa = 42;
    static int sb;
    static String sc;
    static Object sd;

    RuntimeCompilations(int a) {
        this.a = a;
    }

    @NeverInline("For testing")
    private void breakHere() {
    }

    @NeverInline("For testing")
    private void breakHere(@SuppressWarnings("unused") Object... pin) {
    }

    @NeverInline("For testing")
    private void breakHere(@SuppressWarnings("unused") WordBase... pin) {
    }

    @NeverInline("For testing")
    public Integer paramMethod(Integer param1, int param2, String param3, Object param4) {
        a = param1;
        breakHere();
        b = param2;
        breakHere();
        c = param3;
        breakHere();
        d = param4;
        breakHere(param1);
        return a;
    }

    @NeverInline("For testing")
    public Integer noParamMethod() {
        return a;
    }

    @NeverInline("For testing")
    public void voidMethod() {
        a *= 2;
        breakHere();
    }

    @NeverInline("For testing")
    public int primitiveReturnMethod(int param1) {
        b += param1;
        breakHere();
        return b;
    }

    @NeverInline("For testing")
    public Integer[] arrayMethod(int[] param1, @SuppressWarnings("unused") String[] param2) {
        a = param1[0];
        breakHere();
        return new Integer[]{a};
    }

    @NeverInline("For testing")
    @SuppressWarnings("unused")
    public float localsMethod(String param1) {
        float f = 1.5f;
        String x = param1;
        breakHere();
        byte[] bytes = x.getBytes();
        breakHere();
        return f + bytes.length;
    }

    @NeverInline("For testing")
    public static Integer staticMethod(Integer param1, int param2, String param3, Object param4) {
        sa = param1;
        sb = param2;
        sc = param3;
        sd = param4;
        return sa + sb;
    }

    @NeverInline("For testing")
    public void cPointerTypes(CCharPointer charPtr, CCharPointerPointer charPtrPtr) {
        breakHere(charPtr, charPtrPtr);
    }

    @NeverInline("For testing")
    public void inlineTest(String param1) {
        inlineMethod1(param1);
        breakHere();
        inlineMethod2(param1);
        breakHere();
        noInlineMethod1(param1);
        breakHere();
        noInlineMethod2(param1);
    }

    @AlwaysInline("For testing")
    private void inlineMethod1(String param1) {
        String inlineParam = param1;
        breakHere(inlineParam);
    }

    @AlwaysInline("For testing")
    protected void inlineMethod2(@SuppressWarnings("unused") String param1) {
        String p1 = "1";
        inlineMethod1(p1);
        breakHere();

        String p2 = "2";
        noInlineMethod1(p2);
        breakHere();
    }

    @NeverInline("For testing")
    private void noInlineMethod1(String param1) {
        String noInlineParam = param1;
        breakHere(noInlineParam);
    }

    @NeverInline("For testing")
    protected void noInlineMethod2(@SuppressWarnings("unused") String param1) {
        String p1 = "a";
        noInlineMethod1(p1);
        breakHere();

        String p2 = "b";
        inlineMethod1(p2);
        breakHere();

        String p3 = "c";
        inlineMethod2(p3);
        breakHere();
    }
}

public class RuntimeCompileDebugInfoTest {
    static class RuntimeHolder {
        SubstrateMethod testMethod1;
        SubstrateMethod testMethod2;
        SubstrateMethod testMethod3;
        SubstrateMethod testMethod4;
        SubstrateMethod testMethod5;
        SubstrateMethod testMethod6;
        SubstrateMethod testMethod7;
        SubstrateMethod testMethod8;
        SubstrateMethod testMethod9;
        SubstrateMethod testMethod10;
        SubstrateMethod testMethod11;
    }

    public static class RegisterMethodsFeature implements Feature {
        RegisterMethodsFeature() {
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, RegisterMethodsFeature.class, false,
                            "org.graalvm.nativeimage.builder",
                            "com.oracle.svm.graal", "com.oracle.svm.graal.hosted.runtimecompilation", "com.oracle.svm.hosted", "com.oracle.svm.core.util");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, RegisterMethodsFeature.class, false,
                            "jdk.internal.vm.ci",
                            "jdk.vm.ci.code");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, RegisterMethodsFeature.class, false,
                            "jdk.graal.compiler",
                            "jdk.graal.compiler.api.directives", "jdk.graal.compiler.word");
        }

        @Override
        public List<Class<? extends Feature>> getRequiredFeatures() {
            return List.of(RuntimeCompilationFeature.class);
        }

        @Override
        public void beforeAnalysis(BeforeAnalysisAccess a) {
            BeforeAnalysisAccessImpl config = (BeforeAnalysisAccessImpl) a;

            RuntimeHolder holder = new RuntimeHolder();
            RuntimeClassInitialization.initializeAtBuildTime(RuntimeHolder.class);
            ImageSingletons.add(RuntimeHolder.class, holder);

            RuntimeCompilationFeature runtimeCompilationFeature = RuntimeCompilationFeature.singleton();
            runtimeCompilationFeature.initializeRuntimeCompilationForTesting(config);

            holder.testMethod1 = prepareMethodForRuntimeCompilation(config, runtimeCompilationFeature, RuntimeCompilations.class, "paramMethod", Integer.class, int.class, String.class, Object.class);
            holder.testMethod2 = prepareMethodForRuntimeCompilation(config, runtimeCompilationFeature, RuntimeCompilations.class, "noParamMethod");
            holder.testMethod3 = prepareMethodForRuntimeCompilation(config, runtimeCompilationFeature, RuntimeCompilations.class, "voidMethod");
            holder.testMethod4 = prepareMethodForRuntimeCompilation(config, runtimeCompilationFeature, RuntimeCompilations.class, "primitiveReturnMethod", int.class);
            holder.testMethod5 = prepareMethodForRuntimeCompilation(config, runtimeCompilationFeature, RuntimeCompilations.class, "arrayMethod", int[].class, String[].class);
            holder.testMethod6 = prepareMethodForRuntimeCompilation(config, runtimeCompilationFeature, RuntimeCompilations.class, "localsMethod", String.class);
            holder.testMethod7 = prepareMethodForRuntimeCompilation(config, runtimeCompilationFeature, RuntimeCompilations.class, "staticMethod", Integer.class, int.class, String.class, Object.class);
            holder.testMethod8 = prepareMethodForRuntimeCompilation(config, runtimeCompilationFeature, RuntimeCompilations.class, "inlineTest", String.class);
            holder.testMethod9 = prepareMethodForRuntimeCompilation(config, runtimeCompilationFeature, CStructTests.class, "mixedArguments");
            holder.testMethod10 = prepareMethodForRuntimeCompilation(config, runtimeCompilationFeature, CStructTests.class, "weird");
            holder.testMethod11 = prepareMethodForRuntimeCompilation(config, runtimeCompilationFeature, RuntimeCompilations.class, "cPointerTypes", CCharPointer.class, CCharPointerPointer.class);
        }

        private static SubstrateMethod prepareMethodForRuntimeCompilation(BeforeAnalysisAccessImpl config, RuntimeCompilationFeature runtimeCompilationFeature, Class<?> holder, String methodName,
                        Class<?>... signature) {
            RuntimeClassInitialization.initializeAtBuildTime(holder);
            try {
                return runtimeCompilationFeature.prepareMethodForRuntimeCompilation(holder.getMethod(methodName, signature), config);
            } catch (NoSuchMethodException ex) {
                throw shouldNotReachHere(ex);
            }
        }
    }

    interface TestFunctionPointer extends CFunctionPointer {
        @InvokeJavaFunctionPointer
        Integer invoke(Object receiver, Integer arg1, int arg2, String arg3, Object arg4);

        @InvokeJavaFunctionPointer
        Object invoke(Object receiver);

        @InvokeJavaFunctionPointer
        void invoke();

        @InvokeJavaFunctionPointer
        void invoke(Object receiver, CCharPointer arg1, CCharPointerPointer arg2);

        @InvokeJavaFunctionPointer
        int invoke(Object receiver, int arg1);

        @InvokeJavaFunctionPointer
        Integer[] invoke(Object receiver, int[] arg1, String[] arg2);

        @InvokeJavaFunctionPointer
        float invoke(Object receiver, String arg1);

        @InvokeJavaFunctionPointer
        Integer invoke(Integer arg1, int arg2, String arg3, Object arg4);
    }

    private static RuntimeHolder getHolder() {
        return ImageSingletons.lookup(RuntimeHolder.class);
    }

    private static Integer invoke(TestFunctionPointer functionPointer, Object receiver, Integer arg1, int arg2, String arg3, Object arg4) {
        return functionPointer.invoke(receiver, arg1, arg2, arg3, arg4);
    }

    private static void invoke(TestFunctionPointer functionPointer) {
        functionPointer.invoke();
    }

    private static void invoke(TestFunctionPointer functionPointer, Object receiver, CCharPointer arg1, CCharPointerPointer arg2) {
        functionPointer.invoke(receiver, arg1, arg2);
    }

    private static Object invoke(TestFunctionPointer functionPointer, Object receiver) {
        return functionPointer.invoke(receiver);
    }

    private static int invoke(TestFunctionPointer functionPointer, Object receiver, int arg1) {
        return functionPointer.invoke(receiver, arg1);
    }

    private static Integer[] invoke(TestFunctionPointer functionPointer, Object receiver, int[] arg1, String[] arg2) {
        return functionPointer.invoke(receiver, arg1, arg2);
    }

    private static float invoke(TestFunctionPointer functionPointer, Object receiver, String arg1) {
        return functionPointer.invoke(receiver, arg1);
    }

    private static Integer invoke(TestFunctionPointer functionPointer, Integer arg1, int arg2, String arg3, Object arg4) {
        return functionPointer.invoke(arg1, arg2, arg3, arg4);
    }

    private static TestFunctionPointer getFunctionPointer(InstalledCode installedCode) {
        return WordFactory.pointer(installedCode.getEntryPoint());
    }

    @SuppressWarnings("unused")
    public static void testParams() {
        InstalledCode installedCode = SubstrateGraalUtils.compileAndInstall(getHolder().testMethod1);
        InstalledCode installedCodeStatic = SubstrateGraalUtils.compileAndInstall(getHolder().testMethod7);

        RuntimeCompilations x = new RuntimeCompilations(11);
        Integer param1 = 42;
        int param2 = 27;
        String param3 = "test";
        Object param4 = new ArrayList<>();

        Integer result = invoke(getFunctionPointer(installedCode), x, param1, param2, param3, param4);
        Integer resultStatic = invoke(getFunctionPointer(installedCodeStatic), param1, param2, param3, param4);

        installedCode.invalidate();
        installedCodeStatic.invalidate();
    }

    @SuppressWarnings("unused")
    public static void testNoParam() {
        InstalledCode installedCode = SubstrateGraalUtils.compileAndInstall(getHolder().testMethod2);

        RuntimeCompilations x = new RuntimeCompilations(11);

        Integer result = (Integer) invoke(getFunctionPointer(installedCode), x);

        installedCode.invalidate();
    }

    public static void testVoid() {
        InstalledCode installedCode = SubstrateGraalUtils.compileAndInstall(getHolder().testMethod3);

        RuntimeCompilations x = new RuntimeCompilations(11);

        invoke(getFunctionPointer(installedCode), x);

        installedCode.invalidate();
    }

    @SuppressWarnings("unused")
    public static void testPrimitiveReturn() {
        InstalledCode installedCode = SubstrateGraalUtils.compileAndInstall(getHolder().testMethod4);

        RuntimeCompilations x = new RuntimeCompilations(11);
        int param1 = 42;

        int result = invoke(getFunctionPointer(installedCode), x, param1);

        installedCode.invalidate();
    }

    @SuppressWarnings("unused")
    public static void testArray() {
        InstalledCode installedCode = SubstrateGraalUtils.compileAndInstall(getHolder().testMethod5);

        RuntimeCompilations x = new RuntimeCompilations(11);
        int[] param1 = new int[]{1, 2, 3, 4};
        String[] param2 = new String[]{"this", "is", "an", "array"};

        Integer[] result = invoke(getFunctionPointer(installedCode), x, param1, param2);

        installedCode.invalidate();
    }

    @SuppressWarnings("unused")
    public static void testLocals() {
        InstalledCode installedCode = SubstrateGraalUtils.compileAndInstall(getHolder().testMethod6);

        RuntimeCompilations x = new RuntimeCompilations(11);
        String param1 = "test";

        float result = invoke(getFunctionPointer(installedCode), x, param1);

        installedCode.invalidate();
    }

    public static void testInlining() {
        InstalledCode installedCode = SubstrateGraalUtils.compileAndInstall(getHolder().testMethod8);

        RuntimeCompilations x = new RuntimeCompilations(11);
        String param1 = "test";

        invoke(getFunctionPointer(installedCode), x, param1);

        installedCode.invalidate();
    }

    public static void testCStructs() {
        InstalledCode installedCode1 = SubstrateGraalUtils.compileAndInstall(getHolder().testMethod9);
        InstalledCode installedCode2 = SubstrateGraalUtils.compileAndInstall(getHolder().testMethod10);

        invoke(getFunctionPointer(installedCode1));
        invoke(getFunctionPointer(installedCode2));

        installedCode1.invalidate();
        installedCode2.invalidate();
    }

    public static void testCPointer() {
        InstalledCode installedCode = SubstrateGraalUtils.compileAndInstall(getHolder().testMethod11);

        RuntimeCompilations x = new RuntimeCompilations(11);
        CCharPointer param1 = CTypeConversion.toCString("test").get();
        CCharPointerPointer param2 = UnmanagedMemory.malloc(SizeOf.get(CCharPointer.class));
        param2.write(param1);

        invoke(getFunctionPointer(installedCode), x, param1, param2);

        installedCode.invalidate();
        UnmanagedMemory.free(param2);
    }

    @SuppressWarnings("unused")
    public static void testAOTCompiled() {

        RuntimeCompilations x = new RuntimeCompilations(11);
        Integer param1 = 42;
        int param2 = 27;
        String param3 = "test";
        Object param4 = new ArrayList<>();

        Integer result = x.paramMethod(param1, param2, param3, param4);
        Integer result2 = RuntimeCompilations.staticMethod(param1, param2, param3, param4);
        Integer result3 = x.noParamMethod();
    }

    public static void main(String[] args) {
        /* Run startup hooks to, e.g., install the segfault handler so that we get crash reports. */
        VMRuntime.initialize();

        // aot compiled code for comparing generated debug infos
        testAOTCompiled();

        // use runtime compiled code
        testParams();
        testNoParam();
        testVoid();
        testPrimitiveReturn();
        testArray();
        testLocals();
        testInlining();
        testCStructs();
        testCPointer();
    }
}
