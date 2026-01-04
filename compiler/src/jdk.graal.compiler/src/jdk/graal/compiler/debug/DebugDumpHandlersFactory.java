/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.debug;

import java.util.Iterator;
import java.util.List;

import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.serviceprovider.LibGraalService;

/**
 * Factory for creating {@link DebugDumpHandler}s.
 */
@LibGraalService
public interface DebugDumpHandlersFactory {

    /**
     * Creates {@link DebugDumpHandler}s based on {@code options}.
     *
     * @param options options to control type and name of the channel
     * @return list of debug handers that have been created
     */
    List<DebugDumpHandler> createHandlers(OptionValues options);

    /**
     * Loads {@link DebugDumpHandlersFactory}s on demand via {@link GraalServices#load(Class)}.
     */
    Iterable<DebugDumpHandlersFactory> LOADER = new Iterable<>() {
        @Override
        public Iterator<DebugDumpHandlersFactory> iterator() {
            return GraalServices.load(DebugDumpHandlersFactory.class).iterator();
        }
    };
}
