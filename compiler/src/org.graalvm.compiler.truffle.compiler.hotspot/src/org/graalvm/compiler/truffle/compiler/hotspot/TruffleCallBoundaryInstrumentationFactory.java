/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.hotspot;

import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompilerRuntime;

import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * A service for creating a specialized {@link CompilationResultBuilder} used to inject code into
 * the beginning of a {@linkplain HotSpotTruffleCompilerRuntime#getTruffleCallBoundaryMethods() call
 * boundary method}. The injected code tests the {@code entryPoint} field of the
 * {@code installedCode} field of the receiver and tail calls it if it is non-zero:
 *
 * <pre>
 * long ep = this.installedCode.entryPoint;
 * // post-volatile-read barrier
 * if (ep != null) {
 *     tailcall(ep);
 * }
 * // normal compiled code
 * </pre>
 */
public abstract class TruffleCallBoundaryInstrumentationFactory {

    public abstract static class TruffleCompilationResultBuilderFactory implements CompilationResultBuilderFactory {
        protected MetaAccessProvider metaAccess;
        protected GraalHotSpotVMConfig config;
        protected HotSpotRegistersProvider registers;

        public TruffleCompilationResultBuilderFactory(MetaAccessProvider metaAccess, GraalHotSpotVMConfig config, HotSpotRegistersProvider registers) {
            this.metaAccess = metaAccess;
            this.config = config;
            this.registers = registers;
        }
    }

    public abstract CompilationResultBuilderFactory create(MetaAccessProvider metaAccess, GraalHotSpotVMConfig config, HotSpotRegistersProvider registers);

    /**
     * Gets the architecture supported by this factory.
     */
    public abstract String getArchitecture();
}
