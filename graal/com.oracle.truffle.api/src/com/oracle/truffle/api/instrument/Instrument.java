/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrument;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.impl.*;
import com.oracle.truffle.api.nodes.*;

/**
 * A receiver of Truffle AST {@link ExecutionEvents}, propagated from a {@link Probe} to which the
 * instrument is attached, for the benefit of associated <em>tools</em>.
 * <p>
 * Guidelines for implementing Instruments, with particular attention to avoiding undesired runtime
 * performance overhead:
 * <ol>
 * <li>Extend {@link Instrument} and override only the event handling methods for which some action
 * is needed.</li>
 * <li>Instruments are Truffle {@link Node}s and should be coded as much as possible in the desired
 * <em>Truffle style</em>, documented more thoroughly elsewhere.</li>
 * <li>Maintain as little state as possible.</li>
 * <li>If state is necessary, make it {@code final} if possible.</li>
 * <li>If non-final state is necessary, annotate it as {@link CompilationFinal} and call
 * {@linkplain InstrumentationNode#notifyProbeChanged(Instrument)} whenever it is modified.</li>
 * <li>Never store a {@link Frame} value in a field.</li>
 * <li>Minimize computation in standard execution paths.</li>
 * <li>Callbacks to tools should be made via callbacks provided at construction and stored in
 * {@code final} fields.</li>
 * <li>Tool callback methods should usually be annotated as {@link SlowPath} to prevent them from
 * being inlined into fast execution paths.</li>
 * <li>If computation is needed, and if performance is important, then the computation is best
 * expressed as a guest language AST and evaluated using standard Truffle mechanisms so that
 * standard Truffle optimizations can be applied.</li>
 * </ol>
 * <p>
 * Guidelines for attachment to a {@link Probe}:
 * <ol>
 * <li>An Instrument instance must only attached to a single {@link Probe}, each of which is
 * associated uniquely with a specific syntactic unit of a guest language program, and thus
 * (initially) to a specific {@linkplain Node Truffle AST node}.</li>
 * <li>When the AST containing such a node is copied at runtime, the {@link Probe} will be shared by
 * every copy, and so the Instrument will receive events corresponding to the intended syntactic
 * unit of code, independent of which AST copy is being executed.</li>
 * </ol>
 *
 * <p>
 * <strong>Disclaimer:</strong> experimental; under development.
 *
 * @see Probe
 * @see ASTNodeProber
 */
public class Instrument extends InstrumentationNode {

    protected Instrument() {
    }

    public void enter(Node astNode, VirtualFrame frame) {
    }

    public void leave(Node astNode, VirtualFrame frame) {
    }

    public void leave(Node astNode, VirtualFrame frame, boolean result) {
        leave(astNode, frame, (Object) result);
    }

    public void leave(Node astNode, VirtualFrame frame, byte result) {
        leave(astNode, frame, (Object) result);
    }

    public void leave(Node astNode, VirtualFrame frame, short result) {
        leave(astNode, frame, (Object) result);
    }

    public void leave(Node astNode, VirtualFrame frame, int result) {
        leave(astNode, frame, (Object) result);
    }

    public void leave(Node astNode, VirtualFrame frame, long result) {
        leave(astNode, frame, (Object) result);
    }

    public void leave(Node astNode, VirtualFrame frame, char result) {
        leave(astNode, frame, (Object) result);
    }

    public void leave(Node astNode, VirtualFrame frame, float result) {
        leave(astNode, frame, (Object) result);
    }

    public void leave(Node astNode, VirtualFrame frame, double result) {
        leave(astNode, frame, (Object) result);
    }

    public void leave(Node astNode, VirtualFrame frame, Object result) {
    }

    public void leaveExceptional(Node astNode, VirtualFrame frame, Exception e) {
    }

}
