/*
 * Copyright (c) 2007, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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
package com.oracle.svm.core.jdk;

import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.Reset;
import static com.oracle.svm.core.snippets.KnownIntrinsics.readHub;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Containers;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jdk.JavaLangSubstitutions.ClassValueSupport;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.util.VMError;

@TargetClass(java.lang.Object.class)
@SuppressWarnings("static-method")
final class Target_java_lang_Object {

    @Substitute
    @TargetElement(name = "registerNatives", onlyWith = JDK11OrEarlier.class)
    private static void registerNativesSubst() {
        /* We reimplemented all native methods, so nothing to do. */
    }

    @Substitute
    @TargetElement(name = "getClass")
    private Object getClassSubst() {
        return readHub(this);
    }

    @Substitute
    @TargetElement(name = "hashCode")
    private int hashCodeSubst() {
        throw VMError.shouldNotReachHere("Intrinsified in SubstrateGraphBuilderPlugins");
    }

    @Substitute
    @TargetElement(name = "wait")
    private void waitSubst(long timeoutMillis) throws InterruptedException {
        /*
         * JDK 19 and later: our monitor implementation does not pin virtual threads, so avoid
         * jdk.internal.misc.Blocker which expects and asserts that a virtual thread is pinned.
         * Also, we get interrupted on the virtual thread instead of the carrier thread, which
         * clears the carrier thread's interrupt status too, so we don't have to intercept an
         * InterruptedException from the carrier thread to clear the virtual thread interrupt.
         */
        MonitorSupport.singleton().wait(this, timeoutMillis);
    }

    @Delete
    @TargetElement(onlyWith = JDK19OrLater.class)
    private native void wait0(long timeoutMillis);

    @Substitute
    @TargetElement(name = "notify")
    private void notifySubst() {
        MonitorSupport.singleton().notify(this, false);
    }

    @Substitute
    @TargetElement(name = "notifyAll")
    private void notifyAllSubst() {
        MonitorSupport.singleton().notify(this, true);
    }
}

@TargetClass(classNameProvider = Package_jdk_internal_loader_helper.class, className = "ClassLoaderHelper")
final class Target_jdk_internal_loader_ClassLoaderHelper {
    @Alias
    static native File mapAlternativeName(File lib);
}

@TargetClass(java.lang.Enum.class)
final class Target_java_lang_Enum {

    @Substitute
    private static Enum<?> valueOf(Class<Enum<?>> enumType, String name) {
        /*
         * The original implementation creates and caches a HashMap to make the lookup faster. For
         * simplicity, we do a linear search for now.
         */
        Enum<?>[] enumConstants = DynamicHub.fromClass(enumType).getEnumConstantsShared();
        if (enumConstants == null) {
            throw new IllegalArgumentException(enumType.getName() + " is not an enum type");
        }
        for (Enum<?> e : enumConstants) {
            if (e.name().equals(name)) {
                return e;
            }
        }
        if (name == null) {
            throw new NullPointerException("Name is null");
        } else {
            throw new IllegalArgumentException("No enum constant " + enumType.getName() + "." + name);
        }
    }

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public native int ordinal();
}

@TargetClass(java.lang.String.class)
final class Target_java_lang_String {

    @Substitute
    public String intern() {
        String thisStr = SubstrateUtil.cast(this, String.class);
        return ImageSingletons.lookup(StringInternSupport.class).intern(thisStr);
    }

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    native boolean isLatin1();

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public native boolean isEmpty();

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public native int length();

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    native byte coder();

    @Alias //
    byte[] value;

    @Alias //
    int hash;
}

@TargetClass(className = "java.lang.StringLatin1")
final class Target_java_lang_StringLatin1 {

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static native char getChar(byte[] val, int index);

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static native int hashCode(byte[] value);
}

@TargetClass(className = "java.lang.StringUTF16")
final class Target_java_lang_StringUTF16 {

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static native char getChar(byte[] val, int index);

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static native int hashCode(byte[] value);
}

