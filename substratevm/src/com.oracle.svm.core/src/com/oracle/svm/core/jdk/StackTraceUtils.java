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
import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.ArrayList;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.code.FrameSourceInfo;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.stack.JavaStackFrameVisitor;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.Target_jdk_internal_vm_Continuation;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.guest.staging.jdk.InternalVMMethod;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.util.AnnotationUtil;

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
    public static StackTraceElement[] getCurrentThreadStackTrace(boolean filterExceptions, Pointer startSP, Pointer endSP) {
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
        if (thread == null) {
            return NO_ELEMENTS;
        }
        return JavaThreads.getStackTraceAtSafepoint(thread, readCallerStackPointer());
    }

    public static StackTraceElement[] getStackTraceAtSafepoint(IsolateThread isolateThread) {
        return getStackTraceAtSafepoint(isolateThread, Word.nullPointer());
    }

    public static StackTraceElement[] getStackTraceAtSafepoint(IsolateThread isolateThread, Pointer endSP) {
        assert VMOperation.isInProgressAtSafepoint();
        if (isolateThread.isNull()) { // recently launched thread
            return NO_ELEMENTS;
        }
        BuildStackTraceVisitor visitor = new BuildStackTraceVisitor(false, SubstrateOptions.maxJavaStackTraceDepth());
        JavaStackWalker.walkThread(isolateThread, endSP, visitor, null);
        return visitor.trace.toArray(NO_ELEMENTS);
    }

    public static StackTraceElement[] getStackTraceAtSafepoint(IsolateThread isolateThread, Pointer startSP, Pointer endSP) {
        assert VMOperation.isInProgressAtSafepoint();
        BuildStackTraceVisitor visitor = new BuildStackTraceVisitor(false, SubstrateOptions.maxJavaStackTraceDepth());
        JavaStackWalker.walkThread(isolateThread, startSP, endSP, Word.nullPointer(), visitor);
        return visitor.trace.toArray(NO_ELEMENTS);
    }

    public static Class<?>[] getClassContext(Pointer startSP) {
        GetClassContextVisitor visitor = new GetClassContextVisitor();
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

    /**
     * Indicates whether the frame should be displayed in the context of Java backtracing. Returns
     * true if so, and false otherwise. Backtracing means that there are no lambda or hidden frames
     * present. To learn more about backtracing, refer to {@link BacktraceDecoder}. For more
     * fine-grained control over what is displayed, see
     * {@link #shouldShowFrame(Class, String, boolean, boolean, boolean)}.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean shouldShowFrame(Class<?> clazz, String method) {
        return shouldShowFrame(clazz, method, false, true, false);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean shouldShowFrame(FrameSourceInfo frameSourceInfo) {
        return shouldShowFrame(frameSourceInfo.getSourceClass(), frameSourceInfo.getSourceMethodName());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean shouldShowFrame(FrameSourceInfo frameSourceInfo, boolean showLambdaFrames, boolean showReflectFrames, boolean showHiddenFrames) {
        return shouldShowFrame(frameSourceInfo.getSourceClass(), frameSourceInfo.getSourceMethodName(), showLambdaFrames, showReflectFrames, showHiddenFrames);
    }

    /*
     * Note that this method is duplicated below to work on compiler metadata. Make sure to always
     * keep both versions in sync, otherwise intrinsifications by the compiler will return different
     * results than stack walking at run time.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean shouldShowFrame(Class<?> clazz, String methodName, boolean showLambdaFrames, boolean showReflectFrames, boolean showHiddenFrames) {
        if (showHiddenFrames) {
            /* No filtering, all frames including internal frames are shown. */
            return true;
        }

        if (isVMInternalFrameClass(clazz)) {
            return false;
        }

        if (!showLambdaFrames && DynamicHub.fromClass(clazz).isLambdaFormHidden()) {
            return false;
        }

        if (!showReflectFrames) {
            if (clazz == java.lang.reflect.Method.class && UninterruptibleUtils.String.equals("invoke", methodName)) {
                /*
                 * Ignore a reflective method invocation frame. Note that the classes cannot be
                 * annotated with @InternalFrame because 1) they are JDK classes and 2) only one
                 * method of each class is affected.
                 */
                return false;
            } else if ((clazz == java.lang.reflect.Constructor.class || clazz == java.lang.Class.class) && UninterruptibleUtils.String.equals("newInstance", methodName)) {
                /* Ignore a constructor invocation frame (see the comment above). */
                return false;
            }
        }

        if (clazz == Target_jdk_internal_vm_Continuation.class && (UninterruptibleUtils.String.startsWith(methodName, "enter") || UninterruptibleUtils.String.startsWith(methodName, "yield"))) {
            return false;
        }

        return true;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static boolean isVMInternalFrameClass(Class<?> clazz) {
        if (clazz == null) {
            /*
             * We don't have a Java class. This must be an internal frame. This path mostly exists
             * to be defensive, there should actually never be a frame where we do not have a Java
             * class. GR-76063: We should remove the need for this defensive check and ensure no
             * such frames reach here.
             */
            return true;
        }
        return DynamicHub.fromClass(clazz).isVMInternal();
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
        if (AnnotationUtil.isAnnotationPresent(clazz, InternalVMMethod.class)) {
            return false;
        }

        if (!showLambdaFrames && AnnotationUtil.isAnnotationPresent(clazz, LambdaFormHiddenMethod.class)) {
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
        if (thread == null || !thread.isAlive()) {
            /* Avoid triggering a safepoint operation below if the thread is not even alive. */
            return NO_ELEMENTS;
        }
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
            result = getStackTraceAtSafepoint(thread);
        }
    }

}

