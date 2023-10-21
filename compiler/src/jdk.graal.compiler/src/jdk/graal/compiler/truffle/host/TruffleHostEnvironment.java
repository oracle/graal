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
package jdk.graal.compiler.truffle.host;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.truffle.TruffleCompilerConfiguration;
import jdk.graal.compiler.truffle.TruffleCompilerImpl;

import com.oracle.truffle.compiler.HostMethodInfo;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilerRuntime;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This class manages Truffle resources used during host Java compilation. A host environment is
 * only available if the Truffle runtime was fully initialized. In practice this means that several
 * Truffle classes are needed to be initialized before Truffle can be considered initialized. In
 * addition, any {@link #get(ResolvedJavaType) lookup} of the host environment needs to be qualified
 * using a {@link ResolvedJavaType}. This allows to qualify which Truffle classes should be used if
 * multiple Truffle runtimes are initialized.
 * <p>
 * This class is intended to be used during host compilation only. Access to the Truffle runtime is
 * provided during guest compilation with {@link TruffleCompilerConfiguration#runtime()} instead.
 * The Truffle host environment may be used for host compilation in four scenarios:
 * <ul>
 * <li>Truffle on HotSpot without libgraal: The Truffle runtime object is registered at
 * initialization and used directly as a Java object. The object is only registered for runtimes
 * that do guest compilation. For other runtimes any lookup will return <code>null</code>.
 * <li>Truffle on HotSpot with libgraal: The runtime object is registered in a global data structure
 * such that the Truffle runtime instance is available for all libgraal isolates using a global JNI
 * weak reference.
 * <li>Truffle on SubstrateVM with enabled guest compilation (TruffleFeature): Here the runtime
 * object is already available at image build time and we can provide it as a singleton.
 * <li>Truffle on SubstrateVM without enable guest compilation (only TruffleBaseFeature): There the
 * environment is always <code>null</code>.
 * <ul>
 * Note that currently Truffle is limited to support one optimized runtime at a time per Java heap.
 * If a class loader decides to load multiple runtime instances we fail at Truffle initialization
 * time. This restriction may be lifted in the future.
 * <p>
 * More details on host compilation can be found here: <a href=
 * "https://github.com/oracle/graal/blob/master/truffle/docs/HostCompilation.md">HostCompilation.md</a>
 */
public abstract class TruffleHostEnvironment {

    protected static final int HOST_METHOD_CACHE_SIZE = 4096;

    private static Lookup lookup = GraalServices.loadSingle(Lookup.class, true);
    private final TruffleCompilerRuntime runtime;
    private final TruffleKnownHostTypes types;

    private volatile TruffleCompilerImpl compiler;

    public TruffleHostEnvironment(TruffleCompilerRuntime runtime, MetaAccessProvider metaAccess) {
        this.runtime = runtime;
        this.types = new TruffleKnownHostTypes(runtime, metaAccess);
    }

    /**
     * Used to override the lookup that should be used from now on. Used by libgraal and
     * native-image compilation to support specific lookup semantics.
     */
    public static void overrideLookup(Lookup l) {
        TruffleHostEnvironment.lookup = l;
    }

    public abstract HostMethodInfo getHostMethodInfo(ResolvedJavaMethod method);

    public final TruffleCompilerRuntime runtime() {
        return runtime;
    }

    public final TruffleKnownHostTypes types() {
        return types;
    }

    /**
     * Returns a Truffle compiler instance usable during host compilation. This may be useful to
     * inline compiled Truffle ASTs during hosted compilation.
     *
     * @throws UnsupportedOperationException if the truffle compiler cannot be obtained (e.g. on
     *             SVM)
     */
    public final TruffleCompilerImpl getTruffleCompiler(TruffleCompilable compilable) throws UnsupportedOperationException {
        TruffleCompilerImpl c = compiler;
        if (c == null) {
            c = initializeCompiler(compilable);
        }
        return c;
    }

    private synchronized TruffleCompilerImpl initializeCompiler(TruffleCompilable compilable) {
        if (this.compiler != null) {
            return compiler;
        }
        compiler = createCompiler(compilable);
        return compiler;
    }

    protected abstract TruffleCompilerImpl createCompiler(TruffleCompilable compilable);

    /**
     * Looks up a Truffle host environment relative to a Java method. This method forwards to
     * {@link #get(ResolvedJavaType)} using the method's
     * {@link ResolvedJavaMethod#getDeclaringClass() declaring class}.
     *
     * @see #get(ResolvedJavaType)
     */
    public static TruffleHostEnvironment get(ResolvedJavaMethod relativeTo) {
        return get(relativeTo.getDeclaringClass());
    }

    /**
     * Looks up a Truffle host environment relative to a Java type. The class provided must have
     * access to the Truffle runtime, otherwise <code>null</code> is returned. The runtime will only
     * be returned if the runtime was initialized.
     * <p>
     * The provided type must be return {@link JavaKind#Object} for
     * {@link ResolvedJavaType#getJavaKind()}.
     */
    public static TruffleHostEnvironment get(ResolvedJavaType relativeTo) {
        GraalError.guarantee(lookup != null, "Lookup not installed.");
        GraalError.guarantee(relativeTo.getJavaKind() == JavaKind.Object, "Must be object kind. Primitive types are not allowed.");
        return lookup.lookup(relativeTo);
    }

    public interface Lookup {

        TruffleHostEnvironment lookup(ResolvedJavaType type);

    }

}
