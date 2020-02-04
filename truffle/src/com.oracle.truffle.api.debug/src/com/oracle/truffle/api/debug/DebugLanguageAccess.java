/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

import java.util.Objects;

public final class DebugLanguageAccess {

    private final Class<? extends TruffleLanguage<?>> languageClass;


    private DebugLanguageAccess(Class<? extends TruffleLanguage<?>> languageClass) {
        this.languageClass = languageClass;
    }

    /**
     * Get a language-specific accessor for looking up various raw guest objects. The lookup always
     * return <code>null</code> in case the language class associated with the accessor does not
     * match with the original language behind the debug objects.
     *
     * @param languageClass
     *
     * @since 20.1
     */
    public static DebugLanguageAccess get(Class<? extends TruffleLanguage<?>> languageClass) {
        Objects.requireNonNull(languageClass);
        return new DebugLanguageAccess(languageClass);
    }

    /**
     * Returns the current node on stack frame, or <code>null</code> if the requesting language
     * class does not match the root node guest language.
     *
     * @param stackFrame the stack frame instance to look up the current node for
     * @return the node associated with the frame
     *
     * @since 20.1
     */
    public Node getRawNode(DebugStackFrame stackFrame) {
        Objects.requireNonNull(stackFrame);
        RootNode rootNode = stackFrame.findCurrentRoot();
        if (rootNode == null) {
            return null;
        }
        // check if language class of the root node corresponds to the input language
        TruffleLanguage<?> language = Debugger.ACCESSOR.nodeSupport().getLanguage(rootNode);
        return language != null && language.getClass() == languageClass ? stackFrame.getCurrentNode() : null;
    }

    /**
     * Returns the underlying materialized frame for this debug stack frame or <code>null</code> if
     * the requesting language class does not match the root node guest language.
     *
     * @param stackFrame the stack frame instance to look up the raw guest frame for
     * @return the materialized frame
     *
     * @since 20.1
     */
    public MaterializedFrame getRawFrame(DebugStackFrame stackFrame) {
        Objects.requireNonNull(stackFrame);
        RootNode rootNode = stackFrame.findCurrentRoot();
        if (rootNode == null) {
            return null;
        }
        // check if language class of the root node corresponds to the input language
        TruffleLanguage<?> language = Debugger.ACCESSOR.nodeSupport().getLanguage(rootNode);
        return language != null && language.getClass() == languageClass ? stackFrame.findTruffleFrame() : null;
    }

    /**
     * Returns the guest language representation of the exception, or <code>null</code> if the
     * requesting language class does not match the root node language at the throw location.
     *
     * @param debugException the debug exception instance to look up the raw guest exception for
     * @return the throwable guest language exception object
     *
     * @since 20.1
     */
    public Throwable getRawException(DebugException debugException) {
        Objects.requireNonNull(debugException);
        RootNode rootNode = debugException.getThrowLocationNode().getRootNode();
        if (rootNode == null) {
            return null;
        }
        // check if language class of the root node corresponds to the input language
        TruffleLanguage<?> language = Debugger.ACCESSOR.nodeSupport().getLanguage(rootNode);
        return language != null && language.getClass() == languageClass ? debugException.getRawException() : null;
    }

    /**
     * Returns the underlying guest value object held by this {@link DebugValue}.
     *
     * @param debugValue the debug value to look up the raw guest value from
     * @return the guest language object or null if the language differs from the language that
     *         created the underlying {@link DebugValue}
     *
     * @since 20.1
     */
    Object getRawValue(DebugValue debugValue) {
        Objects.requireNonNull(debugValue);
        RootNode rootNode = debugValue.getScope().getRoot();
        if (rootNode == null) {
            return null;
        }
        // check if language class of the root node corresponds to the input language
        TruffleLanguage<?> language = Debugger.ACCESSOR.nodeSupport().getLanguage(rootNode);
        return language != null && language.getClass() == languageClass ? debugValue.get() : null;
    }
}
