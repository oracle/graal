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

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.oracle.svm.configure.UnresolvedAccessCondition;
import com.oracle.svm.configure.config.ForeignConfiguration.ConfigurationFunctionDescriptor;
import com.oracle.svm.configure.config.ForeignConfiguration.StubDesc;
import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.ArenaIntrinsics;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.foreign.ForeignFunctionsRuntime.LinkRequest;
import com.oracle.svm.core.nodes.foreign.ScopedMemExceptionHandlerClusterNode.ClusterBeginNode;
import com.oracle.svm.core.nodes.foreign.ScopedMemExceptionHandlerClusterNode.ExceptionInputNode;
import com.oracle.svm.core.nodes.foreign.ScopedMemExceptionHandlerClusterNode.ExceptionPathNode;
import com.oracle.svm.core.nodes.foreign.ScopedMemExceptionHandlerClusterNode.RegularPathNode;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.util.json.JsonPrintable;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.layout.AbstractLayout;

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
        GraalDirectives.controlFlowAnchor(scope);
    }

    /**
     * Generates a JSON configuration entry that will specify the stub required by the given link
     * request. This method is meant to be used to generate a useful error message for the user if a
     * stub lookup failed.
     */
    static JsonPrintable linkRequestToJsonPrintable(LinkRequest linkRequest) {
        List<String> parameterTypes = new LinkedList<>();
        for (MemoryLayout argumentLayout : linkRequest.functionDescriptor().argumentLayouts()) {
            parameterTypes.add(memoryLayoutToString(argumentLayout));
        }
        Optional<MemoryLayout> memoryLayout = linkRequest.functionDescriptor().returnLayout();
        String returnLayout = memoryLayout.map(SubstrateForeignUtil::memoryLayoutToString).orElse("void");
        ConfigurationFunctionDescriptor desc = new ConfigurationFunctionDescriptor(returnLayout, parameterTypes);

        Map<String, Object> jsonOptions = Map.of();
        if (!LinkerOptions.empty().equals(linkRequest.linkerOptions())) {
            jsonOptions = new HashMap<>();
            if (linkRequest.linkerOptions().isCritical()) {
                jsonOptions.put("critical", Map.of("allowHeapAccess", linkRequest.linkerOptions().allowsHeapAccess()));
            }
            if (linkRequest.linkerOptions().isVariadicFunction()) {
                jsonOptions.put("firstVariadicArg", linkRequest.linkerOptions().firstVariadicArgIndex());
            }
            if (linkRequest.linkerOptions().hasCapturedCallState()) {
                jsonOptions.put("captureCallState", true);
            }
        }

        return new StubDesc(UnresolvedAccessCondition.unconditional(), desc, jsonOptions);
    }

    /**
     * Generates a memory layout description as defined in {@code FFM-API.md} to be used in the
     * configuration file (usually {@code reachability-metadata.json}). This method implements the
     * reverse operation of {@code com.oracle.svm.hosted.foreign.MemoryLayoutParser}.
     */
    private static String memoryLayoutToString(MemoryLayout memoryLayout) {
        String layoutString = switch (memoryLayout) {
            case ValueLayout valueLayout -> {
                Class<?> carrier = valueLayout.carrier();
                if (carrier == MemorySegment.class) {
                    yield "void*";
                }
                yield "j" + carrier.getName();
            }
            case GroupLayout structLayout -> {
                String[] memberStrings = new String[structLayout.memberLayouts().size()];
                int i = 0;
                for (MemoryLayout memberLayout : structLayout.memberLayouts()) {
                    memberStrings[i++] = memoryLayoutToString(memberLayout);
                }
                String prefix = structLayout instanceof StructLayout ? "struct(" : "union(";
                yield prefix + String.join(",", memberStrings) + ")";
            }
            case SequenceLayout sequenceLayout -> "sequence(" + sequenceLayout.elementCount() + ", " + memoryLayoutToString(sequenceLayout.elementLayout()) + ")";
            case PaddingLayout paddingLayout -> "padding(" + paddingLayout.byteSize() + ")";
        };
        if (memoryLayout instanceof AbstractLayout<?> abstractLayout && !abstractLayout.hasNaturalAlignment()) {
            return "align(" + memoryLayout.byteAlignment() + ", " + layoutString + ")";
        }
        return layoutString;
    }
}
