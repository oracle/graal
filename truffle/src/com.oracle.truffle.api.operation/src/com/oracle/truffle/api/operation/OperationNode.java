/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.operation;

import java.util.function.Function;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public abstract class OperationNode extends Node {

    protected final OperationNodes nodes;

    private static final int SOURCE_INFO_BCI_INDEX = 0;
    private static final int SOURCE_INFO_START = 1;
    private static final int SOURCE_INFO_LENGTH = 2;
    private static final int SOURCE_INFO_STRIDE = 3;

    protected OperationNode(OperationNodes nodes) {
        this.nodes = nodes;
    }

    protected abstract int[] getSourceInfo();

    public abstract FrameDescriptor createFrameDescriptor();

    public abstract Object execute(VirtualFrame frame);

    public final OperationNodes getOperationNodes() {
        return nodes;
    }

    public final <T> T getMetadata(MetadataKey<T> key) {
        return key.getValue(this);
    }

    protected static <T> void setMetadataAccessor(MetadataKey<T> key, Function<OperationNode, T> getter) {
        key.setGetter(getter);
    }

    // ------------------------------------ sources ------------------------------------

    @Override
    public final SourceSection getSourceSection() {
        int[] sourceInfo = getSourceInfo();
        Source[] sources = nodes.sources;

        if (sourceInfo == null || sources == null) {
            return null;
        }

        for (int i = 0; i < sourceInfo.length; i += SOURCE_INFO_STRIDE) {
            if (sourceInfo[i + SOURCE_INFO_START] >= 0) {
                // return the first defined source section - that one should encompass the entire
                // function
                return sources[sourceInfo[i + SOURCE_INFO_BCI_INDEX] >> 16].createSection(sourceInfo[i + SOURCE_INFO_START], sourceInfo[i + SOURCE_INFO_LENGTH]);
            }
        }

        return null;
    }

    @ExplodeLoop
    protected final SourceSection getSourceSectionAtBci(int bci) {
        int[] sourceInfo = getSourceInfo();

        if (sourceInfo == null) {
            return null;
        }

        int i;
        // find the index of the first greater BCI
        for (i = 0; i < sourceInfo.length; i += SOURCE_INFO_STRIDE) {
            if ((sourceInfo[i + SOURCE_INFO_BCI_INDEX] & 0xffff) > bci) {
                break;
            }
        }

        if (i == 0) {
            return null;
        } else {
            i -= SOURCE_INFO_STRIDE;
            int sourceIndex = sourceInfo[i + SOURCE_INFO_BCI_INDEX] >> 16;
            if (sourceIndex < 0) {
                return null;
            }

            int sourceStart = sourceInfo[i + SOURCE_INFO_START];
            int sourceLength = sourceInfo[i + SOURCE_INFO_LENGTH];
            if (sourceStart < 0) {
                return null;
            }
            return nodes.sources[sourceIndex].createSection(sourceStart, sourceLength);
        }
    }

    public final Node createLocationNode(final int bci) {
        return new Node() {
            @Override
            public SourceSection getSourceSection() {
                return getSourceSectionAtBci(bci);
            }

            @Override
            public SourceSection getEncapsulatingSourceSection() {
                return getSourceSectionAtBci(bci);
            }
        };
    }

    public abstract String dump();
}
