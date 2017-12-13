/*
 * Copyright (c) 2007, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
import static com.oracle.svm.core.snippets.KnownIntrinsics.unsafeCast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.word.BarrieredAccess;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.UnsafeAccess;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.VMError;

@TargetClass(java.lang.Object.class)
final class Target_java_lang_Object {

    @Substitute
    @TargetElement(name = "getClass")
    private Object getClassSubst() {
        return readHub(this);
    }

    @Substitute
    @TargetElement(name = "hashCode")
    private int hashCodeSubst() {
        return System.identityHashCode(this);
    }

    @Substitute
    @TargetElement(name = "toString")
    private String toStringSubst() {
        return getClass().getName() + "@" + Long.toHexString(Word.objectToUntrackedPointer(this).rawValue());
    }

    @Substitute
    @TargetElement(name = "wait")
    private void waitSubst(long timeoutMillis) throws InterruptedException {
        Util_java_lang_Object.wait(this, timeoutMillis);
    }

    @Substitute
    @TargetElement(name = "notify")
    private void notifySubst() {
        Util_java_lang_Object.notify(this);
    }

    @Substitute
    @TargetElement(name = "notifyAll")
    private void notifyAllSubst() {
        Util_java_lang_Object.notifyAll(this);
    }
}

final class Util_java_lang_Object {

    @SuppressFBWarnings(value = {"WA_AWAIT_NOT_IN_LOOP"}, justification = "This method is a wait implementation.")
    protected static void wait(Object receiver, long timeoutMillis) throws InterruptedException {
        /* Required checks on the arguments. */
        /* (1) Have I already been interrupted? */
        if (Thread.interrupted()) {
            throw new InterruptedException("Object.wait(long), but this thread is already interrupted.");
        }
        /* (2) Is the timeout negative? (Also, convert from milliseconds to nanoseconds.) */
        final long timeoutNanos = ensureTimeoutNanos(timeoutMillis);
        if (SubstrateOptions.MultiThreaded.getValue()) {
            /* (3) Does current thread hold the lock on the receiver? */
            final Lock lock = ensureReceiverLocked(receiver, "Object.wait(long)");
            /* Find the wait/notify condition field of the receiver, which might be null. */
            final DynamicHub hub = ObjectHeader.readDynamicHubFromObject(receiver);
            final int conditionOffset = hub.getWaitNotifyOffset();
            VMError.guarantee(conditionOffset != 0, "Object.wait(long), but the receiver was not given a condition variable field.");
            Object conditionField = KnownIntrinsics.convertUnknownValue(BarrieredAccess.readObject(receiver, conditionOffset), Condition.class);
            final Condition condition;
            if (conditionField == null) {
                /* Get a new Condition object from the Lock. */
                final Condition newCondition = lock.newCondition();
                /* CompareAndSwap it in place of the null at the conditionOffset. */
                if (!UnsafeAccess.UNSAFE.compareAndSwapObject(receiver, conditionOffset, null, newCondition)) {
                    /* If I lose the race, use the condition some other thread installed. */
                    conditionField = KnownIntrinsics.convertUnknownValue(BarrieredAccess.readObject(receiver, conditionOffset), Condition.class);
                    condition = KnownIntrinsics.unsafeCast(conditionField, Condition.class);
                } else {
                    condition = newCondition;
                }
            } else {
                /* Use the existing Condition object. */
                condition = KnownIntrinsics.unsafeCast(conditionField, Condition.class);
            }
            /* Choose between await() or awaitNanos(long). */
            if (timeoutNanos == 0L) {
                condition.await();
            } else {
                condition.awaitNanos(timeoutNanos);
            }
        } else {
            /* Single-threaded wait. */
            if (timeoutMillis == 0) {
                Thread.sleep(Long.MAX_VALUE);
            } else {
                Thread.sleep(timeoutMillis);
            }
        }
    }

    protected static void notify(Object receiver) {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            /* Make sure the current thread holds the lock on the receiver. */
            ensureReceiverLocked(receiver, "Object.notify()");
            /* Find the wait/notify condition field of the receiver. */
            final DynamicHub hub = ObjectHeader.readDynamicHubFromObject(receiver);
            final int conditionOffset = hub.getWaitNotifyOffset();
            VMError.guarantee(conditionOffset != 0, "Object.wait(long), but the receiver was not given a condition variable field.");
            Object conditionField = KnownIntrinsics.convertUnknownValue(BarrieredAccess.readObject(receiver, conditionOffset), Condition.class);
            final Condition condition;
            /* If the receiver does not have a condition field, then it has not been waited on. */
            if (conditionField != null) {
                condition = KnownIntrinsics.unsafeCast(conditionField, Condition.class);
                condition.signal();
            }
        } else {
            /* Single-threaded notify() is a no-op. */
        }
    }

    protected static void notifyAll(Object receiver) {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            /* Make sure the current thread holds the lock on the receiver. */
            ensureReceiverLocked(receiver, "Object.notifyAll()");
            /* Find the wait/notify condition field of the receiver. */
            final DynamicHub hub = ObjectHeader.readDynamicHubFromObject(receiver);
            final int conditionOffset = hub.getWaitNotifyOffset();
            VMError.guarantee(conditionOffset != 0, "Object.wait(long), but the receiver was not given a condition variable field.");
            Object conditionField = KnownIntrinsics.convertUnknownValue(BarrieredAccess.readObject(receiver, conditionOffset), Condition.class);
            final Condition condition;
            /* If the receiver does not have a condition field, then it has not been waited on. */
            if (conditionField != null) {
                condition = KnownIntrinsics.unsafeCast(conditionField, Condition.class);
                condition.signalAll();
            }
        } else {
            /* Single-threaded notifyAll() is a no-op. */
        }
    }

    /** Return the lock of the receiver. */
    private static Lock ensureReceiverLocked(Object receiver, String methodName) {
        final DynamicHub hub = ObjectHeader.readDynamicHubFromObject(receiver);
        final int monitorOffset = hub.getMonitorOffset();
        VMError.guarantee(monitorOffset != 0, "Util_java_lang_Object.ensureReceiverLocked, but the receiver was not given a lock field.");
        final Object monitorField = BarrieredAccess.readObject(receiver, monitorOffset);
        final ReentrantLock lockObject = KnownIntrinsics.unsafeCast(monitorField, ReentrantLock.class);
        /* If the monitor field is null then it has not been locked by this thread. */
        /* If there is a monitor, make sure it is locked by this thread. */
        if ((lockObject == null) || (!lockObject.isHeldByCurrentThread())) {
            throw new IllegalMonitorStateException(methodName + ", but the receiver is not locked by the current thread.");
        }
        return lockObject;
    }

    /** Return the timeout in nanoseconds. */
    private static long ensureTimeoutNanos(long timeoutMillis) {
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("Object.wait(long), but timeout is negative.");
        }
        final long result = TimeUtils.millisToNanos(timeoutMillis);
        return result;
    }
}

