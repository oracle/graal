/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.snippets.KnownIntrinsics.readCallerStackPointer;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackFrameVisitor;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.LoomSupport;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.Target_java_lang_Thread;
import com.oracle.svm.core.thread.Target_jdk_internal_vm_Continuation;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VirtualThreads;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class StackTraceUtils {

    private static final Class<?>[] NO_CLASSES = new Class<?>[0];
    private static final StackTraceElement[] NO_ELEMENTS = new StackTraceElement[0];

    /**
     * Captures the stack trace of the current thread. In almost any context, calling
     * {@link JavaThreads#getStackTrace} for {@link Thread#currentThread()} is preferable.
     *
     * Captures at most {@link SubstrateOptions#maxJavaStackTraceDepth()} stack trace elements if
     * max depth > 0, or all if max depth <= 0.
     */
    public static StackTraceElement[] getStackTrace(boolean filterExceptions, Pointer startSP, Pointer endSP) {
        BuildStackTraceVisitor visitor = new BuildStackTraceVisitor(filterExceptions, SubstrateOptions.maxJavaStackTraceDepth());
        visitCurrentThreadStackFrames(startSP, endSP, visitor);
        return visitor.trace.toArray(NO_ELEMENTS);
    }

    public static void visitCurrentThreadStackFrames(Pointer startSP, Pointer endSP, StackFrameVisitor visitor) {
        JavaStackWalker.walkCurrentThread(startSP, endSP, visitor);
    }

    /**
     * Captures the stack trace of a thread (potentially the current thread) while stopped at a
     * safepoint. Used by {@link Thread#getStackTrace()} and {@link Thread#getAllStackTraces()}.
     *
     * Captures at most {@link SubstrateOptions#maxJavaStackTraceDepth()} stack trace elements if
     * max depth > 0, or all if max depth <= 0.
     */
    @NeverInline("Potentially starting a stack walk in the caller frame")
    public static StackTraceElement[] getStackTraceAtSafepoint(Thread thread) {
        assert VMOperation.isInProgressAtSafepoint();
        if (VirtualThreads.isSupported()) { // NOTE: also for platform threads!
            return VirtualThreads.singleton().getVirtualOrPlatformThreadStackTraceAtSafepoint(thread, readCallerStackPointer());
        }
        return PlatformThreads.getStackTraceAtSafepoint(thread, readCallerStackPointer());
    }

    public static StackTraceElement[] getThreadStackTraceAtSafepoint(IsolateThread isolateThread, Pointer endSP) {
        assert VMOperation.isInProgressAtSafepoint();
        BuildStackTraceVisitor visitor = new BuildStackTraceVisitor(false, SubstrateOptions.maxJavaStackTraceDepth());
        JavaStackWalker.walkThread(isolateThread, endSP, visitor, null);
        return visitor.trace.toArray(NO_ELEMENTS);
    }

    public static StackTraceElement[] getThreadStackTraceAtSafepoint(Pointer startSP, Pointer endSP, CodePointer startIP) {
        assert VMOperation.isInProgressAtSafepoint();
        BuildStackTraceVisitor visitor = new BuildStackTraceVisitor(false, SubstrateOptions.maxJavaStackTraceDepth());
        JavaStackWalker.walkThreadAtSafepoint(startSP, endSP, startIP, visitor);
        return visitor.trace.toArray(NO_ELEMENTS);
    }

    public static Class<?>[] getClassContext(int skip, Pointer startSP) {
        GetClassContextVisitor visitor = new GetClassContextVisitor(skip);
        JavaStackWalker.walkCurrentThread(startSP, visitor);
        return visitor.trace.toArray(NO_CLASSES);
    }

    /**
     * Implements the shared semantic of Reflection.getCallerClass and StackWalker.getCallerClass.
     */
    public static Class<?> getCallerClass(Pointer startSP, boolean showLambdaFrames) {
        return getCallerClass(startSP, showLambdaFrames, 0, true);
    }

    public static Class<?> getCallerClass(Pointer startSP, boolean showLambdaFrames, int depth, boolean ignoreFirst) {
        GetCallerClassVisitor visitor = new GetCallerClassVisitor(showLambdaFrames, depth, ignoreFirst);
        JavaStackWalker.walkCurrentThread(startSP, visitor);
        return visitor.result;
    }

    /*
     * Note that this method is duplicated below to work on compiler metadata. Make sure to always
     * keep both versions in sync, otherwise intrinsifications by the compiler will return different
     * results than stack walking at run time.
     */
    public static boolean shouldShowFrame(FrameInfoQueryResult frameInfo, boolean showLambdaFrames, boolean showReflectFrames, boolean showHiddenFrames) {
        if (showHiddenFrames) {
            /* No filtering, all frames including internal frames are shown. */
            return true;
        }

        Class<?> clazz = frameInfo.getSourceClass();
        if (clazz == null) {
            /*
             * We don't have a Java class. This must be an internal frame. This path mostly exists
             * to be defensive, there should actually never be a frame where we do not have a Java
             * class.
             */
            return false;
        }

        if (DynamicHub.fromClass(clazz).isVMInternal()) {
            return false;
        }

        if (!showLambdaFrames && DynamicHub.fromClass(clazz).isLambdaFormHidden()) {
            return false;
        }

        if (!showReflectFrames && ((clazz == java.lang.reflect.Method.class && "invoke".equals(frameInfo.getSourceMethodName())) ||
                        (clazz == java.lang.reflect.Constructor.class && "newInstance".equals(frameInfo.getSourceMethodName())) ||
                        (clazz == java.lang.Class.class && "newInstance".equals(frameInfo.getSourceMethodName())))) {
            /*
             * Ignore a reflective method / constructor invocation frame. Note that the classes
             * cannot be annotated with @InternalFrame because 1) they are JDK classes and 2) only
             * one method of each class is affected.
             */
            return false;
        }

        if (LoomSupport.isEnabled() && clazz == Target_jdk_internal_vm_Continuation.class) {
            String name = frameInfo.getSourceMethodName();
            if ("enter0".equals(name) || "enterSpecial".equals(name)) {
                return false;
            }
        }

        return true;
    }

    /*
     * Note that this method is duplicated (and commented) above for stack walking at run time. Make
     * sure to always keep both versions in sync.
     */
    public static boolean shouldShowFrame(MetaAccessProvider metaAccess, ResolvedJavaMethod method, boolean showLambdaFrames, boolean showReflectFrames, boolean showHiddenFrames) {
        if (showHiddenFrames) {
            return true;
        }

        ResolvedJavaType clazz = method.getDeclaringClass();
        if (AnnotationAccess.isAnnotationPresent(clazz, InternalVMMethod.class)) {
            return false;
        }

        if (!showLambdaFrames && AnnotationAccess.isAnnotationPresent(clazz, LambdaFormHiddenMethod.class)) {
            return false;
        }

        if (!showReflectFrames && ((clazz.equals(metaAccess.lookupJavaType(java.lang.reflect.Method.class)) && "invoke".equals(method.getName())) ||
                        (clazz.equals(metaAccess.lookupJavaType(java.lang.reflect.Constructor.class)) && "newInstance".equals(method.getName())) ||
                        (clazz.equals(metaAccess.lookupJavaType(java.lang.Class.class)) && "newInstance".equals(method.getName())))) {
            return false;
        }

        return true;
    }

    public static boolean ignoredBySecurityStackWalk(MetaAccessProvider metaAccess, ResolvedJavaMethod method) {
        return !shouldShowFrame(metaAccess, method, true, false, false);
    }

    public static ClassLoader latestUserDefinedClassLoader(Pointer startSP) {
        GetLatestUserDefinedClassLoaderVisitor visitor = new GetLatestUserDefinedClassLoaderVisitor();
        JavaStackWalker.walkCurrentThread(startSP, visitor);
        return visitor.result;
    }

    public static StackTraceElement[] asyncGetStackTrace(Thread thread) {
        GetStackTraceOperation vmOp = new GetStackTraceOperation(thread);
        vmOp.enqueue();
        return vmOp.result;
    }

    private static class GetStackTraceOperation extends JavaVMOperation {
        private final Thread thread;
        StackTraceElement[] result;

        GetStackTraceOperation(Thread thread) {
            super(VMOperationInfos.get(GetStackTraceOperation.class, "Get stack trace", SystemEffect.SAFEPOINT));
            this.thread = thread;
        }

        @Override
        protected void operate() {
            if (thread.isAlive()) {
                result = getStackTraceAtSafepoint(thread);
            } else {
                result = Target_java_lang_Thread.EMPTY_STACK_TRACE;
            }
        }
    }

}

