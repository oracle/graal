/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.util.DirectAnnotationAccess;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.stack.JavaStackFrameVisitor;
import com.oracle.svm.core.stack.JavaStackWalker;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class StackTraceUtils {

    private static final Class<?>[] NO_CLASSES = new Class<?>[0];
    private static final StackTraceElement[] NO_ELEMENTS = new StackTraceElement[0];

    public static StackTraceElement[] getStackTrace(boolean filterExceptions, Pointer startSP) {
        BuildStackTraceVisitor visitor = new BuildStackTraceVisitor(filterExceptions);
        JavaStackWalker.walkCurrentThread(startSP, visitor);
        return visitor.trace.toArray(NO_ELEMENTS);
    }

    public static StackTraceElement[] getStackTrace(boolean filterExceptions, IsolateThread thread) {
        BuildStackTraceVisitor visitor = new BuildStackTraceVisitor(filterExceptions);
        JavaStackWalker.walkThread(thread, visitor);
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
    public static Class<?> getCallerClass(Pointer startSP) {
        return getCallerClass(startSP, 0);
    }

    public static Class<?> getCallerClass(Pointer startSP, int depth) {
        GetCallerClassVisitor visitor = new GetCallerClassVisitor(depth);
        JavaStackWalker.walkCurrentThread(startSP, visitor);
        return visitor.result;
    }

    /*
     * Note that this method is duplicated below to work on compiler metadata. Make sure to always
     * keep both versions in sync, otherwise intrinsifications by the compiler will return different
     * results than stack walking at run time.
     */
    public static boolean shouldShowFrame(FrameInfoQueryResult frameInfo, boolean showReflectFrames, boolean showHiddenFrames) {
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

        if (DirectAnnotationAccess.isAnnotationPresent(clazz, InternalVMMethod.class)) {
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

        return true;
    }

    /*
     * Note that this method is duplicated (and commented) above for stack walking at run time. Make
     * sure to always keep both versions in sync.
     */
    public static boolean shouldShowFrame(MetaAccessProvider metaAccess, ResolvedJavaMethod method, boolean showReflectFrames, boolean showHiddenFrames) {
        if (showHiddenFrames) {
            return true;
        }

        ResolvedJavaType clazz = method.getDeclaringClass();
        if (DirectAnnotationAccess.isAnnotationPresent(clazz, InternalVMMethod.class)) {
            return false;
        }

        if (!showReflectFrames && ((clazz.equals(metaAccess.lookupJavaType(java.lang.reflect.Method.class)) && "invoke".equals(method.getName())) ||
                        (clazz.equals(metaAccess.lookupJavaType(java.lang.reflect.Constructor.class)) && "newInstance".equals(method.getName())) ||
                        (clazz.equals(metaAccess.lookupJavaType(java.lang.Class.class)) && "newInstance".equals(method.getName())))) {
            return false;
        }

        return true;
    }
}

class BuildStackTraceVisitor extends JavaStackFrameVisitor {
    private final boolean filterExceptions;
    final ArrayList<StackTraceElement> trace;

    BuildStackTraceVisitor(boolean filterExceptions) {
        this.filterExceptions = filterExceptions;
        this.trace = new ArrayList<>();
    }

    @Override
    public boolean visitFrame(FrameInfoQueryResult frameInfo) {
        if (!StackTraceUtils.shouldShowFrame(frameInfo, true, false)) {
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
        return true;
    }
}

class GetCallerClassVisitor extends JavaStackFrameVisitor {
    private int depth;
    private boolean foundCallee;
    Class<?> result;

    GetCallerClassVisitor(final int depth) {
        this.depth = depth;
    }

    @Override
    public boolean visitFrame(FrameInfoQueryResult frameInfo) {
        if (!foundCallee) {
            /*
             * Skip the frame that contained the invocation of getCallerFrame() and continue the
             * stack walk. Note that this could be a frame related to reflection, but we still must
             * not ignore it: For example, Constructor.newInstance calls Reflection.getCallerClass
             * and for this check Constructor.newInstance counts as a frame. But if the actual
             * invoked constructor calls Reflection.getCallerClass, then Constructor.newInstance
             * does not count as as frame (handled by the shouldShowFrame check below because this
             * path was already taken for the constructor frame).
             */
            foundCallee = true;
            return true;

        } else if (!StackTraceUtils.shouldShowFrame(frameInfo, false, false)) {
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
        } else if (StackTraceUtils.shouldShowFrame(frameInfo, false, false)) {
            trace.add(frameInfo.getSourceClass());
        }
        return true;
    }
}
