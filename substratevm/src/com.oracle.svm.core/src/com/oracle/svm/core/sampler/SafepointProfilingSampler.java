/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.util.TimeUtils;
import org.graalvm.collections.LockFreePrefixTree;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.thread.ThreadListener;
import com.oracle.svm.core.thread.ThreadingSupportImpl;

public class SafepointProfilingSampler implements ProfilingSampler, ThreadListener {

    private final LockFreePrefixTree prefixTree = new LockFreePrefixTree(new LockFreePrefixTree.HeapAllocator());

    @Platforms(Platform.HOSTED_ONLY.class)
    public SafepointProfilingSampler() {
    }

    @Override
    public void beforeThreadRun() {
        ThreadingSupportImpl.RecurringCallbackTimer callback = ThreadingSupportImpl.createRecurringCallbackTimer(TimeUtils.millisToNanos(10), (access) -> sampleThreadStack());
        ThreadingSupportImpl.setRecurringCallback(CurrentIsolate.getCurrentThread(), callback);
    }

    @Override
    public LockFreePrefixTree prefixTree() {
        return prefixTree;
    }

    private void sampleThreadStack() {
        SamplingStackVisitor visitor = new SamplingStackVisitor();
        SamplingStackVisitor.StackTrace data = new SamplingStackVisitor.StackTrace();
        walkCurrentThread(data, visitor);
        long[] result = data.data;
        LockFreePrefixTree.Node node = prefixTree.root();
        for (int i = data.num - 1; i >= 0; i--) {
            node = node.at(result[i]);
        }
        incStackTraceCounter(node);
    }

    private static void incStackTraceCounter(LockFreePrefixTree.Node node) {
        node.incValue();
    }

    @NeverInline("Starts a stack walk in the caller frame")
    private static void walkCurrentThread(SamplingStackVisitor.StackTrace data, SamplingStackVisitor visitor) {
        Pointer sp = KnownIntrinsics.readStackPointer();
        JavaStackWalker.walkCurrentThread(sp, visitor, data);
    }
}