/**
 * Visits the stack frames and collects a backtrace in an internal format to be stored in
 * {@link Target_java_lang_Throwable#backtrace}.
 */
final class BacktraceVisitor extends StackFrameVisitor {

    private int index = 0;
    private final int limit = SubstrateOptions.maxJavaStackTraceDepth();

    /*
     * Empirical data suggests that most stack traces tend to be relatively short (<100). We choose
     * the initial size so that these cases do not need to reallocate the array.
     */
    private static final int INITIAL_TRACE_SIZE = 80;
    private long[] trace = new long[INITIAL_TRACE_SIZE];

    @Uninterruptible(reason = "Prevent the GC from freeing the CodeInfo object.")
    private static boolean decodeCodePointer(BuildStackTraceVisitor visitor, CodePointer ip) {
        UntetheredCodeInfo untetheredInfo = CodeInfoTable.lookupCodeInfo(ip);
        if (untetheredInfo.isNull()) {
            /* Unknown frame. Must not happen for AOT-compiled code. */
            throw VMError.shouldNotReachHere("Stack walk must walk only frames of known code.");
        }

        Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
        try {
            CodeInfo tetheredCodeInfo = CodeInfoAccess.convert(untetheredInfo, tether);
            if (!visitFrame(visitor, ip, tetheredCodeInfo)) {
                return true;
            }
        } finally {
            CodeInfoAccess.releaseTether(untetheredInfo, tether);
        }
        return false;
    }

