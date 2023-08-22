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
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
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
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jdk.JavaLangSubstitutions.ClassValueSupport;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.loader.ClassLoaderValue;
import jdk.internal.module.ServicesCatalog;

@TargetClass(java.lang.Object.class)
@SuppressWarnings("static-method")
final class Target_java_lang_Object {

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

@TargetClass(className = "jdk.internal.loader.ClassLoaderHelper")
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

    @Alias @RecomputeFieldValue(kind = Kind.None, isFinal = true) //
    byte[] value;

    @Alias //
    int hash;

    /**
     * This is a copy of String.split from the JDK, but with the fastpath loop factored out into a
     * separate method. This allows inlining and constant folding of the condition for call sites
     * where the regex is a constant (which is a common usage pattern).
     *
     * JDK-8262994 should make that refactoring in OpenJDK, after which this substitution can be
     * removed.
     */
    @Substitute
    public String[] split(String regex, int limit) {
        /*
         * fastpath if the regex is a (1) one-char String and this character is not one of the
         * RegEx's meta characters ".$|()[{^?*+\\", or (2) two-char String and the first char is the
         * backslash and the second is not the ascii digit or ascii letter.
         */
        char ch = 0;
        if (((regex.length() == 1 &&
                        ".$|()[{^?*+\\".indexOf(ch = regex.charAt(0)) == -1) ||
                        (regex.length() == 2 &&
                                        regex.charAt(0) == '\\' &&
                                        (((ch = regex.charAt(1)) - '0') | ('9' - ch)) < 0 &&
                                        ((ch - 'a') | ('z' - ch)) < 0 &&
                                        ((ch - 'A') | ('Z' - ch)) < 0)) &&
                        (ch < Character.MIN_HIGH_SURROGATE ||
                                        ch > Character.MAX_LOW_SURROGATE)) {
            return StringHelper.simpleSplit(SubstrateUtil.cast(this, String.class), limit, ch);
        }
        return Pattern.compile(regex).split(SubstrateUtil.cast(this, String.class), limit);
    }
}

final class StringHelper {
    static String[] simpleSplit(String that, int limit, char ch) {
        int off = 0;
        int next = 0;
        boolean limited = limit > 0;
        ArrayList<String> list = new ArrayList<>();
        while ((next = that.indexOf(ch, off)) != -1) {
            if (!limited || list.size() < limit - 1) {
                list.add(that.substring(off, next));
                off = next + 1;
            } else {    // last one
                // assert (list.size() == limit - 1);
                int last = that.length();
                list.add(that.substring(off, last));
                off = last;
                break;
            }
        }
        // If no match was found, return this
        if (off == 0) {
            return new String[]{that};
        }
        // Add remaining segment
        if (!limited || list.size() < limit) {
            list.add(that.substring(off, that.length()));
        }
        // Construct result
        int resultSize = list.size();
        if (limit == 0) {
            while (resultSize > 0 && list.get(resultSize - 1).isEmpty()) {
                resultSize--;
            }
        }
        String[] result = new String[resultSize];
        return list.subList(0, resultSize).toArray(result);
    }
}

@TargetClass(className = "java.lang.StringLatin1")
final class Target_java_lang_StringLatin1 {

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static native char getChar(byte[] val, int index);
}

@TargetClass(className = "java.lang.StringUTF16")
final class Target_java_lang_StringUTF16 {

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static native char getChar(byte[] val, int index);
}

@TargetClass(java.lang.Throwable.class)
@Platforms(InternalPlatform.NATIVE_ONLY.class)
@SuppressWarnings({"unused"})
final class Target_java_lang_Throwable {

    @Alias @RecomputeFieldValue(kind = Reset)//
    Object backtrace;

    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = ThrowableStackTraceFieldValueTransformer.class)//
    StackTraceElement[] stackTrace;

    @Alias String detailMessage;

    // Checkstyle: stop
    @Alias//
    static StackTraceElement[] UNASSIGNED_STACK;
    // Checkstyle: resume

    /**
     * Fills in the execution stack trace. {@link Throwable#fillInStackTrace()} cannot be
     * {@code synchronized}, because it might be called in a {@link VMOperation} (via one of the
     * {@link Throwable} constructors), where we are not allowed to block. To work around that, we
     * do the following:
     * <ul>
     * <li>If we are not in a {@link VMOperation}, it executes {@link #fillInStackTrace(int)} in a
     * block {@code synchronized} by the supplied {@link Throwable}. This is the default case.
     * <li>If we are in a {@link VMOperation}, it checks if the {@link Throwable} is currently
     * locked. If not, {@link #fillInStackTrace(int)} is called without synchronization, which is
     * safe in a {@link VMOperation}. If it is locked, we do not do any filling (and thus do not
     * collect the stack trace).
     * </ul>
     */
    @Substitute
    @NeverInline("Starting a stack walk in the caller frame")
    public Target_java_lang_Throwable fillInStackTrace() {
        if (VMOperation.isInProgress()) {
            if (MonitorSupport.singleton().isLockedByAnyThread(this)) {
                /*
                 * The Throwable is locked. We cannot safely fill in the stack trace. Do nothing and
                 * accept that we will not get a stack track.
                 */
            } else {
                /*
                 * The Throwable is not locked. We can safely fill the stack trace without
                 * synchronization because we VMOperation is single threaded.
                 */

                /* Copy of `Throwable#fillInStackTrace()` */
                if (stackTrace != null || backtrace != null) {
                    fillInStackTrace(0);
                    stackTrace = UNASSIGNED_STACK;
                }
            }
        } else {
            synchronized (this) {
                /* Copy of `Throwable#fillInStackTrace()` */
                if (stackTrace != null || backtrace != null) {
                    fillInStackTrace(0);
                    stackTrace = UNASSIGNED_STACK;
                }
            }
        }
        return this;
    }

    /**
     * Records the execution stack in an internal format. The information is transformed into a
     * {@link StackTraceElement} array in
     * {@link Target_java_lang_StackTraceElement#of(Object, int)}.
     *
     * @param dummy to change signature
     */
    @Substitute
    @NeverInline("Starting a stack walk in the caller frame")
    Target_java_lang_Throwable fillInStackTrace(int dummy) {
        /*
         * Start out by clearing the backtrace for this object, in case the VM runs out of memory
         * while allocating the stack trace.
         */
        backtrace = null;

        BacktraceVisitor visitor = new BacktraceVisitor();
        JavaThreads.visitCurrentStackFrames(visitor);
        backtrace = visitor.getArray();
        return this;
    }
}

