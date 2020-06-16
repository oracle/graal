/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BlockNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

final class DefaultBlockNode<T extends Node> extends BlockNode<T> {

    final ElementExecutor<T> executor;

    DefaultBlockNode(T[] elements, ElementExecutor<T> executor) {
        super(elements);
        this.executor = executor;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame, int arg) {
        ElementExecutor<T> ex = this.executor;
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; ++i) {
            ex.executeVoid(frame, e[i], i, arg);
        }
        return ex.executeGeneric(frame, e[last], last, arg);
    }

    @Override
    public void executeVoid(VirtualFrame frame, int arg) {
        ElementExecutor<T> ex = this.executor;
        T[] e = getElements();
        for (int i = 0; i < e.length; ++i) {
            ex.executeVoid(frame, e[i], i, arg);
        }
    }

    @Override
    public byte executeByte(VirtualFrame frame, int arg) throws UnexpectedResultException {
        ElementExecutor<T> ex = this.executor;
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; ++i) {
            ex.executeVoid(frame, e[i], i, arg);
        }
        return ex.executeByte(frame, e[last], last, arg);
    }

    @Override
    public short executeShort(VirtualFrame frame, int arg) throws UnexpectedResultException {
        ElementExecutor<T> ex = this.executor;
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; ++i) {
            ex.executeVoid(frame, e[i], i, arg);
        }
        return ex.executeShort(frame, e[last], last, arg);
    }

    @Override
    public char executeChar(VirtualFrame frame, int arg) throws UnexpectedResultException {
        ElementExecutor<T> ex = this.executor;
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; ++i) {
            ex.executeVoid(frame, e[i], i, arg);
        }
        return ex.executeChar(frame, e[last], last, arg);
    }

    @Override
    public int executeInt(VirtualFrame frame, int arg) throws UnexpectedResultException {
        ElementExecutor<T> ex = this.executor;
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; ++i) {
            ex.executeVoid(frame, e[i], i, arg);
        }
        return ex.executeInt(frame, e[last], last, arg);
    }

    @Override
    public long executeLong(VirtualFrame frame, int arg) throws UnexpectedResultException {
        ElementExecutor<T> ex = this.executor;
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; ++i) {
            ex.executeVoid(frame, e[i], i, arg);
        }
        return ex.executeLong(frame, e[last], last, arg);
    }

    @Override
    public float executeFloat(VirtualFrame frame, int arg) throws UnexpectedResultException {
        ElementExecutor<T> ex = this.executor;
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; ++i) {
            ex.executeVoid(frame, e[i], i, arg);
        }
        return ex.executeFloat(frame, e[last], last, arg);
    }

    @Override
    public double executeDouble(VirtualFrame frame, int arg) throws UnexpectedResultException {
        ElementExecutor<T> ex = this.executor;
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; ++i) {
            ex.executeVoid(frame, e[i], i, arg);
        }
        return ex.executeDouble(frame, e[last], last, arg);
    }

    @Override
    public boolean executeBoolean(VirtualFrame frame, int arg) throws UnexpectedResultException {
        ElementExecutor<T> ex = this.executor;
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = 0; i < last; ++i) {
            ex.executeVoid(frame, e[i], i, arg);
        }
        return ex.executeBoolean(frame, e[last], last, arg);
    }
}
