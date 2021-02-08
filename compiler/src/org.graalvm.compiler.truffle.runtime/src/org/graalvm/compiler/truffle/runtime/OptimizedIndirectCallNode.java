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
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ValueProfile;

/**
 * A call node with a dynamic {@link CallTarget} that can be optimized by Graal.
 */
@NodeInfo
public final class OptimizedIndirectCallNode extends IndirectCallNode {

    @CompilationFinal private ValueProfile exceptionProfile;
    @CompilationFinal private boolean seenInInterpreter;

    /*
     * Should be instantiated with the runtime.
     */
    OptimizedIndirectCallNode() {
    }

    @Override
    public Object call(CallTarget target, Object... arguments) {
        try {
            OptimizedCallTarget optimizedTarget = ((OptimizedCallTarget) target);

            if (CompilerDirectives.inInterpreter() && !seenInInterpreter) {
                /*
                 * No need to deoptimize to modify compilation final state as we only execute this
                 * in the interpreter.
                 */
                this.seenInInterpreter = true;
            }

            /*
             * Indirect calls should not cause invalidations if they were compiled prior to
             * execution. We rather produce a truffle boundary call to the interpreter profile and
             * escape the arguments.
             */
            if (this.seenInInterpreter) {
                optimizedTarget.stopProfilingArguments();
            } else {
                profileIndirectArguments(optimizedTarget, arguments);
            }
            return optimizedTarget.callIndirect(this, arguments);
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

    @TruffleBoundary
    private static void profileIndirectArguments(OptimizedCallTarget optimizedTarget, Object... arguments) {
        optimizedTarget.profileArguments(arguments);
    }

}
