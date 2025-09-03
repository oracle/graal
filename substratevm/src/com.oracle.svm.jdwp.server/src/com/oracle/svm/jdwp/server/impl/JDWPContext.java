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
package com.oracle.svm.jdwp.server.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.svm.jdwp.server.ClassUtils;

public final class JDWPContext {
    private final ThreadsCollector threads = new ThreadsCollector();
    private final Map<Long, CallFrame> frames = new ConcurrentHashMap<>();
    private final Breakpoints breakpoints = new Breakpoints();

    public JDWPContext() {
        assert ClassUtils.UNIVERSE != null;
    }

    void registerCallFrames(CallFrame... newFrames) {
        for (CallFrame f : newFrames) {
            frames.put(f.getFrameId(), f);
        }
    }

    void unregisterCallFrames(CallFrame... newFrames) {
        for (CallFrame f : newFrames) {
            frames.remove(f.getFrameId());
        }
    }

    public ThreadRef getThreadRef(long threadId) {
        return threads.getThreadRef(threadId);
    }

    public ThreadRef getThreadRefIfExists(long threadId) {
        return threads.getThreadRefIfExists(threadId);
    }

    public ThreadsCollector getThreadsCollector() {
        return threads;
    }

    public Breakpoints getBreakpoints() {
        return breakpoints;
    }
}
