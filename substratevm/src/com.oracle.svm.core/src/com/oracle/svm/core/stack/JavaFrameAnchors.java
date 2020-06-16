/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.stack;

import org.graalvm.nativeimage.IsolateThread;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;

/**
 * Maintains the linked list of {@link JavaFrameAnchor} for stack walking.
 */
public class JavaFrameAnchors {

    private static final FastThreadLocalWord<JavaFrameAnchor> lastAnchor = FastThreadLocalFactory.createWord();

    public static void pushFrameAnchor(JavaFrameAnchor anchor) {
        anchor.setPreviousAnchor(lastAnchor.get());
        lastAnchor.set(anchor);
    }

    public static JavaFrameAnchor popFrameAnchor() {
        JavaFrameAnchor result = lastAnchor.get();
        lastAnchor.set(result.getPreviousAnchor());
        return result;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static JavaFrameAnchor getFrameAnchor() {
        return lastAnchor.get();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static JavaFrameAnchor getFrameAnchor(IsolateThread vmThread) {
        return lastAnchor.get(vmThread);
    }
}
