/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.sl.nodes.SLRootNode;

/**
 * SL does not need a sophisticated error checking and reporting mechanism, so all unexpected
 * conditions just abort execution. This exception class is used when we abort from within the SL
 * implementation.
 */
public class SLException extends RuntimeException {

    private static final long serialVersionUID = -6799734410727348507L;

    public SLException(String message) {
        super(message);
        CompilerAsserts.neverPartOfCompilation();
        initCause(new Throwable("Java stack trace"));
    }

    @Override
    @SuppressWarnings("sync-override")
    public Throwable fillInStackTrace() {
        CompilerAsserts.neverPartOfCompilation();
        return fillInSLStackTrace(this);
    }

    /**
     * Uses the Truffle API to iterate the stack frames and to create and set Java
     * {@link StackTraceElement} elements based on the source sections of the call nodes on the
     * stack.
     */
    public static Throwable fillInSLStackTrace(Throwable t) {
        final List<StackTraceElement> stackTrace = new ArrayList<>();
        Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Void>() {
            public Void visitFrame(FrameInstance frame) {
                Node callNode = frame.getCallNode();
                if (callNode == null) {
                    return null;
                }
                RootNode root = callNode.getRootNode();

                /*
                 * There should be no RootNodes other than SLRootNodes on the stack. Just for the
                 * case if this would change.
                 */
                String methodName = "$unknownFunction";
                if (root instanceof SLRootNode) {
                    methodName = ((SLRootNode) root).getName();
                }

                SourceSection sourceSection = callNode.getEncapsulatingSourceSection();
                Source source = sourceSection != null ? sourceSection.getSource() : null;
                String sourceName = source != null ? source.getName() : null;
                int lineNumber;
                try {
                    lineNumber = sourceSection != null ? sourceSection.getStartLine() : -1;
                } catch (UnsupportedOperationException e) {
                    /*
                     * SourceSection#getLineLocation() may throw an UnsupportedOperationException.
                     */
                    lineNumber = -1;
                }
                stackTrace.add(new StackTraceElement("SL", methodName, sourceName, lineNumber));
                return null;
            }
        });
        t.setStackTrace(stackTrace.toArray(new StackTraceElement[stackTrace.size()]));
        return t;
    }
}
