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
package com.oracle.graal.debug.internal;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.debug.*;

public final class DebugScope {

    private static ThreadLocal<DebugScope> instanceTL = new ThreadLocal<>();
    private static ThreadLocal<DebugConfig> configTL = new ThreadLocal<>();
    private static ThreadLocal<Throwable> lastExceptionThrownTL = new ThreadLocal<>();
    private static ThreadLocal<DebugScope> lastLogScope = new ThreadLocal<>();
    private static DebugTimer scopeTime = Debug.timer("ScopeTime");

    private final DebugScope parent;

    private Object[] context;

    private DebugValueMap valueMap;
    private String qualifiedName;

    private static final char SCOPE_SEP = '.';

    private boolean logEnabled;
    private boolean meterEnabled;
    private boolean timeEnabled;
    private boolean dumpEnabled;

    private PrintStream output;

    public static DebugScope getInstance() {
        DebugScope result = instanceTL.get();
        if (result == null) {
            DebugScope topLevelDebugScope = new DebugScope(Thread.currentThread().getName(), "", null);
            instanceTL.set(topLevelDebugScope);
            DebugValueMap.registerTopLevel(topLevelDebugScope.getValueMap());
            return topLevelDebugScope;
        } else {
            return result;
        }
    }

    public static DebugConfig getConfig() {
        return configTL.get();
    }

    private DebugScope(String name, String qualifiedName, DebugScope parent, Object... context) {
        this.parent = parent;
        this.context = context;
        this.qualifiedName = qualifiedName;
        assert context != null;

        if (parent != null) {
            for (DebugValueMap child : parent.getValueMap().getChildren()) {
                if (child.getName().equals(name)) {
                    this.valueMap = child;
                    return;
                }
            }
            this.valueMap = new DebugValueMap(name);
            parent.getValueMap().addChild(this.valueMap);
        } else {
            this.valueMap = new DebugValueMap(name);
        }
    }

    public boolean isDumpEnabled() {
        return dumpEnabled;
    }

    public boolean isLogEnabled() {
        return logEnabled;
    }

    public boolean isMeterEnabled() {
        return meterEnabled;
    }

    public boolean isTimeEnabled() {
        return timeEnabled;
    }

    public void log(String msg, Object... args) {
        if (isLogEnabled()) {
            if (lastLogScope.get() == null || !lastLogScope.get().qualifiedName.equals(this.qualifiedName)) {
                output.println("scope: " + qualifiedName);
                lastLogScope.set(this);
            }
            output.println(String.format(msg, args));
        }
    }

    public void printf(String msg, Object... args) {
        if (isLogEnabled()) {
            if (lastLogScope.get() == null || !lastLogScope.get().qualifiedName.equals(this.qualifiedName)) {
                output.println("scope: " + qualifiedName);
                lastLogScope.set(this);
            }
            output.printf(msg, args);
        }
    }

    public void dump(Object object, String formatString, Object[] args) {
        if (isDumpEnabled()) {
            DebugConfig config = getConfig();
            if (config != null) {
                String message = String.format(formatString, args);
                for (DebugDumpHandler dumpHandler : config.dumpHandlers()) {
                    dumpHandler.dump(object, message);
                }
            }
        }
    }

    /**
     * This method exists mainly to allow a debugger (e.g., Eclipse) to force dump a graph.
     */
    public static void dump(Object object, String message) {
        DebugConfig config = getConfig();
        if (config != null) {
            for (DebugDumpHandler dumpHandler : config.dumpHandlers()) {
                dumpHandler.dump(object, message);
            }
        }
    }

    /**
     * Runs a task in a new debug scope which is either a child of the current scope or a disjoint
     * top level scope.
     * 
     * @param newName the name of the new scope
     * @param runnable the task to run (must be null iff {@code callable} is not null)
     * @param callable the task to run (must be null iff {@code runnable} is not null)
     * @param sandbox specifies if the scope is a child of the current scope or a top level scope
     * @param sandboxConfig the config to use of a new top level scope (ignored if
     *            {@code sandbox == false})
     * @param newContext context objects of the new scope
     * @return the value returned by the task
     */
    public <T> T scope(String newName, Runnable runnable, Callable<T> callable, boolean sandbox, DebugConfig sandboxConfig, Object[] newContext) {
        DebugScope oldContext = getInstance();
        DebugConfig oldConfig = getConfig();
        DebugScope newChild = null;
        if (sandbox) {
            newChild = new DebugScope(newName, newName, null, newContext);
            setConfig(sandboxConfig);
        } else {
            newChild = oldContext.createChild(newName, newContext);
        }
        instanceTL.set(newChild);
        newChild.updateFlags();
        try (TimerCloseable a = scopeTime.start()) {
            return executeScope(runnable, callable);
        } finally {
            newChild.context = null;
            instanceTL.set(oldContext);
            setConfig(oldConfig);
        }
    }

    private <T> T executeScope(Runnable runnable, Callable<T> callable) {

        try {
            if (runnable != null) {
                runnable.run();
            }
            if (callable != null) {
                return call(callable);
            }
        } catch (Throwable e) {
            if (e == lastExceptionThrownTL.get()) {
                throw e;
            } else {
                RuntimeException newException = interceptException(e);
                if (newException == null) {
                    lastExceptionThrownTL.set(e);
                    throw e;
                } else {
                    lastExceptionThrownTL.set(newException);
                    throw newException;
                }
            }
        }
        return null;
    }

    private void updateFlags() {
        DebugConfig config = getConfig();
        if (config == null) {
            logEnabled = false;
            meterEnabled = false;
            timeEnabled = false;
            dumpEnabled = false;
            output = null;
        } else {
            logEnabled = config.isLogEnabled();
            meterEnabled = config.isMeterEnabled();
            timeEnabled = config.isTimeEnabled();
            dumpEnabled = config.isDumpEnabled();
            output = config.output();
        }
    }

    private RuntimeException interceptException(final Throwable e) {
        final DebugConfig config = getConfig();
        if (config != null) {
            return scope("InterceptException", null, new Callable<RuntimeException>() {

                @Override
                public RuntimeException call() throws Exception {
                    try {
                        return config.interceptException(e);
                    } catch (Throwable t) {
                        return new RuntimeException("Exception while intercepting exception", t);
                    }
                }
            }, false, null, new Object[]{e});
        }
        return null;
    }

    private DebugValueMap getValueMap() {
        return valueMap;
    }

    long getCurrentValue(int index) {
        return getValueMap().getCurrentValue(index);
    }

    void setCurrentValue(int index, long l) {
        getValueMap().setCurrentValue(index, l);
    }

    private DebugScope createChild(String newName, Object[] newContext) {
        String newQualifiedName = newName;
        if (this.qualifiedName.length() > 0) {
            newQualifiedName = this.qualifiedName + SCOPE_SEP + newName;
        }
        DebugScope result = new DebugScope(newName, newQualifiedName, this, newContext);
        return result;
    }

    public Iterable<Object> getCurrentContext() {
        final DebugScope scope = this;
        return new Iterable<Object>() {

            @Override
            public Iterator<Object> iterator() {
                return new Iterator<Object>() {

                    DebugScope currentScope = scope;
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

    public void setConfig(DebugConfig newConfig) {
        configTL.set(newConfig);
        updateFlags();
    }

    public String getQualifiedName() {
        return qualifiedName;
    }
}