    @Uninterruptible(reason = "Wraps the now safe call to the possibly interruptible visitor.", callerMustBe = true, calleeMustBe = false)
    private static boolean visitFrame(BuildStackTraceVisitor visitor, CodePointer ip, CodeInfo tetheredCodeInfo) {
        return visitor.visitFrame(WordFactory.nullPointer(), ip, tetheredCodeInfo, null);
    }

    @Override
    protected boolean visitFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptimizedFrame) {
        if (index >= limit) {
            // cutoff
            return false;
        }
        VMError.guarantee(deoptimizedFrame == null, "Deoptimization not supported");
        long rawValue = ip.rawValue();
        VMError.guarantee(rawValue != 0, "Unexpected code pointer: 0");
        add(rawValue);
        return true;
    }

    private void add(long value) {
        if (index == trace.length) {
            trace = Arrays.copyOf(trace, Math.min(trace.length * 2, limit));
        }
        trace[index++] = value;
    }

    /**
     * Gets the backtrace array.
     *
     * Tradeoff question: should we make a copy of the trace array to trim it to length index?
     * <ul>
     * <li>Benefit: lower memory footprint for exceptions that are long-lived.
     * <li>Downside: more work for copying for every exception.
     * </ul>
     * Currently, we do not trim the array. The assumption is that most exception stack traces are
     * short-lived and are never moved by the GC.
     */
    long[] getArray() {
        VMError.guarantee(trace != null, "Already acquired");
        VMError.guarantee(index == trace.length || trace[index] == 0, "Unterminated trace?");
        long[] tmp = trace;
        trace = null;
        return tmp;
    }
}

