/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.foreign;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.ArenaIntrinsics;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.nodes.foreign.ScopedMemExceptionHandlerClusterNode.ClusterBeginNode;
import com.oracle.svm.core.nodes.foreign.ScopedMemExceptionHandlerClusterNode.ExceptionInputNode;
import com.oracle.svm.core.nodes.foreign.ScopedMemExceptionHandlerClusterNode.ExceptionPathNode;
import com.oracle.svm.core.nodes.foreign.ScopedMemExceptionHandlerClusterNode.RegularPathNode;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.internal.foreign.MemorySessionImpl;

/**
 * For details on the implementation of shared arenas on substrate see
 * {@link Target_jdk_internal_misc_ScopedMemoryAccess}.
 *
 * Note that this code should only be called by substitutions in
 * {@link Target_jdk_internal_misc_ScopedMemoryAccess}.
 */
public class SubstrateForeignUtil {
    @AlwaysInline("Compiler expects the code shape")
    public static void logExceptionSeen(Throwable e) {
        if (SubstrateOptions.printClosedArenaUponThrow()) {
            try {
                LogUtils.info("Saw an exception in the cluster portion of the graph, message was=" + e.getMessage() + "\n");
            } catch (Throwable t) {
                // swallow nothing to do
            }
        }
    }

    @AlwaysInline("Compiler expects the code shape")
    public static <T> void checkIdentity(T actual, T expected) {
        if (actual != expected) {
            throw new IllegalArgumentException("Unexpected object");
        }
    }

    @AlwaysInline("factored out only for readability")
    public static void checkSession(MemorySessionImpl session) {
        if (session != null) {
            session.checkValidStateRaw();
        }
    }

    /**
     * A decorator method for {@link MemorySessionImpl#checkValidStateRaw()} which will fail if a
     * shared session is passed. We use this method instead of the decorated one in runtime
     * compiled @Scoped-annotated methods (and their deopt targets) to fail if shared sessions are
     * used in runtime compiled methods (GR-66841).
     */
    @AlwaysInline("factored out only for readability")
    public static void checkValidStateRawInRuntimeCompiledCode(MemorySessionImpl session) {
        /*
         * A closeable session without an owner thread is a shared session (i.e. it can be closed
         * concurrently).
         */
        if (session.isCloseable() && session.ownerThread() == null) {
            throw VMError.unsupportedFeature("Invalid session object. Using a shared session in runtime compiled methods is not supported.");
        }
        session.checkValidStateRaw();
    }

    /**
     * Handles exceptions related to memory sessions within a specific arena scope.
     *
     * This method checks if the {@link java.lang.foreign.Arena} associated with {@code session} is
     * in a valid state. If validation fails, it logs the exception and propagates it through the
     * exception path.
     *
     * @param session the memory session to handle exceptions for
     * @param base the base object associated with the arena
     * @param offset the offset within the arena
     */
    @AlwaysInline("factored out only for readability")
    public static void sessionExceptionHandler(MemorySessionImpl session, Object base, long offset) {
        long scope = ArenaIntrinsics.checkArenaValidInScope(session, base, offset);
        if (scope != 0) {
            ClusterBeginNode.beginExceptionCluster(scope);
            MemorySessionImpl clusterSession = ExceptionInputNode.clusterInputValue(scope, session);
            if (clusterSession != null) {
                try {
                    clusterSession.checkValidStateRaw();
                } catch (Throwable e) {
                    SubstrateForeignUtil.logExceptionSeen(e);
                    throw ExceptionPathNode.endClusterExceptionPath(e, scope);
                }
            }
            RegularPathNode.endClusterNormalPath(scope);
        }
        // avoid any optimization based code duplication in the cluster, it disrupts later matching
        // of control flow when searching for this pattern
        GraalDirectives.controlFlowAnchor();
    }
}
