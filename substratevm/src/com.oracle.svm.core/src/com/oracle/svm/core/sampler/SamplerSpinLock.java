/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.sampler;

import org.graalvm.compiler.nodes.PauseNode;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.util.VMError;

/**
 * The custom implementation of spin lock that is async signal safe.
 * 
 * In some specific situations, the signal handler can interrupt execution while the same thread
 * already has the lock. This implementation will check and fatally fail while other spin locks
 * implementations can deadlock in this case. So it is essential to check if the current thread is
 * the owner of the lock, before acquiring it.
 */
class SamplerSpinLock {
    private final UninterruptibleUtils.AtomicPointer<IsolateThread> owner;

    @Platforms(Platform.HOSTED_ONLY.class)
    SamplerSpinLock() {
        this.owner = new UninterruptibleUtils.AtomicPointer<>();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isOwner() {
        return owner.get().equal(CurrentIsolate.getCurrentThread());
    }

    @Uninterruptible(reason = "This method does not do a transition, so the whole critical section must be uninterruptible.", callerMustBe = true)
    public void lock() {
        VMError.guarantee(!isOwner(), "The current thread already has the lock!");
        IsolateThread currentThread = CurrentIsolate.getCurrentThread();
        while (!owner.compareAndSet(WordFactory.nullPointer(), currentThread)) {
            PauseNode.pause();
        }
    }

    @Uninterruptible(reason = "The whole critical section must be uninterruptible.", callerMustBe = true)
    public void unlock() {
        VMError.guarantee(isOwner(), "The current thread doesn't have the lock!");
        owner.compareAndSet(CurrentIsolate.getCurrentThread(), WordFactory.nullPointer());
    }
}
