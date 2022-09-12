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
package com.oracle.svm.core.jfr;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.util.VMError;

/**
 * List of all possible thread states.
 */
public enum JfrThreadState {
    NEW("STATE_NEW"),
    RUNNABLE("STATE_RUNNABLE"),
    BLOCKED("STATE_BLOCKED"),
    WAITING("STATE_WAITING"),
    TIMED_WAITING("STATE_TIMED_WAITING"),
    TERMINATED("STATE_TERMINATED");

    private final String text;

    @Platforms(Platform.HOSTED_ONLY.class)
    JfrThreadState(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getId() {
        // First entry needs to have id 0.
        return ordinal();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long getId(Thread.State threadState) {
        return threadStateToJfrThreadState(threadState).getId();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static JfrThreadState threadStateToJfrThreadState(Thread.State threadState) {
        switch (threadState) {
            case NEW:
                return NEW;
            case RUNNABLE:
                return RUNNABLE;
            case BLOCKED:
                return BLOCKED;
            case WAITING:
                return WAITING;
            case TIMED_WAITING:
                return TIMED_WAITING;
            case TERMINATED:
                return TERMINATED;
            default:
                throw VMError.shouldNotReachHere("Unknown thread state!");
        }
    }
}