@TargetClass(java.lang.Throwable.class)
@Platforms(InternalPlatform.NATIVE_ONLY.class)
@SuppressWarnings({"unused"})
final class Target_java_lang_Throwable {

    @Alias @RecomputeFieldValue(kind = Reset)//
    private Object backtrace;

    @Alias @RecomputeFieldValue(kind = Reset)//
    StackTraceElement[] stackTrace;

    @Alias String detailMessage;

    @Substitute
    @NeverInline("Starting a stack walk in the caller frame")
    private Object fillInStackTrace() {
        stackTrace = JavaThreads.getStackTrace(true, Thread.currentThread());
        return this;
    }

    @Substitute
    private StackTraceElement[] getOurStackTrace() {
        if (stackTrace != null) {
            return stackTrace;
        } else {
            return new StackTraceElement[0];
        }
    }
}

@TargetClass(java.lang.Runtime.class)
@SuppressWarnings({"static-method"})
final class Target_java_lang_Runtime {

    @Substitute
    public void runFinalization() {
    }

    @Substitute
    @Platforms(InternalPlatform.PLATFORM_JNI.class)
    private int availableProcessors() {
        int optionValue = SubstrateOptions.ActiveProcessorCount.getValue();
        if (optionValue > 0) {
            return optionValue;
        }

        if (SubstrateOptions.MultiThreaded.getValue()) {
            return Containers.activeProcessorCount();
        } else {
            return 1;
        }
    }
}

@TargetClass(java.lang.System.class)
@SuppressWarnings("unused")
final class Target_java_lang_System {

    @Alias private static PrintStream out;
    @Alias private static PrintStream err;
    @Alias private static InputStream in;

    @Substitute
    private static void setIn(InputStream is) {
        in = is;
    }

    @Substitute
    private static void setOut(PrintStream ps) {
        out = ps;
    }

    @Substitute
    private static void setErr(PrintStream ps) {
        err = ps;
    }

    @Substitute
    private static int identityHashCode(Object obj) {
        throw VMError.shouldNotReachHere("Intrinsified in SubstrateGraphBuilderPlugins");
    }

    /* Ensure that we do not leak the full set of properties from the image generator. */
    @Delete //
    private static Properties props;

    @Substitute
    private static Properties getProperties() {
        return ImageSingletons.lookup(SystemPropertiesSupport.class).getProperties();
    }

    @Substitute
    private static void setProperties(Properties props) {
        ImageSingletons.lookup(SystemPropertiesSupport.class).setProperties(props);
    }

    @Substitute
    public static String setProperty(String key, String value) {
        checkKey(key);
        return ImageSingletons.lookup(SystemPropertiesSupport.class).setProperty(key, value);
    }

    @Substitute
    private static String getProperty(String key) {
        checkKey(key);
        return ImageSingletons.lookup(SystemPropertiesSupport.class).getProperty(key);
    }

    @Substitute
    public static String clearProperty(String key) {
        checkKey(key);
        return ImageSingletons.lookup(SystemPropertiesSupport.class).clearProperty(key);
    }

    @Substitute
    private static String getProperty(String key, String def) {
        String result = getProperty(key);
        return result != null ? result : def;
    }

    @Alias
    private static native void checkKey(String key);

    /*
     * Note that there is no substitution for getSecurityManager, but instead getSecurityManager it
     * is intrinsified in SubstrateGraphBuilderPlugins to always return null. This allows better
     * constant folding of SecurityManager code already during static analysis.
     */
    @Substitute
    private static void setSecurityManager(SecurityManager s) {
        if (s != null) {
            /*
             * We deliberately treat this as a non-recoverable fatal error. We want to prevent bugs
             * where an exception is silently ignored by an application and then necessary security
             * checks are not in place.
             */
            throw VMError.shouldNotReachHere("Installing a SecurityManager is not yet supported");
        }
    }

}

final class NotAArch64 implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return !Platform.includedIn(Platform.AARCH64.class);
    }
}

/**
 * When the intrinsics below are used outside of {@link java.lang.Math}, they are lowered to a
 * foreign call. This foreign call must be uninterruptible as it results from lowering a floating
 * node. Otherwise, we would introduce a safepoint in places where no safepoint is allowed.
 */
