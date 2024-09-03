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
package com.oracle.svm.core.locks;

import java.util.Comparator;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateDiagnostics.DiagnosticThunk;
import com.oracle.svm.core.SubstrateDiagnostics.ErrorContext;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.util.ImageHeapList;

import jdk.graal.compiler.api.replacements.Fold;

@AutomaticallyRegisteredFeature
final class VMLockFeature implements InternalFeature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (!ImageSingletons.contains(VMLockSupport.class)) {
            /* The platform uses a VMThreads implementation that does not rely on VMLockSupport. */
            return;
        }
        VMLockSupport support = ImageSingletons.lookup(VMLockSupport.class);

        support.mutexReplacer = new ClassInstanceReplacer<>(VMMutex.class, support.mutexes, support::replaceVMMutex);
        support.conditionReplacer = new ClassInstanceReplacer<>(VMCondition.class, support.conditions, support::replaceVMCondition);
        support.semaphoreReplacer = new ClassInstanceReplacer<>(VMSemaphore.class, support.semaphores, support::replaceSemaphore);

        access.registerObjectReplacer(support.mutexReplacer);
        access.registerObjectReplacer(support.conditionReplacer);
        access.registerObjectReplacer(support.semaphoreReplacer);
    }
}

public abstract class VMLockSupport {

    @Fold
    public static VMLockSupport singleton() {
        return ImageSingletons.lookup(VMLockSupport.class);
    }

    /** All mutexes, so that we can initialize them at run time when the VM starts. */
    protected final List<VMMutex> mutexes = ImageHeapList.create(VMMutex.class, Comparator.comparing(VMMutex::getName));
    /** All conditions, so that we can initialize them at run time when the VM starts. */
    protected final List<VMCondition> conditions = ImageHeapList.create(VMCondition.class, Comparator.comparing(VMCondition::getName));
    /** All semaphores, so that we can initialize them at run time when the VM starts. */
    protected final List<VMSemaphore> semaphores = ImageHeapList.create(VMSemaphore.class, Comparator.comparing(VMSemaphore::getName));

    @Platforms(Platform.HOSTED_ONLY.class) //
    protected ClassInstanceReplacer<VMMutex, VMMutex> mutexReplacer;
    @Platforms(Platform.HOSTED_ONLY.class) //
    protected ClassInstanceReplacer<VMCondition, VMCondition> conditionReplacer;
    @Platforms(Platform.HOSTED_ONLY.class) //
    protected ClassInstanceReplacer<VMSemaphore, VMSemaphore> semaphoreReplacer;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected abstract VMMutex replaceVMMutex(VMMutex source);

    @Platforms(Platform.HOSTED_ONLY.class)
    protected abstract VMCondition replaceVMCondition(VMCondition source);

    @Platforms(Platform.HOSTED_ONLY.class)
    protected abstract VMSemaphore replaceSemaphore(VMSemaphore source);

    /**
     * Initializes all {@link VMMutex}, {@link VMCondition}, and {@link VMSemaphore} objects.
     *
     * @return {@code true} if the initialization was successful, {@code false} if an error
     *         occurred.
     */
    @Uninterruptible(reason = "Too early for safepoints.")
    public final boolean initialize() {
        for (int i = 0; i < mutexes.size(); i++) {
            if (mutexes.get(i).initialize() != 0) {
                return false;
            }
        }
        for (int i = 0; i < conditions.size(); i++) {
            if (conditions.get(i).initialize() != 0) {
                return false;
            }
        }
        for (int i = 0; i < semaphores.size(); i++) {
            if (semaphores.get(i).initialize() != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Destroys all {@link VMMutex}, {@link VMCondition}, and {@link VMSemaphore} objects.
     *
     * @return {@code true} if the destruction was successful, {@code false} if an error occurred.
     */
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public final boolean destroy() {
        for (int i = 0; i < semaphores.size(); i++) {
            if (semaphores.get(i).destroy() != 0) {
                return false;
            }
        }
        for (int i = 0; i < conditions.size(); i++) {
            if (conditions.get(i).destroy() != 0) {
                return false;
            }
        }
        for (int i = 0; i < mutexes.size(); i++) {
            if (mutexes.get(i).destroy() != 0) {
                return false;
            }
        }
        return true;
    }

    public static class DumpVMMutexes extends DiagnosticThunk {
        @Override
        public int maxInvocationCount() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            log.string("VM mutexes:").indent(true);

            VMLockSupport support = null;
            if (ImageSingletons.contains(VMLockSupport.class)) {
                support = ImageSingletons.lookup(VMLockSupport.class);
            }

            if (support == null || support.mutexes == null) {
                log.string("No mutex information is available.").newline();
            } else {
                for (int i = 0; i < support.mutexes.size(); i++) {
                    VMMutex mutex = support.mutexes.get(i);
                    IsolateThread owner = mutex.owner;
                    log.string("mutex \"").string(mutex.getName()).string("\" ");
                    if (owner.isNull()) {
                        log.string("is unlocked.");
                    } else {
                        log.string("is locked by ");
                        if (owner.equal(VMMutex.UNSPECIFIED_OWNER)) {
                            log.string("an unspecified thread.");
                        } else {
                            log.string("thread ").zhex(owner);
                        }
                    }
                    log.newline();
                }
            }

            log.indent(false);
        }
    }
}
