/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.locks.Lock;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.InlineParsingRequest;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;

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
     * Since languages were singletons in the past, we cannot use the Env instance stored in
     * TruffleLanguage for languages that are not yet migrated. We use this sourceVM reference
     * instead for compatibility.
     */
    @CompilationFinal Object sourceVM;
    final TruffleLanguage<?> language;
    @CompilationFinal ReferenceCache referenceCache;

    /**
     * Creates new executable node with a given language instance. The language instance is
     * obtainable while {@link TruffleLanguage#parse(InlineParsingRequest)} is executed.
     *
     * @param language the language this executable node is associated with
     * @since 0.31
     */
    protected ExecutableNode(TruffleLanguage<?> language) {
        CompilerAsserts.neverPartOfCompilation();
        this.language = language;
        if (this.language != null) {
            this.sourceVM = NodeAccessor.ACCESSOR.engineSupport().getVMFromLanguageObject(NodeAccessor.ACCESSOR.languageSupport().getVMObject(this.language));
        } else {
            this.sourceVM = getCurrentVM();
        }
        if (language != null && getLanguageInfo() == null) {
            throw new IllegalArgumentException("Truffle language instance is not initialized.");
        }
    }

    private static Object getCurrentVM() {
        EngineSupport engine = NodeAccessor.ACCESSOR.engineSupport();
        if (engine != null) {
            return engine.getCurrentVM();
        } else {
            return null;
        }
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
     * language within the language implementation use {@link #lookupLanguageReference(Class)}.
     *
     * @since 0.31
     */
    public final LanguageInfo getLanguageInfo() {
        if (language != null) {
            return NodeAccessor.ACCESSOR.languageSupport().getLanguageInfo(language);
        } else {
            return null;
        }
    }

    /**
     * Returns the language instance associated with this executable node. The language instance is
     * intended for internal use in languages and is only accessible if the concrete type of the
     * language is known. Public information about the language can be accessed using
     * {@link #getLanguageInfo()}. The language is <code>null</code> if the executable node is not
     * associated with a <code>null</code> language.
     *
     * @see #getLanguageInfo()
     * @since 0.31
     * @deprecated use {@link #getLanguageReference(Class)} instead.
     */
    @Deprecated
    @SuppressWarnings({"rawtypes", "unchecked"})
    public final <C extends TruffleLanguage> C getLanguage(Class<C> languageClass) {
        if (language == null) {
            return null;
        }
        TruffleLanguage<?> spi = this.language;
        if (spi.getClass() != languageClass) {
            if (!languageClass.isInstance(spi) || languageClass == TruffleLanguage.class || !TruffleLanguage.class.isAssignableFrom(languageClass)) {
                CompilerDirectives.transferToInterpreter();
                throw new ClassCastException("Illegal language class specified. Expected " + spi.getClass().getName() + ".");
            }
        }
        return (C) spi;
    }

    static final class ReferenceCache {

        final Class<?> languageClass;
        final LanguageReference<?> languageReference;
        final ContextReference<?> contextReference;
        final ReferenceCache next;

        @SuppressWarnings("unchecked")
        ReferenceCache(ExecutableNode executableNode, @SuppressWarnings("rawtypes") Class<? extends TruffleLanguage> languageClass, ReferenceCache next) {
            this.languageClass = languageClass;
            if (languageClass != null) {
                this.languageReference = NodeAccessor.ACCESSOR.engineSupport().lookupLanguageReference(executableNode.sourceVM,
                                executableNode.language, languageClass);
                this.contextReference = NodeAccessor.ACCESSOR.engineSupport().lookupContextReference(executableNode.sourceVM,
                                executableNode.language, languageClass);
            } else {
                this.languageReference = null;
                this.contextReference = null;
            }
            this.next = next;
        }

    }

    @ExplodeLoop(kind = LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    @SuppressWarnings("rawtypes")
    final ReferenceCache lookupReferenceCache(Class<? extends TruffleLanguage> languageClass) {
        do {
            ReferenceCache current = this.referenceCache;
            if (current == GENERIC) {
                return null;
            }
            while (current != null) {
                if ((current.languageClass == languageClass)) {
                    return current;
                }
                current = current.next;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            specializeReferenceCache(languageClass);
        } while (true);
    }

    private static final ReferenceCache GENERIC = new ReferenceCache(null, null, null);

    @SuppressWarnings("rawtypes")
    private void specializeReferenceCache(Class<? extends TruffleLanguage> languageClass) {
        Lock lock = getLock();
        lock.lock();
        try {
            ReferenceCache current = this.referenceCache;
            if (current == null) {
                this.referenceCache = new ReferenceCache(this, languageClass, null);
            } else {
                if (sourceVM == null) {
                    this.referenceCache = GENERIC;
                } else {
                    int count = 0;
                    ReferenceCache original = current;
                    do {
                        count++;
                        current = current.next;
                    } while (current != null);
                    if (count >= 5) {
                        this.referenceCache = GENERIC;
                    } else {
                        this.referenceCache = new ReferenceCache(this, languageClass, original);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

}