@TargetClass(value = java.lang.Math.class, onlyWith = NotAArch64.class)
final class Target_java_lang_Math {
    @Substitute
    @Uninterruptible(reason = "Must not contain a safepoint.")
    @SubstrateForeignCallTarget(fullyUninterruptible = true, stubCallingConvention = false)
    public static double sin(double a) {
        return UnaryMathIntrinsicNode.compute(a, UnaryOperation.SIN);
    }

    @Substitute
    @Uninterruptible(reason = "Must not contain a safepoint.")
    @SubstrateForeignCallTarget(fullyUninterruptible = true, stubCallingConvention = false)
    public static double cos(double a) {
        return UnaryMathIntrinsicNode.compute(a, UnaryOperation.COS);
    }

    @Substitute
    @Uninterruptible(reason = "Must not contain a safepoint.")
    @SubstrateForeignCallTarget(fullyUninterruptible = true, stubCallingConvention = false)
    public static double tan(double a) {
        return UnaryMathIntrinsicNode.compute(a, UnaryOperation.TAN);
    }

    @Substitute
    @Uninterruptible(reason = "Must not contain a safepoint.")
    @SubstrateForeignCallTarget(fullyUninterruptible = true, stubCallingConvention = false)
    public static double log(double a) {
        return UnaryMathIntrinsicNode.compute(a, UnaryOperation.LOG);
    }

    @Substitute
    @Uninterruptible(reason = "Must not contain a safepoint.")
    @SubstrateForeignCallTarget(fullyUninterruptible = true, stubCallingConvention = false)
    public static double log10(double a) {
        return UnaryMathIntrinsicNode.compute(a, UnaryOperation.LOG10);
    }

    @Substitute
    @Uninterruptible(reason = "Must not contain a safepoint.")
    @SubstrateForeignCallTarget(fullyUninterruptible = true, stubCallingConvention = false)
    public static double exp(double a) {
        return UnaryMathIntrinsicNode.compute(a, UnaryOperation.EXP);
    }

    @Substitute
    @Uninterruptible(reason = "Must not contain a safepoint.")
    @SubstrateForeignCallTarget(fullyUninterruptible = true, stubCallingConvention = false)
    public static double pow(double a, double b) {
        return BinaryMathIntrinsicNode.compute(a, b, BinaryOperation.POW);
    }
}

@TargetClass(java.lang.StrictMath.class)
@Platforms(InternalPlatform.NATIVE_ONLY.class)
final class Target_java_lang_StrictMath {

    @Substitute
    private static double sin(double a) {
        return StrictMathInvoker.sin(WordFactory.nullPointer(), WordFactory.nullPointer(), a);
    }

    @Substitute
    private static double cos(double a) {
        return StrictMathInvoker.cos(WordFactory.nullPointer(), WordFactory.nullPointer(), a);
    }

    @Substitute
    private static double tan(double a) {
        return StrictMathInvoker.tan(WordFactory.nullPointer(), WordFactory.nullPointer(), a);
    }

    @Substitute
    private static double asin(double a) {
        return StrictMathInvoker.asin(WordFactory.nullPointer(), WordFactory.nullPointer(), a);
    }

    @Substitute
    private static double acos(double a) {
        return StrictMathInvoker.acos(WordFactory.nullPointer(), WordFactory.nullPointer(), a);
    }

    @Substitute
    private static double atan(double a) {
        return StrictMathInvoker.atan(WordFactory.nullPointer(), WordFactory.nullPointer(), a);
    }

    @Substitute
    private static double log(double a) {
        return StrictMathInvoker.log(WordFactory.nullPointer(), WordFactory.nullPointer(), a);
    }

    @Substitute
    private static double log10(double a) {
        return StrictMathInvoker.log10(WordFactory.nullPointer(), WordFactory.nullPointer(), a);
    }

    @Substitute
    private static double sqrt(double a) {
        return StrictMathInvoker.sqrt(WordFactory.nullPointer(), WordFactory.nullPointer(), a);
    }