final class ThrowableStackTraceFieldValueTransformer implements FieldValueTransformer {

    private static final StackTraceElement[] UNASSIGNED_STACK = ReflectionUtil.readStaticField(Throwable.class, "UNASSIGNED_STACK");

    @Override
    public Object transform(Object receiver, Object originalValue) {
        if (originalValue == null) { // Immutable stack
            return null;
        }
        return UNASSIGNED_STACK;
    }
}

@TargetClass(java.lang.StackTraceElement.class)
@Platforms(InternalPlatform.NATIVE_ONLY.class)
final class Target_java_lang_StackTraceElement {
    /**
     * Constructs the {@link StackTraceElement} array from a backtrace.
     *
     * @param x backtrace stored in {@link Target_java_lang_Throwable#backtrace}
     * @param depth ignored
     */
    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    static StackTraceElement[] of(Object x, int depth) {
        return StackTraceBuilder.build((long[]) x);
    }

    /**
     * Constructs the {@link StackTraceElement} array from a {@link Throwable}.
     *
     * @param t the {@link Throwable} object
     * @param depth ignored
     */
    @Substitute
    @TargetElement(onlyWith = JDK17OrEarlier.class)
    static StackTraceElement[] of(Target_java_lang_Throwable t, int depth) {
        Object x = t.backtrace;
        return StackTraceBuilder.build((long[]) x);
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
        return SystemPropertiesSupport.singleton().getProperties();
    }

    @Substitute
    private static void setProperties(Properties props) {
        SystemPropertiesSupport.singleton().setProperties(props);
    }

    @Substitute
    public static String setProperty(String key, String value) {
        checkKey(key);
        return SystemPropertiesSupport.singleton().setProperty(key, value);
    }

    @Substitute
    private static String getProperty(String key) {
        checkKey(key);
        return SystemPropertiesSupport.singleton().getProperty(key);
    }

    @Substitute
    public static String clearProperty(String key) {
        checkKey(key);
        return SystemPropertiesSupport.singleton().clearProperty(key);
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

@TargetClass(value = StrictMath.class, onlyWith = JDK20OrEarlier.class)
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
@TargetClass(className = "java.lang.Compiler", onlyWith = JDK20OrEarlier.class)
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

    @SuppressWarnings("unused")
    @Substitute
    private static void loadLibrary(String name) {
        System.loadLibrary(name);
    }

    @Substitute
    private static boolean hasClassPath() {
        return true;
    }

    @Substitute
    public static URL findResource(String name) {
        return ResourcesHelper.nameToResourceURL(name);
    }

    @Substitute
    public static Enumeration<URL> findResources(String name) {
        return ResourcesHelper.nameToResourceEnumerationURLs(name);
    }

    /**
     * Most {@link ClassLoaderValue}s are reset. For the list of preserved transformers see
     * {@link ClassLoaderValueMapFieldValueTransformer}.
     */
    // Checkstyle: stop
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ClassLoaderValueMapFieldValueTransformer.class, isFinal = true)//
    static ConcurrentHashMap<?, ?> CLASS_LOADER_VALUE_MAP;
    // Checkstyle: resume
}

final class ClassLoaderValueMapFieldValueTransformer implements FieldValueTransformer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        if (originalValue == null) {
            return null;
        }

        ConcurrentHashMap<?, ?> original = (ConcurrentHashMap<?, ?>) originalValue;
        List<ClassLoaderValue<?>> clvs = Arrays.asList(
                        ReflectionUtil.readField(ServicesCatalog.class, "CLV", null),
                        ReflectionUtil.readField(ModuleLayer.class, "CLV", null));

        var res = new ConcurrentHashMap<>();
        for (ClassLoaderValue<?> clv : clvs) {
            if (clv == null) {
                throw VMError.shouldNotReachHere("Field must not be null. Please check what changed in the JDK.");
            }
            var catalog = original.get(clv);
            if (catalog != null) {
                res.put(clv, catalog);
            }
        }

        return res;
    }
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
