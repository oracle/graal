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
package com.oracle.truffle.polyglot;

import java.util.Collection;
import java.util.Collections;

import org.graalvm.options.OptionDescriptors;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.impl.Accessor;

class VMAccessor extends Accessor {

    static final VMAccessor SPI = new VMAccessor();

    static final Nodes NODES = SPI.nodes();
    static final SourceSupport SOURCE = SPI.sourceSupport();
    static final InstrumentSupport INSTRUMENT = SPI.instrumentSupport();
    static final LanguageSupport LANGUAGE = SPI.languageSupport();

    static EngineSupport engine() {
        return SPI.engineSupport();
    }

    static Collection<ClassLoader> allLoaders() {
        return TruffleOptions.AOT ? Collections.emptyList() : SPI.loaders();
    }

    @Override
    protected void initializeNativeImageTruffleLocator() {
        super.initializeNativeImageTruffleLocator();
    }

    @Override
    protected OptionDescriptors getCompilerOptions() {
        return super.getCompilerOptions();
    }

    @Override
    protected EngineSupport engineSupport() {
        return new PolyglotImpl.EngineImpl();
    }

    @Override
    protected void initializeProfile(CallTarget target, Class<?>[] argmentTypes) {
        super.initializeProfile(target, argmentTypes);
    }

    @Override
    protected Object callProfiled(CallTarget target, Object... args) {
        return super.callProfiled(target, args);
    }

    @Override
    protected boolean isGuestCallStackElement(StackTraceElement element) {
        return super.isGuestCallStackElement(element);
    }

}
