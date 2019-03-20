/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.hotspot;

import static org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions.TraceTruffleStackTraceLimit;
import static org.graalvm.compiler.truffle.runtime.SharedTruffleRuntimeOptions.TraceTruffleTransferToInterpreter;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompiler;
import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompilerRuntime;
import org.graalvm.compiler.truffle.runtime.BackgroundCompileQueue;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.OptimizedOSRLoopNode;
import org.graalvm.compiler.truffle.runtime.TruffleCallBoundary;
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.runtime.JVMCI;
import sun.misc.Unsafe;

/**
 * HotSpot specific implementation of a Graal-enabled Truffle runtime.
 *
 * This class contains functionality for interfacing with a HotSpot-based Truffle compiler,
 * independent of where the compiler resides (i.e., co-located in the HotSpot heap or running in a
 * native-image shared library).
 */
public abstract class AbstractHotSpotTruffleRuntime extends GraalTruffleRuntime implements HotSpotTruffleCompilerRuntime {
    private static final sun.misc.Unsafe UNSAFE;

    static {
        UNSAFE = getUnsafe();
    }

    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e) {
        }
        try {
            Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeInstance.setAccessible(true);
            return (Unsafe) theUnsafeInstance.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
        }
    }

    /**
     * Contains lazily computed data such as the compilation queue and helper for stack
     * introspection.
     */
    static class Lazy extends BackgroundCompileQueue {
        StackIntrospection stackIntrospection;

        Lazy(AbstractHotSpotTruffleRuntime runtime) {
            runtime.installDefaultListeners();
        }
    }

    private Boolean traceTransferToInterpreter;

    public AbstractHotSpotTruffleRuntime() {
        super(Arrays.asList(HotSpotOptimizedCallTarget.class));
        setDontInlineCallBoundaryMethod();
    }

    private volatile Lazy lazy;
    private volatile String lazyConfigurationName;

    private Lazy lazy() {
        if (lazy == null) {
            synchronized (this) {
                if (lazy == null) {
                    lazy = new Lazy(this);
                }
            }
        }
        return lazy;
    }

    private List<ResolvedJavaMethod> truffleCallBoundaryMethods;

    @Override
    public synchronized Iterable<ResolvedJavaMethod> getTruffleCallBoundaryMethods() {
        if (truffleCallBoundaryMethods == null) {
            truffleCallBoundaryMethods = new ArrayList<>();
            MetaAccessProvider metaAccess = getMetaAccess();
            ResolvedJavaType type = metaAccess.lookupJavaType(OptimizedCallTarget.class);
            for (ResolvedJavaMethod method : type.getDeclaredMethods()) {
                if (method.getAnnotation(TruffleCallBoundary.class) != null) {
                    truffleCallBoundaryMethods.add(method);
                }
            }
        }
        return truffleCallBoundaryMethods;
    }

    @Override
    protected StackIntrospection getStackIntrospection() {
        Lazy l = lazy();
        if (l.stackIntrospection == null) {
            l.stackIntrospection = HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getStackIntrospection();
        }
        return l.stackIntrospection;
    }

    @Override
    public HotSpotTruffleCompiler getTruffleCompiler() {
        if (truffleCompiler == null) {
            initializeTruffleCompiler();
        }
        return (HotSpotTruffleCompiler) truffleCompiler;
    }

    protected boolean reportedTruffleCompilerInitializationFailure;

    private void initializeTruffleCompiler() {
        synchronized (this) {
            // might occur for multiple compiler threads at the same time.
            if (truffleCompiler == null) {
                try {
                    truffleCompiler = newTruffleCompiler();
                } catch (Throwable e) {
                    if (!reportedTruffleCompilerInitializationFailure) {
                        // This should never happen so report it (once)
                        reportedTruffleCompilerInitializationFailure = true;
                        log(printStackTraceToString(e));
                    }
                }
            }
        }
    }

    @Override
    public OptimizedCallTarget createOptimizedCallTarget(OptimizedCallTarget source, RootNode rootNode) {
        return new HotSpotOptimizedCallTarget(source, rootNode);
    }

    @Override
    public void onCodeInstallation(CompilableTruffleAST compilable, InstalledCode installedCode) {
        HotSpotOptimizedCallTarget callTarget = (HotSpotOptimizedCallTarget) compilable;
        callTarget.setInstalledCode(installedCode);
    }

    /**
     * Creates a log that {@linkplain HotSpotSpeculationLog#managesFailedSpeculations() manages} a
     * native failed speculations list. An important invariant is that an nmethod compiled with this
     * log can never be executing once the log object dies. When the log object dies, it frees the
     * failed speculations list thus invalidating the
     * {@linkplain HotSpotSpeculationLog#getFailedSpeculationsAddress() failed speculations address}
     * embedded in the nmethod. If the nmethod were to execute after this point and fail a
     * speculation, it would append the failed speculation to the already freed list.
     * <p>
     * Truffle ensures this cannot happen as it only attaches managed speculation logs to
     * {@link OptimizedCallTarget}s and {@link OptimizedOSRLoopNode}s. Executions of nmethods
     * compiled for an {@link OptimizedCallTarget} or {@link OptimizedOSRLoopNode} object will have
     * a strong reference to the object (i.e., as the receiver). This guarantees that such an
     * nmethod cannot be executing after the object has died.
     */
    @Override
    public SpeculationLog createSpeculationLog() {
        return new HotSpotSpeculationLog();
    }

    /**
     * Prevents C1 or C2 from inlining a call to a method annotated by {@link TruffleCallBoundary}
     * so that we never miss the chance to switch from the Truffle interpreter to compiled code.
     *
     * @see HotSpotTruffleCompiler#installTruffleCallBoundaryMethods()
     */
    public static void setDontInlineCallBoundaryMethod() {
        MetaAccessProvider metaAccess = getMetaAccess();
        ResolvedJavaType type = metaAccess.lookupJavaType(OptimizedCallTarget.class);
        for (ResolvedJavaMethod method : type.getDeclaredMethods()) {
            if (method.getAnnotation(TruffleCallBoundary.class) != null) {
                setNotInlinableOrCompilable(method);
            }
        }
    }

    static MetaAccessProvider getMetaAccess() {
        return JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess();
    }

    /**
     * Informs the VM to never compile or inline {@code method}.
     */
    private static void setNotInlinableOrCompilable(ResolvedJavaMethod method) {
        // JDK-8180487 and JDK-8186478 introduced breaking API changes so reflection is required.
        Method[] methods = HotSpotResolvedJavaMethod.class.getMethods();
        for (Method m : methods) {
            if (m.getName().equals("setNotInlineable") || m.getName().equals("setNotInlinableOrCompilable") || m.getName().equals("setNotInlineableOrCompileable")) {
                try {
                    m.invoke(method);
                    return;
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    throw new InternalError(e);
                }
            }
        }
        throw new InternalError(String.format(
                        "Could not find setNotInlineable, setNotInlinableOrCompilable or setNotInlineableOrCompileable in %s",
                        HotSpotResolvedJavaMethod.class));
    }

    @Override
    protected BackgroundCompileQueue getCompileQueue() {
        return lazy();
    }

    @Override
    protected String getCompilerConfigurationName() {
        TruffleCompiler compiler = truffleCompiler;
        String compilerConfig;
        if (compiler != null) {
            compilerConfig = compiler.getCompilerConfigurationName();
            // disabled GR-10618
            // assert verifyCompilerConfiguration(compilerConfig);
        } else {
            compilerConfig = getLazyCompilerConfigurationName();
        }
        return compilerConfig;
    }

    @SuppressWarnings("unused")
    private boolean verifyCompilerConfiguration(String name) {
        String lazyName = getLazyCompilerConfigurationName();
        if (!name.equals(lazyName)) {
            throw new AssertionError("Expected compiler configuration name " + name + " but was " + lazyName + ".");
        }
        return true;
    }

    private String getLazyCompilerConfigurationName() {
        String compilerConfig = this.lazyConfigurationName;
        if (compilerConfig == null) {
            synchronized (this) {
                compilerConfig = this.lazyConfigurationName;
                if (compilerConfig == null) {
                    compilerConfig = initLazyCompilerConfigurationName();
                    this.lazyConfigurationName = compilerConfig;
                }
            }
        }
        return compilerConfig;
    }

    /**
     * Gets the compiler configuration name without requiring a compiler to be created.
     */
    protected abstract String initLazyCompilerConfigurationName();

    @Override
    public boolean cancelInstalledTask(OptimizedCallTarget optimizedCallTarget, Object source, CharSequence reason) {
        if (lazy == null) {
            // if Truffle wasn't initialized yet, this is a noop
            return false;
        }

        return super.cancelInstalledTask(optimizedCallTarget, source, reason);
    }

    @SuppressWarnings("try")
    @Override
    public void bypassedInstalledCode() {
        getTruffleCompiler().installTruffleCallBoundaryMethods();
    }

    @Override
    protected CallMethods getCallMethods() {
        if (callMethods == null) {
            lookupCallMethods(getMetaAccess());
        }
        return callMethods;
    }

    @Override
    public void notifyTransferToInterpreter() {
        CompilerAsserts.neverPartOfCompilation();
        if (traceTransferToInterpreter == null) {
            this.traceTransferToInterpreter = TruffleRuntimeOptions.getValue(TraceTruffleTransferToInterpreter);
        }

        if (traceTransferToInterpreter) {
            TraceTransferToInterpreterHelper.traceTransferToInterpreter(this, this.getTruffleCompiler());
        }
    }

    @Override
    protected JavaConstant forObject(final Object object) {
        final HotSpotConstantReflectionProvider constantReflection = (HotSpotConstantReflectionProvider) HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getConstantReflection();
        return constantReflection.forObject(object);
    }

    @Override
    protected <T> T asObject(final Class<T> type, final JavaConstant constant) {
        if (constant.isNull()) {
            return null;
        }
        final HotSpotObjectConstant hsConstant = (HotSpotObjectConstant) constant;
        return hsConstant.asObject(type);
    }

    private static class TraceTransferToInterpreterHelper {
        private static final long THREAD_EETOP_OFFSET;

        static {
            try {
                THREAD_EETOP_OFFSET = UNSAFE.objectFieldOffset(Thread.class.getDeclaredField("eetop"));
            } catch (Exception e) {
                throw new InternalError(e);
            }
        }

        static void traceTransferToInterpreter(AbstractHotSpotTruffleRuntime runtime, HotSpotTruffleCompiler compiler) {
            long thread = UNSAFE.getLong(Thread.currentThread(), THREAD_EETOP_OFFSET);
            long pendingTransferToInterpreterAddress = thread + compiler.pendingTransferToInterpreterOffset();
            boolean deoptimized = UNSAFE.getByte(pendingTransferToInterpreterAddress) != 0;
            if (deoptimized) {
                logTransferToInterpreter(runtime);
                UNSAFE.putByte(pendingTransferToInterpreterAddress, (byte) 0);
            }
        }

        private static String formatStackFrame(FrameInstance frameInstance, CallTarget target) {
            StringBuilder builder = new StringBuilder();
            if (target instanceof RootCallTarget) {
                RootNode root = ((RootCallTarget) target).getRootNode();
                String name = root.getName();
                if (name == null) {
                    builder.append("unnamed-root");
                } else {
                    builder.append(name);
                }
                Node callNode = frameInstance.getCallNode();
                SourceSection sourceSection = null;
                if (callNode != null) {
                    sourceSection = callNode.getEncapsulatingSourceSection();
                }
                if (sourceSection == null) {
                    sourceSection = root.getSourceSection();
                }

                if (sourceSection == null || sourceSection.getSource() == null) {
                    builder.append("(Unknown)");
                } else {
                    builder.append("(").append(formatPath(sourceSection)).append(":").append(sourceSection.getStartLine()).append(")");
                }

                if (target instanceof OptimizedCallTarget) {
                    OptimizedCallTarget callTarget = ((OptimizedCallTarget) target);
                    if (callTarget.isValid()) {
                        builder.append(" <opt>");
                    }
                    if (callTarget.getSourceCallTarget() != null) {
                        builder.append(" <split-" + Integer.toHexString(callTarget.hashCode()) + ">");
                    }
                }

            } else {
                builder.append(target.toString());
            }
            return builder.toString();
        }

        private static String formatPath(SourceSection sourceSection) {
            if (sourceSection.getSource().getPath() != null) {
                Path path = FileSystems.getDefault().getPath(".").toAbsolutePath();
                Path filePath = FileSystems.getDefault().getPath(sourceSection.getSource().getPath()).toAbsolutePath();

                try {
                    return path.relativize(filePath).toString();
                } catch (IllegalArgumentException e) {
                    // relativization failed
                }
            }
            return sourceSection.getSource().getName();
        }

        private static void logTransferToInterpreter(final AbstractHotSpotTruffleRuntime runtime) {
            final int limit = TruffleRuntimeOptions.getValue(TraceTruffleStackTraceLimit);

            runtime.log("[truffle] transferToInterpreter at");
            runtime.iterateFrames(new FrameInstanceVisitor<Object>() {
                int frameIndex = 0;

                @Override
                public Object visitFrame(FrameInstance frameInstance) {
                    CallTarget target = frameInstance.getCallTarget();
                    StringBuilder line = new StringBuilder("  ");
                    if (frameIndex > 0) {
                        line.append("  ");
                    }
                    line.append(formatStackFrame(frameInstance, target));
                    frameIndex++;

                    runtime.log(line.toString());
                    if (frameIndex < limit) {
                        return null;
                    } else {
                        runtime.log("    ...");
                        return frameInstance;
                    }
                }

            });
            final int skip = 3;

            StackTraceElement[] stackTrace = new Throwable().getStackTrace();
            String suffix = stackTrace.length > skip + limit ? "\n    ..." : "";
            runtime.log(Arrays.stream(stackTrace).skip(skip).limit(limit).map(StackTraceElement::toString).collect(Collectors.joining("\n    ", "  ", suffix)));
        }
    }
}