/**
 * Decodes the internal backtrace stored in {@link Target_java_lang_Throwable#backtrace} and creates
 * the corresponding {@link StackTraceElement} array.
 */
final class StackTraceBuilder extends BacktraceDecoder {

    static StackTraceElement[] build(Object x) {
        var stackTraceBuilder = new StackTraceBuilder();
        stackTraceBuilder.visitBacktrace(x, Integer.MAX_VALUE, SubstrateOptions.maxJavaStackTraceDepth());
        return stackTraceBuilder.trace.toArray(new StackTraceElement[0]);
    }

    private final ArrayList<StackTraceElement> trace = new ArrayList<>();

    @Override
    protected void processSourceReference(Class<?> sourceClass, String sourceMethodName, int sourceLineNumber) {
        StackTraceElement sourceReference = FrameSourceInfo.getSourceReference(sourceClass, sourceMethodName, sourceLineNumber);
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
    public boolean visitFrame(FrameSourceInfo frameSourceInfo, Pointer sp) {
        if (!StackTraceUtils.shouldShowFrame(frameSourceInfo)) {
            /* Always ignore the frame. It is an internal frame of the VM. */
            return true;

        } else if (filterExceptions && trace.size() == 0 && Throwable.class.isAssignableFrom(frameSourceInfo.getSourceClass())) {
            /*
             * We are still in the constructor invocation chain at the beginning of the stack trace,
             * which is also filtered by the Java HotSpot VM.
             */
            return true;
        }

        StackTraceElement sourceReference = frameSourceInfo.getSourceReference();
        trace.add(sourceReference);
        return trace.size() != limit;
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
    public boolean visitFrame(FrameSourceInfo frameSourceInfo, Pointer sp) {
        assert depth >= 0;

        if (ignoreFirst) {
            /*
             * Skip the frame that contained the invocation of getCallerFrame() and continue the
             * stack walk. Note that this could be a frame related to reflection, but we still must
             * not ignore it: For example, Constructor.newInstance calls Reflection.getCallerClass
             * and for this check Constructor.newInstance counts as a frame. But if the actual
             * invoked constructor calls Reflection.getCallerClass, then Constructor.newInstance
             * does not count as a frame (handled by the shouldShowFrame check below because this
             * path was already taken for the constructor frame).
             */
            /*
             * We want to make sure to use `ignoreFirst` for the first real frame. For example if
             * Reflection.getCallerClass was called from the interpreter, we must skip the internal
             * frames that are in between the caller and Reflection.getCallerClass.
             */
            if (!StackTraceUtils.isVMInternalFrameClass(frameSourceInfo.getSourceClass())) {
                ignoreFirst = false;
            }
            return true;

        } else if (!StackTraceUtils.shouldShowFrame(frameSourceInfo, showLambdaFrames, false, false)) {
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
            result = frameSourceInfo.getSourceClass();
            return false;
        }
    }
}

class GetClassContextVisitor extends JavaStackFrameVisitor {
    final ArrayList<Class<?>> trace;

    GetClassContextVisitor() {
        trace = new ArrayList<>();
    }

    @Override
    public boolean visitFrame(FrameSourceInfo frameSourceInfo, Pointer sp) {
        if (StackTraceUtils.shouldShowFrame(frameSourceInfo, true, false, false)) {
            trace.add(frameSourceInfo.getSourceClass());
        }
        return true;
    }
}

class GetLatestUserDefinedClassLoaderVisitor extends JavaStackFrameVisitor {
    ClassLoader result;

    GetLatestUserDefinedClassLoaderVisitor() {
    }

    @Override
    public boolean visitFrame(FrameSourceInfo frameSourceInfo, Pointer sp) {
        if (!StackTraceUtils.shouldShowFrame(frameSourceInfo, true, true, false)) {
            // Skip internal frames.
            return true;
        }

        ClassLoader classLoader = frameSourceInfo.getSourceClass().getClassLoader();
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
