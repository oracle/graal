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

import java.util.function.Supplier;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.RootNode;

/*
 * TODO merge this with PolyglotValue.PolyglotNode
 */
@SuppressWarnings("deprecation")
final class PolyglotBoundaryRootNode extends RootNode {

    private static final Object UNINITIALIZED_CONTEXT = new Object();
    private final Supplier<String> name;

    @CompilationFinal private boolean seenEnter;
    @CompilationFinal private boolean seenNonEnter;

    @CompilationFinal private Object constantContext = UNINITIALIZED_CONTEXT;

    @Child private ExecutableNode executable;

    protected PolyglotBoundaryRootNode(Supplier<String> name, ExecutableNode executable) {
        super(null);
        this.name = name;
        this.executable = executable;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object[] args = frame.getArguments();
        Object languageContext = profileContext(args[0]);
        PolyglotContextImpl context = languageContext != null ? ((PolyglotLanguageContext) languageContext).context : null;
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
            return executable.execute(frame);
        } catch (Throwable e) {
            CompilerDirectives.transferToInterpreter();
            throw PolyglotImpl.wrapGuestException(((PolyglotLanguageContext) languageContext), e);
        } finally {
            if (needsEnter) {
                context.leave(prev);
            }
        }
    }

    private Object profileContext(Object languageContext) {
        if (constantContext != null) {
            if (constantContext == languageContext) {
                return constantContext;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (constantContext == UNINITIALIZED_CONTEXT) {
                    constantContext = languageContext;
                } else {
                    constantContext = null;
                }
            }
        }
        return languageContext;
    }

    @Override
    public String getName() {
        return name.get();
    }

    @Override
    public String toString() {
        return getName();
    }
}
