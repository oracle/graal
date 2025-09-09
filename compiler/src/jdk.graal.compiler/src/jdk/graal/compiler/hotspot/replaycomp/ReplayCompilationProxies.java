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

import java.io.Serial;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GlobalMetrics;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.hotspot.CompilationContext;
import jdk.graal.compiler.hotspot.HotSpotGraalServices;
import jdk.graal.compiler.hotspot.Platform;
import jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.MetaAccessProvider;

//JaCoCo Exclude

/**
 * A holder and factory for compiler-interface proxies during a replayed compilation.
 * <p>
 * This class creates proxies for the classes registered in {@link CompilerInterfaceDeclarations}.
 * The behavior of proxy method invocation is also specified in
 * {@link CompilerInterfaceDeclarations}. In general, the proxies usually try to produce the same
 * results as those from the recorded compilation.
 * <p>
 * The recorded operations (from a JSON file produced during the recorded compilation) are loaded
 * using {@link #loadOperationResults}. This class creates a flat representation of the data to keep
 * memory usage low, which is important when replaying many compilations on libgraal and
 * benchmarking compile time.
 * <p>
 * It is necessary to call {@link #findLocalMirrors} after the operations are loaded and JVMCI
 * providers are created. The local mirrors may be required during a replayed compilation to query
 * information from the local VM, which is needed for snippet parsing.
 *
 * @see ReplayCompilationSupport
 */
@LibGraalSupport.HostedOnly(unlessTrue = ReplayCompilationSupport.ENABLE_REPLAY_LAUNCHER_PROP)
public class ReplayCompilationProxies implements CompilationProxies {
    public static class Options {
        // @formatter:off
        @Option(help = "Fail when there is no recorded result for a JVMCI operation during compilation replay.", type = OptionType.Debug)
        public static final OptionKey<Boolean> ReplayDivergenceIsFailure = new OptionKey<>(false);
        // @formatter:on
    }

    /**
     * Metadata for a proxy object.
     */
    private static final class ProxyInfo {
        /**
         * The {@link #operationResults} index where the recorded operations for this proxy begin.
         */
        private int resultsBegin;

        /**
         * The {@link #operationResults} index where the recorded operations for this proxy end.
         */
        private int resultsEnd;

        /**
         * A local mirror identified for this proxy or {@code null}.
         */
        private Object localMirror;

        private ProxyInfo() {
        }

        public void setLocalMirror(Object newLocalMirror) {
            localMirror = newLocalMirror;
        }
    }

    /**
     * Declares which objects require a proxy (registered objects) and defines the behavior of
     * method invocation.
     */
    private final CompilerInterfaceDeclarations declarations;

    /**
     * A flattened representation of the recorded operations and their results. This representation
     * aims to save memory when replaying a large number of compilation units as a benchmark.
     * <p>
     * The recorded operations for a given proxy are stored as a subarray from
     * {@link ProxyInfo#resultsBegin} to {@link ProxyInfo#resultsEnd}. The subarray is formed by
     * multiple runs, each run corresponding to a particular symbolic method. The first element in
     * every run is the corresponding method.
     * <ul>
     * <li>If the method is parameterless, the next (and last element) of the run is the return
     * value.</li>
     * <li>If the method has {@code p > 0} parameters, the next element is the number of records
     * {@code r > 0}. This is followed by {@code r} records. Every record contains {@code p}
     * argument elements and a single return value.</li>
     * </ul>
     */
    private Object[] operationResults;

    /**
     * A map of proxies created for singleton compiler-interface classes (as defined by
     * {@link #declarations}).
     */
    private final EconomicMap<Class<?>, CompilationProxy> singletonProxies;

    /**
     * A map of compiler-interface singletons.
     */
    private final EconomicMap<Class<?>, Object> singletonObjects;

    /**
     * The host meta access provider.
     */
    private MetaAccessProvider hostMetaAccess;

    /**
     * Proxifies and unproxifies composite objects.
     */
    private final CompilationProxyMapper proxyMapper;

    /**
     * Maps metadata to all created proxies.
     */
    private final EconomicMap<CompilationProxy, ProxyInfo> createdProxies;

    /**
     * Maps local objects to created proxies.
     */
    private final EconomicMap<Object, CompilationProxy> localMirrorToProxy;

    /**
     * The platform of the target system.
     */
    private Platform targetPlatform;

    /**
     * The global metrics where the metrics from the current {@link #debug} context should be
     * accumulated.
     */
    private final GlobalMetrics globalMetrics;

    /**
     * The debug information for the current context or {@code null}.
     */
    private DebugContext debug;

