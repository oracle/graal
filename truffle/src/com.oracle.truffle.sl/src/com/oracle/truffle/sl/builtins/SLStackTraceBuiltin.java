/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.builtins;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.nodes.SLEvalRootNode;
import com.oracle.truffle.sl.nodes.SLRootNode;
import com.oracle.truffle.sl.runtime.SLStrings;

/**
 * Returns a string representation of the current stack. This includes the {@link CallTarget}s and
 * the contents of the {@link Frame}.
 */
@NodeInfo(shortName = "stacktrace")
public abstract class SLStackTraceBuiltin extends SLBuiltinNode {

    public static final TruffleString FRAME = SLStrings.constant("Frame: root ");
    public static final TruffleString SEPARATOR = SLStrings.constant(", ");
    public static final TruffleString EQUALS = SLStrings.constant("=");

    @Specialization
    public TruffleString trace() {
        return createStackTrace();
    }

    @TruffleBoundary
    private static TruffleString createStackTrace() {
        final TruffleStringBuilder str = TruffleStringBuilder.create(SLLanguage.STRING_ENCODING);

        Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Integer>() {
            private int skip = 1; // skip stack trace builtin

            @Override
            public Integer visitFrame(FrameInstance frameInstance) {
                if (skip > 0) {
                    skip--;
                    return null;
                }
                CallTarget callTarget = frameInstance.getCallTarget();
                Frame frame = frameInstance.getFrame(FrameAccess.READ_ONLY);
                RootNode rn = ((RootCallTarget) callTarget).getRootNode();
                // ignore internal or interop stack frames
                if (rn.isInternal() || rn.getLanguageInfo() == null) {
                    return 1;
                }
                if (str.byteLength() > 0) {
                    str.appendStringUncached(SLStrings.fromJavaString(System.getProperty("line.separator")));
                }
                str.appendStringUncached(FRAME);
                str.appendStringUncached(getRootNodeName(rn));
                FrameDescriptor frameDescriptor = frame.getFrameDescriptor();
                int count = frameDescriptor.getNumberOfSlots();
                for (int i = 0; i < count; i++) {
                    str.appendStringUncached(SEPARATOR);
                    str.appendStringUncached((TruffleString) frameDescriptor.getSlotName(i));
                    str.appendStringUncached(EQUALS);
                    str.appendStringUncached(SLStrings.fromObject(frame.getValue(i)));
                }
                return null;
            }
        });
        return str.toStringUncached();
    }

    private static TruffleString getRootNodeName(RootNode rootNode) {
        if (rootNode instanceof SLRootNode) {
            return ((SLRootNode) rootNode).getTSName();
        } else if (rootNode instanceof SLEvalRootNode) {
            return SLEvalRootNode.getTSName();
        } else {
            return SLStrings.fromJavaString(rootNode.getName());
        }
    }
}
