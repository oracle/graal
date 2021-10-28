/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug.test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

/**
 * A test language that does not have call nodes.
 */
public class NoCallNodeTestLanguage extends ProxyLanguage {

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        Source source = request.getSource();
        NCRootNode root = new NCRootNode(source.createSection(0, source.getLength()));
        return root.getCallTarget();
    }

    private static final class NCRootNode extends RootNode {

        @Child NCStatement statement;

        NCRootNode(SourceSection sourceSection) {
            super(ProxyLanguage.get(null));
            this.statement = new NCStatement(sourceSection);
        }

        @Override
        public String getName() {
            return statement.getSourceSection().getCharacters().toString();
        }

        @Override
        public SourceSection getSourceSection() {
            return statement.getSourceSection();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return statement.executeStatement(frame);
        }
    }

    @GenerateWrapper
    static class NCStatement extends Node implements InstrumentableNode {

        private final SourceSection sourceSection;
        private final CallTarget childTarget;

        NCStatement(SourceSection sourceSection) {
            this.sourceSection = sourceSection;
            int sectionLength = sourceSection.getCharLength();
            if (sectionLength > 1) {
                Source source = sourceSection.getSource();
                SourceSection childSection = source.createSection(sourceSection.getCharIndex() + 1, sectionLength - 1);
                childTarget = new NCRootNode(childSection).getCallTarget();
            } else {
                childTarget = null;
            }
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

        @SuppressWarnings("unused")
        Object executeStatement(VirtualFrame frame) {
            if (childTarget != null) {
                return childTarget.call();
            } else {
                return 1;
            }
        }

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public WrapperNode createWrapper(ProbeNode probe) {
            return new NCStatementWrapper(sourceSection, this, probe);
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return DebuggerTags.AlwaysHalt.class == tag ||
                            StandardTags.StatementTag.class == tag;
        }
    }

}
