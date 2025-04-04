/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.serviceprovider.JavaVersionUtil;

@TargetClass(java.util.concurrent.CompletableFuture.class)
final class Target_java_util_concurrent_CompletableFuture {
    // Checkstyle: stop
    @Alias @InjectAccessors(CompletableFutureUseCommonPoolAccessor.class) //
    @TargetElement(onlyWith = JDK21OrEarlier.class) //
    private static boolean USE_COMMON_POOL;

    @Alias @InjectAccessors(CompletableFutureAsyncPoolAccessor.class) //
    @TargetElement(onlyWith = JDK21OrEarlier.class) //
    private static Executor ASYNC_POOL;
    // Checkstyle: resume
}

@TargetClass(className = "java.util.concurrent.DelayScheduler", onlyWith = JDKLatest.class)
final class Target_java_util_concurrent_DelayScheduler {
    @Alias @InjectAccessors(DelaySchedulerNanoTimeOffsetAccessor.class) //
    private static long nanoTimeOffset;
}

class DelaySchedulerNanoTimeOffsetAccessor {
    static long get() {
        return DelaySchedulerNanoTimeOffsetHolder.NANO_TIME_OFFSET;
    }
}

/**
 * Holder for {@link DelaySchedulerNanoTimeOffsetHolder#NANO_TIME_OFFSET}. Initialized at runtime
 * via {@link CompletableFutureFeature}.
 */
class DelaySchedulerNanoTimeOffsetHolder {

    public static final long NANO_TIME_OFFSET;

    static {
        if (SubstrateUtil.HOSTED) {
            throw VMError.shouldNotReachHere(DelaySchedulerNanoTimeOffsetHolder.class.getName() + " must only be initialized at runtime");
        }
        NANO_TIME_OFFSET = Math.min(System.nanoTime(), 0L) + Long.MIN_VALUE;
    }
}

class CompletableFutureUseCommonPoolAccessor {
    static boolean get() {
        return CompletableFutureFieldHolder.USE_COMMON_POOL;
    }
}

class CompletableFutureAsyncPoolAccessor {
    static Executor get() {
        return CompletableFutureFieldHolder.ASYNC_POOL;
    }
}

/* Note that this class is initialized at run time. */
class CompletableFutureFieldHolder {
    /* The following is copied from CompletableFuture. */

    static final boolean USE_COMMON_POOL = ForkJoinPool.getCommonPoolParallelism() > 1;

    static final Executor ASYNC_POOL;

    static {
        if (JavaVersionUtil.JAVA_SPEC <= 21) {
            if (USE_COMMON_POOL) {
                ASYNC_POOL = ForkJoinPool.commonPool();
            } else {
                ASYNC_POOL = SubstrateUtil.cast(new Target_java_util_concurrent_CompletableFuture_ThreadPerTaskExecutor(), Executor.class);
            }
        } else {
            ASYNC_POOL = null;
        }
    }
}

@TargetClass(value = java.util.concurrent.CompletableFuture.class, innerClass = "ThreadPerTaskExecutor", onlyWith = JDK21OrEarlier.class)
final class Target_java_util_concurrent_CompletableFuture_ThreadPerTaskExecutor {
}

@AutomaticallyRegisteredFeature
class CompletableFutureFeature implements InternalFeature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        RuntimeClassInitialization.initializeAtRunTime(CompletableFutureFieldHolder.class);
        RuntimeClassInitialization.initializeAtRunTime(DelaySchedulerNanoTimeOffsetHolder.class);
    }
}
