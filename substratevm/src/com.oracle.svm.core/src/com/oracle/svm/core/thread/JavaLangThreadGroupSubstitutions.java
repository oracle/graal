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

import java.lang.ref.WeakReference;
import java.util.Arrays;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jfr.JfrThreadRepository;
import com.oracle.svm.util.ReflectionUtil;

@TargetClass(ThreadGroup.class)
final class Target_java_lang_ThreadGroup {

    @Inject //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ThreadGroupThreadsRecomputation.class)//
    Thread[] injectedThreads;

    /*
     * All ThreadGroups in the image heap are strong and will be stored in ThreadGroup.groups.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    private int nweaks;
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    private WeakReference<ThreadGroup>[] weaks;

    @Inject @InjectAccessors(ThreadGroupIdAccessor.class) //
    public long id;

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    long injectedId;

    @AnnotateOriginal
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public native String getName();

    @Alias ThreadGroup parent;
}

/**
 * This class assigns a unique id to each thread group, and this unique id is used by JFR.
 */
class ThreadGroupIdAccessor {
    private static final UninterruptibleUtils.AtomicLong nextID = new UninterruptibleUtils.AtomicLong(JfrThreadRepository.VIRTUAL_THREAD_GROUP_ID + 1);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static long getId(Target_java_lang_ThreadGroup that) {
        if (that.injectedId == 0) {
            Target_java_lang_ThreadGroup virtualThreadGroup = SubstrateUtil.cast(Target_java_lang_Thread.virtualThreadGroup(), Target_java_lang_ThreadGroup.class);
            that.injectedId = that == virtualThreadGroup ? JfrThreadRepository.VIRTUAL_THREAD_GROUP_ID : nextID.getAndIncrement();
        }
        return that.injectedId;
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ThreadIdRecomputation implements FieldValueTransformer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        Thread thread = (Thread) receiver;
        return JavaThreadsFeature.threadId(thread);
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ThreadStatusRecomputation implements FieldValueTransformer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        Thread thread = (Thread) receiver;
        if (thread.getState() == Thread.State.TERMINATED) {
            return ThreadStatus.TERMINATED;
        }
        assert thread.getState() == Thread.State.NEW : "All threads are in NEW state during image generation";
        if (thread == PlatformThreads.singleton().mainThread) {
            /* The main thread is recomputed as running. */
            return ThreadStatus.RUNNABLE;
        } else {
            /* All other threads remain unstarted. */
            return ThreadStatus.NEW;
        }
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ThreadHolderRecomputation implements FieldValueTransformer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        int threadStatus = ReflectionUtil.readField(ReflectionUtil.lookupClass(false, "java.lang.Thread$FieldHolder"), "threadStatus", receiver);
        if (threadStatus == ThreadStatus.TERMINATED) {
            return ThreadStatus.TERMINATED;
        }
        assert threadStatus == ThreadStatus.NEW : "All threads are in NEW state during image generation";
        if (receiver == ReflectionUtil.readField(Thread.class, "holder", PlatformThreads.singleton().mainThread)) {
            /* The main thread is recomputed as running. */
            return ThreadStatus.RUNNABLE;
        } else {
            /* All other threads remain unstarted. */
            return ThreadStatus.NEW;
        }
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ThreadGroupThreadsRecomputation implements FieldValueTransformer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        ThreadGroup group = (ThreadGroup) receiver;

        if (group == PlatformThreads.singleton().mainGroup) {
            /* The main group contains the main thread, which we recompute as running. */
            return PlatformThreads.singleton().mainGroupThreadsArray;
        } else {
            /* No other thread group has a thread running at startup. */
            return null;
        }
    }
}

final class ThreadGroupThreadsAccessor {
    static Thread[] get(Target_java_lang_ThreadGroup that) {
        return that.injectedThreads;
    }

    static void set(Target_java_lang_ThreadGroup that, Thread[] value) {
        if (that.injectedThreads != null && Heap.getHeap().isInImageHeap(that.injectedThreads)) {
            Arrays.fill(that.injectedThreads, null);
        }
        that.injectedThreads = value;
    }

    private ThreadGroupThreadsAccessor() {
    }
}

public class JavaLangThreadGroupSubstitutions {

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static ThreadGroup getParentThreadGroupUnsafe(ThreadGroup threadGroup) {
        return SubstrateUtil.cast(threadGroup, Target_java_lang_ThreadGroup.class).parent;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long getThreadGroupId(ThreadGroup threadGroup) {
        return SubstrateUtil.cast(threadGroup, Target_java_lang_ThreadGroup.class).id;
    }
}
