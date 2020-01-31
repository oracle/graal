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

package com.oracle.truffle.api.matrix;

import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;

@GenerateLibrary
public abstract class MatrixLibrary extends Library {

    public abstract Object elementWiseExp(Object receiver) throws UnsupportedMessageException;
    public abstract Object elementWiseLog(Object receiver) throws UnsupportedMessageException;
    public abstract Object elementWiseSqrt(Object receiver) throws UnsupportedMessageException;

    public abstract Object scalarAddition(Object receiver, Object scalar) throws UnsupportedMessageException;
    public abstract Object scalarMultiplication(Object receiver, Object scalar) throws UnsupportedMessageException;
    public abstract Object scalarExponentiation(Object receiver, Object scalar) throws UnsupportedMessageException;

    public abstract Object leftMatrixMultiplication(Object receiver, Object vector) throws UnsupportedMessageException;
    public abstract Object rightMatrixMultiplication(Object receiver, Object vector) throws UnsupportedMessageException;

    public abstract Object transpose(Object receiver) throws UnsupportedMessageException;
    public abstract Object invertMatrix(Object receiver) throws UnsupportedMessageException;
    public abstract Object crossProduct(Object receiver) throws UnsupportedMessageException;
    public abstract Object crossProductDuo(Object receiver, Object another) throws UnsupportedMessageException;

    public abstract Object diagonal(Object receiver) throws UnsupportedMessageException;

    public abstract Object rowSum(Object receiver) throws UnsupportedMessageException;
    public abstract Object columnSum(Object receiver) throws UnsupportedMessageException;
    public abstract Double elementWiseSum(Object receiver) throws UnsupportedMessageException;

    public abstract Object rowWiseAppend(Object receiver, Object tensor) throws UnsupportedMessageException;
    public abstract Object columnWiseAppend(Object receiver, Object tensor) throws UnsupportedMessageException;

    public abstract Object splice(Object receiver, Integer rowStart, Integer rowEnd, Integer colStart, Integer colEnd) throws UnsupportedMessageException;
    public abstract Object matrixAddition(Object receiver, Object otherMatrix) throws UnsupportedMessageException;
    public abstract Integer getNumRows(Object receiver) throws UnsupportedMessageException;
    public abstract Integer getNumCols(Object receiver) throws UnsupportedMessageException;
    public abstract Object removeAbstractions(Object receiver) throws UnsupportedMessageException;
    public abstract Object unwrap(Object receiver);

}