/**
 * Decodes the internal backtrace stored in {@link Target_java_lang_Throwable#backtrace} and creates
 * the corresponding {@link StackTraceElement} array.
 */
final class StackTraceBuilder extends BacktraceDecoder {

    static StackTraceElement[] build(Object backtrace) {
        var stackTraceBuilder = new StackTraceBuilder();
        stackTraceBuilder.visitBacktrace(backtrace, Integer.MAX_VALUE, SubstrateOptions.maxJavaStackTraceDepth());
        return stackTraceBuilder.trace.toArray(new StackTraceElement[0]);
    }

    private final ArrayList<StackTraceElement> trace = new ArrayList<>();

    @Override
    protected void processFrameInfo(FrameInfoQueryResult frameInfo) {
        StackTraceElement sourceReference = frameInfo.getSourceReference();
        trace.add(sourceReference);
    }
}

class BuildStackTraceVisitor extends JavaStackFrameVisitor {
    private final boolean filterExceptions;
    final ArrayList<StackTraceElement> trace;
    final int limit;

    BuildStackTraceVisitor(boolean filterExceptions, int limit) {
        this.filterExceptions = filterExceptions;
        this.trace = new ArrayList<>();
        this.limit = limit;
    }

    @Override
    public boolean visitFrame(FrameInfoQueryResult frameInfo) {
        if (!StackTraceUtils.shouldShowFrame(frameInfo, false, true, false)) {
            /* Always ignore the frame. It is an internal frame of the VM. */
            return true;

        } else if (filterExceptions && trace.size() == 0 && Throwable.class.isAssignableFrom(frameInfo.getSourceClass())) {
            /*
             * We are still in the constructor invocation chain at the beginning of the stack trace,
             * which is also filtered by the Java HotSpot VM.
             */
            return true;
        }

        StackTraceElement sourceReference = frameInfo.getSourceReference();
        trace.add(sourceReference);
        if (trace.size() == limit) {
            return false;
        }
        return true;
    }
}

class GetCallerClassVisitor extends JavaStackFrameVisitor {
    private final boolean showLambdaFrames;
    private int depth;
    private boolean ignoreFirst;
    Class<?> result;

    GetCallerClassVisitor(boolean showLambdaFrames, int depth, boolean ignoreFirst) {
        this.showLambdaFrames = showLambdaFrames;
        this.ignoreFirst = ignoreFirst;
        this.depth = depth;
        assert depth >= 0;
    }

    @Override
    public boolean visitFrame(FrameInfoQueryResult frameInfo) {
        assert depth >= 0;

        if (ignoreFirst) {
            /*
             * Skip the frame that contained the invocation of getCallerFrame() and continue the
             * stack walk. Note that this could be a frame related to reflection, but we still must
             * not ignore it: For example, Constructor.newInstance calls Reflection.getCallerClass
             * and for this check Constructor.newInstance counts as a frame. But if the actual
             * invoked constructor calls Reflection.getCallerClass, then Constructor.newInstance
             * does not count as as frame (handled by the shouldShowFrame check below because this
             * path was already taken for the constructor frame).
             */
            ignoreFirst = false;
            return true;

        } else if (!StackTraceUtils.shouldShowFrame(frameInfo, showLambdaFrames, false, false)) {
            /*
             * Always ignore the frame. It is an internal frame of the VM or a frame related to
             * reflection.
             */
            return true;

        } else if (depth > 0) {
            /* Skip the number of frames specified by "depth". */
            depth--;
            return true;

        } else {
            /* Found the caller frame, remember it and end the stack walk. */
            result = frameInfo.getSourceClass();
            return false;
        }
    }
}

class GetClassContextVisitor extends JavaStackFrameVisitor {
    private int skip;
    final ArrayList<Class<?>> trace;

