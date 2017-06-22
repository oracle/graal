/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

import java.util.Collection;
import java.util.Collections;

import org.graalvm.options.OptionDescriptors;

import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.impl.Accessor;

class VMAccessor extends Accessor {

    static VMAccessor SPI;

    static Nodes NODES;
    static InstrumentSupport INSTRUMENT;
    static JavaInteropSupport JAVAINTEROP;
    static LanguageSupport LANGUAGE;

    private static volatile EngineSupport engineSupport;

    static EngineSupport engine() {
        return SPI.engineSupport();
    }

    static InstrumentSupport instrumentAccess() {
        return SPI.instrumentSupport();
    }

    Collection<ClassLoader> allLoaders() {
        return TruffleOptions.AOT ? Collections.emptyList() : loaders();
    }

    @Override
    protected OptionDescriptors getCompilerOptions() {
        return super.getCompilerOptions();
    }

    @Override
    protected EngineSupport engineSupport() {
        return engineSupport;
    }

    static void initialize(EngineSupport support) {
        engineSupport = support;
        SPI = new VMAccessor();
        NODES = SPI.nodes();
        INSTRUMENT = SPI.instrumentSupport();
        JAVAINTEROP = SPI.javaInteropSupport();
        LANGUAGE = SPI.languageSupport();
    }

    @Override
    protected boolean isGuestCallStackElement(StackTraceElement element) {
        return super.isGuestCallStackElement(element);
    }

}
