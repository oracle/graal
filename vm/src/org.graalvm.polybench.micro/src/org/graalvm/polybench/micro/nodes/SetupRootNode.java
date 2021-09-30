/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polybench.micro.nodes;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.polybench.micro.EvalError;
import org.graalvm.polybench.micro.Microbench;
import org.graalvm.polybench.micro.MicrobenchLanguage;
import org.graalvm.polybench.micro.Runner;
import org.graalvm.polybench.micro.expr.Expression;

public abstract class SetupRootNode extends RootNode {

    final CallTarget workload;
    final Microbench spec;

    @Children Expression[] prepare;

    public SetupRootNode(MicrobenchLanguage language, Microbench spec, Expression[] prepare) {
        super(language);
        MicrobenchRootNode workloadRoot = new MicrobenchRootNode(language, spec);

        this.workload = workloadRoot.getCallTarget();
        this.prepare = prepare;
        this.spec = spec;
    }

    @ExplodeLoop
    private Object[] prepareValues(VirtualFrame frame) {
        Object[] ret = new Object[prepare.length];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = prepare[i].execute(frame);
        }
        return ret;
    }

    @Specialization
    public Object doPrepare(VirtualFrame frame,
                    @Bind("getPolyglotBindings()") Object bindings,
                    @CachedLibrary("bindings") InteropLibrary interop) {
        Object[] prepared = prepareValues(frame);
        Runner runner = new Runner(spec, workload, prepared);
        try {
            interop.writeMember(bindings, "run", runner);
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new EvalError(this, "error writing to bindings");
        }
        return runner;
    }

    Object getPolyglotBindings() {
        return MicrobenchLanguage.getContext(this).getPolyglotBindings();
    }
}
