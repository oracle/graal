/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

import static com.oracle.truffle.api.nodes.NodeAccessor.ENGINE;
import static com.oracle.truffle.api.nodes.NodeAccessor.LANGUAGE;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.InlineParsingRequest;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Represents an executable node in a Truffle AST. The executable node represents an AST fragment
 * that can be {@link #execute(VirtualFrame) executed} using a {@link VirtualFrame frame} instance
 * created by the framework.
 *
 * @see TruffleLanguage#parse(com.oracle.truffle.api.TruffleLanguage.InlineParsingRequest)
 * @since 0.31
 */
public abstract class ExecutableNode extends Node {

    /*
     * Instance of PolyglotSharingLayer.
     */
    @CompilationFinal private Object polyglotRef;

    /**
     * Creates new executable node with a given language instance. The language instance is
     * obtainable while {@link TruffleLanguage#parse(InlineParsingRequest)} is executed.
     *
     * @param language the language this executable node is associated with
     * @since 0.31
     */
    protected ExecutableNode(TruffleLanguage<?> language) {
        CompilerAsserts.neverPartOfCompilation();
        if (language != null) {
            assert !NodeAccessor.HOST.isHostLanguage(language.getClass()) : "do not create create executable nodes with host language";
            this.polyglotRef = language;
        } else {
            this.polyglotRef = ENGINE.getCurrentSharingLayer();
        }
        /*
         * This can no longer happen for normal languages. It could only happen for language
         * instances that were created directly without service provider. This case is rare enough,
         * to not be worth a check for every root node.
         */
        assert language == null || getLanguageInfo() != null : "Truffle language instance is not initialized.";
    }

    final TruffleLanguage<?> getLanguage() {
        Object ref = polyglotRef;
        if (ref instanceof TruffleLanguage<?>) {
            return (TruffleLanguage<?>) ref;
        } else {
            return null;
        }
    }

    final void applyEngineRef(ExecutableNode node) {
        this.polyglotRef = node.polyglotRef;
    }

    final Object getSharingLayer() {
        Object ref = polyglotRef;
        if (ref instanceof TruffleLanguage<?>) {
            return ENGINE.getPolyglotSharingLayer(LANGUAGE.getPolyglotLanguageInstance((TruffleLanguage<?>) ref));
        } else {
            return ref;
        }
    }

    final void setSharingLayer(Object layer) {
        assert !(polyglotRef instanceof TruffleLanguage<?>) : "not allowed overwrite language";
        this.polyglotRef = layer;
    }

    /**
     * Execute this fragment at the place where it was parsed.
     *
     * @param frame the actual frame valid at the parsed location
     * @return the result of the execution, must be an interop type (i.e. either implementing
     *         TruffleObject or be a primitive value), or <code>null</code>.
     * @since 0.31
     */
    public abstract Object execute(VirtualFrame frame);

    /**
     * Returns public information about the language. The language can be assumed equal if the
     * instances of the language info instance are the same. To access internal details of the
     * language within the language implementation use a {@link LanguageReference}.
     *
     * @since 0.31
     */
    public final LanguageInfo getLanguageInfo() {
        TruffleLanguage<?> language = getLanguage();
        if (language != null) {
            return LANGUAGE.getLanguageInfo(language);
        } else {
            return null;
        }
    }

    /**
     * Returns the language instance associated with this executable node. The language instance is
     * intended for internal use in languages and is only accessible if the concrete type of the
     * language is known. Public information about the language can be accessed using
     * {@link #getLanguageInfo()}. The language is <code>null</code> if the executable node is not
     * associated with a language. This method is guaranteed to return a
     * {@link CompilerDirectives#isPartialEvaluationConstant(Object) PE constant} if the root node
     * is also a PE constant.
     *
     * @see #getLanguageInfo()
     * @since 0.31
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public final <C extends TruffleLanguage> C getLanguage(Class<C> languageClass) {
        TruffleLanguage<?> language = getLanguage();
        if (language == null) {
            return null;
        }
        if (language.getClass() != languageClass) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new ClassCastException(String.format("Illegal language class specified. Expected '%s'.", language.getClass().getName()));
        }
        return (C) language;
    }
}
