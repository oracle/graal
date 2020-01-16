/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import java.io.PrintStream;

import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Debugger abstraction of EventContext.
 */
interface SuspendedContext {

    static SuspendedContext create(EventContext eventContext, TruffleInstrument.Env env) {
        return new SuspendedEventContext(eventContext, env);
    }

    static SuspendedContext create(Node node, ThreadDeath unwind) {
        return new CallerEventContext(node, unwind);
    }

    /**
     * Stack depth of this context.
     */
    int getStackDepth();

    SourceSection getInstrumentedSourceSection();

    Node getInstrumentedNode();

    boolean hasTag(Class<? extends Tag> tag);

    boolean isLanguageContextInitialized();

    ThreadDeath createUnwind(Object info, EventBinding<?> unwindBinding);

    final class SuspendedEventContext implements SuspendedContext {

        private final EventContext eventContext;
        private final TruffleInstrument.Env env;

        private SuspendedEventContext(EventContext eventContext, TruffleInstrument.Env env) {
            this.eventContext = eventContext;
            this.env = env;
        }

        @Override
        public int getStackDepth() {
            return 0;
        }

        public SourceSection getInstrumentedSourceSection() {
            SourceSection ss = eventContext.getInstrumentedSourceSection();
            if (ss == null) {
                Node node = eventContext.getInstrumentedNode();
                // Nodes tagged with standard tags should have a source section attached.
                PrintStream err = new PrintStream(env.err());
                err.print("WARNING: Instrumented node " + node + " of class " + node.getClass() + " has null SourceSection.");
                ss = DebugSourcesResolver.findEncapsulatedSourceSection(node);
                if (ss == null) {
                    RootNode root = node.getRootNode();
                    err.print("WARNING: and null encapsulating SourceSection under " + root + " of class = " + root.getClass());
                }
                err.flush();
            }
            return ss;
        }

        public Node getInstrumentedNode() {
            return eventContext.getInstrumentedNode();
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return eventContext.hasTag(tag);
        }

        public boolean isLanguageContextInitialized() {
            return eventContext.isLanguageContextInitialized();
        }

        public ThreadDeath createUnwind(Object info, EventBinding<?> unwindBinding) {
            return eventContext.createUnwind(info, unwindBinding);
        }

        @Override
        public String toString() {
            return eventContext.toString();
        }

    }

    final class CallerEventContext implements SuspendedContext {

        private final Node node;
        private final ThreadDeath unwind;

        private CallerEventContext(Node node, ThreadDeath unwind) {
            this.node = node;
            this.unwind = unwind;
        }

        @Override
        public int getStackDepth() {
            return 1;
        }

        @Override
        public SourceSection getInstrumentedSourceSection() {
            if (node == null) {
                return null;
            } else {
                return DebugSourcesResolver.findEncapsulatedSourceSection(node);
            }
        }

        @Override
        public Node getInstrumentedNode() {
            return node;
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return ((node instanceof InstrumentableNode) && ((InstrumentableNode) node).hasTag(tag));
        }

        @Override
        public boolean isLanguageContextInitialized() {
            return true;
        }

        @Override
        public ThreadDeath createUnwind(Object info, EventBinding<?> unwindBinding) {
            return unwind;
        }

        @Override
        public String toString() {
            return "CallerContext[source=" + getInstrumentedSourceSection() + "]";
        }
    }
}