    /**
     * {@code true} if failing to find a recorded result should throw an exception.
     */
    private final boolean divergenceIsFailure;

    @SuppressWarnings("this-escape")
    public ReplayCompilationProxies(CompilerInterfaceDeclarations declarations, GlobalMetrics globalMetrics, OptionValues options) {
        this.declarations = declarations;
        this.singletonProxies = EconomicMap.create();
        this.singletonObjects = EconomicMap.create();
        this.proxyMapper = new CompilationProxyMapper(declarations, this::proxify);
        this.createdProxies = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        this.localMirrorToProxy = EconomicMap.create();
        this.globalMetrics = globalMetrics;
        this.divergenceIsFailure = Options.ReplayDivergenceIsFailure.getValue(options);
    }

    /**
     * Sets the target platform for replay compilation.
     *
     * @param platform the target platform
     */
    public void setTargetPlatform(Platform platform) {
        targetPlatform = platform;
    }

    @Override
    public Platform targetPlatform() {
        return targetPlatform;
    }

    @Override
    public DebugCloseable withDebugContext(DebugContext debugContext) {
        debug = debugContext;
        return () -> {
            globalMetrics.add(debug);
            debug = null;
        };
    }

    @Override
    public DebugCloseable enterCompilationContext() {
        // The handles created during replay are cached across compilations.
        CompilationContext context = HotSpotGraalServices.enterGlobalCompilationContext();
        if (context == null) {
            return DebugCloseable.VOID_CLOSEABLE;
        } else {
            return context::close;
        }
    }

    /**
     * Loads the recorded operations from a collection.
     *
     * @param operations the collection of recorded operations
     * @param internPool the pool of interned objects
     */
    public void loadOperationResults(Collection<OperationRecorder.RecordedOperation> operations, EconomicMap<Object, Object> internPool) {
        /*
         * Maps a proxy to another map, which will form the subarray of the recorded operations for
         * the given proxy.
         */
        EconomicMap<CompilationProxy, EconomicMap<CompilationProxy.SymbolicMethod, List<Object>>> results = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        int length = 0; // the length of operationResults
        for (OperationRecorder.RecordedOperation operation : operations) {
            CompilationProxy proxy = (CompilationProxy) operation.receiver();
            /*
             * Maps a method to the flattened records for the recorded invocations of the method
             * using this proxy as a receiver.
             */
            EconomicMap<CompilationProxy.SymbolicMethod, List<Object>> proxyMap = results.get(proxy);
            if (proxyMap == null) {
                proxyMap = EconomicMap.create();
                results.put(proxy, proxyMap);
            }
            // The flattened records for this proxy.
            List<Object> list = proxyMap.get(operation.method());
            if (list == null) {
                list = new ArrayList<>();
                proxyMap.put(operation.method(), list);
                ++length;
                if (operation.method().hasParams()) {
                    ++length;
                }
            }
            // Add a record for the given proxy and a method.
            if (operation.method().hasParams()) {
                // The record starts with the argument values.
                for (Object arg : operation.args()) {
                    list.add(arg);
                    ++length;
                }
            } else if (!list.isEmpty()) {
                // Redundant recorded operation, skipping.
                continue;
            }
            // Finish the record by adding the return value.
            list.add(operation.resultOrMarker());
            ++length;
        }
        operationResults = new Object[length];
        int index = 0;
        var proxyCursor = createdProxies.getEntries();
        while (proxyCursor.advance()) {
            // Begin the subarray with recorded operations for the given proxy.
            CompilationProxy proxy = proxyCursor.getKey();
            ProxyInfo proxyInfo = proxyCursor.getValue();
            proxyInfo.resultsBegin = index;
            var proxyMap = results.get(proxy);
            if (proxyMap != null) {
                var proxyMapCursor = proxyMap.getEntries();
                while (proxyMapCursor.advance()) {
                    CompilationProxy.SymbolicMethod method = proxyMapCursor.getKey();
                    List<Object> list = proxyMapCursor.getValue();
                    // Store the method at the beginning of a run.
                    operationResults[index++] = intern(method, internPool);
                    if (method.hasParams()) {
                        // For a method with parameters, store the number of records.
                        int recordCount = list.size() / (method.paramCount() + 1);
                        // Intern the boxed integer.
                        operationResults[index++] = intern(recordCount, internPool);
                    } else {
                        GraalError.guarantee(list.size() == 1, "records for parameterless methods contain only the return value");
                    }
                    // Write the records (or just the return value if the method is parameterless).
                    for (Object object : list) {
                        operationResults[index++] = intern(object, internPool);
                    }
                }
            }
            proxyInfo.resultsEnd = index;
        }
    }

