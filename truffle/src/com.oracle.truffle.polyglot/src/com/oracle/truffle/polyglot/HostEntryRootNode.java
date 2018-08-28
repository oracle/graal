/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.polyglot;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;

/*
 * TODO merge this with PolyglotValue.PolyglotNode
 */
abstract class HostEntryRootNode<T> extends RootNode {

    private static final Object UNINITIALIZED_CONTEXT = new Object();

    @CompilationFinal private boolean seenEnter;
    @CompilationFinal private boolean seenNonEnter;

    @CompilationFinal private Object constantContext = UNINITIALIZED_CONTEXT;

    HostEntryRootNode() {
        super(null);
    }

    protected abstract Class<? extends T> getReceiverType();

    @Override
    public final Object execute(VirtualFrame frame) {
        Object[] args = frame.getArguments();
        PolyglotLanguageContext languageContext = profileContext(args[0]);
        assert languageContext != null;
        PolyglotContextImpl context = languageContext.context;
        boolean needsEnter = languageContext != null && context.needsEnter();
        Object prev;
        if (needsEnter) {
            if (!seenEnter) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                seenEnter = true;
            }
            prev = context.enter();
        } else {
            if (!seenNonEnter) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                seenNonEnter = true;
            }
            prev = null;
        }
        try {
            Object[] arguments = frame.getArguments();
            T receiver = getReceiverType().cast(arguments[1]);
            Object result;
            result = executeImpl(languageContext, receiver, arguments, 2);
            assert !(result instanceof TruffleObject);
            return result;
        } catch (Throwable e) {
            CompilerDirectives.transferToInterpreter();
            throw PolyglotImpl.wrapGuestException((languageContext), e);
        } finally {
            if (needsEnter) {
                context.leave(prev);
            }
        }
    }

    private PolyglotLanguageContext profileContext(Object languageContext) {
        if (constantContext != null) {
            if (constantContext == languageContext) {
                return (PolyglotLanguageContext) constantContext;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (constantContext == UNINITIALIZED_CONTEXT) {
                    constantContext = languageContext;
                } else {
                    constantContext = null;
                }
            }
        }
        return (PolyglotLanguageContext) languageContext;
    }

    protected abstract Object executeImpl(PolyglotLanguageContext languageContext, T receiver, Object[] args, int offset);

    protected static CallTarget createTarget(HostEntryRootNode<?> node) {
        return Truffle.getRuntime().createCallTarget(node);
    }

    static <T> T installHostCodeCache(PolyglotLanguageContext languageContext, Object key, T value, Class<T> expectedType) {
        T result = expectedType.cast(languageContext.context.engine.javaInteropCodeCache.putIfAbsent(key, value));
        if (result != null) {
            return result;
        } else {
            return value;
        }
    }

    static <T> T lookupHostCodeCache(PolyglotLanguageContext languageContext, Object key, Class<T> expectedType) {
        return expectedType.cast(languageContext.context.engine.javaInteropCodeCache.get(key));
    }

}