@TargetClass(java.lang.ClassLoader.class)
@Substitute
@SuppressWarnings("static-method")
final class Target_java_lang_ClassLoader {
    /*
     * Substituting the whole class allows us to have fields of declared type ClassLoader, but still
     * get an error if anyone tries to access a field or call a method on it that we have not
     * explicitly substituted below.
     */

    @Substitute
    private InputStream getResourceAsStream(String name) {
        return getSystemResourceAsStream(name);
    }

    @Substitute
    private static InputStream getSystemResourceAsStream(String name) {
        List<byte[]> arr = Resources.get(name);
        return arr == null ? null : new ByteArrayInputStream(arr.get(0));
    }

    @Substitute
    private URL getResource(String name) {
        return getSystemResource(name);
    }

    @Substitute
    private static URL getSystemResource(String name) {
        List<byte[]> arr = Resources.get(name);
        return arr == null ? null : Resources.createURL(name, new ByteArrayInputStream(arr.get(0)));
    }

    @Substitute
    private Enumeration<URL> getResources(String name) {
        return getSystemResources(name);
    }

    @Substitute
    private static Enumeration<URL> getSystemResources(String name) {
        List<byte[]> arr = Resources.get(name);
        if (arr == null) {
            return Collections.emptyEnumeration();
        }
        List<URL> res = new ArrayList<>(arr.size());
        for (byte[] data : arr) {
            res.add(Resources.createURL(name, new ByteArrayInputStream(data)));
        }
        return Collections.enumeration(res);
    }

    @Substitute
    public static ClassLoader getSystemClassLoader() {
        /*
         * ClassLoader.getSystemClassLoader() is used as a parameter for Class.forName(String,
         * boolean, ClassLoader) which is implemented as ClassForNameSupport.forName(name) and
         * ignores the class loader.
         */
        return null;
    }

