package com.oracle.svm.test.debug;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.c.InvokeJavaFunctionPointer;
import com.oracle.svm.graal.SubstrateGraalUtils;
import com.oracle.svm.graal.hosted.runtimecompilation.RuntimeCompilationFeature;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.util.ModuleSupport;
import jdk.vm.ci.code.InstalledCode;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.word.WordFactory;

import java.util.ArrayList;
import java.util.List;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

class RuntimeCompilations {

    Integer a;
    int b;
    String c;
    Object d;

    static Integer sa = 42;
    static String sb;
    static Object sc;

    RuntimeCompilations(int a) {
        this.a = a;
    }

    @NeverInline("For testing")
    private void breakHere() {}

    @NeverInline("For testing")
    public Integer paramMethod(int param1, String param2, Object param3) {
        breakHere();
        b = param1;
        breakHere();
        c = param2;
        breakHere();
        d = param3;
        breakHere();
        return a;
    }

    @NeverInline("For testing")
    public Integer noParamMethod() {
        return a;
    }

    @NeverInline("For testing")
    public void voidMethod() {
        a *= 2;
    }

    @NeverInline("For testing")
    public int primitiveReturnMethod(int param1) {
        a = param1;
        return param1;
    }

    @NeverInline("For testing")
    public Integer[] arrayMethod(int[] param1, String[] param2) {
        a = param1[0];
        return new Integer[] { a };
    }

    @NeverInline("For testing")
    public float localsMethod(String param1) {
        float f = 1.5f;
        String x = param1;
        byte[] bytes = x.getBytes();
        return f;
    }

    @NeverInline("For testing")
    public static Integer staticMethod(int param1, String param2, Object param3) {
        sa = param1;
        sb = param2;
        sc = param3;
        return sa;
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
    }

    public static class RegisterMethodsFeature implements Feature {
        RegisterMethodsFeature() {
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, RegisterMethodsFeature.class, false,
                    "org.graalvm.nativeimage.builder",
                    "com.oracle.svm.graal", "com.oracle.svm.graal.hosted.runtimecompilation", "com.oracle.svm.hosted");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, RegisterMethodsFeature.class, false,
                    "jdk.internal.vm.ci",
                    "jdk.vm.ci.code");
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, RegisterMethodsFeature.class, false,
                    "jdk.graal.compiler",
                    "jdk.graal.compiler.api.directives");
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

            holder.testMethod1 = prepareMethodForRuntimeCompilation(config, runtimeCompilationFeature, RuntimeCompilations.class, "paramMethod", int.class, String.class, Object.class);
            holder.testMethod2 = prepareMethodForRuntimeCompilation(config, runtimeCompilationFeature, RuntimeCompilations.class, "noParamMethod");
            holder.testMethod3 = prepareMethodForRuntimeCompilation(config, runtimeCompilationFeature, RuntimeCompilations.class, "voidMethod");
            holder.testMethod4 = prepareMethodForRuntimeCompilation(config, runtimeCompilationFeature, RuntimeCompilations.class, "primitiveReturnMethod", int.class);
            holder.testMethod5 = prepareMethodForRuntimeCompilation(config, runtimeCompilationFeature, RuntimeCompilations.class, "arrayMethod", int[].class, String[].class);
            holder.testMethod6 = prepareMethodForRuntimeCompilation(config, runtimeCompilationFeature, RuntimeCompilations.class, "localsMethod", String.class);
            holder.testMethod7 = prepareMethodForRuntimeCompilation(config, runtimeCompilationFeature, RuntimeCompilations.class, "staticMethod", int.class, String.class, Object.class);
        }

        private SubstrateMethod prepareMethodForRuntimeCompilation(BeforeAnalysisAccessImpl config, RuntimeCompilationFeature runtimeCompilationFeature, Class<?> holder, String methodName, Class<?>... signature) {
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
        Integer invoke(Object receiver, int arg1, String arg2, Object arg3);
        @InvokeJavaFunctionPointer
        Object invoke(Object receiver);
        @InvokeJavaFunctionPointer
        int invoke(Object receiver, int arg1);
        @InvokeJavaFunctionPointer
        Integer[] invoke(Object receiver, int[] arg1, String[] arg2);
        @InvokeJavaFunctionPointer
        float invoke(Object receiver, String arg1);
        @InvokeJavaFunctionPointer
        Integer invoke(int arg1, String arg2, Object arg3);
    }

    private static RuntimeHolder getHolder() {
        return ImageSingletons.lookup(RuntimeHolder.class);
    }

    private static Integer invoke(TestFunctionPointer functionPointer, Object receiver, int arg1, String arg2, Object arg3) {
        return functionPointer.invoke(receiver, arg1, arg2, arg3);
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

    private static Integer invoke(TestFunctionPointer functionPointer, Integer arg1, String arg2, Object arg3) {
        return functionPointer.invoke(arg1, arg2, arg3);
    }

    private static TestFunctionPointer getFunctionPointer(InstalledCode installedCode) {
        return WordFactory.pointer(installedCode.getEntryPoint());
    }

    public static void testParams() {

        InstalledCode installedCode = SubstrateGraalUtils.compileAndInstall(getHolder().testMethod1);
        InstalledCode installedCodeStatic = SubstrateGraalUtils.compileAndInstall(getHolder().testMethod7);

        RuntimeCompilations x = new RuntimeCompilations(11);
        int param1 = 27;
        String param2 = "test";
        Object param3 = new ArrayList<>();

        Integer result = invoke(getFunctionPointer(installedCode), x, param1, param2, param3);
        Integer resultStatic = invoke(getFunctionPointer(installedCodeStatic), param1, param2, param3);

        installedCode.invalidate();
        installedCodeStatic.invalidate();
    }

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

    public static void testPrimitiveReturn() {

        InstalledCode installedCode = SubstrateGraalUtils.compileAndInstall(getHolder().testMethod4);

        RuntimeCompilations x = new RuntimeCompilations(11);
        int param1 = 42;

        int result = invoke(getFunctionPointer(installedCode), x, param1);

        installedCode.invalidate();
    }

    public static void testArray() {

        InstalledCode installedCode = SubstrateGraalUtils.compileAndInstall(getHolder().testMethod5);

        RuntimeCompilations x = new RuntimeCompilations(11);
        int[] param1 = new int[] {1,2,3,4};
        String[] param2 = new String[] {"this", "is", "an", "array"};

        Integer[] result = invoke(getFunctionPointer(installedCode), x, param1, param2);

        installedCode.invalidate();
    }

    public static void testLocals() {

        InstalledCode installedCode = SubstrateGraalUtils.compileAndInstall(getHolder().testMethod6);

        RuntimeCompilations x = new RuntimeCompilations(11);
        String param1 = "test";

        float result = invoke(getFunctionPointer(installedCode), x, param1);

        installedCode.invalidate();
    }

    public static void testAOTCompiled() {

        RuntimeCompilations x = new RuntimeCompilations(11);
        int param1 = 27;
        String param2 = "test";
        Object param3 = new ArrayList<>();

        Integer result = x.paramMethod(param1, param2, param3);
        Integer result2 = RuntimeCompilations.staticMethod(param1, param2, param3);
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
    }
}
