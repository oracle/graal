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

import java.util.concurrent.ThreadFactory;

import org.graalvm.compiler.api.replacements.Fold;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.util.VMError;

/**
 * Code specific to virtual threads is part of the {@link Thread} methods, e.g. {@code yield} or
 * {@code sleep}, and the implementation for platform threads generally in {@code yield0} or
 * {@code sleep0}, so we only substitute this platform thread code in
 * {@link Target_java_lang_Thread}, and never expect these methods to be reachable, therefore
 * extending {@link NoVirtualThreads}.
 */
final class LoomVirtualThreads extends NoVirtualThreads {
    private static Target_java_lang_VirtualThread cast(Thread thread) {
        return SubstrateUtil.cast(thread, Target_java_lang_VirtualThread.class);
    }

    @Fold
    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public ThreadFactory createFactory() {
        throw VMError.unimplemented();
    }

    @AlwaysInline("Eliminate code handling virtual threads.")
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
}