    @Substitute
    @SuppressWarnings("unused")
    static void loadLibrary(Class<?> fromClass, String name, boolean isAbsolute) {
        NativeLibrarySupport.singleton().loadLibrary(name, isAbsolute);
    }

    @Substitute
    private Class<?> loadClass(String name) throws ClassNotFoundException {
        return ClassForNameSupport.forName(name);
    }
}

@TargetClass(className = "java.lang.ClassLoaderHelper")
final class Target_java_lang_ClassLoaderHelper {
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
        for (Enum<?> e : unsafeCast(enumType, DynamicHub.class).getEnumConstantsShared()) {
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
}

@TargetClass(java.lang.String.class)
final class Target_java_lang_String {

    @Substitute
    public String intern() {
        String thisStr = unsafeCast(this, String.class);
        return ImageSingletons.lookup(StringInternSupport.class).intern(thisStr);
    }
}

@TargetClass(java.lang.Throwable.class)
@SuppressWarnings({"unused"})
final class Target_java_lang_Throwable {

    @Alias @RecomputeFieldValue(kind = Reset)//
    private Object backtrace;

    @Alias @RecomputeFieldValue(kind = Reset)//
    private StackTraceElement[] stackTrace;

    @Alias String detailMessage;

    /*
     * Suppressed exception handling is disabled for now.
     */
    @Substitute
    private void addSuppressed(Throwable exception) {
        /*
         * This method is called frequently from try-with-resource blocks. The original
         * implementation performs allocations, which are problematic when allocations are disabled.
         * For now, we just do nothing until someone needs suppressed exception handling.
         */
    }

    @Substitute
    @NeverInline("Prevent inlining in Truffle compilations")
    private Object fillInStackTrace() {
        Pointer sp = KnownIntrinsics.readCallerStackPointer();
        CodePointer ip = KnownIntrinsics.readReturnAddress();

        StackTraceBuilder stackTraceBuilder = new StackTraceBuilder();
        JavaStackWalker.walkCurrentThread(sp, ip, stackTraceBuilder);
        this.stackTrace = stackTraceBuilder.getTrace();

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

    @Substitute
    int getStackTraceDepth() {
        if (stackTrace != null) {
            return stackTrace.length;
        }
        return 0;
    }

    @Substitute
    StackTraceElement getStackTraceElement(int index) {
        if (stackTrace == null) {
            throw new IndexOutOfBoundsException();
        }
        return stackTrace[index];
    }
}

@TargetClass(java.lang.Runtime.class)
@SuppressWarnings({"static-method"})
final class Target_java_lang_Runtime {

    @Substitute
    private void addShutdownHook(java.lang.Thread hook) {
        RuntimeSupport.getRuntimeSupport().addShutdownHook(hook);
    }

    @Substitute
    private boolean removeShutdownHook(java.lang.Thread hook) {
        return RuntimeSupport.getRuntimeSupport().removeShutdownHook(hook);
    }

    @Substitute
    public void loadLibrary(String libname) {
        // Substituted because the original is caller-sensitive, which we don't support
        loadLibrary0(null, libname);
    }

    @Substitute
    public void load(String filename) {
        // Substituted because the original is caller-sensitive, which we don't support
        load0(null, filename);
    }

    // Checkstyle: stop
    @Alias
    synchronized native void loadLibrary0(Class<?> fromClass, String libname);

    @Alias
    synchronized native void load0(Class<?> fromClass, String libname);
    // Checkstyle: resume
}

/**
 * Provides replacement values for the {@link System#out}, {@link System#err}, and {@link System#in}
 * streams at run time. We want a fresh set of objects, so that any buffers filled during image
 * generation, as well as any redirection of the streams to new values, do not change the behavior
 * at run time.
 *
 * We use an {@link Feature.DuringSetupAccess#registerObjectReplacer object replacer} because the
 * streams can be cached in other instance and static fields in addition to the fields in
 * {@link System}. We do not know all these places, so we do now know where to place
 * {@link RecomputeFieldValue} annotations.
 */
@AutomaticFeature
class SystemFeature implements Feature {
    private static final PrintStream newOut = new PrintStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 128), true);
    private static final PrintStream newErr = new PrintStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.err), 128), true);
    private static final InputStream newIn = new BufferedInputStream(new FileInputStream(FileDescriptor.in));

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(SystemFeature::replaceStreams);
    }

    // Checkstyle: stop
    private static Object replaceStreams(Object object) {
        if (object == System.out) {
            return newOut;
        } else if (object == System.err) {
            return newErr;
        } else if (object == System.in) {
            return newIn;
        } else {
            return object;
        }
    }
    // Checkstyle: resume
}

