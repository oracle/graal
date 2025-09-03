/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.runtime.dispatch.messages;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.nodes.RootNode;

public final class InteropMessageRootNode extends RootNode {
    @Child InteropMessage node;
    private final UncaughtExceptionHandler interopExceptionHandler;

    public InteropMessageRootNode(TruffleLanguage<?> language, InteropMessage node) {
        this(language, node, UncaughtExceptionHandler.DEFAULT);
    }

    public InteropMessageRootNode(TruffleLanguage<?> language, InteropMessage node, UncaughtExceptionHandler handler) {
        super(language);
        this.node = insert(node);
        this.interopExceptionHandler = handler;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            return node.execute(frame.getArguments());
        } catch (InteropException e) {
            interopExceptionHandler.doHandle(e);
            return null;
        }
    }

    @Override
    public String getName() {
        return "RootNode for interop message: '" + node.name() + "'.";
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable ex) throws T {
        throw (T) ex;
    }

    public abstract static class UncaughtExceptionHandler {
        private static final UncaughtExceptionHandler DEFAULT = new UncaughtExceptionHandler() {
            @Override
            protected void handle(Throwable t) {
                throw sneakyThrow(t);
            }
        };

        @TruffleBoundary
        private void doHandle(Throwable t) {
            handle(t);
        }

        protected abstract void handle(Throwable t);
    }
}
