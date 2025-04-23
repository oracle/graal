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
package com.oracle.svm.core.jvmti;

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.jvmti.headers.JvmtiExternalEnv;
import com.oracle.svm.core.locks.VMMutex;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Stores information about all currently existing JVMTI environments and manages their lifecycle.
 */
public final class JvmtiEnvs {
    private final VMMutex mutex = new VMMutex("jvmtiEnvManager");

    private JvmtiEnv headEnv;
    private JvmtiEnv tailEnv;

    private int threadsIteratingEnvs;
    private boolean hasDisposedEnvs;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JvmtiEnvs() {
    }

    @Fold
    public static JvmtiEnvs singleton() {
        return ImageSingletons.lookup(JvmtiEnvs.class);
    }

    public JvmtiEnv getHead() {
        return headEnv;
    }

    public JvmtiExternalEnv create() {
        JvmtiEnv env = JvmtiEnvUtil.allocate();
        if (env.isNull()) {
            return Word.nullPointer();
        }

        mutex.lock();
        try {
            if (headEnv.isNull()) {
                headEnv = env;
            } else {
                tailEnv.setNext(env);
            }
            tailEnv = env;
            assert env.getNext().isNull();
            return JvmtiEnvUtil.toExternal(env);
        } finally {
            mutex.unlock();
        }
    }

    public void dispose(JvmtiEnv env) {
        mutex.lock();
        try {
            JvmtiEnvUtil.dispose(env);
            hasDisposedEnvs = true;
        } finally {
            mutex.unlock();
        }
    }

    public void enterEnvIteration() {
        mutex.lock();
        try {
            threadsIteratingEnvs++;
        } finally {
            mutex.unlock();
        }
    }

    public void leaveEnvIteration() {
        mutex.lock();
        try {
            int remainingThreads = threadsIteratingEnvs--;
            assert remainingThreads >= 0;
            if (remainingThreads == 0 && hasDisposedEnvs) {
                cleanup();
            }
        } finally {
            mutex.unlock();
        }
    }

    private void cleanup() {
        assert mutex.isOwner();
        assert hasDisposedEnvs;

        JvmtiEnv cur = headEnv;
        JvmtiEnv prev = Word.nullPointer();
        while (cur.isNonNull()) {
            if (JvmtiEnvUtil.isDead(cur)) {
                remove(cur, prev);
                JvmtiEnvUtil.free(cur);
            }

            prev = cur;
            cur = cur.getNext();
        }

        hasDisposedEnvs = false;
    }

    private void remove(JvmtiEnv cur, JvmtiEnv prev) {
        assert mutex.isOwner();
        if (prev.isNull()) {
            headEnv = cur.getNext();
        } else {
            prev.setNext(cur.getNext());
        }

        if (tailEnv == cur) {
            tailEnv = prev;
        }
        cur.setNext(null);
    }

    public void teardown() {
        JvmtiEnv cur = headEnv;
        while (cur.isNonNull()) {
            JvmtiEnv next = cur.getNext();
            JvmtiEnvUtil.free(cur);
            cur = next;
        }

        headEnv = Word.nullPointer();
        tailEnv = Word.nullPointer();
    }
}