@TargetClass(java.lang.System.class)
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
    private static void exit(int status) {
        ConfigurationValues.getOSInterface().exit(status);
    }

    @Substitute
    private static int identityHashCode(Object obj) {
        if (obj == null) {
            return 0;
        }
        DynamicHub hub = KnownIntrinsics.readHub(obj);
        int hashCodeOffset = hub.getHashCodeOffset();
        if (hashCodeOffset == 0) {
            throw VMError.shouldNotReachHere("identityHashCode called on illegal object");
        }
        UnsignedWord hashCodeOffsetWord = WordFactory.unsigned(hashCodeOffset);
        int hashCode = ObjectAccess.readInt(obj, hashCodeOffsetWord);
        if (hashCode != 0) {
            return hashCode;
        }

        /* On the first invocation for an object create a new hash code. */
        hashCode = IdentityHashCodeSupport.generateHashCode();

        if (!UnsafeAccess.UNSAFE.compareAndSwapInt(obj, hashCodeOffset, 0, hashCode)) {
            /* We lost the race, so there now must be a hash code installed from another thread. */
            hashCode = ObjectAccess.readInt(obj, hashCodeOffsetWord);
        }
        VMError.guarantee(hashCode != 0, "Missing identity hash code");
        return hashCode;
    }

    /* Ensure that we do not leak the full set of properties from the image generator. */
    @Delete //
    private static Properties props;

    @Substitute
    private static Properties getProperties() {
        return ImageSingletons.lookup(SystemPropertiesSupport.class).getProperties();
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
    private static String getProperty(String key, String def) {
        String result = getProperty(key);
        return result != null ? result : def;
    }

    @Alias
    private static native void checkKey(String key);

    @Substitute
    public static void loadLibrary(String libname) {
        // Substituted because the original is caller-sensitive, which we don't support
        Runtime.getRuntime().loadLibrary(libname);
    }

    @Substitute
    public static void load(String filename) {
        // Substituted because the original is caller-sensitive, which we don't support
        Runtime.getRuntime().load(filename);
    }
}

@TargetClass(java.lang.StrictMath.class)
@CLibrary("strictmath")
final class Target_java_lang_StrictMath {
    // Checkstyle: stop

    @Substitute
    @CFunction(value = "StrictMath_sin", transition = CFunction.Transition.NO_TRANSITION)
    private static native double sin(double a);

    @Substitute
    @CFunction(value = "StrictMath_cos", transition = CFunction.Transition.NO_TRANSITION)
    private static native double cos(double a);

    @Substitute
    @CFunction(value = "StrictMath_tan", transition = CFunction.Transition.NO_TRANSITION)
    private static native double tan(double a);

    @Substitute
    @CFunction(value = "StrictMath_asin", transition = CFunction.Transition.NO_TRANSITION)
    private static native double asin(double a);

    @Substitute
    @CFunction(value = "StrictMath_acos", transition = CFunction.Transition.NO_TRANSITION)
    private static native double acos(double a);

    @Substitute
    @CFunction(value = "StrictMath_atan", transition = CFunction.Transition.NO_TRANSITION)
    private static native double atan(double a);

    @Substitute
    @CFunction(value = "StrictMath_exp", transition = CFunction.Transition.NO_TRANSITION)
    private static native double exp(double a);

    @Substitute
    @CFunction(value = "StrictMath_log", transition = CFunction.Transition.NO_TRANSITION)
    private static native double log(double a);

    @Substitute
    @CFunction(value = "StrictMath_log10", transition = CFunction.Transition.NO_TRANSITION)
    private static native double log10(double a);

    @Substitute
    @CFunction(value = "StrictMath_sqrt", transition = CFunction.Transition.NO_TRANSITION)
    private static native double sqrt(double a);

    @Substitute
    @CFunction(value = "StrictMath_cbrt", transition = CFunction.Transition.NO_TRANSITION)
    private static native double cbrt(double a);

    @Substitute
    @CFunction(value = "StrictMath_IEEEremainder", transition = CFunction.Transition.NO_TRANSITION)
    private static native double IEEEremainder(double f1, double f2);

