/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ValueProfile;

/**
 * A call node with a dynamic {@link CallTarget} that can be optimized by Graal.
 */
@NodeInfo
public final class OptimizedIndirectCallNode extends IndirectCallNode {

    @CompilationFinal private ValueProfile exceptionProfile;

    /*
     * Should be instantiated with the runtime.
     */
    OptimizedIndirectCallNode() {
    }

    @Override
    public Object call(CallTarget target, Object... arguments) {
        try {
            return ((OptimizedCallTarget) target).callIndirect(this, arguments);
        } catch (Throwable t) {
            if (exceptionProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                exceptionProfile = ValueProfile.createClassProfile();
            }
            Throwable profiledT = exceptionProfile.profile(t);
            GraalRuntimeAccessor.LANGUAGE.onThrowable(this, null, profiledT, null);
            throw OptimizedCallTarget.rethrow(profiledT);
        }
    }

    static IndirectCallNode createUncached() {
        return new IndirectCallNode() {
            @Override
            public boolean isAdoptable() {
                return false;
            }

            @Override
            @TruffleBoundary
            public Object call(CallTarget target, Object... arguments) {
                /*
                 * Clear encapsulating node for uncached indirect call boundary. The encapsulating
                 * node is not longer needed if a call boundary is crossed.
                 */
                EncapsulatingNodeReference encapsulating = EncapsulatingNodeReference.getCurrent();
                Node prev = encapsulating.set(null);
                try {
                    return ((OptimizedCallTarget) target).callIndirect(prev, arguments);
                } finally {
                    encapsulating.set(prev);
                }
            }
        };
    }

}
