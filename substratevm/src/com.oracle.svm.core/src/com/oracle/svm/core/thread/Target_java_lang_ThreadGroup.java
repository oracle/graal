/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

@TargetClass(ThreadGroup.class)
final class Target_java_lang_ThreadGroup {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ThreadGroupNUnstartedThreadsRecomputation.class)//
    private int nUnstartedThreads;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ThreadGroupNThreadsRecomputation.class)//
    private int nthreads;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ThreadGroupThreadsRecomputation.class)//
    private Thread[] threads;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ThreadGroupNGroupsRecomputation.class)//
    private int ngroups;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ThreadGroupGroupsRecomputation.class)//
    private ThreadGroup[] groups;

    @Alias
    native void addUnstarted();

    @Alias
    native void add(Thread t);
}

@Platforms(Platform.HOSTED_ONLY.class)
class ThreadIdRecomputation implements RecomputeFieldValue.CustomFieldValueComputer {
    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        Thread thread = (Thread) receiver;
        return JavaThreadsFeature.threadId(thread);
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ThreadStatusRecomputation implements RecomputeFieldValue.CustomFieldValueComputer {
    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        Thread thread = (Thread) receiver;
        if (thread.getState() == Thread.State.TERMINATED) {
            return ThreadStatus.TERMINATED;
        }
        assert thread.getState() == Thread.State.NEW : "All threads are in NEW state during image generation";
        if (thread == JavaThreads.singleton().mainThread) {
            /* The main thread is recomputed as running. */
            return ThreadStatus.RUNNABLE;
        } else {
            /* All other threads remain unstarted. */
            return ThreadStatus.NEW;
        }
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ThreadGroupNUnstartedThreadsRecomputation implements RecomputeFieldValue.CustomFieldValueComputer {
    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        ThreadGroup group = (ThreadGroup) receiver;
        int result = 0;
        for (Thread thread : JavaThreadsFeature.singleton().reachableThreads.keySet()) {
            /* The main thread is recomputed as running and therefore not counted as unstarted. */
            if (thread.getThreadGroup() == group && thread != JavaThreads.singleton().mainThread) {
                result++;
            }
        }
        return result;
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ThreadGroupNThreadsRecomputation implements RecomputeFieldValue.CustomFieldValueComputer {
    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        ThreadGroup group = (ThreadGroup) receiver;

        if (group == JavaThreads.singleton().mainGroup) {
            /* The main group contains the main thread, which we recompute as running. */
            return 1;
        } else {
            /* No other thread group has a thread running at startup. */
            return 0;
        }
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ThreadGroupThreadsRecomputation implements RecomputeFieldValue.CustomFieldValueComputer {
    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        ThreadGroup group = (ThreadGroup) receiver;

        if (group == JavaThreads.singleton().mainGroup) {
            /* The main group contains the main thread, which we recompute as running. */
            return JavaThreads.singleton().mainGroupThreadsArray;
        } else {
            /* No other thread group has a thread running at startup. */
            return null;
        }
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ThreadGroupNGroupsRecomputation implements RecomputeFieldValue.CustomFieldValueComputer {
    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        ThreadGroup group = (ThreadGroup) receiver;
        return JavaThreadsFeature.singleton().reachableThreadGroups.get(group).ngroups;
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ThreadGroupGroupsRecomputation implements RecomputeFieldValue.CustomFieldValueComputer {
    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        ThreadGroup group = (ThreadGroup) receiver;
        return JavaThreadsFeature.singleton().reachableThreadGroups.get(group).groups;
    }
}
