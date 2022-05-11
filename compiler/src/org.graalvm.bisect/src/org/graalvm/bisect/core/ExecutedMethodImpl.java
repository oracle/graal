/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.bisect.core;

import org.graalvm.bisect.core.optimization.OptimizationPhase;

public class ExecutedMethodImpl implements ExecutedMethod {
    private final String compilationId;
    private final String compilationMethodName;
    private final OptimizationPhase rootPhase;
    private final long period;
    private boolean hot;

    public ExecutedMethodImpl(String compilationId,
                              String compilationMethodName,
                              OptimizationPhase rootPhase,
                              long period) {
        this.compilationId = compilationId;
        this.compilationMethodName = compilationMethodName;
        this.period = period;
        this.rootPhase = rootPhase;
    }

    @Override
    public String getCompilationId() {
        return compilationId;
    }

    @Override
    public String getCompilationMethodName() {
        return compilationMethodName;
    }

    @Override
    public OptimizationPhase getRootPhase() {
        return rootPhase;
    }

    @Override
    public boolean isHot() {
        return hot;
    }

    @Override
    public void setHot(boolean hot) {
        this.hot = hot;
    }

    @Override
    public long getPeriod() {
        return period;
    }
}
