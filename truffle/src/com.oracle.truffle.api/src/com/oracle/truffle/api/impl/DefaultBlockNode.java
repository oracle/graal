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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BlockNode;
import com.oracle.truffle.api.nodes.BlockNode.VoidElement;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

final class DefaultBlockNode<T extends Node & VoidElement> extends BlockNode<T> {

    @CompilationFinal Class<? extends ElementExceptionHandler> exceptionHandlerClass = ElementExceptionHandler.class;

    DefaultBlockNode(T[] elements) {
        super(elements);
    }

    @Override
    public Object execute(VirtualFrame frame, int start, ElementExceptionHandler exceptionHandler) {
        verifyStart(start, exceptionHandler);
        T[] e = getElements();
        int last = e.length - 1;
        int i = start;
        for (; i < last; ++i) {
            try {
                e[i].executeVoid(frame);
            } catch (Throwable ex) {
                handleElementException(frame, exceptionHandler, i, ex);
                throw ex;
            }
        }
        try {
            return ((GenericElement) e[last]).execute(frame);
        } catch (Throwable ex) {
            handleElementException(frame, exceptionHandler, last, ex);
            throw ex;
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return execute(frame, 0, null);
    }

    private static void handleElementException(VirtualFrame frame, ElementExceptionHandler exceptionHandler, int index, Throwable ex) {
        if (exceptionHandler != null) {
            exceptionHandler.onBlockElementException(frame, ex, index);
        }
    }

    @Override
    public void executeVoid(VirtualFrame frame, int start, ElementExceptionHandler exceptionHandler) {
        verifyStart(start, exceptionHandler);
        T[] e = getElements();
        int length = e.length;
        for (int i = start; i < length; ++i) {
            try {
                e[i].executeVoid(frame);
            } catch (Throwable ex) {
                handleElementException(frame, exceptionHandler, i, ex);
                throw ex;
            }
        }
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        executeVoid(frame, 0, null);
    }

    @Override
    public byte executeByte(VirtualFrame frame, int start, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException {
        verifyStart(start, exceptionHandler);
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = start; i < last; ++i) {
            try {
                e[i].executeVoid(frame);
            } catch (Throwable ex) {
                handleElementException(frame, exceptionHandler, i, ex);
                throw ex;
            }
        }
        try {
            return ((TypedElement) e[last]).executeByte(frame);
        } catch (UnexpectedResultException ex) {
            throw ex;
        } catch (Throwable ex) {
            handleElementException(frame, exceptionHandler, last, ex);
            throw ex;
        }
    }

    @Override
    public byte executeByte(VirtualFrame frame) throws UnexpectedResultException {
        return executeByte(frame, 0, null);
    }

    @Override
    public short executeShort(VirtualFrame frame, int start, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException {
        verifyStart(start, exceptionHandler);
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = start; i < last; ++i) {
            try {
                e[i].executeVoid(frame);
            } catch (Throwable ex) {
                handleElementException(frame, exceptionHandler, i, ex);
                throw ex;
            }
        }
        try {
            return ((TypedElement) e[last]).executeShort(frame);
        } catch (UnexpectedResultException ex) {
            throw ex;
        } catch (Throwable ex) {
            handleElementException(frame, exceptionHandler, last, ex);
            throw ex;
        }
    }

    @Override
    public short executeShort(VirtualFrame frame) throws UnexpectedResultException {
        return executeShort(frame, 0, null);
    }

    @Override
    public int executeInt(VirtualFrame frame, int start, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException {
        verifyStart(start, exceptionHandler);
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = start; i < last; ++i) {
            try {
                e[i].executeVoid(frame);
            } catch (Throwable ex) {
                handleElementException(frame, exceptionHandler, i, ex);
                throw ex;
            }
        }
        try {
            return ((TypedElement) e[last]).executeInt(frame);
        } catch (UnexpectedResultException ex) {
            throw ex;
        } catch (Throwable ex) {
            handleElementException(frame, exceptionHandler, last, ex);
            throw ex;
        }
    }

    @Override
    public int executeInt(VirtualFrame frame) throws UnexpectedResultException {
        return executeInt(frame, 0, null);
    }

    @Override
    public char executeChar(VirtualFrame frame, int start, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException {
        verifyStart(start, exceptionHandler);
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = start; i < last; ++i) {
            try {
                e[i].executeVoid(frame);
            } catch (Throwable ex) {
                handleElementException(frame, exceptionHandler, i, ex);
                throw ex;
            }
        }
        try {
            return ((TypedElement) e[last]).executeChar(frame);
        } catch (UnexpectedResultException ex) {
            throw ex;
        } catch (Throwable ex) {
            handleElementException(frame, exceptionHandler, last, ex);
            throw ex;
        }
    }

    @Override
    public char executeChar(VirtualFrame frame) throws UnexpectedResultException {
        return executeChar(frame, 0, null);
    }

    @Override
    public long executeLong(VirtualFrame frame, int start, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException {
        verifyStart(start, exceptionHandler);
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = start; i < last; ++i) {
            try {
                e[i].executeVoid(frame);
            } catch (Throwable ex) {
                handleElementException(frame, exceptionHandler, i, ex);
                throw ex;
            }
        }
        try {
            return ((TypedElement) e[last]).executeLong(frame);
        } catch (UnexpectedResultException ex) {
            throw ex;
        } catch (Throwable ex) {
            handleElementException(frame, exceptionHandler, last, ex);
            throw ex;
        }
    }

    @Override
    public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
        return executeLong(frame, 0, null);
    }

