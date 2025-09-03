/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.security.AccessControlContext;
import java.util.ArrayDeque;
import java.util.Objects;

/**
 * Stack for storing AccessControlContexts. Used in conjunction with
 * {@code StackAccessControlContextVisitor}.
 */
class PrivilegedStack {

    public static class StackElement {
        protected AccessControlContext context;
        protected Class<?> caller;

        StackElement(AccessControlContext context, Class<?> caller) {
            this.context = context;
            this.caller = caller;
        }

        public AccessControlContext getContext() {
            return context;
        }

        public Class<?> getCaller() {
            return caller;
        }
    }

    /* Local AccessControlContext stack */
    private static final ThreadLocal<ArrayDeque<StackElement>> stack = new ThreadLocal<>();

    @SuppressWarnings("unchecked")
    private static ArrayDeque<StackElement> getStack() {
        if (stack.get() == null) {
            initializeStack();
        }
        return stack.get();
    }

    private static void initializeStack() {
        ArrayDeque<StackElement> tmp = new ArrayDeque<>();
        stack.set(tmp);
    }

    public static void push(AccessControlContext context, Class<?> caller) {
        getStack().push(new StackElement(context, caller));
    }

    public static void pop() {
        getStack().pop();
    }

    public static AccessControlContext peekContext() {
        return Objects.requireNonNull(getStack().peek()).getContext();
    }

    public static Class<?> peekCaller() {
        return Objects.requireNonNull(getStack().peek()).getCaller();
    }

    public static int length() {
        return getStack().size();
    }
}
