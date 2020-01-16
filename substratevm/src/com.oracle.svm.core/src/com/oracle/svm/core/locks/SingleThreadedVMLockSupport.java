/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.util.VMError;

/**
 * Support of {@link VMMutex} and {@link VMCondition} in single-threaded environments. No real
 * locking is necessary.
 */
final class SingleThreadedVMLockSupport {
    // Empty class to have the same name as the source file.
}

@AutomaticFeature
final class SingleThreadedVMLockFeature implements Feature {

    private final ClassInstanceReplacer<VMMutex, VMMutex> mutexReplacer = new ClassInstanceReplacer<VMMutex, VMMutex>(VMMutex.class) {
        @Override
        protected VMMutex createReplacement(VMMutex source) {
            return new SingleThreadedVMMutex();
        }
    };

    private final ClassInstanceReplacer<VMCondition, VMCondition> conditionReplacer = new ClassInstanceReplacer<VMCondition, VMCondition>(VMCondition.class) {
        @Override
        protected VMCondition createReplacement(VMCondition source) {
            return new SingleThreadedVMCondition((SingleThreadedVMMutex) mutexReplacer.apply(source.getMutex()));
        }
    };

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return !SubstrateOptions.MultiThreaded.getValue();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(mutexReplacer);
        access.registerObjectReplacer(conditionReplacer);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        /* Seal the lists. */
        mutexReplacer.getReplacements();
        conditionReplacer.getReplacements();
    }
}

final class SingleThreadedVMMutex extends VMMutex {
    @Platforms(Platform.HOSTED_ONLY.class)
    protected SingleThreadedVMMutex() {
    }

    @Override
    public VMMutex lock() {
        assertNotOwner("Recursive locking is not supported");
        setOwnerToCurrentThread();
        return this;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true, callerMustBe = true)
    public void lockNoTransition() {
        assertNotOwner("Recursive locking is not supported");
        setOwnerToCurrentThread();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true, callerMustBe = true)
    public void lockNoTransitionUnspecifiedOwner() {
        setOwnerToUnspecified();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void unlock() {
        clearCurrentThreadOwner();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void unlockNoTransitionUnspecifiedOwner() {
        clearUnspecifiedOwner();
    }

    @Override
    public void unlockWithoutChecks() {
        clearCurrentThreadOwner();
    }
}

final class SingleThreadedVMCondition extends VMCondition {

    SingleThreadedVMCondition(SingleThreadedVMMutex mutex) {
        super(mutex);
    }

    @Override
    public void block() {
        VMError.shouldNotReachHere("Cannot block in a single-threaded environment, because there is no other thread that could signal");
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", callerMustBe = true)
    @Override
    public void blockNoTransition() {
        VMError.shouldNotReachHere("Cannot block in a single-threaded environment, because there is no other thread that could signal");
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", callerMustBe = true)
    @Override
    public void blockNoTransitionUnspecifiedOwner() {
        VMError.shouldNotReachHere("Cannot block in a single-threaded environment, because there is no other thread that could signal");
    }

    @Override
    public long block(long nanos) {
        VMError.shouldNotReachHere("Cannot block in a single-threaded environment, because there is no other thread that could signal");
        return 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", callerMustBe = true)
    @Override
    public long blockNoTransition(long nanos) {
        VMError.shouldNotReachHere("Cannot block in a single-threaded environment, because there is no other thread that could signal");
        return 0;
    }

    @Override
    public void signal() {
        /* Nothing to do. */
    }

    @Override
    public void broadcast() {
        /* Nothing to do. */
    }
}
