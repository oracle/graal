/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.shell;

import org.graalvm.visualizer.filter.FilterEnvironment;
import org.graalvm.visualizer.filter.FilterEvent;
import org.graalvm.visualizer.filter.FilterExecution;
import org.graalvm.visualizer.filter.FilterListener;
import org.graalvm.visualizer.script.ScriptEnvironment;
import org.openide.util.Exceptions;
import org.openide.util.WeakListeners;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author sdedic
 */
public final class ShellSession {
    private final SharedScriptEnv environment;
    private final SL execListener;
    private final FilterListener weakL;

    public ShellSession() {
        environment = new SharedScriptEnv();
        execListener = new SL();
        FilterExecution.getExecutionService().addFilterListener(
                weakL = WeakListeners.create(FilterListener.class, execListener,
                        FilterExecution.getExecutionService()
                )
        );
    }

    private static ShellSession current;

    public static synchronized ShellSession getCurrentSession() {
        if (current == null) {
            current = new ShellSession();
        }
        return current;
    }

    private void close() throws IOException {
        getSharedEnvironment().close();
        FilterExecution.getExecutionService().removeFilterListener(weakL);
    }

    public static ShellSession recycle() {
        ShellSession c;
        ShellSession r;
        synchronized (ShellSession.class) {
            c = current;
            r = current = new ShellSession();
        }
        if (c != null) {
            try {
                c.close();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        return r;
    }

    class SL implements FilterListener {
        @Override
        public void filterStart(FilterEvent e) {
        }

        @Override
        public void filterEnd(FilterEvent e) {
            FilterEnvironment fenv = e.getExecution().getEnvironment();
        }

        @Override
        public void executionStart(FilterEvent e) {
            FilterEnvironment fenv = e.getExecution().getEnvironment();
            if (fenv.getScriptEnvironment() == getSharedEnvironment()) {
                environment.setExecutionThread(Thread.currentThread());
            }
        }

        @Override
        public void executionEnd(FilterEvent e) {
            FilterEnvironment fenv = e.getExecution().getEnvironment();
            if (fenv.getScriptEnvironment() == getSharedEnvironment()) {
                environment.setExecutionThread(null);
            }
        }
    }

    public ScriptEnvironment getSharedEnvironment() {
        return environment;
    }

    static class SharedScriptEnv extends ScriptEnvironment {
        private volatile Thread executionThread;

        public Thread getExecutionThread() {
            return executionThread;
        }

        public void setExecutionThread(Thread executionThread) {
            this.executionThread = executionThread;
        }

        private void checkThread() {
            Thread t = executionThread;
            if (t == null || t == Thread.currentThread()) {
                return;
            }
            throw new IllegalStateException("Multiple threads are not supported");
        }

        private final Map<Object, Object> vals = new HashMap<>();

        @Override
        public <T> T setValue(Object key, T val) {
            checkThread();
            return (T) vals.put(key, val);
        }

        @Override
        public <T> T getValue(Object key) {
            checkThread();
            return (T) vals.get(key);
        }

        @Override
        public Set keys() {
            checkThread();
            return vals.keySet();
        }

        @Override
        public Iterable values() {
            return vals.values();
        }
    }

}
