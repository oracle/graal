/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.test.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

import java.util.AbstractList;
import java.util.List;

final class TruffleList<T> extends AbstractList<T> {
    private final TruffleObject array;
    private final Class<T> type;

    private TruffleList(Class<T> elementType, TruffleObject array) {
        this.array = array;
        this.type = elementType;
    }

    public static <T> List<T> create(Class<T> elementType, TruffleObject array) {
        return new TruffleList<>(elementType, array);
    }

    @Override
    public T get(int index) {
        try {
            return type.cast(message(Message.READ, array, index));
        } catch (InteropException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public T set(int index, T element) {
        T prev = get(index);
        try {
            message(Message.WRITE, array, index, element);
        } catch (InteropException e) {
            throw new IllegalStateException(e);
        }
        return prev;
    }

    @Override
    public int size() {
        try {
            return (Integer) message(Message.GET_SIZE, array);
        } catch (InteropException e) {
            throw new IllegalStateException(e);
        }
    }

    private static class TemporaryRoot extends RootNode {
        @Node.Child private Node foreignAccess;
        private final TruffleObject function;

        @SuppressWarnings("rawtypes")
        TemporaryRoot(Class<? extends TruffleLanguage> lang, Node foreignAccess, TruffleObject function) {
            super(lang, null, null);
            this.foreignAccess = foreignAccess;
            this.function = function;
        }

        @SuppressWarnings("deprecation")
        @Override
        public Object execute(VirtualFrame frame) {
            return ForeignAccess.execute(foreignAccess, frame, function, frame.getArguments());
        }
    }

    @SuppressWarnings("unused")
    @TruffleBoundary
    static Object message(final Message m, Object receiver, Object... arr) throws InteropException {
        Node n = m.createNode();
        CallTarget callTarget = Truffle.getRuntime().createCallTarget(new TemporaryRoot(TruffleLanguage.class, n, (TruffleObject) receiver));
        return callTarget.call(arr);
    }
}
