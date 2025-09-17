/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.hotspot.Platform;
import jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase;
import jdk.graal.compiler.util.EconomicHashSet;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * A holder and factory for compiler-interface proxies during a recorded compilation.
 * <p>
 * A proxy created by this class delegates method invocation to an original object (one obtained
 * from the host VM). The proxy may also record the result of the invocation, which can be later
 * serialized into a JSON file ({@link RecordedOperationPersistence}). The behavior of the proxy for
 * individual methods is configured in {@link CompilerInterfaceDeclarations}. All subsequent method
 * invocations with equal arguments produce the result computed initially.
 * <p>
 * The result of an operation is either a return value or a thrown exception.
 *
 * @see CompilerInterfaceDeclarations
 * @see ReplayCompilationSupport
 */
class RecordingCompilationProxies implements CompilationProxies {
    /**
     * Records the results of method invocations and also ensures the methods return stable values.
     */
    private final OperationRecorder recorder;

    /**
     * Declares which objects require a proxy (registered objects) and defines the behavior of
     * method invocation.
     */
    private final CompilerInterfaceDeclarations declarations;

    /**
     * Used to replace registered objects inside composite objects with proxies and vice versa.
     */
    private final CompilationProxyMapper proxyMapper;

    RecordingCompilationProxies(CompilerInterfaceDeclarations declarations) {
        this.recorder = new OperationRecorder();
        this.declarations = declarations;
        this.proxyMapper = new CompilationProxyMapper(declarations, this::proxify);
    }

    /**
     * Collects and returns a list of operations that should be serialized for the current
     * compilation unit. This list includes the global operations needed to initialize the compiler,
     * the operations recorded in this compilation unit, and the operations that should be recorded
     * because they could be needed during replay (e.g., to produce debug output). These operations
     * are defined in {@link CompilerInterfaceDeclarations}.
     * <p>
     * Since the method calls performed and recorded by this method can return newly discovered
     * objects that have operations to record of their own, this method uses a worklist to compute a
     * transitive closure.
     *
     * @return a list of recorded compilation
     */
    public List<OperationRecorder.RecordedOperation> collectOperationsForSerialization() {
        List<Object> worklist = new ArrayList<>();
        Set<Object> visited = new EconomicHashSet<>();
        CompilationProxyMapper worklistAdder = new CompilationProxyMapper(declarations, (input) -> {
            if (!visited.contains(input)) {
                visited.add(input);
                worklist.add(input);
            }
            return proxify(input);
        });
        // Add all referenced proxies to the worklist.
        for (OperationRecorder.RecordedOperation operation : recorder.getCurrentRecordedOperations()) {
            worklistAdder.proxifyRecursive(operation.receiver());
            worklistAdder.proxifyRecursive(operation.args());
            Object result = operation.resultOrMarker();
            if (result instanceof SpecialResultMarker.ExceptionThrownMarker thrownMarker) {
                worklistAdder.proxifyRecursive(thrownMarker.getThrown());
            } else if (!(result instanceof SpecialResultMarker)) {
                worklistAdder.proxifyRecursive(result);
            }
        }
        /*
         * Perform and record the calls that could be needed during replay, except those that were
         * already recorded during the compilation.
         */
        while (!worklist.isEmpty()) {
            Object receiver = worklist.removeLast();
            CompilerInterfaceDeclarations.Registration registration = declarations.findRegistrationForInstance(receiver);
            for (CompilerInterfaceDeclarations.MethodCallToRecord methodCall : registration.getMethodCallsToRecord(receiver)) {
                OperationRecorder.RecordedOperationKey key = new OperationRecorder.RecordedOperationKey(methodCall.receiver(), methodCall.symbolicMethod(), methodCall.args());
                if (recorder.getRecordedResultOrMarker(key) != SpecialResultMarker.NO_RESULT_MARKER) {
                    // The result of the call is already recorded.
                    continue;
                }
                try {
                    Object result = methodCall.invokableMethod().invoke(methodCall.receiver(), methodCall.args());
                    recorder.recordReturnValue(key, result);
                    // Make sure to record the method calls for the results of this call as well.
                    worklistAdder.proxifyRecursive(result);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    recorder.recordExceptionThrown(key, cause);
                    worklistAdder.proxifyRecursive(cause);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Recording failed due to an exception", e);
                }
            }
        }
        return recorder.getCurrentRecordedOperations();
    }

