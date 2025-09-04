/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jdwp.impl;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.MethodHook;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.MethodVariable;

public final class MethodBreakpointInfo extends AbstractBreakpointInfo implements MethodHook, Consumer<KlassRef> {

    private final Set<KlassRef> classes = ConcurrentHashMap.newKeySet();
    private MethodRef[] methods = new MethodRef[0];
    private final boolean isMethodEntry;
    private final boolean isMethodExit;

    public MethodBreakpointInfo(RequestFilter filter) {
        super(filter);
        this.isMethodEntry = filter.getEventKind() == RequestedJDWPEvents.METHOD_ENTRY;
        this.isMethodExit = filter.getEventKind() == RequestedJDWPEvents.METHOD_EXIT || filter.getEventKind() == RequestedJDWPEvents.METHOD_EXIT_WITH_RETURN_VALUE;
    }

    @Override
    public void accept(KlassRef klass) {
        if (classes.add(klass)) {
            if (getFilter().matchesType(klass)) {
                for (MethodRef method : klass.getDeclaredMethods()) {
                    method.addMethodHook(this);
                    addMethod(method);
                }
            }
        }
    }

    public void addMethod(MethodRef method) {
        methods = Arrays.copyOf(methods, methods.length + 1);
        methods[methods.length - 1] = method;
    }

    public MethodRef[] getMethods() {
        return methods;
    }

    @Override
    public Kind getKind() {
        return Kind.INDEFINITE;
    }

    @Override
    public boolean onMethodEnter(@SuppressWarnings("unused") MethodRef method, @SuppressWarnings("unused") MethodVariable[] variables) {
        return isMethodEntry;
    }

    @Override
    public boolean onMethodExit(@SuppressWarnings("unused") MethodRef method, @SuppressWarnings("unused") Object returnValue) {
        return isMethodExit;
    }

    @Override
    public void dispose() {
        for (MethodRef method : methods) {
            method.disposeHooks();
        }
    }
}