    @Override
    public float executeFloat(VirtualFrame frame, int start, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException {
        verifyStart(start, exceptionHandler);
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = start; i < last; ++i) {
            try {
                e[i].executeVoid(frame);
            } catch (Throwable ex) {
                handleElementException(frame, exceptionHandler, i, ex);
                throw ex;
            }
        }
        try {
            return ((TypedElement) e[last]).executeFloat(frame);
        } catch (UnexpectedResultException ex) {
            throw ex;
        } catch (Throwable ex) {
            handleElementException(frame, exceptionHandler, last, ex);
            throw ex;
        }
    }

    @Override
    public float executeFloat(VirtualFrame frame) throws UnexpectedResultException {
        return executeFloat(frame, 0, null);
    }

    @Override
    public double executeDouble(VirtualFrame frame, int start, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException {
        verifyStart(start, exceptionHandler);
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = start; i < last; ++i) {
            try {
                e[i].executeVoid(frame);
            } catch (Throwable ex) {
                handleElementException(frame, exceptionHandler, i, ex);
                throw ex;
            }
        }
        try {
            return ((TypedElement) e[last]).executeDouble(frame);
        } catch (UnexpectedResultException ex) {
            throw ex;
        } catch (Throwable ex) {
            handleElementException(frame, exceptionHandler, last, ex);
            throw ex;
        }
    }

    @Override
    public double executeDouble(VirtualFrame frame) throws UnexpectedResultException {
        return executeDouble(frame, 0, null);
    }

    @Override
    public boolean executeBoolean(VirtualFrame frame, int start, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException {
        verifyStart(start, exceptionHandler);
        T[] e = getElements();
        int last = e.length - 1;
        for (int i = start; i < last; ++i) {
            try {
                e[i].executeVoid(frame);
            } catch (Throwable ex) {
                handleElementException(frame, exceptionHandler, i, ex);
                throw ex;
            }
        }
        try {
            return ((TypedElement) e[last]).executeBoolean(frame);
        } catch (UnexpectedResultException ex) {
            throw ex;
        } catch (Throwable ex) {
            handleElementException(frame, exceptionHandler, last, ex);
            throw ex;
        }
    }

    @Override
    public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
        return executeBoolean(frame, 0, null);
    }

    private void verifyStart(int start, ElementExceptionHandler eh) {
        if (start < 0 || start >= getElements().length) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException("Invalid startIndex " + start + " for block with " + getElements().length + " elements.");
        }
        assert assertExceptionHandler(eh);
    }

    private boolean assertExceptionHandler(ElementExceptionHandler eh) {
        Class<? extends ElementExceptionHandler> cachedEhClass = this.exceptionHandlerClass;
        Class<? extends ElementExceptionHandler> ehClass = eh == null ? null : eh.getClass();
        if (cachedEhClass == ElementExceptionHandler.class) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            exceptionHandlerClass = cachedEhClass = ehClass;
        }
        if (cachedEhClass != ehClass) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException(String.format("Block node must be invoked with a compilation final exception handler type. " +
                            "Got type %s but was expecting type %s from a previous execution.", ehClass, cachedEhClass));
        }
        return true;
    }
}
