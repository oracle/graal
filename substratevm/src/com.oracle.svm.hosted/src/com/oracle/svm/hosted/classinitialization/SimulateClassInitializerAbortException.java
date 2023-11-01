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
package com.oracle.svm.hosted.classinitialization;

import jdk.graal.compiler.core.common.GraalBailoutException;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;

/**
 * Exception used to abort a simulation when it is known that simulation cannot succeed. This avoids
 * decoding further Graal IR nodes of the class initializer.
 *
 * See {@link SimulateClassInitializerSupport} for an overview of class initializer simulation.
 */
public final class SimulateClassInitializerAbortException extends GraalBailoutException {
    private static final long serialVersionUID = 1L;

    static RuntimeException doAbort(SimulateClassInitializerClusterMember clusterMember, StructuredGraph graph, Object reason) {
        graph.getDebug().dump(DebugContext.BASIC_LEVEL, graph, "SimulateClassInitializer abort: %s", reason);
        clusterMember.notInitializedReasons.add(reason);
        throw new SimulateClassInitializerAbortException();
    }

    private SimulateClassInitializerAbortException() {
        super("");
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        /* Exception is used to abort parsing, stack trace is not necessary. */
        return this;
    }
}
