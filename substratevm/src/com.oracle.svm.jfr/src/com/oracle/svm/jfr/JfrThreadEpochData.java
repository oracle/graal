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
package com.oracle.svm.jfr;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleAbstractHashtable;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

public class JfrThreadEpochData {
    private final UninterruptibleAbstractHashtable<JfrThreadRepository.JfrVisited> visitedThreadGroups;

    private JfrBuffer threadBuffer;
    private int threadCount;
    private JfrBuffer threadGroupBuffer;
    private int threadGroupCount;

    @Platforms(Platform.HOSTED_ONLY.class)
    JfrThreadEpochData() {
        this.threadCount = 0;
        this.threadGroupCount = 0;
        this.visitedThreadGroups = new JfrThreadRepository.JfrVisitedThreadGroups();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UninterruptibleAbstractHashtable<JfrThreadRepository.JfrVisited> getVisitedThreadGroups() {
        return visitedThreadGroups;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public JfrBuffer getThreadBuffer() {
        return threadBuffer;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void setThreadBuffer(JfrBuffer threadBuffer) {
        this.threadBuffer = threadBuffer;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getThreadCount() {
        return threadCount;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void incThreadCount() {
        threadCount++;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public JfrBuffer getThreadGroupBuffer() {
        return threadGroupBuffer;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void setThreadGroupBuffer(JfrBuffer threadGroupBuffer) {
        this.threadGroupBuffer = threadGroupBuffer;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getThreadGroupCount() {
        return threadGroupCount;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void setThreadGroupCount(int threadGroupCount) {
        this.threadGroupCount = threadGroupCount;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void incThreadGroupCount() {
        threadGroupCount++;
    }
}
