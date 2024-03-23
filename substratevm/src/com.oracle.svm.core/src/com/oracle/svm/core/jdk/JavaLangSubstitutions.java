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
import java.security.Permission;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Processor;
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

import jdk.graal.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import jdk.graal.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation;
import jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;
import jdk.internal.loader.ClassLoaderValue;
import jdk.internal.module.ServicesCatalog;

@TargetClass(java.lang.Object.class)
@SuppressWarnings("static-method")
final class Target_java_lang_Object {

    @Substitute
    @TargetElement(name = "getClass")
    private DynamicHub getClassSubst() {
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
        Object[] enumConstants = DynamicHub.fromClass(enumType).getEnumConstantsShared();
        if (enumConstants == null) {
            throw new IllegalArgumentException(enumType.getName() + " is not an enum type");
        }
        for (Object o : enumConstants) {
            Enum<?> e = (Enum<?>) o;
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

    // Checkstyle: stop
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.None, isFinal = true) //
    public static boolean COMPACT_STRINGS;
    // Checkstyle: resume

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.None, isFinal = true) //
    public static byte LATIN1;

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
    public byte[] value;

    @Alias //
    int hash;

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

    @Alias //
    @TargetElement(onlyWith = JDK22OrLater.class) //
    @RecomputeFieldValue(kind = Kind.FromAlias, isFinal = true) //
    static boolean jfrTracing = false;

    @Alias @RecomputeFieldValue(kind = Reset)//
    Object backtrace;

    @Alias//
    Throwable cause;

    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = ThrowableStackTraceFieldValueTransformer.class)//
    StackTraceElement[] stackTrace;

    @Alias String detailMessage;

    // Checkstyle: stop
    @Alias//
    static StackTraceElement[] UNASSIGNED_STACK;
    // Checkstyle: resume

    /**
     * Fills in the execution stack trace. Our {@link Throwable#fillInStackTrace()} cannot be
     * declared {@code synchronized} because it might be called in a {@link VMOperation} (via one of
     * the {@link Throwable} constructors), where we are not allowed to block. We work around that
     * in {@link #fillInStackTrace(int)}.
     */
    @Substitute
    public Target_java_lang_Throwable fillInStackTrace() {
        return fillInStackTrace(0);
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
        if (VMOperation.isInProgress()) {
            if (MonitorSupport.singleton().isLockedByAnyThread(this)) {
                /*
                 * The Throwable is locked. We cannot safely fill in the stack trace. Do nothing and
                 * accept that we will not get a stack trace.
                 */
            } else {
                /*
                 * The Throwable is not locked. We can safely fill the stack trace without
                 * synchronization because we VMOperation is single threaded.
                 */

                if (stackTrace != null || backtrace != null) {
                    backtrace = null;

                    BacktraceVisitor visitor = new BacktraceVisitor();
                    JavaThreads.visitCurrentStackFrames(visitor);
                    backtrace = visitor.getArray();

                    stackTrace = UNASSIGNED_STACK;
                }
            }
        } else {
            /* Execute with synchronization. This is the default case. */
            synchronized (this) {
                if (stackTrace != null || backtrace != null) {
                    backtrace = null;

                    BacktraceVisitor visitor = new BacktraceVisitor();
                    JavaThreads.visitCurrentStackFrames(visitor);
                    backtrace = visitor.getArray();

                    stackTrace = UNASSIGNED_STACK;
                }
            }
        }
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
    static StackTraceElement[] of(Object x, int depth) {
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
        return Processor.singleton().getActiveProcessorCount();
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

    /**
     * Force System.Never in case it was set at build time via the `-Djava.security.manager=allow`
     * passed to the image builder.
     */
    @Alias @RecomputeFieldValue(kind = Kind.FromAlias, isFinal = true) //
    private static int allowSecurityManager = 1;

    /**
     * We do not support the {@link SecurityManager} so this method must throw a
     * {@link SecurityException} when 'java.security.manager' is set to anything but
     * <code>disallow</code>.
     * 
     * @see System#setSecurityManager(SecurityManager)
     * @see SecurityManager
     */
    @Substitute
    @SuppressWarnings({"removal", "javadoc"})
    private static void setSecurityManager(SecurityManager sm) {
        if (sm != null) {
            /* Read the property collected at isolate creation as that is what happens on the JVM */
            String smp = SystemPropertiesSupport.singleton().getSavedProperties().get("java.security.manager");
            if (smp != null && !smp.equals("disallow")) {
                throw new SecurityException("Setting the SecurityManager is not supported by Native Image");
            } else {
                throw new UnsupportedOperationException(
                                "The Security Manager is deprecated and will be removed in a future release");
            }
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

@TargetClass(java.lang.NullPointerException.class)
final class Target_java_lang_NullPointerException {

    /**
     * {@link NullPointerException} overrides {@link Throwable#fillInStackTrace()} with a
     * {@code synchronized} method which is not permitted in a {@link VMOperation}. We hand over to
     * {@link Target_java_lang_Throwable#fillInStackTrace(int)} which already handles this properly.
     */
    @Substitute
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    Target_java_lang_Throwable fillInStackTrace() {
        return SubstrateUtil.cast(this, Target_java_lang_Throwable.class).fillInStackTrace(0);
    }

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
    // Checkstyle: stop
    @Delete //
    static String JAVA_HOME;
    // Checkstyle: resume

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
        return ClassForNameSupport.singleton().forNameOrNull(name, null);
    }

    @SuppressWarnings("unused")
    @Substitute
    private static Class<?> loadClass(Module module, String name) {
        /* The module system is not supported for now, therefore the module parameter is ignored. */
        return ClassForNameSupport.singleton().forNameOrNull(name, null);
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

@TargetClass(value = jdk.internal.logger.LoggerFinderLoader.class)
final class Target_jdk_internal_logger_LoggerFinderLoader {
    // Checkstyle: stop
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset, isFinal = true)//
    static Permission READ_PERMISSION;
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