    /**
     * Interns the given object in the provided intern pool.
     *
     * @param object the object to be interned
     * @param internPool the map used to store interned objects
     * @return the interned object
     */
    private static Object intern(Object object, EconomicMap<Object, Object> internPool) {
        if (object == null) {
            return null;
        }
        if (object instanceof CompilationProxy || object instanceof Object[] || object instanceof TargetDescription) {
            return object;
        }
        Object interned = internPool.get(object);
        if (interned == null) {
            interned = object;
            internPool.put(interned, interned);
        }
        return interned;
    }

    /**
     * Finds local mirrors for all created proxies.
     * <p>
     * A local mirror of a proxy is a JVMCI object obtained from the host VM that is logically
     * equivalent to the proxy. For example, for a given type proxy, we can find the local type with
     * the same name. Local mirrors can be used to query information about the object if the
     * information was not recorded in the JSON file. This is particularly useful for snippet
     * parsing, which is intentionally not recorded.
     * <p>
     * During snippet parsing, the compiler may discover a JVMCI object which has no matching proxy.
     * For such objects, local-only proxies are created using {@link #proxify(Object)}.
     *
     * @param jvmciRuntime the JVMCI runtime
     */
    public void findLocalMirrors(HotSpotJVMCIRuntime jvmciRuntime) {
        HotSpotConstantReflectionProvider constantReflection = (HotSpotConstantReflectionProvider) singletonObjects.get(HotSpotConstantReflectionProvider.class);
        var cursor = createdProxies.getEntries();
        while (cursor.advance()) {
            CompilationProxy proxy = cursor.getKey();
            CompilerInterfaceDeclarations.Registration registration = declarations.findRegistrationForInstance(proxy);
            if (registration.mirrorLocator() != null) {
                Object localMirror = registration.mirrorLocator().findLocalMirror(proxy, hostMetaAccess, constantReflection, jvmciRuntime);
                if (localMirror != null) {
                    Object previousProxy = localMirrorToProxy.put(localMirror, proxy);
                    GraalError.guarantee(previousProxy == null, "there must be at most one proxy instance for an object");
                    ProxyInfo proxyInfo = cursor.getValue();
                    proxyInfo.setLocalMirror(localMirror);
                    registerLocalMirrorsForConstantResults(proxyInfo);
                }
            }
        }
    }

    /**
     * Registers the local mirrors for constant results of a given proxy object.
     * <p>
     * This method finds whether the object has any getters that returns a proxy representing a
     * constant. If so, it invokes the getters on the local mirror of the proxy object to obtain the
     * local mirror of the constant proxy.
     *
     * @param proxyInfo metadata for the proxy object
     */
    private void registerLocalMirrorsForConstantResults(ProxyInfo proxyInfo) {
        for (int i = proxyInfo.resultsBegin; i < proxyInfo.resultsEnd;) {
            CompilationProxy.SymbolicMethod method = (CompilationProxy.SymbolicMethod) operationResults[i++];
            if (method.hasParams()) {
                int entries = (Integer) operationResults[i++];
                i += (method.paramCount() + 1) * entries;
            } else {
                Object result = operationResults[i++];
                if (!(result instanceof Constant)) {
                    continue;
                }
                Object receiver = proxyInfo.localMirror;
                CompilationProxy.InvokableMethod invokable = declarations.findRegistrationForInstance(receiver).findInvokableMethod(method);
                if (invokable == null) {
                    continue;
                }
                Object localMirror;
                try {
                    localMirror = invokable.invoke(receiver, new Object[0]);
                } catch (Exception ignored) {
                    continue;
                }
                createdProxies.get((CompilationProxy) result).setLocalMirror(localMirror);
            }
        }
    }

    @Override
    public CompilationProxy proxify(Object input) {
        if (input instanceof CompilationProxy compilationProxy) {
            return compilationProxy;
        }
        CompilationProxy proxy = localMirrorToProxy.get(input);
        if (proxy != null) {
            return proxy;
        }
        CompilerInterfaceDeclarations.Registration registration = declarations.findRegistrationForInstance(input);
        GraalError.guarantee(registration != null, "the input must be an instance of a registered class");
        proxy = createProxy(registration);
        ProxyInfo proxyInfo = createdProxies.get(proxy);
        proxyInfo.setLocalMirror(input);
        localMirrorToProxy.put(input, proxy);
        if (registration.singleton()) {
            singletonObjects.put(registration.clazz(), input);
            if (input instanceof MetaAccessProvider metaAccess) {
                hostMetaAccess = metaAccess;
            }
        }
        return proxy;
    }

