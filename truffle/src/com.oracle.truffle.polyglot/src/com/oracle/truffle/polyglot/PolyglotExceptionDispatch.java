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
package com.oracle.truffle.polyglot;

import java.io.PrintStream;
import java.io.PrintWriter;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractExceptionDispatch;

final class PolyglotExceptionDispatch extends AbstractExceptionDispatch {

    protected PolyglotExceptionDispatch(AbstractPolyglotImpl engineImpl) {
        super(engineImpl);
    }

    @Override
    public boolean isInternalError(Object receiver) {
        return ((PolyglotExceptionImpl) receiver).isInternalError();
    }

    @Override
    public boolean isCancelled(Object receiver) {
        return ((PolyglotExceptionImpl) receiver).isCancelled();
    }

    @Override
    public boolean isExit(Object receiver) {
        return ((PolyglotExceptionImpl) receiver).isExit();
    }

    @Override
    public int getExitStatus(Object receiver) {
        return ((PolyglotExceptionImpl) receiver).getExitStatus();
    }

    @Override
    public Iterable<StackFrame> getPolyglotStackTrace(Object receiver) {
        return ((PolyglotExceptionImpl) receiver).getPolyglotStackTrace();
    }

    @Override
    public boolean isSyntaxError(Object receiver) {
        return ((PolyglotExceptionImpl) receiver).isSyntaxError();
    }

    @Override
    public Value getGuestObject(Object receiver) {
        return ((PolyglotExceptionImpl) receiver).getGuestObject();
    }

    @Override
    public boolean isIncompleteSource(Object receiver) {
        return ((PolyglotExceptionImpl) receiver).isIncompleteSource();
    }

    @Override
    public void onCreate(Object receiver, PolyglotException api) {
        ((PolyglotExceptionImpl) receiver).onCreate(api);
    }

    @Override
    public void printStackTrace(Object receiver, PrintStream s) {
        ((PolyglotExceptionImpl) receiver).printStackTrace(s);
    }

    @Override
    public void printStackTrace(Object receiver, PrintWriter s) {
        ((PolyglotExceptionImpl) receiver).printStackTrace(s);
    }

    @Override
    public StackTraceElement[] getStackTrace(Object receiver) {
        return ((PolyglotExceptionImpl) receiver).getStackTrace();
    }

    @Override
    public String getMessage(Object receiver) {
        return ((PolyglotExceptionImpl) receiver).getMessage();
    }

    @Override
    public boolean isHostException(Object receiver) {
        return ((PolyglotExceptionImpl) receiver).isHostException();
    }

    @Override
    public Throwable asHostException(Object receiver) {
        return ((PolyglotExceptionImpl) receiver).asHostException();
    }

    @Override
    public SourceSection getSourceLocation(Object receiver) {
        return ((PolyglotExceptionImpl) receiver).getSourceLocation();
    }

    @Override
    public boolean isResourceExhausted(Object receiver) {
        return ((PolyglotExceptionImpl) receiver).isResourceExhausted();
    }

    @Override
    public boolean isInterrupted(Object receiver) {
        return ((PolyglotExceptionImpl) receiver).isInterrupted();
    }

}
