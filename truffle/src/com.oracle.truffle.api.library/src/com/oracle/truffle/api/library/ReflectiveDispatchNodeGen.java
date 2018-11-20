/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
// CheckStyle: start generated
package com.oracle.truffle.api.library;

import java.util.concurrent.locks.Lock;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;

@GeneratedBy(ReflectiveDispatchNode.class)
final class ReflectiveDispatchNodeGen extends ReflectiveDispatchNode {

    @CompilationFinal private int state_;
    @CompilationFinal private int exclude_;
    @Child private SendCachedData sendCached_cache;

    private ReflectiveDispatchNodeGen() {
    }

    @ExplodeLoop(kind = LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    @Override
    Object executeSend(Object arg0Value, Message arg1Value, Object[] arg2Value, int parameterOffset) throws Exception {
        int state = state_;
        if (state != 0 /*
                        * is-active doSendCached(Object, Message, Object[], int, ResolvedLibrary<>,
                        * Library) || doSendGeneric(Object, Message, Object[], int)
                        */) {
            if ((state & 0b1) != 0 /*
                                    * is-active doSendCached(Object, Message, Object[], int,
                                    * ResolvedLibrary<>, Library)
                                    */) {
                SendCachedData s1_ = this.sendCached_cache;
                while (s1_ != null) {
                    if ((s1_.cachedReflection_ == arg1Value.library) && (s1_.cachedLibrary_.accepts(arg0Value))) {
                        return doSendCached(arg0Value, arg1Value, arg2Value, parameterOffset, s1_.cachedReflection_, s1_.cachedLibrary_);
                    }
                    s1_ = s1_.next_;
                }
            }
            if ((state & 0b10) != 0 /* is-active doSendGeneric(Object, Message, Object[], int) */) {
                return doSendGeneric(arg0Value, arg1Value, arg2Value, parameterOffset);
            }
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        return executeAndSpecialize(arg0Value, arg1Value, arg2Value, parameterOffset);
    }

    private Object executeAndSpecialize(Object arg0Value, Message arg1Value, Object[] arg2Value, int parameterOffset) throws Exception {
        Lock lock = getLock();
        boolean hasLock = true;
        lock.lock();
        int state = state_;
        int exclude = exclude_;
        try {
            if ((exclude) == 0 /*
                                * is-not-excluded doSendCached(Object, Message, Object[], int,
                                * ResolvedLibrary<>, Library)
                                */) {
                int count1_ = 0;
                SendCachedData s1_ = this.sendCached_cache;
                if ((state & 0b1) != 0 /*
                                        * is-active doSendCached(Object, Message, Object[], int,
                                        * ResolvedLibrary<>, Library)
                                        */) {
                    while (s1_ != null) {
                        if ((s1_.cachedReflection_ == arg1Value.library) && (s1_.cachedLibrary_.accepts(arg0Value))) {
                            break;
                        }
                        s1_ = s1_.next_;
                        count1_++;
                    }
                }
                if (s1_ == null) {
                    Library cachedLibrary__ = (arg1Value.library.lookupExport(arg0Value).createCached(arg0Value));
                    assert (cachedLibrary__ = (arg1Value.library.createAssertions(cachedLibrary__))) != null;
                    // assert (s1_.cachedReflection_ == arg1Value.library);
                    if ((cachedLibrary__.accepts(arg0Value)) && count1_ < (ReflectiveDispatchNode.LIMIT)) {
                        s1_ = new SendCachedData(sendCached_cache);
                        s1_.cachedReflection_ = (arg1Value.library);
                        s1_.cachedLibrary_ = cachedLibrary__;
                        this.sendCached_cache = super.insert(s1_);
                        this.state_ = state = state | 0b1 /*
                                                           * add-active doSendCached(Object,
                                                           * Message, Object[], int,
                                                           * ResolvedLibrary<>, Library)
                                                           */;
                    }
                }
                if (s1_ != null) {
                    lock.unlock();
                    hasLock = false;
                    return doSendCached(arg0Value, arg1Value, arg2Value, parameterOffset, s1_.cachedReflection_, s1_.cachedLibrary_);
                }
            }
            this.exclude_ = exclude = exclude | 0b1 /*
                                                     * add-excluded doSendCached(Object, Message,
                                                     * Object[], int, ResolvedLibrary<>, Library)
                                                     */;
            this.sendCached_cache = null;
            state = state & 0xfffffffe /*
                                        * remove-active doSendCached(Object, Message, Object[], int,
                                        * ResolvedLibrary<>, Library)
                                        */;
            this.state_ = state = state | 0b10 /*
                                                * add-active doSendGeneric(Object, Message,
                                                * Object[], int)
                                                */;
            lock.unlock();
            hasLock = false;
            return doSendGeneric(arg0Value, arg1Value, arg2Value, parameterOffset);
        } finally {
            if (hasLock) {
                lock.unlock();
            }
        }
    }

    @Override
    public NodeCost getCost() {
        int state = state_;
        if (state == 0b0) {
            return NodeCost.UNINITIALIZED;
        } else if ((state & (state - 1)) == 0 /* is-single-active */) {
            SendCachedData s1_ = this.sendCached_cache;
            if ((s1_ == null || s1_.next_ == null)) {
                return NodeCost.MONOMORPHIC;
            }
        }
        return NodeCost.POLYMORPHIC;
    }

    public static ReflectiveDispatchNode create() {
        return new ReflectiveDispatchNodeGen();
    }

    @GeneratedBy(ReflectiveDispatchNode.class)
    private static final class SendCachedData extends Node {

        @Child SendCachedData next_;
        @CompilationFinal ResolvedLibrary<?> cachedReflection_;
        @Child Library cachedLibrary_;

        SendCachedData(SendCachedData next_) {
            this.next_ = next_;
        }

        @Override
        public NodeCost getCost() {
            return NodeCost.NONE;
        }

    }
}
