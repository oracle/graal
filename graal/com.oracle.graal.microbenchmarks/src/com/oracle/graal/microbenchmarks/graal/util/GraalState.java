package com.oracle.graal.microbenchmarks.graal.util;

import jdk.internal.jvmci.meta.*;

import org.openjdk.jmh.annotations.*;

import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.runtime.*;

/**
 * Read-only, benchmark-wide state providing Graal runtime context.
 */
@State(Scope.Benchmark)
public class GraalState {

    public final Backend backend;
    public final Providers providers;
    public final MetaAccessProvider metaAccess;

    public GraalState() {
        backend = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend();
        providers = backend.getProviders();
        metaAccess = providers.getMetaAccess();
    }
}
