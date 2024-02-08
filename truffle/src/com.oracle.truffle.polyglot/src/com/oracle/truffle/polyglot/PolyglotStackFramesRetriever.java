/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.RootNode;

final class PolyglotStackFramesRetriever {

    static void populateHeapRoots(PolyglotContextImpl context, List<Object> heapRoots) {
        Future<Void> future;
        synchronized (context) {
            if (!context.state.isClosed()) {
                future = context.threadLocalActions.submit(null, PolyglotEngineImpl.ENGINE_ID, new ThreadLocalAction(false, false) {
                    @Override
                    protected void perform(Access access) {
                        Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<>() {
                            @Override
                            public Object visitFrame(FrameInstance frameInstance) {
                                populateHeapRootsFromFrame(context, frameInstance, heapRoots);
                                return null;
                            }
                        });
                    }
                }, false);
            } else {
                future = CompletableFuture.completedFuture(null);
            }
        }

        TruffleSafepoint.setBlockedThreadInterruptible(context.uncachedLocation, new TruffleSafepoint.Interruptible<Future<Void>>() {
            @Override
            public void apply(Future<Void> arg) throws InterruptedException {
                if (!context.state.isClosed()) {
                    try {
                        arg.get();
                    } catch (ExecutionException e) {
                        throw CompilerDirectives.shouldNotReachHere(e);
                    }
                }
            }
        }, future);
    }

    private static void addRootPointerForGuestToHostStackFrameArgument(PolyglotContextImpl context, Object obj, List<Object> heapRoots) {
        if (InteropLibrary.isValidValue(obj)) {
            heapRoots.add(obj);
        } else if (obj instanceof PolyglotWrapper) {
            heapRoots.add(((PolyglotWrapper) obj).getGuestObject());
        } else if (context.getAPIAccess().isValue(obj)) {
            heapRoots.add(context.getAPIAccess().getValueReceiver(obj));
        }
    }

    static void populateHeapRootsFromFrame(PolyglotContextImpl context, FrameInstance frameInstance, List<Object> heapRoots) {
        Frame frame = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
        RootNode rootNode = ((RootCallTarget) frameInstance.getCallTarget()).getRootNode();
        if (rootNode instanceof HostToGuestRootNode) {
            /*
             * HostToGuestRootNode frames are ignored. We don't care about objects referenced only
             * form the host side.
             */
        } else if (EngineAccessor.HOST.isGuestToHostRootNode(rootNode)) {
            /*
             * For GuestToHostRootNode frames, we are interested only in arguments, and only those
             * arguments which wrap guest objects or those that are valid interop values.
             */
            for (Object obj : frame.getArguments()) {
                /*
                 * Argument array of the called host method is an element in the frame's argument
                 * array.
                 */
                if (obj instanceof Object[]) {
                    for (Object elem : ((Object[]) obj)) {
                        addRootPointerForGuestToHostStackFrameArgument(context, elem, heapRoots);
                    }
                } else {
                    addRootPointerForGuestToHostStackFrameArgument(context, obj, heapRoots);
                }
            }
        } else {
            /*
             * All types in the frame are safe to traverse.
             */
            heapRoots.add(frame);
        }
    }
}
