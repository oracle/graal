/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.debug;

import java.io.*;
import java.util.*;

public class DelegatingDebugConfig implements DebugConfig {

    protected final DebugConfig delegate;

    public DelegatingDebugConfig(DebugConfig delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean isLogEnabled() {
        return delegate.isLogEnabled();
    }

    public boolean isLogEnabledForMethod() {
        return delegate.isLogEnabledForMethod();
    }

    @Override
    public boolean isMeterEnabled() {
        return delegate.isMeterEnabled();
    }

    @Override
    public boolean isDumpEnabled() {
        return delegate.isDumpEnabled();
    }

    public boolean isDumpEnabledForMethod() {
        return delegate.isDumpEnabledForMethod();
    }

    @Override
    public boolean isTimeEnabled() {
        return delegate.isTimeEnabled();
    }

    @Override
    public RuntimeException interceptException(Throwable e) {
        return delegate.interceptException(e);
    }

    @Override
    public Collection<DebugDumpHandler> dumpHandlers() {
        return delegate.dumpHandlers();
    }

    @Override
    public PrintStream output() {
        return delegate.output();
    }

    @Override
    public void addToContext(Object o) {
        delegate.addToContext(o);
    }

    @Override
    public void removeFromContext(Object o) {
        delegate.removeFromContext(o);
    }
}