    // Checkstyle: stop
    @Substitute
    private static double IEEEremainder(double f1, double f2) {
        return StrictMathInvoker.IEEEremainder(WordFactory.nullPointer(), WordFactory.nullPointer(), f1, f2);
    }
    // Checkstyle: resume

    @Substitute
    private static double atan2(double y, double x) {
        return StrictMathInvoker.atan2(WordFactory.nullPointer(), WordFactory.nullPointer(), y, x);
    }

    @Substitute
    private static double sinh(double x) {
        return StrictMathInvoker.sinh(WordFactory.nullPointer(), WordFactory.nullPointer(), x);
    }

    @Substitute
    private static double cosh(double x) {
        return StrictMathInvoker.cosh(WordFactory.nullPointer(), WordFactory.nullPointer(), x);
    }

    @Substitute
    private static double tanh(double x) {
        return StrictMathInvoker.tanh(WordFactory.nullPointer(), WordFactory.nullPointer(), x);
    }

    @Substitute
    private static double expm1(double x) {
        return StrictMathInvoker.expm1(WordFactory.nullPointer(), WordFactory.nullPointer(), x);
    }

    @Substitute
    private static double log1p(double x) {
        return StrictMathInvoker.log1p(WordFactory.nullPointer(), WordFactory.nullPointer(), x);
    }
}

@CLibrary(value = "java", requireStatic = true, dependsOn = "fdlibm")
final class StrictMathInvoker {

    @CFunction(value = "Java_java_lang_StrictMath_sin", transition = CFunction.Transition.NO_TRANSITION)
    static native double sin(WordBase jnienv, WordBase clazz, double a);

    @CFunction(value = "Java_java_lang_StrictMath_cos", transition = CFunction.Transition.NO_TRANSITION)
    static native double cos(WordBase jnienv, WordBase clazz, double a);

    @CFunction(value = "Java_java_lang_StrictMath_tan", transition = CFunction.Transition.NO_TRANSITION)
    static native double tan(WordBase jnienv, WordBase clazz, double a);

    @CFunction(value = "Java_java_lang_StrictMath_asin", transition = CFunction.Transition.NO_TRANSITION)
    static native double asin(WordBase jnienv, WordBase clazz, double a);

    @CFunction(value = "Java_java_lang_StrictMath_acos", transition = CFunction.Transition.NO_TRANSITION)
    static native double acos(WordBase jnienv, WordBase clazz, double a);

    @CFunction(value = "Java_java_lang_StrictMath_atan", transition = CFunction.Transition.NO_TRANSITION)
    static native double atan(WordBase jnienv, WordBase clazz, double a);

    @CFunction(value = "Java_java_lang_StrictMath_exp", transition = CFunction.Transition.NO_TRANSITION)
    static native double exp(WordBase jnienv, WordBase clazz, double a);

    @CFunction(value = "Java_java_lang_StrictMath_log", transition = CFunction.Transition.NO_TRANSITION)
    static native double log(WordBase jnienv, WordBase clazz, double a);

    @CFunction(value = "Java_java_lang_StrictMath_log10", transition = CFunction.Transition.NO_TRANSITION)
    static native double log10(WordBase jnienv, WordBase clazz, double a);

    @CFunction(value = "Java_java_lang_StrictMath_sqrt", transition = CFunction.Transition.NO_TRANSITION)
    static native double sqrt(WordBase jnienv, WordBase clazz, double a);

    @CFunction(value = "Java_java_lang_StrictMath_cbrt", transition = CFunction.Transition.NO_TRANSITION)
    static native double cbrt(WordBase jnienv, WordBase clazz, double a);

    // Checkstyle: stop
    @CFunction(value = "Java_java_lang_StrictMath_IEEEremainder", transition = CFunction.Transition.NO_TRANSITION)
    static native double IEEEremainder(WordBase jnienv, WordBase clazz, double f1, double f2);
    // Checkstyle: resume

