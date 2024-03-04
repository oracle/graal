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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.jvmti.headers.JvmtiExternalEnv;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.snippets.ImplicitExceptions;

import jdk.graal.compiler.api.replacements.Fold;

public final class JvmtiEnvManager {

    private PointerBase headEnvironment;
    private PointerBase tailEnvironment;

    private final VMMutex mutex;
    private PointerBase sharedEnvironment;
    private boolean initialized;

    private int phase;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JvmtiEnvManager() {
        headEnvironment = WordFactory.nullPointer();
        tailEnvironment = WordFactory.nullPointer();
        mutex = new VMMutex("jvmtiEnvManager");
        sharedEnvironment = WordFactory.nullPointer();
        initialized = false;
        phase = -1;
    }

    @Fold
    public static JvmtiEnvManager singleton() {
        return ImageSingletons.lookup(JvmtiEnvManager.class);
    }

    public JvmtiEnv getHeadEnvironment() {
        return (JvmtiEnv) headEnvironment;
    }

    public JvmtiEnv getTailEnvironment() {
        return (JvmtiEnv) tailEnvironment;
    }

    public void setTailEnvironment(JvmtiEnv env) {
        mutex.lock();
        tailEnvironment = env;
        mutex.unlock();
    }

    public boolean hasAnyEnvironments() {
        return headEnvironment.isNonNull();
    }

    public JvmtiEnvShared getSharedEnvironment() {
        return (JvmtiEnvShared) sharedEnvironment;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public int getPhase() {
        return phase;
    }

    public void setPhase(int phase) {
        mutex.lock();
        this.phase = phase;
        mutex.unlock();
    }

    public void createJvmtiEnv(WordPointer env) {
        JvmtiExternalEnv envExt = JvmtiEnvUtil.allocate();
        JvmtiEnv envInt = JvmtiEnvUtil.toInternal(envExt);

        mutex.lock();
        if (!initialized) {
            initialize();
        }
        JvmtiEnvUtil.initialize(envInt, (JvmtiEnvShared) sharedEnvironment);

        if (headEnvironment.isNull()) {
            headEnvironment = envInt;
        } else {
            JvmtiEnvUtil.setNextEnvironment((JvmtiEnv) tailEnvironment, envInt);
        }
        tailEnvironment = envInt;
        env.write(envExt);

        mutex.unlock();
    }

    public void destroyJvmtiEnv(JvmtiEnv env) {
        mutex.lock();
        JvmtiEnv current = (JvmtiEnv) headEnvironment;
        JvmtiEnv previous = WordFactory.nullPointer();
        while (current.notEqual(env)) {
            previous = current;
            current = current.getNextEnv();
        }

        if (headEnvironment.equal(tailEnvironment) && headEnvironment.equal(env)) {
            // TODO @dprcci check whether this is correct?
            destroy();
        } else if (previous.isNull()) {
            headEnvironment = current.getNextEnv();
        } else {
            previous.setNextEnv(current.getNextEnv());
            if (current.equal(tailEnvironment)) {
                tailEnvironment = previous;
            }
        }
        JvmtiEnvUtil.free(env);
        mutex.unlock();
    }

    private void initialize() {
        JvmtiEnvShared envShared = JvmtiEnvSharedUtil.allocate();
        JvmtiEnvSharedUtil.initialize(envShared);
        sharedEnvironment = envShared;
        initialized = true;
    }

    public void destroy() {
        if (!initialized) {
            if (sharedEnvironment.isNonNull()) {
                // TODO @dprcci illegalstateexception would make more sense?
                try {
                    throw ImplicitExceptions.CACHED_ILLEGAL_ARGUMENT_EXCEPTION;
                } finally {
                    if (mutex.isOwner()) {
                        mutex.unlock();
                    }
                }
                // throw new RuntimeException("Global environment destruction can only occur once
                // and has already been performed");
            }
            // already destroyed
            return;
        }
        JvmtiEnvSharedUtil.free(getSharedEnvironment());
        sharedEnvironment = WordFactory.nullPointer();
        headEnvironment = WordFactory.nullPointer();
        tailEnvironment = WordFactory.nullPointer();
        initialized = false;
    }

}
