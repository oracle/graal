/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.debug.DebugException.CatchLocation;
import com.oracle.truffle.api.debug.ObjectStructures.MessageNodes;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.StandardTags.TryBlockTag;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
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
        CatchLocation catchLocation = getCatchNode(debugger, throwNode, exception);
        boolean exceptionCaught = catchLocation != null;
        return new Match(caught && exceptionCaught || uncaught && !exceptionCaught, catchLocation);
    }

    static CatchLocation getCatchNode(Debugger debugger, Node throwNode, Throwable exception) {
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
                    Node catchNode = getCatchNode(debugger.getMessageNodes(), node, exception);
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

    private static Node getCatchNode(MessageNodes msgNodes, Node node, Throwable exception) {
        if (node instanceof InstrumentableNode) {
            InstrumentableNode inode = (InstrumentableNode) node;
            if (inode.isInstrumentable() && inode.hasTag(TryBlockTag.class)) {
                Object exceptionObject = ((TruffleException) exception).getExceptionObject();
                Object nodeObject = inode.getNodeObject();
                if (nodeObject instanceof TruffleObject && exceptionObject != null) {
                    TruffleObject object = (TruffleObject) nodeObject;
                    int keyInfo = ForeignAccess.sendKeyInfo(msgNodes.keyInfo, object, "catches");
                    if (KeyInfo.isInvocable(keyInfo)) {
                        Object catches;
                        try {
                            catches = ForeignAccess.sendInvoke(msgNodes.invoke1, object, "catches", exceptionObject);
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
            return getCatchNode(msgNodes, parent, exception);
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
