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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.util.VMError;

/**
 * In a Project Loom JDK, code specific to virtual threads is part of the {@link Thread} methods,
 * e.g. {@code yield} or {@code sleep}, and the implementation for platform threads is generally in
 * methods named {@code yield0} or {@code sleep0}, so we only substitute that platform thread code
 * in {@link Target_java_lang_Thread}, and generally do not expect these methods to be reachable.
 *
 * @see <a href="https://openjdk.java.net/projects/loom/">Project Loom (Wiki, code, etc.)</a>
 */
final class LoomVirtualThreads implements VirtualThreads {
    private static Target_java_lang_VirtualThread cast(Thread thread) {
        return SubstrateUtil.cast(thread, Target_java_lang_VirtualThread.class);
    }

    @Override
    public ThreadFactory createFactory() {
        return Target_java_lang_Thread.ofVirtual().factory();
    }

    @Override
    public boolean isVirtual(Thread thread) {
        return Target_java_lang_VirtualThread.class.isInstance(thread);
    }

    @Override
    public void join(Thread thread, long millis) throws InterruptedException {
        if (thread.isAlive()) {
            long nanos = MILLISECONDS.toNanos(millis);
            cast(thread).joinNanos(nanos);
        }
    }

    @Platforms({}) // fails image build if reachable
    private static RuntimeException unreachable() {
        return VMError.shouldNotReachHere();
    }

    @Override
    public boolean getAndClearInterrupt(Thread thread) {
        throw unreachable();
    }

    @Override
    public void yield() {
        throw unreachable();
    }

    @Override
    public void sleepMillis(long millis) {
        throw unreachable();
    }

    @Override
    public boolean isAlive(Thread thread) {
        throw unreachable();
    }

    @Override
    public void unpark(Thread thread) {
        throw unreachable();
    }

    @Override
    public void park() {
        throw unreachable();
    }

    @Override
    public void parkNanos(long nanos) {
        throw unreachable();
    }

    @Override
    public void parkUntil(long deadline) {
        throw unreachable();
    }

    @Override
    public void pinCurrent() {
        Target_jdk_internal_vm_Continuation.pin();
    }

    @Override
    public void unpinCurrent() {
        Target_jdk_internal_vm_Continuation.unpin();
    }

    @Override
    public Executor getScheduler(Thread thread) {
        return cast(thread).scheduler;
    }
}
