/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.st;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Node that "wraps" AST nodes of interest (Nodes that correspond to expressions in our case as
 * defined by the filter given to the {@link Instrumenter} in
 * {@link SimpleCoverageInstrument#onCreate(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env) }
 * ), and informs the {@link SimpleCoverageInstrument} that we
 * {@link SimpleCoverageInstrument#addCovered(SourceSection) covered} it's
 * {@link #instrumentedSourceSection source section}.
 */
final class CoverageNode extends ExecutionEventNode {

    private final SimpleCoverageInstrument instrument;
    @CompilerDirectives.CompilationFinal private boolean covered;

    /**
     * Each node knows which {@link SourceSection} it instruments.
     */
    private final SourceSection instrumentedSourceSection;

    CoverageNode(SimpleCoverageInstrument instrument, SourceSection instrumentedSourceSection) {
        this.instrument = instrument;
        this.instrumentedSourceSection = instrumentedSourceSection;
    }

    /**
     * The {@link ExecutionEventNode} class let's us define several events that we can intercept.
     * The one of interest to us is
     * {@link ExecutionEventNode#onReturnValue(com.oracle.truffle.api.frame.VirtualFrame, Object) }
     * as we wish to track this nodes {@link #instrumentedSourceSection} in the
     * {@link SimpleCoverageInstrument#coverageMap} only once the node is successfully executed (as
     * oppose to, for example,
     * {@link ExecutionEventNode#onReturnExceptional(com.oracle.truffle.api.frame.VirtualFrame, Throwable) }
     * ).
     *
     * Each node keeps a {@link #covered} flag so that the removal only happens once. The fact that
     * the flag is annotated with {@link CompilerDirectives.CompilationFinal} means that this flag
     * will be treated as {@code final} during compilation of instrumented source code (i.e. the
     * {@code false} branch of the if statement can be optimized away).
     *
     * The way it's used in this method is a pattern when writing Truffle nodes:
     * <ul>
     * <li>If we are compiling a covered node, the if condition will evaluate to false and the
     * if-guarded code will be optimized away. This means that once this {@link SourceSection} is
     * confirmed to be covered, there is no further instrumentation overhead on performance.
     * <li>If we are compiling a not-yet-covered node, the if condition will evaluate to true, and
     * the if-guarded code will be included for compilation. The first statement in this block is a
     * {@link CompilerDirectives#transferToInterpreterAndInvalidate() directive to the compiler} to
     * make sure that if this point in the execution is reached, the execution should return to the
     * interpreter and the existing compiled code is no longer valid (since once the covered flag is
     * set to true, the check is unnecessary). The code following the directive is thus always
     * executed in the interpreter: We set the {@link #covered} flag to true, ensuring that the next
     * compilation will have no instrumentation overhead on performance.</li>
     * </ul>
     *
     * @param vFrame unused
     * @param result unused
     */
    @Override
    public void onReturnValue(VirtualFrame vFrame, Object result) {
        if (!covered) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            covered = true;
            instrument.addCovered(instrumentedSourceSection);
        }
    }

}
