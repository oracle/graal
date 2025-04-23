/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.hotspot;

import jdk.graal.compiler.core.ArchitectureSpecific;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.meta.HotSpotRegistersProvider;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.asm.EntryPointDecorator;
import jdk.graal.compiler.serviceprovider.LibGraalService;
import jdk.graal.compiler.truffle.TruffleCompilerConfiguration;

import com.oracle.truffle.compiler.TruffleCompilable;

/**
 * A service for creating a specialized {@link CompilationResultBuilder} used to inject code into
 * the beginning of a
 * {@linkplain HotSpotTruffleCompilerImpl#installTruffleCallBoundaryMethod(jdk.vm.ci.meta.ResolvedJavaMethod, TruffleCompilable)
 * call boundary method}. The injected code tests the {@code entryPoint} field of the
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
@LibGraalService
public abstract class TruffleCallBoundaryInstrumentationFactory implements ArchitectureSpecific {

    public abstract EntryPointDecorator create(TruffleCompilerConfiguration compilerConfig, GraalHotSpotVMConfig config, HotSpotRegistersProvider registers);

    @Override
    public abstract String getArchitecture();
}