    @Override
    public CompilerInterfaceDeclarations getDeclarations() {
        return declarations;
    }

    @Override
    public DebugCloseable enterSnippetContext() {
        return DebugCloseable.VOID_CLOSEABLE;
    }

    private static final TimerKey JVMCIReplayTime = DebugContext.timer("JVMCIReplayTime");

    private static final class NotUnproxifiableException extends RuntimeException {
        @Serial private static final long serialVersionUID = -4899571461334839074L;
    }

    /**
     * Creates a new proxy object for a specific registered compiler-interface class.
     *
     * @param registration a registration for a compiler-interface class
     * @return a new proxy object
     */
    @SuppressWarnings("try")
    public CompilationProxy createProxy(CompilerInterfaceDeclarations.Registration registration) {
        if (registration.singleton()) {
            CompilationProxy found = singletonProxies.get(registration.clazz());
            if (found != null) {
                return found;
            }
        }
        ProxyInfo proxyInfo = new ProxyInfo();
        CompilationProxy instance = CompilationProxy.newProxyInstance(registration.clazz(), (proxy, method, callback, args) -> {
            try (DebugCloseable ignored = (debug == null) ? null : JVMCIReplayTime.start(debug)) {
                if (method.equals(CompilationProxyBase.unproxifyMethod)) {
                    Object localMirror = proxyInfo.localMirror;
                    if (localMirror == null) {
                        throw new NotUnproxifiableException();
                    }
                    return localMirror;
                } else if (method.equals(CompilationProxyBase.equalsMethod)) {
                    return proxy == args[0];
                } else if (method.equals(CompilationProxyBase.hashCodeMethod)) {
                    return System.identityHashCode(proxy);
                }
                Object input;
                if (registration.singleton()) {
                    input = singletonObjects.get(registration.clazz());
                } else {
                    input = null;
                }
                CompilerInterfaceDeclarations.MethodStrategy strategy = registration.findStrategy(method);
                if (strategy == CompilerInterfaceDeclarations.MethodStrategy.Passthrough) {
                    if (proxyInfo.localMirror == null) {
                        CompilerInterfaceDeclarations.OperationResultSupplier handler = registration.findFallbackHandler(method);
                        if (handler != null) {
                            return proxyMapper.proxifyRecursive(handler.apply(proxy, method, args, hostMetaAccess));
                        }
                    }
                    GraalError.guarantee(proxyInfo.localMirror != null, "a proxy with passthrough strategy must have a local mirror or fallback handler");
                    return callback.invoke(proxyInfo.localMirror, args);
                } else if (strategy == CompilerInterfaceDeclarations.MethodStrategy.DefaultValue) {
                    return proxyMapper.proxifyRecursive(registration.findDefaultValue(proxy, method, args, hostMetaAccess));
                }
                Object result = findResult(proxyInfo, method, args);
                if (result != SpecialResultMarker.NO_RESULT_MARKER) {
                    if (result instanceof SpecialResultMarker marker) {
                        // We proxify the result due to objects with delayed deserialization.
                        return proxyMapper.proxifyRecursive(marker.materialize());
                    } else {
                        return proxyMapper.proxifyRecursive(result);
                    }
                }
                try {
                    Object[] unproxifiedArgs = (Object[]) proxyMapper.unproxifyRecursive(args);
                    if (registration.singleton()) {
                        return proxyMapper.proxifyRecursive(callback.invoke(input, unproxifiedArgs));
                    }
                    Object localMirror = proxyInfo.localMirror;
                    if (localMirror != null) {
                        return proxyMapper.proxifyRecursive(callback.invoke(localMirror, unproxifiedArgs));
                    }
                } catch (NotUnproxifiableException ignored2) {
                    // Cannot unproxify the arguments: continue.
                } catch (InvocationTargetException exception) {
                    // The result of the invocation is a thrown exception.
                    throw exception.getTargetException();
                }
                CompilerInterfaceDeclarations.OperationResultSupplier handler = registration.findFallbackHandler(method);
                if (handler != null) {
                    return proxyMapper.proxifyRecursive(handler.apply(proxy, method, args, hostMetaAccess));
                }
                if (args != null) {
                    for (Object arg : args) {
                        if (arg instanceof CompilationProxy unproxifiable) {
                            GraalError.guarantee(createdProxies.containsKey(unproxifiable), "a proxy argument was not created by this instance");
                        }
                    }
                }
                if (divergenceIsFailure) {
                    failOnDivergence(proxy, proxyInfo, method, args);
                }
                return proxyMapper.proxifyRecursive(registration.findDefaultValue(proxy, method, args, hostMetaAccess));
            }
        });
        if (registration.singleton()) {
            singletonProxies.put(registration.clazz(), instance);
        }
        createdProxies.put(instance, proxyInfo);
        return instance;
    }

