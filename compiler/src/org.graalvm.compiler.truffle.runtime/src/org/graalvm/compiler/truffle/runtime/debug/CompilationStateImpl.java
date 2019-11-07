/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.debug;

import com.oracle.truffle.api.instrumentation.CompilationState;

public class CompilationStateImpl implements CompilationState {

    private final int queued;
    private final int running;
    private final int finished;
    private final int failures;
    private final int dequeues;
    private final int deoptimizations;

    public CompilationStateImpl(int queued, int running, int finished, int failures, int dequeues, int deoptimizations) {
        this.queued = queued;
        this.running = running;
        this.finished = finished;
        this.failures = failures;
        this.dequeues = dequeues;
        this.deoptimizations = deoptimizations;
    }

    @Override
    public int getQueued() {
        return queued;
    }

    @Override
    public int getRunning() {
        return running;
    }

    @Override
    public int getFinished() {
        return finished;
    }

    @Override
    public int getFailed() {
        return failures;
    }

    @Override
    public int getDequeued() {
        return dequeues;
    }

    @Override
    public int getDeoptimizations() {
        return deoptimizations;
    }

}
