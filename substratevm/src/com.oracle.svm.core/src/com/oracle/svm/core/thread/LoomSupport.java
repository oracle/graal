/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import static com.oracle.svm.core.SubstrateOptions.UseEpsilonGC;
import static com.oracle.svm.core.SubstrateOptions.UseSerialGC;

import java.lang.reflect.Field;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.stack.JavaFrameAnchor;
import com.oracle.svm.core.stack.JavaFrameAnchors;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.misc.Unsafe;

public final class LoomSupport {
    private static final boolean isEnabled;
    static {
        boolean enabled = false;
        if (JavaVersionUtil.JAVA_SPEC == 19 && (UseSerialGC.getValue() || UseEpsilonGC.getValue())) {
            try {
                enabled = (Boolean) Class.forName("jdk.internal.misc.PreviewFeatures")
                                .getDeclaredMethod("isEnabled").invoke(null);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        isEnabled = enabled;
    }

    public static final int YIELD_SUCCESS = 0;
    public static final int PINNED_CRITICAL_SECTION = 1;
    public static final int PINNED_NATIVE = 2;

    @Fold
    public static boolean isEnabled() {
        return isEnabled;
    }

    public static int yield(Target_java_lang_Continuation cont) {
        return convertInternalYieldResult(cont.internal.yield());
    }

    static int convertInternalYieldResult(int value) {
        return value; // ideally, the values are the same
    }

    public static int isPinned(Target_java_lang_Thread thread, Target_java_lang_ContinuationScope scope, boolean isCurrentThread) {
        Target_java_lang_Continuation cont = thread.getContinuation();

        IsolateThread vmThread = isCurrentThread ? CurrentIsolate.getCurrentThread() : PlatformThreads.getIsolateThread(SubstrateUtil.cast(thread, Thread.class));

        if (cont != null) {
            while (true) {
                if (cont.cs > 0) {
                    return PINNED_CRITICAL_SECTION;
                }

                if (cont.getParent() != null && cont.getScope() != scope) {
                    cont = cont.getParent();
                } else {
                    break;
                }
            }

            JavaFrameAnchor anchor = JavaFrameAnchors.getFrameAnchor(vmThread);
            if (anchor.isNonNull() && cont.internal.getBaseSP().aboveThan(anchor.getLastJavaSP())) {
                return PINNED_NATIVE;
            }
        }
        return YIELD_SUCCESS;
    }

    public static boolean isStarted(Target_java_lang_Continuation cont) {
        return cont.isStarted();
    }

    public static Pointer getBaseSP(Target_java_lang_Continuation cont) {
        return cont.internal.getBaseSP();
    }

    /**
     * Note this is different than `Thread.getContinuation`. `Thread.getContinuation` is orthogonal
     * with `VirtualThread.cont`.
     */
    public static Target_java_lang_Continuation getContinuation(Target_java_lang_Thread thread) {
        if (thread.isVirtual()) {
            Target_java_lang_VirtualThread vthread = SubstrateUtil.cast(thread, Target_java_lang_VirtualThread.class);
            return vthread.cont;
        }
        return thread.cont;
    }

    public static class CompatibilityUtil {
        private static final Field FIELDHOLDER_STATUS_FIELD = (ImageInfo.inImageCode() && (isEnabled() || JavaVersionUtil.JAVA_SPEC >= 19))
                        ? ReflectionUtil.lookupField(Target_java_lang_Thread_FieldHolder.class, "threadStatus")
                        : null;
        private static final Field THREAD_STATUS_FIELD = (ImageInfo.inImageCode() && !(isEnabled() || JavaVersionUtil.JAVA_SPEC >= 19))
                        ? ReflectionUtil.lookupField(Target_java_lang_Thread.class, "threadStatus")
                        : null;

        static long getStackSize(Target_java_lang_Thread tjlt) {
            return isEnabled() || JavaVersionUtil.JAVA_SPEC >= 19 ? tjlt.holder.stackSize : tjlt.stackSize;
        }

        static int getThreadStatus(Target_java_lang_Thread tjlt) {
            return isEnabled() || JavaVersionUtil.JAVA_SPEC >= 19 ? tjlt.holder.threadStatus : tjlt.threadStatus;
        }

        static void setThreadStatus(Target_java_lang_Thread tjlt, int threadStatus) {
            if (isEnabled() || JavaVersionUtil.JAVA_SPEC >= 19) {
                tjlt.holder.threadStatus = threadStatus;
            } else {
                tjlt.threadStatus = threadStatus;
            }
        }

        static boolean compareAndSetThreadStatus(Target_java_lang_Thread tjlt, int expectedStatus, int newStatus) {
            if (isEnabled() || JavaVersionUtil.JAVA_SPEC >= 19) {
                return Unsafe.getUnsafe().compareAndSetInt(tjlt.holder, Unsafe.getUnsafe().objectFieldOffset(FIELDHOLDER_STATUS_FIELD), expectedStatus, newStatus);
            } else {
                return Unsafe.getUnsafe().compareAndSetInt(tjlt, Unsafe.getUnsafe().objectFieldOffset(THREAD_STATUS_FIELD), expectedStatus, newStatus);
            }
        }

        static int getPriority(Target_java_lang_Thread tjlt) {
            if (isEnabled() || JavaVersionUtil.JAVA_SPEC >= 19) {
                return tjlt.holder.priority;
            } else {
                return tjlt.priority;
            }
        }

        static void setPriority(Target_java_lang_Thread tjlt, int priority) {
            if (isEnabled() || JavaVersionUtil.JAVA_SPEC >= 19) {
                tjlt.holder.priority = priority;
            } else {
                tjlt.priority = priority;
            }
        }

        static void setStackSize(Target_java_lang_Thread tjlt, long stackSize) {
            if (isEnabled() || JavaVersionUtil.JAVA_SPEC >= 19) {
                tjlt.holder.stackSize = stackSize;
            } else {
                tjlt.stackSize = stackSize;
            }
        }

        static void setDaemon(Target_java_lang_Thread tjlt, boolean isDaemon) {
            if (isEnabled() || JavaVersionUtil.JAVA_SPEC >= 19) {
                tjlt.holder.daemon = isDaemon;
            } else {
                tjlt.daemon = isDaemon;
            }
        }

        static void setGroup(Target_java_lang_Thread tjlt, ThreadGroup group) {
            if (isEnabled() || JavaVersionUtil.JAVA_SPEC >= 19) {
                tjlt.holder.group = group;
            } else {
                tjlt.group = group;
            }
        }

        static void setTarget(Target_java_lang_Thread tjlt, Runnable target) {
            if (isEnabled() || JavaVersionUtil.JAVA_SPEC >= 19) {
                tjlt.holder.task = target;
            } else {
                tjlt.target = target;
            }
        }

        static void initThreadFields(Target_java_lang_Thread tjlt, ThreadGroup group, Runnable target, long stackSize, int priority, boolean daemon, int threadStatus) {
            if (isEnabled() || JavaVersionUtil.JAVA_SPEC >= 19) {
                tjlt.holder = new Target_java_lang_Thread_FieldHolder(null, null, 0, 0, false);
            }
            setGroup(tjlt, group);

            setPriority(tjlt, priority);
            setDaemon(tjlt, daemon);

            CompatibilityUtil.setTarget(tjlt, target);
            tjlt.setPriority(priority);

            /* Stash the specified stack size in case the VM cares */
            CompatibilityUtil.setStackSize(tjlt, stackSize);

            CompatibilityUtil.setThreadStatus(tjlt, threadStatus);
        }
    }

    private LoomSupport() {
    }
}