    @CFunction(value = "Java_java_lang_StrictMath_atan2", transition = CFunction.Transition.NO_TRANSITION)
    static native double atan2(WordBase jnienv, WordBase clazz, double y, double x);

    @CFunction(value = "Java_java_lang_StrictMath_pow", transition = CFunction.Transition.NO_TRANSITION)
    static native double pow(WordBase jnienv, WordBase clazz, double a, double b);

    @CFunction(value = "Java_java_lang_StrictMath_sinh", transition = CFunction.Transition.NO_TRANSITION)
    static native double sinh(WordBase jnienv, WordBase clazz, double x);

    @CFunction(value = "Java_java_lang_StrictMath_cosh", transition = CFunction.Transition.NO_TRANSITION)
    static native double cosh(WordBase jnienv, WordBase clazz, double x);

    @CFunction(value = "Java_java_lang_StrictMath_tanh", transition = CFunction.Transition.NO_TRANSITION)
    static native double tanh(WordBase jnienv, WordBase clazz, double x);

    @CFunction(value = "Java_java_lang_StrictMath_hypot", transition = CFunction.Transition.NO_TRANSITION)
    static native double hypot(WordBase jnienv, WordBase clazz, double x, double y);

    @CFunction(value = "Java_java_lang_StrictMath_expm1", transition = CFunction.Transition.NO_TRANSITION)
    static native double expm1(WordBase jnienv, WordBase clazz, double x);

    @CFunction(value = "Java_java_lang_StrictMath_log1p", transition = CFunction.Transition.NO_TRANSITION)
    static native double log1p(WordBase jnienv, WordBase clazz, double x);
}

/**
 * We do not have dynamic class loading (and therefore no class unloading), so it is not necessary
 * to keep the complicated code that the JDK uses. However, our simple substitutions have a drawback
 * (not a problem for now):
 * <ul>
 * <li>We do not implement the complicated state machine semantics for concurrent calls to
 * {@link #get} and {@link #remove} that are explained in {@link ClassValue#remove}.
 * </ul>
 */
@TargetClass(java.lang.ClassValue.class)
@Substitute
final class Target_java_lang_ClassValue {

    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = JavaLangSubstitutions.ClassValueInitializer.class)//
    private final ConcurrentMap<Class<?>, Object> values;

    @Substitute
    private Target_java_lang_ClassValue() {
        values = new ConcurrentHashMap<>();
    }

    /*
     * This method cannot be declared private, because we need the Java compiler to create a
     * invokevirtual bytecode when invoking it.
     */
    @KeepOriginal
    native Object computeValue(Class<?> type);

    @Substitute
    private Object get(Class<?> type) {
        Object result = values.get(type);
        if (result == null) {
            Object newValue = computeValue(type);
            if (newValue == null) {
                /* values can't store null, replace with NULL_MARKER */
                newValue = ClassValueSupport.NULL_MARKER;
            }
            Object oldValue = values.putIfAbsent(type, newValue);
            result = oldValue != null ? oldValue : newValue;
        }
        if (result == ClassValueSupport.NULL_MARKER) {
            /* replace NULL_MARKER back to real null */
            result = null;
        }
        return result;
    }

    @Substitute
    private void remove(Class<?> type) {
        values.remove(type);
    }
}

@SuppressWarnings({"deprecation", "unused"})
@TargetClass(java.lang.Compiler.class)
final class Target_java_lang_Compiler {
    @Substitute
    static Object command(Object arg) {
        return null;
    }

    @SuppressWarnings({"unused"})
    @Substitute
    static boolean compileClass(Class<?> clazz) {
        return false;
    }

    @SuppressWarnings({"unused"})
    @Substitute
    static boolean compileClasses(String string) {
        return false;
    }

    @Substitute
    static void enable() {
    }

    @Substitute
    static void disable() {
    }
}

@TargetClass(java.lang.NullPointerException.class)
final class Target_java_lang_NullPointerException {

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    @SuppressWarnings("static-method")
    private String getExtendedNPEMessage() {
        return null;
    }
}

