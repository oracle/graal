/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.debug.internal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;


public final class DebugScope {

    private static ThreadLocal<DebugScope> instance = new ThreadLocal<>();
    private final List<DebugScope> children = new ArrayList<>(4);
    private final String name;
    private final DebugScope parent;
    private final Object[] context;
    private final DebugValueMap valueMap = new DebugValueMap();

    public static final DebugScope DEFAULT_CONTEXT = new DebugScope("DEFAULT", null);

    public static DebugScope getInstance() {
        DebugScope result = instance.get();
        if (result == null) {
            return DEFAULT_CONTEXT;
        } else {
            return result;
        }
    }

    private DebugScope(String name, DebugScope parent, Object... context) {
        this.name = name;
        this.parent = parent;
        this.context = context;
    }

    public void log(String msg, Object... args) {
    }

    public <T> T scope(String newName, Runnable runnable, Callable<T> callable, boolean sandbox, Object[] newContext) {
        DebugScope oldContext = getInstance();
        DebugScope newChild = null;
        if (sandbox) {
            newChild = new DebugScope(name, null, newContext);
        } else {
            oldContext.createChild(newName, newContext);
        }
        instance.set(newChild);
        T result = null;
        try {
            if (runnable != null) {
                runnable.run();
            }
            if (callable != null) {
                call(callable);
            }
        } catch (RuntimeException e) {
            throw interceptException(e);
        } finally {
            instance.set(oldContext);
        }
        return result;
    }

    public DebugValueMap getValueMap() {
        return valueMap;
    }

    private RuntimeException interceptException(RuntimeException e) {
        return e;
    }

    long getCurrentValue(int index) {
        return valueMap.getCurrentValue(index);
    }

    void setCurrentValue(int index, long l) {
        valueMap.setCurrentValue(index, l);
    }

    private DebugScope createChild(String newName, Object[] newContext) {
        DebugScope result = new DebugScope(newName, this, newContext);
        children.add(result);
        return result;
    }

    public Iterable<Object> getCurrentContext() {
        return new Iterable<Object>() {

            @Override
            public Iterator<Object> iterator() {
                return new Iterator<Object>() {

                    DebugScope currentScope = DebugScope.this;
                    int objectIndex;

                    @Override
                    public boolean hasNext() {
                        selectScope();
                        return currentScope != null;
                    }

                    private void selectScope() {
                        while (currentScope != null && currentScope.context.length <= objectIndex) {
                            currentScope = currentScope.parent;
                            objectIndex = 0;
                        }
                    }

                    @Override
                    public Object next() {
                        selectScope();
                        if (currentScope != null) {
                            return currentScope.context[objectIndex++];
                        }
                        throw new IllegalStateException("May only be called if there is a next element.");
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException("This iterator is read only.");
                    }
                };
            }
        };
    }

    public static <T> T call(Callable<T> callable) {
        try {
            return callable.call();
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        }
    }
}