    /**
     * Finds the recorded result for a given method invocation on a proxy object.
     * <p>
     * This method iterates over the recorded operations for the proxy object and checks if the
     * method and arguments match the recorded operation. If a match is found, it returns the
     * recorded result. If no match is found, it returns
     * {@link SpecialResultMarker#NO_RESULT_MARKER}.
     *
     * @param proxyInfo metadata for the proxy object
     * @param method the symbolic method being invoked
     * @param args the arguments passed to the method invocation
     * @return the recorded result or {@link SpecialResultMarker#NO_RESULT_MARKER} if not found
     */
    private Object findResult(ProxyInfo proxyInfo, CompilationProxy.SymbolicMethod method, Object[] args) {
        for (int i = proxyInfo.resultsBegin; i < proxyInfo.resultsEnd;) {
            CompilationProxy.SymbolicMethod resultMethod = (CompilationProxy.SymbolicMethod) operationResults[i++];
            if (resultMethod.hasParams()) {
                int entries = (Integer) operationResults[i++];
                if (!method.equals(resultMethod)) {
                    i += (resultMethod.paramCount() + 1) * entries;
                    continue;
                }
                entry: for (int j = 0; j < entries; j++) {
                    for (int k = 0; k < method.paramCount(); k++) {
                        Object arg = operationResults[i++];
                        if (!Objects.equals(args[k], arg)) {
                            i += method.paramCount() - k;
                            continue entry;
                        }
                    }
                    return operationResults[i];
                }
            } else {
                if (!method.equals(resultMethod)) {
                    ++i;
                    continue;
                }
                return operationResults[i];
            }
            return SpecialResultMarker.NO_RESULT_MARKER;
        }
        return SpecialResultMarker.NO_RESULT_MARKER;
    }

    /**
     * Throws an exception when a recorded result is not found for a given method invocation during
     * replay compilation.
     *
     * @param proxy the proxy object on which the method was invoked
     * @param proxyInfo metadata for the proxy object
     * @param method the symbolic method being invoked
     * @param args the arguments passed to the method invocation
     * @throws GraalError always thrown to indicate the divergence
     */
    private void failOnDivergence(Object proxy, ProxyInfo proxyInfo, CompilationProxy.SymbolicMethod method, Object[] args) {
        StringBuilder sb = new StringBuilder();
        sb.append("No result for ").append(method).append('[').append(Arrays.toString(args)).append(']').append(System.lineSeparator());
        formatRecordedOperations(sb, proxyInfo, method);
        if (!method.equals(CompilationProxyBase.toStringMethod)) {
            sb.append("Receiver: ").append(proxy).append(System.lineSeparator());
        }
        sb.append("Local mirror: ").append(proxyInfo.localMirror).append(System.lineSeparator());
        throw new GraalError(sb.toString());
    }

    /**
     * Formats the recorded operations for a given proxy object and method into a string builder.
     * <p>
     * This method iterates over the recorded operations for the proxy object and appends the
     * available keys for the given method to the string builder.
     *
     * @param sb the string builder to append the formatted recorded operations to
     * @param proxyInfo metadata for the proxy object
     * @param method the symbolic method being invoked
     */
    private void formatRecordedOperations(StringBuilder sb, ProxyInfo proxyInfo, CompilationProxy.SymbolicMethod method) {
        for (int i = proxyInfo.resultsBegin; i < proxyInfo.resultsEnd;) {
            CompilationProxy.SymbolicMethod resultMethod = (CompilationProxy.SymbolicMethod) operationResults[i++];
            if (resultMethod.hasParams()) {
                int entries = (Integer) operationResults[i++];
                if (!method.equals(resultMethod)) {
                    i += (resultMethod.paramCount() + 1) * entries;
                    continue;
                }
                for (int j = 0; j < entries; j++) {
                    Object[] args = new Object[method.paramCount()];
                    for (int k = 0; k < method.paramCount(); k++) {
                        args[k] = operationResults[i++];
                    }
                    i++;
                    sb.append("    Available key: ").append(method).append('[').append(Arrays.toString(args)).append(']').append(System.lineSeparator());
                }
            } else {
                ++i;
            }
            return;
        }
    }
}