    @Override
    public DebugCloseable enterCompilationContext() {
        return recorder.enterCompilationContext();
    }

    @Override
    public DebugCloseable enterSnippetContext() {
        return recorder.enterIgnoredContext();
    }

    @Override
    public Platform targetPlatform() {
        return Platform.ofCurrentHost();
    }

    @Override
    public DebugCloseable withDebugContext(DebugContext debugContext) {
        return DebugCloseable.VOID_CLOSEABLE;
    }

    @Override
    public CompilationProxy proxify(Object input) {
        if (input instanceof CompilationProxy compilationProxy) {
            return compilationProxy;
        }
        CompilerInterfaceDeclarations.Registration registration = declarations.findRegistrationForInstance(input);
        if (registration == null) {
            throw new IllegalArgumentException(input + " is not an instance of a registered class.");
        }
        return CompilationProxy.newProxyInstance(registration.clazz(), (proxy, method, callback, args) -> {
            if (method.equals(CompilationProxyBase.unproxifyMethod)) {
                return input;
            }
            Object[] unproxifiedArgs = (Object[]) unproxifyRecursive(args);
            CompilerInterfaceDeclarations.MethodStrategy methodStrategy = registration.findStrategy(method);
            OperationRecorder.RecordedOperationKey key = new OperationRecorder.RecordedOperationKey(input, method, unproxifiedArgs);
            Object result;
            if (methodStrategy == CompilerInterfaceDeclarations.MethodStrategy.RecordReplay) {
                Object resultOrMarker = recorder.getRecordedResultOrMarker(key);
                if (resultOrMarker instanceof SpecialResultMarker.ExceptionThrownMarker exceptionThrownMarker) {
                    throw (Throwable) proxifyRecursive(exceptionThrownMarker.getThrown());
                } else if (resultOrMarker == SpecialResultMarker.NULL_RESULT_MARKER) {
                    return null;
                } else if (resultOrMarker != SpecialResultMarker.NO_RESULT_MARKER) {
                    return proxifyRecursive(resultOrMarker);
                }
            }
            try {
                result = callback.invoke(input, unproxifiedArgs);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (methodStrategy == CompilerInterfaceDeclarations.MethodStrategy.RecordReplay) {
                    recorder.recordExceptionThrown(key, cause);
                }
                throw (Throwable) proxifyRecursive(cause);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Recording failed due to an exception", e);
            }
            if (methodStrategy == CompilerInterfaceDeclarations.MethodStrategy.RecordReplay) {
                recorder.recordReturnValue(key, result);
            }
            return proxifyRecursive(result);
        });
    }

    @Override
    public CompilerInterfaceDeclarations getDeclarations() {
        return declarations;
    }

    private Object proxifyRecursive(Object input) {
        return proxyMapper.proxifyRecursive(input);
    }

    private Object unproxifyRecursive(Object input) {
        return proxyMapper.unproxifyRecursive(input);
    }

    private static final CompilationProxy.SymbolicMethod getProfilingInfo = new CompilationProxy.SymbolicMethod(ResolvedJavaMethod.class, "getProfilingInfo", boolean.class, boolean.class);

    /**
     * Injects profiling information for the given method.
     * <p>
     * Profiles can be injected when recording a retried compilation. All subsequent calls to
     * {@link ResolvedJavaMethod#getProfilingInfo()} when the receiver is a proxy representing the
     * given method will return the specified profiles.
     *
     * @param method the method to inject profiles into
     * @param includeNormal whether to include normal profiles
     * @param includeOSR whether to include OSR profiles
     * @param profilingInfo the profiling information to inject
     */
    public void injectProfiles(ResolvedJavaMethod method, boolean includeNormal, boolean includeOSR, ProfilingInfo profilingInfo) {
        recorder.recordReturnValue(new OperationRecorder.RecordedOperationKey(method, getProfilingInfo, new Object[]{includeNormal, includeOSR}), profilingInfo);
    }
}
