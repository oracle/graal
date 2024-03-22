/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.host;

import com.oracle.truffle.compiler.TruffleCompilerRuntime;

import jdk.graal.compiler.truffle.AbstractKnownTruffleTypes;
import jdk.graal.compiler.truffle.KnownTruffleTypes;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Known truffle types only needed for host compilation. For types needed during Truffle guest
 * compilation see {@link KnownTruffleTypes}.
 */
public final class TruffleKnownHostTypes extends AbstractKnownTruffleTypes {

    // Checkstyle: stop field name check

    // truffle.api.frame
    public final ResolvedJavaType FrameDescriptor = lookupTypeCached("com.oracle.truffle.api.frame.FrameDescriptor");

    // truffle.api.impl
    public final ResolvedJavaType FrameWithoutBoxing = lookupType("com.oracle.truffle.api.impl.FrameWithoutBoxing");
    public final ResolvedJavaType OptimizedCallTarget = lookupTypeCached("com.oracle.truffle.runtime.OptimizedCallTarget");
    public final ResolvedJavaMethod OptimizedCallTarget_call = findMethod(OptimizedCallTarget, "call", lookupType(Object[].class));

    // truffle.api
    public final ResolvedJavaType CompilerDirectives = lookupTypeCached("com.oracle.truffle.api.CompilerDirectives");
    public final ResolvedJavaMethod CompilerDirectives_transferToInterpreter = findMethod(CompilerDirectives, "transferToInterpreter");
    public final ResolvedJavaMethod CompilerDirectives_transferToInterpreterAndInvalidate = findMethod(CompilerDirectives, "transferToInterpreterAndInvalidate");
    public final ResolvedJavaMethod CompilerDirectives_inInterpreter = findMethod(CompilerDirectives, "inInterpreter");

    public final ResolvedJavaType HostCompilerDirectives = lookupTypeCached("com.oracle.truffle.api.HostCompilerDirectives");
    public final ResolvedJavaMethod HostCompilerDirectives_inInterpreterFastPath = findMethod(HostCompilerDirectives, "inInterpreterFastPath");

    // Checkstyle: resume field name check

    protected TruffleKnownHostTypes(TruffleCompilerRuntime runtime, MetaAccessProvider metaAccess) {
        super(runtime, metaAccess);
    }

    /**
     * Determines if {@code method} is the inInterpeter method from CompilerDirectives.
     */
    public boolean isInInterpreter(ResolvedJavaMethod method) {
        return method.equals(CompilerDirectives_inInterpreter);
    }

    /**
     * Determines if {@code method} is the inInterpeterFastPath method from HostCompilerDirectives.
     */
    public boolean isInInterpreterFastPath(ResolvedJavaMethod method) {
        return method.equals(HostCompilerDirectives_inInterpreterFastPath);
    }

    /**
     * Determines if {@code method} is a method is a transferToInterpreter method from
     * CompilerDirectives.
     */
    public boolean isTransferToInterpreterMethod(ResolvedJavaMethod method) {
        return method.equals(CompilerDirectives_transferToInterpreter) || method.equals(CompilerDirectives_transferToInterpreterAndInvalidate);
    }

}
