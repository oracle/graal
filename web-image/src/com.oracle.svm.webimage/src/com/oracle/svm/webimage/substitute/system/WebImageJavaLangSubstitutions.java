/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.substitute.system;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.AccessibleObject;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;

import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.jdk.resources.NativeImageResourceFileSystem;
import com.oracle.svm.core.jdk.resources.ResourceStorageEntryBase;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.webimage.functionintrinsics.JSFunctionIntrinsics;
import com.oracle.svm.webimage.functionintrinsics.JSInternalErrors;
import com.oracle.svm.webimage.platform.WebImageJSPlatform;
import com.oracle.svm.webimage.platform.WebImagePlatform;
import com.oracle.svm.webimage.platform.WebImageWasmGCPlatform;
import org.graalvm.nativeimage.ProcessProperties;

/*
 * Checkstyle: stop method name check
 * Method names have to match the target class and are not under our control
 */
public class WebImageJavaLangSubstitutions {
    // dummy
}

@TargetClass(java.net.Socket.class)
@Delete
final class Target_java_net_Socket_Web {
}

@TargetClass(java.lang.SecurityManager.class)
@SuppressWarnings("all")
final class Target_java_lang_SecurityManager_Web {

    @Substitute
    @NeverInline("Starting a stack walk in the caller frame")
    Class<?>[] getClassContext() {
        // TODO: 35973 - replace/remove this, once walking the stack is supported
        return new Class<?>[0];
    }
}

@TargetClass(java.util.TimeZone.class)
@SuppressWarnings("unused")
final class Target_java_util_TimeZone_Web {

    @Substitute
    private static String getSystemTimeZoneID(String javaHome) {
        return "UTC";
    }

    @Substitute
    private static String getSystemGMTOffsetID() {
        return "GMT+00:00";
    }
}

@TargetClass(com.oracle.svm.core.util.VMError.class)
@Platforms(WebImageJSPlatform.class)
@SuppressWarnings("unused")
final class Target_com_oracle_svm_core_util_VMError_Web {

    /*
     * m These substitutions let the svm print the real message. The original VMError methods throw
     * a VMError, which let the svm just print the type name of VMError.
     */
    @Substitute
    private static RuntimeException shouldNotReachHere(String msg) {
        throw JSInternalErrors.shouldNotReachHere(msg);
    }

    @Substitute
    private static RuntimeException shouldNotReachHere(Throwable ex) {
        throw shouldNotReachHere(ex.toString());
    }

    @Substitute
    private static RuntimeException shouldNotReachHere(String msg, Throwable ex) {
        throw shouldNotReachHere("should not reach here: " + msg + "\n" + ex);
    }

    @Substitute
    private static RuntimeException shouldNotReachHereSubstitution() {
        throw JSInternalErrors.shouldNotReachHere(VMError.msgShouldNotReachHereSubstitution);
    }

    @Substitute
    private static RuntimeException shouldNotReachHereUnexpectedInput(Object input) {
        throw JSInternalErrors.shouldNotReachHere(VMError.msgShouldNotReachHereUnexpectedInput);
    }

    @Substitute
    private static RuntimeException shouldNotReachHereAtRuntime() {
        throw JSInternalErrors.shouldNotReachHere(VMError.msgShouldNotReachHereAtRuntime);
    }

    @Substitute
    private static RuntimeException shouldNotReachHereOverrideInChild() {
        throw JSInternalErrors.shouldNotReachHere(VMError.msgShouldNotReachHereOverrideInChild);
    }

    @Substitute
    private static RuntimeException unsupportedPlatform() {
        throw JSInternalErrors.shouldNotReachHere(VMError.msgShouldNotReachHereUnsupportedPlatform);
    }

    @Substitute
    private static RuntimeException intentionallyUnimplemented() {
        throw unsupportedFeature("unimplemented");
    }

    @Substitute
    private static RuntimeException unsupportedFeature(String msg) {
        throw new Error("UNSUPPORTED FEATURE: " + msg);
    }
}