    GetClassContextVisitor(final int skip) {
        trace = new ArrayList<>();
        this.skip = skip;
    }

    @Override
    public boolean visitFrame(final FrameInfoQueryResult frameInfo) {
        if (skip > 0) {
            skip--;
        } else if (StackTraceUtils.shouldShowFrame(frameInfo, true, false, false)) {
            trace.add(frameInfo.getSourceClass());
        }
        return true;
    }
}

class GetLatestUserDefinedClassLoaderVisitor extends JavaStackFrameVisitor {
    ClassLoader result;

    GetLatestUserDefinedClassLoaderVisitor() {
    }

    @Override
    public boolean visitFrame(FrameInfoQueryResult frameInfo) {
        if (!StackTraceUtils.shouldShowFrame(frameInfo, true, true, false)) {
            // Skip internal frames.
            return true;
        }

        ClassLoader classLoader = frameInfo.getSourceClass().getClassLoader();
        if (classLoader == null || isExtensionOrPlatformLoader(classLoader)) {
            // Skip bootstrap and platform/extension class loader.
            return true;
        }

        result = classLoader;
        return false;
    }

    private static boolean isExtensionOrPlatformLoader(ClassLoader classLoader) {
        return classLoader == Target_jdk_internal_loader_ClassLoaders.platformClassLoader();
    }
}

/* Reimplementation of JVM_GetStackAccessControlContext from JDK15 */
class StackAccessControlContextVisitor extends JavaStackFrameVisitor {
    final ArrayList<ProtectionDomain> localArray;
    boolean isPrivileged;
    ProtectionDomain previousProtectionDomain;
    AccessControlContext privilegedContext;

    StackAccessControlContextVisitor() {
        localArray = new ArrayList<>();
        isPrivileged = false;
        privilegedContext = null;
    }

    @Override
    public boolean visitFrame(final FrameInfoQueryResult frameInfo) {
        if (!StackTraceUtils.shouldShowFrame(frameInfo, true, false, false)) {
            return true;
        }

        Class<?> clazz = frameInfo.getSourceClass();
        String method = frameInfo.getSourceMethodName();

        ProtectionDomain protectionDomain;
        if (PrivilegedStack.length() > 0 && clazz.equals(AccessController.class) && method.equals("doPrivileged")) {
            isPrivileged = true;
            privilegedContext = PrivilegedStack.peekContext();
            protectionDomain = PrivilegedStack.peekCaller().getProtectionDomain();
        } else {
            protectionDomain = clazz.getProtectionDomain();
        }

        if ((protectionDomain != null) && (previousProtectionDomain == null || !previousProtectionDomain.equals(protectionDomain))) {
            localArray.add(protectionDomain);
            previousProtectionDomain = protectionDomain;
        }

        return !isPrivileged;
    }

    @NeverInline("Starting a stack walk in the caller frame")
    @SuppressWarnings({"deprecation"}) // deprecated starting JDK 17
    public static AccessControlContext getFromStack() {
        StackAccessControlContextVisitor visitor = new StackAccessControlContextVisitor();
        JavaStackWalker.walkCurrentThread(KnownIntrinsics.readCallerStackPointer(), visitor);
        Target_java_security_AccessControlContext wrapper;

        if (visitor.localArray.isEmpty()) {
            if (visitor.isPrivileged && visitor.privilegedContext == null) {
                return null;
            }
            wrapper = new Target_java_security_AccessControlContext(null, visitor.privilegedContext);
        } else {
            ProtectionDomain[] context = visitor.localArray.toArray(new ProtectionDomain[visitor.localArray.size()]);
            wrapper = new Target_java_security_AccessControlContext(context, visitor.privilegedContext);
        }

        wrapper.isPrivileged = visitor.isPrivileged;
        wrapper.isAuthorized = true;
        return SubstrateUtil.cast(wrapper, AccessControlContext.class);
    }
}
