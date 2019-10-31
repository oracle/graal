/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.debug.DebugException.CatchLocation;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.StandardTags.TryBlockTag;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;

final class BreakpointExceptionFilter {

    private Debugger debugger;
    final boolean caught;
    final boolean uncaught;
    private final DebuggerSession.StableBoolean haveReportedExceptions = new DebuggerSession.StableBoolean(false);
    private final Set<Throwable> reportedExceptions = Collections.newSetFromMap(new WeakHashMap<>());
    private final ThreadLocal<Throwable> exceptionsOnThreads = new ThreadLocal<>();

    BreakpointExceptionFilter(boolean caught, boolean uncaught) {
        this.caught = caught;
        this.uncaught = uncaught;
    }

    void setDebugger(Debugger debugger) {
        assert this.debugger == null;
        this.debugger = debugger;
    }

    Match matchException(Node throwNode, Throwable exception) {
        if (wasReported(exception)) {
            return Match.UNMATCHED;
        }
        if (caught && uncaught) {
            return Match.MATCHED;
        } else {
            return testExceptionCaught(throwNode, exception);
        }
    }

    @TruffleBoundary
    private Match testExceptionCaught(Node throwNode, Throwable exception) {
        if (!(exception instanceof TruffleException)) {
            return uncaught ? Match.MATCHED : Match.UNMATCHED;
        }
        CatchLocation catchLocation = getCatchNode(throwNode, exception);
        boolean exceptionCaught = catchLocation != null;
        return new Match(caught && exceptionCaught || uncaught && !exceptionCaught, catchLocation);
    }

    static CatchLocation getCatchNode(Node throwNode, Throwable exception) {
        CatchLocation[] catchLocationPtr = new CatchLocation[]{null};
        Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<FrameInstance>() {
            private int depth = 0;

            @Override
            public FrameInstance visitFrame(FrameInstance frameInstance) {
                Node node;
                if (depth == 0) {
                    node = throwNode;
                } else {
                    node = frameInstance.getCallNode();
                }
                if (node != null) {
                    Node catchNode = getCatchNodeImpl(node, exception);
                    if (catchNode != null) {
                        catchLocationPtr[0] = new CatchLocation(catchNode.getSourceSection(), frameInstance, depth);
                        return frameInstance;
                    }
                }
                depth++;
                return null;
            }
        });
        return catchLocationPtr[0];
    }

    private static Node getCatchNodeImpl(Node node, Throwable exception) {
        if (node instanceof InstrumentableNode) {
            InstrumentableNode inode = (InstrumentableNode) node;
            if (inode.isInstrumentable() && inode.hasTag(TryBlockTag.class)) {
                Object exceptionObject = ((TruffleException) exception).getExceptionObject();
                Object nodeObject = inode.getNodeObject();
                if (nodeObject != null && exceptionObject != null) {
                    InteropLibrary library = InteropLibrary.getFactory().getUncached(nodeObject);
                    TruffleObject object = (TruffleObject) nodeObject;
                    if (library.isMemberInvocable(nodeObject, "catches")) {
                        Object catches;
                        try {
                            catches = library.invokeMember(nodeObject, "catches", exceptionObject);
                        } catch (UnsupportedTypeException | ArityException | UnknownIdentifierException | UnsupportedMessageException ex) {
                            throw new IllegalStateException("Unexpected exception from 'catches' on '" + object, exception);
                        }
                        if (!(catches instanceof Boolean)) {
                            throw new IllegalStateException("Unexpected return value from 'catches' on '" + object + "' : " + catches);
                        }
                        if (Boolean.TRUE.equals(catches)) {
                            return node;
                        }
                    } else {
                        return node;
                    }
                } else {
                    return node;
                }
            }
        }
        Node parent = node.getParent();
        if (parent != null) {
            return getCatchNodeImpl(parent, exception);
        }
        return null;
    }

    @TruffleBoundary
    private boolean wasReported(Throwable exception) {
        synchronized (this) {
            boolean reported = reportedExceptions.contains(exception);
            if (!reported) {
                reportedExceptions.add(exception);
            }
            return reported;
        }
    }

    void resetReportedException() {
        if (haveReportedExceptions.get()) {
            doResetReportedException();
        }
    }

    @TruffleBoundary
    private void doResetReportedException() {
        Throwable exception = exceptionsOnThreads.get();
        synchronized (this) {
            if (exception != null) {
                exceptionsOnThreads.remove();
                reportedExceptions.remove(exception);
            }
            if (reportedExceptions.isEmpty()) {
                haveReportedExceptions.set(false);
            }
        }
    }

    static final class Match {

        static final Match MATCHED = new Match(true);
        static final Match UNMATCHED = new Match(false);

        final boolean isMatched;
        final boolean isCatchNodeComputed;
        final CatchLocation catchLocation;

        private Match(boolean isMatched) {
            this.isMatched = isMatched;
            this.isCatchNodeComputed = false;
            this.catchLocation = null;
        }

        private Match(boolean isMatched, CatchLocation catchLocation) {
            this.isMatched = isMatched;
            this.isCatchNodeComputed = true;
            this.catchLocation = catchLocation;
        }

    }
}