@TargetClass(className = "jdk.internal.misc.Unsafe")
@SuppressWarnings("all")
final class Target_sun_misc_Unsafe_Web {

    @Substitute
    public void unpark(Object o) {
        throw VMError.unimplemented("Unsafe.unpark");
    }

    @Substitute
    public void park(boolean b0, long l1) {
        throw VMError.unimplemented("Unsafe.park");
    }

    @Substitute
    public Object allocateInstance(Class<?> cls) throws InstantiationException {
        throw new UnsupportedOperationException("Unsafe.allocateInstance");
    }
}

@TargetClass(java.lang.Thread.class)
@SuppressWarnings({"static-method", "unused"})
final class Target_java_lang_Thread_Web {

    @Substitute
    void setPriority(int newPriority) {
    }

    @Substitute
    void exit() {
    }

    @Substitute
    private void start0() {
    }

    @Substitute
    private long getId() {
        return 1;
    }

    @Substitute
    private Thread.State getState() {
        return Thread.State.RUNNABLE;
    }

    @Substitute
    private void interrupt() {
    }

    @Substitute
    private boolean isInterrupted() {
        return false;
    }

    /**
     * Very simple emulation of thread state. The current thread is alive while all others are not.
     * Non-main threads cannot run anyway in Web Image, but they may still be created in places
     * (e.g. for {@link Runtime#addShutdownHook(Thread)}, which requires a thread that was not
     * started yet).
     */
    @Substitute
    private boolean isAlive() {
        // TODO GR-42163. Update this once threading is supported
        return SubstrateUtil.cast(this, Thread.class) == Thread.currentThread();
    }

    @Substitute
    private static void yield() {
    }

    @Substitute
    private static void sleep(long millis) {
    }

    @Substitute
    private static void sleepNanos0(long nanos) {
    }

    @Substitute
    public static boolean holdsLock(Object obj) {
        /*
         * Truffle asserts on this sometimes. There are no locks in Web Image, so no way to track
         * whether a lock is actually held. Assertions on the negative should be rare though.
         */
        // TODO GR-42163. Update this once threading and locking is supported
        return true;
    }

    @Substitute
    public StackTraceElement[] getStackTrace() {
        return new StackTraceElement[0];
    }

    @Substitute
    public static Map<Thread, StackTraceElement[]> getAllStackTraces() {
        Thread onlyThread = Thread.currentThread();
        return java.util.Collections.singletonMap(onlyThread, onlyThread.getStackTrace());
    }
}

@TargetClass(className = "java.lang.VirtualThread")
@SuppressWarnings("unused")
final class Target_java_lang_VirtualThread_Web {
    @Substitute
    @SuppressWarnings("static-method")
    private void runContinuation() {
        throw new UnsupportedOperationException("VirtualThread.runContinuation");
    }
}

@TargetClass(java.lang.System.class)
@SuppressWarnings("unused")
final class Target_java_lang_System_Web {

    @Substitute
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long currentTimeMillis() {
        return JSFunctionIntrinsics.currentTimeMillis();
    }

    @Substitute
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static long nanoTime() {
        return (long) (JSFunctionIntrinsics.performanceNow() * 1_000_000d);
    }

    @Substitute
    private static void exit(int status) {
        JSFunctionIntrinsics.exit(status);
    }

    @Substitute
    public static void arraycopy(Object src, int srcPos,
                    Object dest, int destPos,
                    int length) {
        JSFunctionIntrinsics.arrayCopy(src, srcPos, dest, destPos, length);
    }

    @Substitute
    public static String mapLibraryName(String libname) {
        return libname;
    }
}