    @Substitute
    @CFunction(value = "StrictMath_atan2", transition = CFunction.Transition.NO_TRANSITION)
    private static native double atan2(double y, double x);

    @Substitute
    @CFunction(value = "StrictMath_pow", transition = CFunction.Transition.NO_TRANSITION)
    private static native double pow(double a, double b);

    @Substitute
    @CFunction(value = "StrictMath_sinh", transition = CFunction.Transition.NO_TRANSITION)
    private static native double sinh(double x);

    @Substitute
    @CFunction(value = "StrictMath_cosh", transition = CFunction.Transition.NO_TRANSITION)
    private static native double cosh(double x);

    @Substitute
    @CFunction(value = "StrictMath_tanh", transition = CFunction.Transition.NO_TRANSITION)
    private static native double tanh(double x);

    @Substitute
    @CFunction(value = "StrictMath_hypot", transition = CFunction.Transition.NO_TRANSITION)
    private static native double hypot(double x, double y);

    @Substitute
    @CFunction(value = "StrictMath_expm1", transition = CFunction.Transition.NO_TRANSITION)
    private static native double expm1(double x);

    @Substitute
    @CFunction(value = "StrictMath_log1p", transition = CFunction.Transition.NO_TRANSITION)
    private static native double log1p(double x);
    // Checkstyle: resume
}

/**
 * We do not have dynamic class loading (and therefore no class unloading), so it is not necessary
 * to keep the complicated code that the JDK uses. However, our simple substitutions have two
 * drawbacks (but they are not a problem for now):
 * <ul>
 * <li>We do not persist values put into the ClassValue during image generation, i.e., we always
 * start with an empty ClassValue at run time.
 * <li>We do not implement the complicated state machine semantics for concurrent calls to
 * {@link #get} and {@link #remove} that are explained in {@link ClassValue#remove}.
 * </ul>
 */
@TargetClass(java.lang.ClassValue.class)
@Substitute
final class Target_java_lang_ClassValue {

    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = ConcurrentHashMap.class)//
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
            Object oldValue = values.putIfAbsent(type, newValue);
            result = oldValue != null ? oldValue : newValue;
        }
        return result;
    }

    @Substitute
    private void remove(Class<?> type) {
        values.remove(type);
    }
}

@TargetClass(java.lang.Compiler.class)
final class Target_java_lang_Compiler {
    @Substitute
    static Object command(Object arg) {
        if (arg instanceof Object[]) {
            Object[] args = (Object[]) arg;
            if (args.length > 0) {
                Object arg0 = args[0];
                if (arg0 instanceof String) {
                    String cmd = (String) arg0;
                    Object[] cmdargs = Arrays.copyOfRange(args, 1, args.length);
                    RuntimeSupport rs = RuntimeSupport.getRuntimeSupport();
                    return rs.runCommand(cmd, cmdargs);
                }
            }
        }
        throw new IllegalArgumentException("Argument to java.lang.Compiler.command(Object) must be an Object[] " +
                        "with the first element being a String providing the name of the SVM command to run " +
                        "and subsequent elements being the arguments to the command");
    }
}

@TargetClass(className = "java.lang.ApplicationShutdownHooks")
final class Target_java_lang_ApplicationShutdownHooks {

    /**
     * Re-initialize the map of registered hooks, because any hooks registered during native image
     * construction can not survive into the running image.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = IdentityHashMap.class)//
    private static IdentityHashMap<Thread, Thread> hooks;

}

@TargetClass(className = "java.lang.Shutdown")
final class Target_java_lang_Shutdown {

    // { Allow all upper-case name: Checkstyle: stop
    @Alias//
    static int MAX_SYSTEM_HOOKS;
    // } Checkstyle: resume

    /**
     * Re-initialize the map of registered hooks, because any hooks registered during native image
     * construction can not survive into the running image.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias)//
    static Runnable[] hooks = new Runnable[MAX_SYSTEM_HOOKS];

    @Substitute
    static void halt0(@SuppressWarnings("unused") int status) {
        throw VMError.unsupportedFeature("java.lang.Shutdown.halt0(int)");
    }

    /* Wormhole for invoking java.lang.ref.Finalizer.runAllFinalizers */
    @Substitute
    static void runAllFinalizers() {
        throw VMError.unsupportedFeature("java.lang.Shudown.runAllFinalizers()");
    }
}

/** Dummy class to have a class with the file's name. */
public final class JavaLangSubstitutions {
}