@TargetClass(value = jdk.internal.loader.ClassLoaders.class)
final class Target_jdk_internal_loader_ClassLoaders {
    @Alias
    static native Target_jdk_internal_loader_BuiltinClassLoader bootLoader();

    @Alias
    public static native ClassLoader platformClassLoader();
}

@TargetClass(value = jdk.internal.loader.BootLoader.class)
final class Target_jdk_internal_loader_BootLoader {

    @Substitute
    static Package getDefinedPackage(String name) {
        if (name != null) {
            Target_java_lang_Package pkg = new Target_java_lang_Package(name, null, null, null,
                            null, null, null, null, null);
            return SubstrateUtil.cast(pkg, Package.class);
        } else {
            return null;
        }
    }

    @Substitute
    public static Stream<Package> packages() {
        Target_jdk_internal_loader_BuiltinClassLoader bootClassLoader = Target_jdk_internal_loader_ClassLoaders.bootLoader();
        Target_java_lang_ClassLoader systemClassLoader = SubstrateUtil.cast(bootClassLoader, Target_java_lang_ClassLoader.class);
        return systemClassLoader.packages();
    }

    @Delete("only used by #packages()")
    private static native String[] getSystemPackageNames();

    @Substitute
    private static Class<?> loadClassOrNull(String name) {
        return ClassForNameSupport.forNameOrNull(name, null);
    }

    @SuppressWarnings("unused")
    @Substitute
    private static Class<?> loadClass(Module module, String name) {
        /* The module system is not supported for now, therefore the module parameter is ignored. */
        return ClassForNameSupport.forNameOrNull(name, null);
    }

    @Substitute
    private static boolean hasClassPath() {
        return true;
    }

    /**
     * All ClassLoaderValue are reset at run time for now. See also
     * {@link Target_java_lang_ClassLoader#classLoaderValueMap} for resetting of individual class
     * loaders.
     */
    // Checkstyle: stop
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = ConcurrentHashMap.class)//
    static ConcurrentHashMap<?, ?> CLASS_LOADER_VALUE_MAP;
    // Checkstyle: resume
}

/** Dummy class to have a class with the file's name. */
public final class JavaLangSubstitutions {

    public static final class StringUtil {
        /**
         * Returns a character from a string at {@code index} position based on the encoding format.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static char charAt(String string, int index) {
            Target_java_lang_String str = SubstrateUtil.cast(string, Target_java_lang_String.class);
            byte[] value = str.value;
            if (str.isLatin1()) {
                return Target_java_lang_StringLatin1.getChar(value, index);
            } else {
                return Target_java_lang_StringUTF16.getChar(value, index);
            }
        }

        public static byte coder(String string) {
            return SubstrateUtil.cast(string, Target_java_lang_String.class).coder();
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static int hashCode(java.lang.String string) {
            return string != null ? hashCode0(string) : 0;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        private static int hashCode0(java.lang.String string) {
            Target_java_lang_String str = SubstrateUtil.cast(string, Target_java_lang_String.class);
            byte[] value = str.value;
            if (str.hash == 0 && value.length > 0) {
                boolean isLatin1 = str.isLatin1();
                if (isLatin1) {
                    str.hash = Target_java_lang_StringLatin1.hashCode(value);
                } else {
                    str.hash = Target_java_lang_StringUTF16.hashCode(value);
                }
            }
            return str.hash;
        }
    }

    public static final class ClassValueSupport {

        /**
         * Marker value that replaces null values in the
         * {@link java.util.concurrent.ConcurrentHashMap}.
         */
        public static final Object NULL_MARKER = new Object();

        final Map<ClassValue<?>, Map<Class<?>, Object>> values;

        public ClassValueSupport(Map<ClassValue<?>, Map<Class<?>, Object>> map) {
            values = map;
        }
    }

    static class ClassValueInitializer implements FieldValueTransformer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            ClassValueSupport support = ImageSingletons.lookup(ClassValueSupport.class);
            ClassValue<?> v = (ClassValue<?>) receiver;
            Map<Class<?>, Object> map = support.values.get(v);
            assert map != null;
            return map;
        }
    }
}