@TargetClass(java.lang.Math.class)
@Platforms(WebImagePlatform.class)
final class Target_java_lang_Math_Web {

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double asin(double a) {
        return JSFunctionIntrinsics.asin(a);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double acos(double a) {
        return JSFunctionIntrinsics.acos(a);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double atan(double a) {
        return JSFunctionIntrinsics.atan(a);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double sqrt(double a) {
        return JSFunctionIntrinsics.sqrt(a);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double cbrt(double a) {
        return JSFunctionIntrinsics.cbrt(a);
    }

    /**
     * This substitution is required in both the JS and WASM backend.
     */
    @Substitute
    private static double IEEEremainder(double f1, double f2) {
        return JSFunctionIntrinsics.IEEEremainder(f1, f2);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double atan2(double y, double x) {
        return JSFunctionIntrinsics.atan2(y, x);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double sinh(double x) {
        return JSFunctionIntrinsics.sinh(x);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double cosh(double x) {
        return JSFunctionIntrinsics.cosh(x);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double tanh(double x) {
        return JSFunctionIntrinsics.tanh(x);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double hypot(double x, double y) {
        return JSFunctionIntrinsics.hypot(x, y);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double expm1(double x) {
        return JSFunctionIntrinsics.expm1(x);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double log1p(double x) {
        return JSFunctionIntrinsics.log1p(x);
    }

}

@TargetClass(java.lang.StrictMath.class)
@Platforms(WebImagePlatform.class)
final class Target_java_lang_StrictMath_Web {

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double sin(double a) {
        return JSFunctionIntrinsics.sin(a);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double cos(double a) {
        return JSFunctionIntrinsics.cos(a);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double tan(double a) {
        return JSFunctionIntrinsics.tan(a);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double asin(double a) {
        return JSFunctionIntrinsics.asin(a);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double acos(double a) {
        return JSFunctionIntrinsics.acos(a);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double atan(double a) {
        return JSFunctionIntrinsics.atan(a);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double exp(double a) {
        return JSFunctionIntrinsics.exp(a);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double log(double a) {
        return JSFunctionIntrinsics.log(a);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double log10(double a) {
        return JSFunctionIntrinsics.log10(a);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double sqrt(double a) {
        return JSFunctionIntrinsics.sqrt(a);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double cbrt(double a) {
        return JSFunctionIntrinsics.cbrt(a);
    }

    /**
     * This substitution is required in both the JS and WASM backend.
     */
    @Substitute
    private static double IEEEremainder(double f1, double f2) {
        return JSFunctionIntrinsics.IEEEremainder(f1, f2);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double atan2(double y, double x) {
        return JSFunctionIntrinsics.atan2(y, x);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double pow(double a, double b) {
        return JSFunctionIntrinsics.pow(a, b);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double sinh(double x) {
        return JSFunctionIntrinsics.sinh(x);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double cosh(double x) {
        return JSFunctionIntrinsics.cosh(x);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double tanh(double x) {
        return JSFunctionIntrinsics.tanh(x);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double hypot(double x, double y) {
        return JSFunctionIntrinsics.hypot(x, y);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double expm1(double x) {
        return JSFunctionIntrinsics.expm1(x);
    }

    @Substitute
    @Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
    private static double log1p(double x) {
        return JSFunctionIntrinsics.log1p(x);
    }
}

/**
 * In the JS runtime we don't have access to the same features as in Java.
 *
 * For free/total/max memory queries we just always return 4GB. Programs that rely on a certain
 * behavior of those values won't work anyway.
 */
@TargetClass(Runtime.class)
@SuppressWarnings("static-method")
@Platforms({WebImageJSPlatform.class, WebImageWasmGCPlatform.class})
final class Target_java_lang_Runtime_Web {

    @Substitute
    private long freeMemory() {
        return 4L * 1024L * 1024L * 1024L;
    }

    @Substitute
    private long totalMemory() {
        return 4L * 1024L * 1024L * 1024L;
    }

    @Substitute
    private long maxMemory() {
        return 4L * 1024L * 1024L * 1024L;
    }

    @Substitute
    private void gc() {
    }

    @Substitute
    public int availableProcessors() {
        return 1;
    }
}

@TargetClass(java.lang.Byte.class)
@Platforms(WebImageJSPlatform.class)
final class Target_java_lang_Byte_Web {
    @Substitute
    public static String toString(byte i) {
        return JSFunctionIntrinsics.numberToString(i);
    }
}

@TargetClass(java.lang.Short.class)
@Platforms(WebImageJSPlatform.class)
final class Target_java_lang_Short_Web {
    @Substitute
    public static String toString(short i) {
        return JSFunctionIntrinsics.numberToString(i);
    }
}

@TargetClass(java.lang.Integer.class)
@Platforms(WebImageJSPlatform.class)
final class Target_java_lang_Integer_Web {
    @Substitute
    public static String toString(int i) {
        return JSFunctionIntrinsics.numberToString(i);
    }
}

@TargetClass(className = "jdk.internal.reflect.Reflection")
@SuppressWarnings("unused")
final class Target_jdk_internal_reflect_Reflection_Web {

    /**
     * JS does not have native support for stack walking, so we just return null. The caller class
     * is mainly used to get the classloader and use it to check permissions. Since there is no
     * security manager in Web Image, those checks are never reached anyway.
     */
    @Substitute
    public static Class<?> getCallerClass() {
        return null;
    }
}

@TargetClass(value = ServiceLoader.class)
@SuppressWarnings("unused")
final class Target_java_util_ServiceLoader_Web {
    @Substitute
    private static void checkCaller(Class<?> caller, Class<?> svc) {
    }
}

@TargetClass(value = AccessibleObject.class)
final class Target_java_lang_reflect_AccessibleObject_Web {
    @SuppressWarnings({"static-method", "unused"})
    @Substitute
    boolean checkCanSetAccessible(Class<?> caller, Class<?> declaringClass, boolean throwExceptionIfDenied) {
        return true;
    }

    /**
     * The jdk implementation gets the caller class through {@code Reflection#getCallerClass} which
     * requires a stack walk. This substitution allows any access, ignoring the visibility
     * modifiers, since we cannot perform a stack walk.
     */
    @Substitute
    @SuppressWarnings({"ignored", "unused"})
    private void checkAccess(Class<?> caller, Class<?> memberClass,
                    Class<?> targetClass, int modifiers) {

    }
}

@TargetClass(java.lang.Module.class)
final class Target_java_lang_Module_Web {
    /**
     * Substitution to reduce image size.
     * <p>
     * Even without the substitution, getting a resource from module will work, however it uses a
     * roundabout way of first creating a URL with a custom schema, which, when accessed, goes
     * through {@link NativeImageResourceFileSystem} to get the {@link InputStream}. Creating this
     * URL, pulls in a ton of extra types, so we circumvent this and directly get the resource data
     * from {@link Resources}.
     */
    @Substitute
    private InputStream getResourceAsStream(String resourceName) {
        String resName = resourceName;
        if (resName.startsWith("/")) {
            resName = resName.substring(1);
        }
        ResourceStorageEntryBase res = Resources.getAtRuntime(SubstrateUtil.cast(this, Module.class), resName, true);
        return res == null ? null : new ByteArrayInputStream(res.getData()[0]);
    }
}

@TargetClass(java.net.NetworkInterface.class)
@SuppressWarnings("all")
final class Target_java_net_NetworkInterface_Web {

    @Substitute
    private static NetworkInterface[] getAll() throws SocketException {
        throw new UnsupportedOperationException("NetworkInterface.getAll");
    }

    @Substitute
    private static NetworkInterface getByName0(String name) throws SocketException {
        throw new UnsupportedOperationException("NetworkInterface.getByName0");
    }

    @Substitute
    private static NetworkInterface getByIndex0(int index) throws SocketException {
        throw new UnsupportedOperationException("NetworkInterface.getByIndex0");
    }

    @Substitute
    private static NetworkInterface getByInetAddress0(InetAddress addr) throws SocketException {
        throw new UnsupportedOperationException("NetworkInterface.getByInetAddress0");
    }

    @Substitute
    private static void init() {
        throw new UnsupportedOperationException("NetworkInterface.init");
    }

    @Substitute
    private static boolean isUp0(String name, int ind) throws SocketException {
        throw new UnsupportedOperationException("NetworkInterface.isUp0");
    }

    @Substitute
    private static boolean isLoopback0(String name, int ind) throws SocketException {
        throw new UnsupportedOperationException("NetworkInterface.isLoopback0");
    }

    @Substitute
    private static boolean supportsMulticast0(String name, int ind) throws SocketException {
        throw new UnsupportedOperationException("NetworkInterface.supportsMulticast0");
    }

    @Substitute
    private static boolean isP2P0(String name, int ind) throws SocketException {
        throw new UnsupportedOperationException("NetworkInterface.isP2P0");
    }

    @Substitute
    private static byte[] getMacAddr0(byte[] inAddr, String name, int ind) throws SocketException {
        throw new UnsupportedOperationException("NetworkInterface.getMacAddr0");
    }

    @Substitute
    private static int getMTU0(String name, int ind) throws SocketException {
        throw new UnsupportedOperationException("NetworkInterface.getMTU0");
    }

    @Substitute
    private static boolean boundInetAddress0(InetAddress addr)
                    throws SocketException {
        throw new UnsupportedOperationException("NetworkInterface.boundInetAddress0");
    }
}

@TargetClass(className = "java.net.Inet4AddressImpl")
@SuppressWarnings("all")
final class Target_java_net_Inet4AddressImpl_Web {

    @Substitute
    public String getLocalHostName() throws UnknownHostException {
        throw new UnsupportedOperationException("Inet4AddressImpl.getLocalHostName");
    }

    @Substitute
    public InetAddress[] lookupAllHostAddr(String hostname) throws UnknownHostException {
        throw new UnsupportedOperationException("Inet4AddressImpl.lookupAllHostAddr");
    }

    @Substitute
    public String getHostByAddr(byte[] addr) throws UnknownHostException {
        throw new UnsupportedOperationException("Inet4AddressImpl.getHostByAddr");
    }

    @Substitute
    private boolean isReachable0(byte[] addr, int timeout, byte[] ifaddr, int ttl) throws IOException {
        throw new UnsupportedOperationException("Inet4AddressImpl.isReachable0");
    }
}

@TargetClass(className = "java.net.Inet6AddressImpl")
@SuppressWarnings("all")
final class Target_java_net_Inet6AddressImpl_Web {

    @Substitute
    public String getLocalHostName() throws UnknownHostException {
        throw new UnsupportedOperationException("Inet6AddressImpl.getLocalHostName");
    }

    @Substitute
    public String getHostByAddr(byte[] addr) throws UnknownHostException {
        throw new UnsupportedOperationException("Inet6AddressImpl.getHostByAddr");
    }

    @Substitute
    private boolean isReachable0(byte[] addr, int scope, int timeout, byte[] inf, int ttl, int if_scope) throws IOException {
        throw new UnsupportedOperationException("Inet6AddressImpl.isReachable0");
    }

    @Substitute
    private InetAddress[] lookupAllHostAddr(String hostname, int characteristics) {
        throw new UnsupportedOperationException("Inet6AddressImpl.lookupAllHostAddr");
    }
}

@TargetClass(java.util.zip.Inflater.class)
@SuppressWarnings("all")
final class Target_java_util_zip_Inflater_Web {

    @Substitute
    private static void initIDs() {
        throw new UnsupportedOperationException("Inflater.initIDs");
    }

    @Substitute
    private static long init(boolean nowrap) {
        throw new UnsupportedOperationException("Inflater.init");
    }

    @Substitute
    private static void setDictionary(long addr, byte[] b, int off, int len) {
        throw new UnsupportedOperationException("Inflater.setDictionary");
    }

    @Substitute
    private long inflateBytesBytes(
                    long addr,
                    byte[] inputArray, int inputOff, int inputLen,
                    byte[] outputArray, int outputOff, int outputLen) throws DataFormatException {
        throw new UnsupportedOperationException("Inflater.inflateBytesBytes");
    }

    @Substitute
    private long inflateBufferBytes(long addr,
                    long inputAddress, int inputLen,
                    byte[] outputArray, int outputOff, int outputLen) throws DataFormatException {
        throw new UnsupportedOperationException("Inflater.inflateBufferBytes");
    }

    @Substitute
    private static int getAdler(long addr) {
        throw new UnsupportedOperationException("Inflater.getAdler");
    }

    @Substitute
    private static void reset(long addr) {
        throw new UnsupportedOperationException("Inflater.reset");
    }

    @Substitute
    private static void end(long addr) {
        throw new UnsupportedOperationException("Inflater.end");
    }
}

@TargetClass(java.util.zip.Deflater.class)
@SuppressWarnings("all")
final class Target_java_util_zip_Deflater_Web {
    @Substitute
    void reset() {
        throw new UnsupportedOperationException("Deflater.reset");
    }

    @Substitute
    private long deflateBytesBytes(long addr,
                    byte[] inputArray, int inputOff, int inputLen,
                    byte[] outputArray, int outputOff, int outputLen,
                    int flush, int params) {
        throw new UnsupportedOperationException("Deflater.deflateBytesBytes");
    }

    @Substitute
    private long deflateBytesBuffer(long addr,
                    byte[] inputArray, int inputOff, int inputLen,
                    long outputAddress, int outputLen,
                    int flush, int params) {
        throw new UnsupportedOperationException("Deflater.deflateBytesBuffer");
    }

    @Substitute
    private long deflateBufferBytes(long addr,
                    long inputAddress, int inputLen,
                    byte[] outputArray, int outputOff, int outputLen,
                    int flush, int params) {
        throw new UnsupportedOperationException("Deflater.deflateBufferBytes");
    }

    @Substitute
    private long deflateBufferBuffer(long addr,
                    long inputAddress, int inputLen,
                    long outputAddress, int outputLen,
                    int flush, int params) {
        throw new UnsupportedOperationException("Deflater.deflateBufferBuffer");
    }

    @Substitute
    static void end(long addr) {
        throw new UnsupportedOperationException("Deflater.end");
    }

    @Substitute
    static long init(int level, int strategy, boolean nowrap) {
        throw new UnsupportedOperationException("Deflater.init");
    }
}

@TargetClass(java.util.zip.CRC32.class)
@SuppressWarnings("all")
final class Target_java_util_zip_CRC32_Web {

    @Substitute
    private static int update(int crc, int b) {
        throw new UnsupportedOperationException("CRC32.update");
    }

    @Substitute
    private static int updateBytes(int crc, byte[] b, int off, int len) {
        throw new UnsupportedOperationException("CRC32.updateBytes");
    }

    @Substitute
    private static int updateByteBuffer(int adler, long addr, int off, int len) {
        throw new UnsupportedOperationException("CRC32.updateByteBuffer");
    }
}

@TargetClass(value = java.util.logging.Logger.class)
@SuppressWarnings("unused")
final class Target_java_util_logging_Logger_Web {
    @Substitute
    public static Logger getLogger(String name) {
        return Logger.getGlobal();
    }
}

@TargetClass(className = "java.lang.ProcessHandleImpl")
@SuppressWarnings("unused")
final class Target_java_lang_ProcessHandleImpl_Web {

    @Substitute
    private static void initNative() {
        // Do nothing. Native code only gathers some information about the underlying system.
    }

    @Substitute
    private static long getCurrentPid0() {
        return ProcessProperties.getProcessID();
    }

    @Substitute
    private static long isAlive0(long pid) {
        if (pid == ProcessProperties.getProcessID()) {
            return 0L;
        }
        return -1L;
    }
}

class IsLinux implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return OS.LINUX.isCurrent();
    }
}

class IsDarwin implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return OS.DARWIN.isCurrent();
    }
}

class IsUnix implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return OS.LINUX.isCurrent() || OS.DARWIN.isCurrent();
    }
}

class IsWindows implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return OS.WINDOWS.isCurrent();
    }
}
