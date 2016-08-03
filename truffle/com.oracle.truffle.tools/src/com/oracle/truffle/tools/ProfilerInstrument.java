/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools;

import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

@Registration(id = ProfilerInstrument.ID)
public class ProfilerInstrument extends TruffleInstrument {
    public static final String ID = "profiler";

    private Profiler profiler;
    private Instrumenter instrumenter;

    @Override
    protected void onCreate(Env env) {
        this.instrumenter = env.getInstrumenter();
        env.registerService(this);
    }

    @Override
    protected void onDispose(Env env) {
        if (profiler != null) {
            profiler.dispose();
        }
    }

    Profiler getProfiler(Factory factory) {
        if (profiler == null && factory != null) {
            profiler = factory.create(instrumenter);
            if (profiler == null) {
                throw new NullPointerException();
            }
        }
        return profiler;
    }

    interface Factory {
        Profiler create(Instrumenter instrumenter);
    }
}
