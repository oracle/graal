package com.oracle.svm.hosted.analysis.ai.util;

import com.oracle.graal.pointsto.BigBang;
import jdk.graal.compiler.debug.DebugContext;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This is a singleton class that provides utility methods for working with {@link ResolvedJavaType}.
 * Fundamentally, its purpose is to act as a wrapper around {@link BigBang} to
 * avoid passing this as an argument to every method that needs it.
 */
public final class ResolvedJavaTypeUtil {

    private static ResolvedJavaTypeUtil instance;
    private final DebugContext debugContext;
    private final BigBang bb;

    private ResolvedJavaTypeUtil(DebugContext debug, BigBang bb) {
        this.debugContext = debug;
        this.bb = bb;
    }

    public static ResolvedJavaTypeUtil getInstance(DebugContext debug, BigBang bb) {
        if (instance == null) {
            instance = new ResolvedJavaTypeUtil(debug, bb);
        }
        return instance;
    }

    public static ResolvedJavaTypeUtil getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ResolvedJavaTypeUtil not initialized. Call getInstance(DebugContext, BigBang) first.");
        }
        return instance;
    }

    public DebugContext getDebugContext() {
        return debugContext;
    }

    public BigBang getBigBang() {
        return bb;
    }

    public ResolvedJavaType lookUpType(Class<?> clazz) {
        return bb.getMetaAccess().lookupJavaType(clazz);
    }
}
