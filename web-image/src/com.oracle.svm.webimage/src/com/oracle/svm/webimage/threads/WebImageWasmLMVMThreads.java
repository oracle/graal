/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.threads;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.webimage.platform.WebImageWasmLMPlatform;

@AutomaticallyRegisteredImageSingleton(VMThreads.class)
@Platforms(WebImageWasmLMPlatform.class)
public class WebImageWasmLMVMThreads extends VMThreads {
    @Override
    @Uninterruptible(reason = "Unknown thread state.")
    public void failFatally(int code, CCharPointer message) {
        throw VMError.shouldNotReachHere("VMThreads.failFatally");
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void joinNoTransition(OSThreadHandle osThreadHandle) {
        throw VMError.shouldNotReachHere("VMThreads.joinNoTransition");
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected OSThreadHandle getCurrentOSThreadHandle() {
        throw VMError.shouldNotReachHere("VMThreads.getCurrentOSThreadHandle");
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected OSThreadId getCurrentOSThreadId() {
        throw VMError.shouldNotReachHere("VMThreads.getCurrentOSThreadId");
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean supportsNativeYieldAndSleep() {
        return true;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void yield() {
        // Do nothing, there is only a single thread
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void nativeSleep(int milliseconds) {
        // Do nothing, there is only a single thread
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void detachThread(IsolateThread thread, boolean currentThread) {
        throw VMError.shouldNotReachHere("VMThreads.detachThread");
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void cleanupExitedOsThreads() {
        // Do nothing, there are no other threads.
    }
}
