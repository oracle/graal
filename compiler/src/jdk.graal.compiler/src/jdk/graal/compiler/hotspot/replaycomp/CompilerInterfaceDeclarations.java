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

import static java.util.FormattableFlags.ALTERNATE;
import static jdk.graal.compiler.bytecode.Bytecodes.INVOKEDYNAMIC;
import static jdk.graal.compiler.bytecode.Bytecodes.INVOKEINTERFACE;
import static jdk.graal.compiler.bytecode.Bytecodes.INVOKESPECIAL;
import static jdk.graal.compiler.bytecode.Bytecodes.INVOKESTATIC;
import static jdk.graal.compiler.bytecode.Bytecodes.INVOKEVIRTUAL;
import static jdk.graal.compiler.core.common.NativeImageSupport.inRuntimeCode;
import static jdk.graal.compiler.hotspot.HotSpotReplacementsImpl.isGraalClass;
import static jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxy.wrapInvocationExceptions;
import static jdk.graal.compiler.java.StableMethodNameFormatter.isMethodHandle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Formattable;
import java.util.Formatter;
import java.util.List;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.UnmodifiableEconomicMap;

import jdk.graal.compiler.bytecode.BytecodeStream;
import jdk.graal.compiler.core.common.CompilerProfiler;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.meta.HotSpotGraalConstantFieldProvider;
import jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxyBase;
import jdk.graal.compiler.hotspot.replaycomp.proxy.CompilerProfilerProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.ConstantPoolProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.HotSpotCodeCacheProviderProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.HotSpotConstantProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.HotSpotConstantReflectionProviderProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.HotSpotMetaspaceConstantProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.HotSpotObjectConstantProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.HotSpotProfilingInfoProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.HotSpotResolvedJavaFieldProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.HotSpotResolvedJavaMethodProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.HotSpotResolvedJavaTypeProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.HotSpotResolvedObjectTypeProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.MetaAccessProviderProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.ProfilingInfoProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.SignatureProxy;
import jdk.graal.compiler.hotspot.replaycomp.proxy.SpeculationLogProxy;
import jdk.graal.compiler.java.LambdaUtils;
import jdk.graal.compiler.options.ExcludeFromJacocoGeneratedReport;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotMemoryAccessProvider;
import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotProfilingInfo;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.hotspot.HotSpotResolvedPrimitiveType;
import jdk.vm.ci.hotspot.HotSpotRuntimeStub;
import jdk.vm.ci.hotspot.HotSpotSignature;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Defines how the VM's interface to the compiler should be recorded and replayed.
 *
 * <p>
 * Recording and replaying is implemented by instrumenting JVMCI. For relevant JVMCI objects and
 * service providers ({@link HotSpotResolvedJavaMethod}, {@link MetaAccessProvider}...), we create
 * proxy implementations ({@link HotSpotResolvedJavaMethodProxy}, {@link MetaAccessProvider}...).
 * These proxies do not implement any specific behavior; {@link CompilerInterfaceDeclarations}
 * declares this behavior. {@link RecordingCompilationProxies} and {@link ReplayCompilationProxies}
 * then provide proxy instance with the declared behavior during recording and replay, respectively.
 *
 * <p>
 * <b>Registered Classes.</b> This class defines for which JVMCI object we must create proxies
 * during recording and replay. Whether an object is an instance of such a class can be queried
 * using {@link #isRegisteredClassInstance} and the registration can be obtained with
 * {@link #findRegistrationForInstance}. The {@link Registration} describes the behavior of the
 * individual methods during replay and recording, how local mirrors are located, and other
 * information.
 *
 * <p>
 * <b>Method Registrations.</b> A {@link MethodRegistration} describes the behavior of a proxy
 * method invocation during recording and replay and also provides the arguments of method calls
 * whose results should always be recorded even if the compiler does not invoke the method during
 * the recorded compilation. The results of these operations may be needed during replay, e.g., to
 * produce some diagnostic output. A default registration
 * ({@link MethodRegistrationBuilder#createDefault}) is used if this class does not explicitly
 * override it. {@link MethodStrategy} defines the behavior of a method during recording and replay.
 *
 * <p>
 * <b>Recording.</b> During recording, the proxies ({@link RecordingCompilationProxies}) either save
 * the result of a method invocation or not. The result of a method invocation (an operation) is
 * either a return value or an exception. The result is saved iff the strategy is
 * {@link MethodStrategy#RecordReplay}. The recording proxies also ensure the result of an operation
 * is stable during a single compilation by caching the result when the operation is performed for
 * the first time. Subsequent proxy method invocations reuse the recorded result.
 * <p>
 * At the end of a compilation, we also perform and record additional calls whose results could be
 * needed during replay. These calls are declared in this class using {@link MethodCallToRecord}
 * instances or a {@link MethodCallsToRecordProvider}.
 *
 * <p>
 * <b>Replay.</b> During a replayed compilation, there are multiple ways how to obtain a result for
 * an operation invoked on a proxy object. The options are the following:
 * <ul>
 * <li>Look up a result in the recorded JSON file using the values of the argument and the
 * receiver.</li>
 * <li>Invoke the same method on a local mirror (may also replace the arguments with local
 * mirrors).</li>
 * <li>Return a predefined default value (e.g., {@code null}, {@code 0}, {@code false}) or a default
 * value computed by an instance of {@link OperationResultSupplier}.</li>
 * <li>Delegate to a fallback handler, which is also an instance of
 * {@link OperationResultSupplier}.</li>
 * <li>Ad-hoc special handling (for {@link Object#equals}, {@link Object#hashCode}).</li>
 * </ul>
 * In many conditions, only some of these options are available (there may be no recorded result, no
 * local mirror, no fallback handler...). A {@link MethodStrategy} defines which of these options
 * should be tried in what order and also which conditions are considered errors.
 */
public final class CompilerInterfaceDeclarations {
    /**
     * The strategy for replaying/recording a method. These strategies are implemented in
     * {@link RecordingCompilationProxies} and {@link ReplayCompilationProxies} for recording and
     * replay, respectively.
     */
    public enum MethodStrategy {
        /**
         * A strategy that records the method's results and replays them. This is the default
         * strategy for most methods.
         * <p>
         * During recording, the strategy records the result of a method the first time it is
         * invoked. If the compiler invokes the method with the same arguments again, the recorded
         * result is returned instead of re-executing the method. This ensures a set of arguments
         * can be unambiguously mapped to a result.
         * <p>
         * During replay, the strategy looks up and returns the result from the recorded JSON file.
         * <ul>
         * <li>If no appropriate recorded result is found, falls back to invoking the same method on
         * local mirrors (replacing both the receiver and arguments with local mirrors).</li>
         * <li>If a local mirror cannot be identified, invokes the fallback handler.</li>
         * <li>If there is no fallback handler, returns the default value. This is considered an
         * error if {@link ReplayCompilationProxies.Options#ReplayDivergenceIsFailure} is set.</li>
         * </ul>
         */
        RecordReplay,

        /**
         * A strategy that does not record the method's results and invokes the local mirror instead
         * (without unproxifying the arguments).
         * <p>
         * During recording, the results of method calls are not recorded since they will not be
         * needed during replay.
         * <p>
         * During replay, the strategy invokes the same method on the local mirror without
         * unproxifying the arguments. Invokes the fallback handler if there is no local mirror.
         */
        Passthrough,

        /**
         * A strategy that does not record the method's results and returns a pre-defined or
         * computed value during replay.
         * <p>
         * During recording, the results of method calls are not recorded since they will not be
         * needed during replay.
         * <p>
         * During replay, the strategy returns the value computed by
         * {@link Registration#findDefaultValue}.
         */
        DefaultValue,

        /**
         * A strategy that does not record the method's results and is handled specially during
         * replay.
         */
        Special
    }

    /**
     * Supplies the result of an operation during replay.
     */
    @FunctionalInterface
    public interface OperationResultSupplier {
        /**
         * Computes the result of the invoked method.
         *
         * @param proxy the proxy receiver
         * @param method the invoked method
         * @param args the arguments to the method
         * @param metaAccess the host meta accces
         * @return the result of the operation
         */
        Object apply(Object proxy, CompilationProxy.SymbolicMethod method, Object[] args, MetaAccessProvider metaAccess);
    }

    /**
     * Finds the local mirror of a proxy object during replay.
     */
    @FunctionalInterface
    public interface LocalMirrorLocator {
        /**
         * Finds and returns the local mirror of the given proxy object or {@code null} if not
         * found.
         *
         * @param proxy the proxy object
         * @param metaAccess the unprofixied host meta access
         * @param constantReflection the unproxified host constant reflection
         * @param jvmciRuntime the JVMCI runtime
         * @return the local mirror or {@code null}
         */
        Object findLocalMirror(Object proxy, MetaAccessProvider metaAccess, HotSpotConstantReflectionProvider constantReflection, HotSpotJVMCIRuntime jvmciRuntime);
    }

    /**
     * Provides a list of method calls that should be recorded for a given receiver object.
     */
    @FunctionalInterface
    public interface MethodCallsToRecordProvider {
        /**
         * Returns a list of method calls that should be recorded in the serialized compilation unit
         * for the given receiver object. Every such call should be performed and recorded at the
         * end of the compilation unless it was already recorded during the compilation.
         *
         * @param receiver the receiver object
         * @return a list of method calls that should be recorded
         */
        List<MethodCallToRecord> getMethodCallsToRecord(Object receiver);
    }

    /**
     * Describes the recording and replay behavior for a method of one of the registered classes.
     *
     * @param methodStrategy the strategy to record/replay the method's results
     * @param defaultValueSupplier a supplier for a default result value or {@code null}
     * @param defaultValue the default result value
     * @param callsToRecordArguments a list of argument arrays for which the method results should
     *            be recorded in the serialized compilation unit
     * @param fallbackInvocationHandler a handler to use as a fallback when there is no recorded
     *            result or {@code null}
     * @param invokableMethod a lambda to invoke the method (needed if there are calls to record) or
     *            {@code null}
     */
    public record MethodRegistration(MethodStrategy methodStrategy, OperationResultSupplier defaultValueSupplier, Object defaultValue,
                    List<Object[]> callsToRecordArguments, OperationResultSupplier fallbackInvocationHandler, CompilationProxy.InvokableMethod invokableMethod) {
        public MethodRegistration {
            if (!callsToRecordArguments.isEmpty()) {
                GraalError.guarantee(invokableMethod != null, "invokable method needed because there are calls that must be recorded");
            }
        }
    }

    /**
     * Describes a method call that should be recorded in the serialized compilation unit.
     *
     * @param receiver the receiver object of the call
     * @param symbolicMethod the method to invoke
     * @param invokableMethod a lambda to invoke the method
     * @param args the arguments to use for the method call (can be {@code null} for methods without
     *            parameters)
     */
    public record MethodCallToRecord(Object receiver, CompilationProxy.SymbolicMethod symbolicMethod, CompilationProxy.InvokableMethod invokableMethod, Object[] args) {
        public MethodCallToRecord {
            int argsLength = (args == null) ? 0 : args.length;
            GraalError.guarantee(argsLength == symbolicMethod.paramCount(), "the provided number of arguments does not match the method signature");
        }
    }

    /**
     * Describes the recording and replay behavior of a compiler-interface class.
     *
     * @param clazz the class
     * @param singleton {@code true} iff the class should be treated as a singleton (e.g., a
     *            provider)
     * @param mirrorLocator a method that can find the local mirror of a proxy during replay or
     *            {@code null}
     * @param methods the recording/replay behavior the methods - only needed for non-default
     *            behavior
     * @param extraInterfaces additional interfaces the proxy should implement
     * @param methodCallsToRecordProvider provides the methods calls that should be recorded in the
     *            serialized compilation unit
     */
    public record Registration(Class<?> clazz, boolean singleton, LocalMirrorLocator mirrorLocator,
                    UnmodifiableEconomicMap<CompilationProxy.SymbolicMethod, MethodRegistration> methods, Class<?>[] extraInterfaces,
                    MethodCallsToRecordProvider methodCallsToRecordProvider) {
        /**
         * Finds the recording/replay strategy for a method of this class.
         *
         * @param method the method
         * @return the strategy
         */
        public MethodStrategy findStrategy(CompilationProxy.SymbolicMethod method) {
            MethodRegistration found = methods.get(method);
            if (found == null) {
                return MethodRegistrationBuilder.defaultStrategy(method);
            } else {
                return found.methodStrategy;
            }
        }

        /**
         * Finds the default value to return for a given call.
         *
         * @param proxy the proxy receiver object
         * @param method the invoked method
         * @param args the arguments passed to the invocation
         * @param metaAccess the host meta access
         * @return the default value
         */
        public Object findDefaultValue(Object proxy, CompilationProxy.SymbolicMethod method, Object[] args, MetaAccessProvider metaAccess) {
            MethodRegistration found = methods.get(method);
            if (found == null) {
                return null;
            } else if (found.defaultValueSupplier != null) {
                return found.defaultValueSupplier.apply(proxy, method, args, metaAccess);
            } else {
                return found.defaultValue;
            }
        }

        /**
         * Finds a fallbacks handler for an invoked method.
         *
         * @param method the invoked method
         * @return a handler or {@code null}
         */
        public OperationResultSupplier findFallbackHandler(CompilationProxy.SymbolicMethod method) {
            MethodRegistration found = methods.get(method);
            if (found == null) {
                return null;
            } else {
                return found.fallbackInvocationHandler;
            }
        }

        /**
         * Gets a list of method calls that should be recorded in the serialized compilation unit.
         *
         * @param receiver the receiver object
         * @return a list of method calls that should be recorded
         */
        public List<MethodCallToRecord> getMethodCallsToRecord(Object receiver) {
            List<MethodCallToRecord> calls = new ArrayList<>();
            var cursor = methods.getEntries();
            while (cursor.advance()) {
                for (Object[] arguments : cursor.getValue().callsToRecordArguments()) {
                    calls.add(new MethodCallToRecord(receiver, cursor.getKey(), cursor.getValue().invokableMethod(), arguments));
                }
            }
            if (methodCallsToRecordProvider != null) {
                calls.addAll(methodCallsToRecordProvider.getMethodCallsToRecord(receiver));
            }
            return calls;
        }

        /**
         * Finds an invokable method for the given symbolic method.
         *
         * @param method the symbolic method
         * @return the invokable method or {@code null} if not found
         */
        public CompilationProxy.InvokableMethod findInvokableMethod(CompilationProxy.SymbolicMethod method) {
            MethodRegistration found = methods.get(method);
            if (found == null) {
                return null;
            }
            return found.invokableMethod;
        }
    }

    private static final class MethodRegistrationBuilder {
        private MethodStrategy methodStrategy;
        private OperationResultSupplier defaultValueSupplier;
        private Object defaultValue;
        private final List<Object[]> callsToRecordArguments;
        private OperationResultSupplier fallbackInvocationHandler;
        private CompilationProxy.InvokableMethod invokableMethod;

        private MethodRegistrationBuilder(MethodStrategy methodStrategy, CompilationProxy.InvokableMethod invokableMethod, List<Object[]> callsToRecordArguments) {
            this.methodStrategy = methodStrategy;
            this.invokableMethod = invokableMethod;
            this.callsToRecordArguments = callsToRecordArguments;
        }

        public static MethodRegistrationBuilder createDefault(CompilationProxy.SymbolicMethod method) {
            CompilationProxy.InvokableMethod invokableMethod = null;
            List<Object[]> callsToRecordArguments = new ArrayList<>();
            if (CompilationProxyBase.toStringMethod.equals(method)) {
                invokableMethod = (receiver, args) -> receiver.toString();
                callsToRecordArguments.add(null);
            }
            return new MethodRegistrationBuilder(defaultStrategy(method), invokableMethod, callsToRecordArguments);
        }

        public static MethodStrategy defaultStrategy(CompilationProxy.SymbolicMethod method) {
            if (CompilationProxyBase.hashCodeMethod.equals(method) || CompilationProxyBase.equalsMethod.equals(method) || CompilationProxyBase.toStringMethod.equals(method)) {
                return MethodStrategy.Special;
            } else {
                return MethodStrategy.RecordReplay;
            }
        }

        private MethodRegistration build() {
            return new MethodRegistration(methodStrategy, defaultValueSupplier, defaultValue, Collections.unmodifiableList(callsToRecordArguments),
                            fallbackInvocationHandler, invokableMethod);
        }
    }

    private static class RegistrationBuilder<T> {
        private final Class<T> clazz;

        private boolean singleton;

        private LocalMirrorLocator mirrorLocator;

        private final EconomicMap<CompilationProxy.SymbolicMethod, MethodRegistrationBuilder> methods;

        private final Class<?>[] extraInterfaces;

        private MethodCallsToRecordProvider methodCallsToRecordProvider;

        RegistrationBuilder(Class<T> clazz, Class<?>... extraInterfaces) {
            this.clazz = clazz;
            this.methods = EconomicMap.create();
            this.extraInterfaces = extraInterfaces;
            this.methods.put(CompilationProxyBase.toStringMethod, MethodRegistrationBuilder.createDefault(CompilationProxyBase.toStringMethod));
        }

        public RegistrationBuilder<T> setLocalMirrorLocator(LocalMirrorLocator newLocator) {
            mirrorLocator = newLocator;
            return this;
        }

        public RegistrationBuilder<T> setSingleton(boolean newSingleton) {
            singleton = newSingleton;
            return this;
        }

        private MethodRegistrationBuilder findRegistrationBuilder(CompilationProxy.SymbolicMethod symbolicMethod) {
            MethodRegistrationBuilder builder = methods.get(symbolicMethod);
            if (builder == null) {
                builder = MethodRegistrationBuilder.createDefault(symbolicMethod);
                methods.put(symbolicMethod, builder);
            }
            return builder;
        }

        public RegistrationBuilder<T> setStrategy(CompilationProxy.SymbolicMethod method, MethodStrategy strategy) {
            findRegistrationBuilder(method).methodStrategy = strategy;
            return this;
        }

        public RegistrationBuilder<T> setFallbackInvocationHandler(CompilationProxy.SymbolicMethod method, OperationResultSupplier handler) {
            findRegistrationBuilder(method).fallbackInvocationHandler = handler;
            return this;
        }

        public RegistrationBuilder<T> setDefaultValueStrategy(CompilationProxy.SymbolicMethod symbolicMethod, Object defaultValue) {
            MethodRegistrationBuilder builder = findRegistrationBuilder(symbolicMethod);
            builder.methodStrategy = MethodStrategy.DefaultValue;
            builder.defaultValue = defaultValue;
            return this;
        }

        public RegistrationBuilder<T> setDefaultValue(CompilationProxy.SymbolicMethod method, Object defaultValue) {
            findRegistrationBuilder(method).defaultValue = defaultValue;
            return this;
        }

        public RegistrationBuilder<T> setDefaultValueSupplier(CompilationProxy.SymbolicMethod symbolicMethod, OperationResultSupplier supplier) {
            MethodRegistrationBuilder builder = findRegistrationBuilder(symbolicMethod);
            builder.defaultValue = null;
            builder.defaultValueSupplier = supplier;
            return this;
        }

        public RegistrationBuilder<T> ensureRecorded(CompilationProxy.SymbolicMethod symbolicMethod, CompilationProxy.InvokableMethod invokableMethod, Object... arguments) {
            MethodRegistrationBuilder builder = findRegistrationBuilder(symbolicMethod);
            builder.callsToRecordArguments.add(arguments);
            builder.invokableMethod = wrapInvocationExceptions(invokableMethod);
            return this;
        }

        public RegistrationBuilder<T> provideMethodCallsToRecord(MethodCallsToRecordProvider newProvider) {
            methodCallsToRecordProvider = newProvider;
            return this;
        }

        private void register(CompilerInterfaceDeclarations declarations) {
            EconomicMap<CompilationProxy.SymbolicMethod, MethodRegistration> registrations = EconomicMap.create();
            var cursor = methods.getEntries();
            while (cursor.advance()) {
                registrations.put(cursor.getKey(), cursor.getValue().build());
            }
            declarations.addRegistration(new Registration(clazz, singleton, mirrorLocator, registrations, extraInterfaces, methodCallsToRecordProvider));
        }
    }

    /**
     * The list of the registered compiler-interface classes whose methods are recorded/replayed.
     */
    private final List<Class<?>> classes;

    /**
     * Maps the registered classes to the appropriate registrations.
     */
    private final EconomicMap<Class<?>, Registration> registrations;

    private final EconomicMap<Class<?>, Class<?>> supertypeCache;

    private static final Class<?> NOT_REGISTERED = Class.class;

    private CompilerInterfaceDeclarations() {
        classes = new ArrayList<>();
        registrations = EconomicMap.create();
        supertypeCache = EconomicMap.create(Equivalence.IDENTITY);
    }

    /**
     * Gets an iterable of the registered compiler-interface classes.
     */
    public Iterable<Registration> getRegistrations() {
        return registrations.getValues();
    }

    private void addRegistration(Registration registration) {
        classes.add(registration.clazz);
        registrations.put(registration.clazz, registration);
    }

    /**
     * Finds and returns the registered supertype of the given type if it has a registered
     * supertype.
     *
     * @param type the given type
     * @return a registered (super)type or {@code null}
     */
    public Class<?> findRegisteredSupertype(Class<?> type) {
        Class<?> cached = supertypeCache.get(type);
        if (cached != null) {
            if (cached == NOT_REGISTERED) {
                return null;
            }
            return cached;
        }
        for (Class<?> declared : classes) {
            if (declared.isAssignableFrom(type)) {
                supertypeCache.put(type, declared);
                return declared;
            }
        }
        supertypeCache.put(type, NOT_REGISTERED);
        return null;
    }

    /**
     * Returns {@code true} if the given object is an instance of a registered class.
     *
     * @param object the object
     * @return {@code true} if the given object is an instance of a registered class
     */
    public boolean isRegisteredClassInstance(Object object) {
        if (object == null) {
            return false;
        }
        return findRegisteredSupertype(object.getClass()) != null;
    }

    /**
     * Finds the registration for the type of the given object if it is an instance of a registered
     * type.
     *
     * @param object the object
     * @return the registration or {@code null}
     */
    public Registration findRegistrationForInstance(Object object) {
        if (object == null) {
            return null;
        }
        return registrations.get(findRegisteredSupertype(object.getClass()));
    }

    /**
     * Creates and returns a description of how the compiler interface should be recorded and
     * replayed.
     *
     * @implNote The {@link MethodRegistrationBuilder#defaultStrategy default strategy} is
     *           sufficient for most methods, and it does not require an explicit registration.
     */
    public static CompilerInterfaceDeclarations build() {
        // @formatter:off
        CompilerInterfaceDeclarations declarations = new CompilerInterfaceDeclarations();
        new RegistrationBuilder<>(HotSpotVMConfigAccess.class).setSingleton(true)
                .register(declarations);
        new RegistrationBuilder<>(MetaAccessProvider.class).setSingleton(true)
                .setStrategy(MetaAccessProviderProxy.encodeDeoptActionAndReasonMethod, MethodStrategy.Passthrough)
                .setStrategy(MetaAccessProviderProxy.decodeDeoptReasonMethod, MethodStrategy.Passthrough)
                .setStrategy(MetaAccessProviderProxy.decodeDeoptActionMethod, MethodStrategy.Passthrough)
                .setStrategy(MetaAccessProviderProxy.decodeDebugIdMethod, MethodStrategy.Passthrough)
                .setStrategy(MetaAccessProviderProxy.encodeSpeculationMethod, MethodStrategy.Passthrough)
                .setDefaultValue(MetaAccessProviderProxy.decodeSpeculationMethod, SpeculationLog.NO_SPECULATION)
                .setStrategy(MetaAccessProviderProxy.getArrayBaseOffsetMethod, MethodStrategy.Passthrough)
                .setStrategy(MetaAccessProviderProxy.getArrayIndexScaleMethod, MethodStrategy.Passthrough)
                .register(declarations);
        new RegistrationBuilder<>(HotSpotConstantReflectionProvider.class).setSingleton(true)
                .ensureRecorded(HotSpotConstantReflectionProviderProxy.forObjectMethod, HotSpotConstantReflectionProviderProxy.forObjectInvokable,(Object) null)
                .setStrategy(HotSpotConstantReflectionProviderProxy.asJavaClassMethod, MethodStrategy.Passthrough)
                .setStrategy(HotSpotConstantReflectionProviderProxy.asObjectHubMethod, MethodStrategy.Passthrough)
                .setDefaultValueSupplier(HotSpotConstantReflectionProviderProxy.asJavaTypeMethod,
                        (proxy, method, args, metaAccess) -> {
                            if (args[0] instanceof HotSpotMetaspaceConstant constant) {
                                return constant.asResolvedJavaType();
                            }
                            return null;
                        })
                .register(declarations);
        new RegistrationBuilder<>(MethodHandleAccessProvider.class)
                .setLocalMirrorLocator((proxy, metaAccess, constantReflection, jvmciRuntime) ->
                    constantReflection.getMethodHandleAccess())
                .register(declarations);
        new RegistrationBuilder<>(HotSpotMemoryAccessProvider.class)
                .setLocalMirrorLocator((proxy, metaAccess, constantReflection, jvmciRuntime) ->
                    constantReflection.getMemoryAccessProvider())
                .register(declarations);
        new RegistrationBuilder<>(HotSpotCodeCacheProvider.class).setSingleton(true)
                .setDefaultValueStrategy(HotSpotCodeCacheProviderProxy.installCodeMethod, null)
                .setDefaultValueSupplier(HotSpotCodeCacheProviderProxy.installCodeMethod, CompilerInterfaceDeclarations::installCodeReplacement)
                // Interpreter frame size is not tracked since the arguments are not serializable.
                .setDefaultValueStrategy(HotSpotCodeCacheProviderProxy.interpreterFrameSizeMethod, 0)
                .register(declarations);
        new RegistrationBuilder<>(CompilerProfiler.class).setSingleton(true)
                .setStrategy(CompilerProfilerProxy.getTicksMethod, MethodStrategy.Passthrough)
                .setStrategy(CompilerProfilerProxy.notifyCompilerPhaseEventMethod, MethodStrategy.Passthrough)
                .setDefaultValueStrategy(CompilerProfilerProxy.notifyCompilerInliningEventMethod, null)
                .register(declarations);
        new RegistrationBuilder<>(HotSpotResolvedObjectType.class, HotSpotResolvedJavaType.class)
                .ensureRecorded(HotSpotResolvedObjectTypeProxy.getNameMethod, HotSpotResolvedObjectTypeProxy.getNameInvokable)
                .ensureRecorded(HotSpotResolvedObjectTypeProxy.getModifiersMethod, HotSpotResolvedObjectTypeProxy.getModifiersInvokable)
                .setLocalMirrorLocator(CompilerInterfaceDeclarations::findObjectTypeMirror)
                // getComponentType() is used by the default implementation of isArray().
                .ensureRecorded(HotSpotResolvedObjectTypeProxy.getComponentTypeMethod, HotSpotResolvedObjectTypeProxy.getComponentTypeInvokable)
                .setStrategy(CompilationProxyBase.CompilationProxyAnnotatedBase.getAnnotationMethod, MethodStrategy.Passthrough)
                .setStrategy(CompilationProxyBase.CompilationProxyAnnotatedBase.getAnnotationsMethod, MethodStrategy.Passthrough)
                .setStrategy(CompilationProxyBase.CompilationProxyAnnotatedBase.getDeclaredAnnotationsMethod, MethodStrategy.Passthrough)
                .ensureRecorded(HotSpotResolvedObjectTypeProxy.getInstanceFieldsMethod,
                        HotSpotResolvedObjectTypeProxy.getInstanceFieldsInvokable, new Object[]{true}) // For snippet decoding
                .ensureRecorded(HotSpotResolvedObjectTypeProxy.getStaticFieldsMethod, HotSpotResolvedObjectTypeProxy.getStaticFieldsInvokable) // For snippet decoding
                .setDefaultValueStrategy(HotSpotResolvedObjectTypeProxy.getJavaKindMethod, JavaKind.Object)
                .setDefaultValueStrategy(HotSpotResolvedJavaTypeProxy.isPrimitiveMethod, false)
                .setDefaultValueSupplier(HotSpotResolvedObjectTypeProxy.findLeastCommonAncestorMethod, (proxy1, method1, args1, metaAccess) -> metaAccess.lookupJavaType(Object.class))
                .setDefaultValue(HotSpotResolvedObjectTypeProxy.isAssignableFromMethod, false)
                .ensureRecorded(HotSpotResolvedObjectTypeProxy.isInterfaceMethod, HotSpotResolvedObjectTypeProxy.isInterfaceInvokable)
                .ensureRecorded(HotSpotResolvedObjectTypeProxy.klassMethod, HotSpotResolvedObjectTypeProxy.klassInvokable)
                .ensureRecorded(HotSpotResolvedObjectTypeProxy.getJavaMirrorMethod, HotSpotResolvedObjectTypeProxy.getJavaMirrorInvokable)
                .setDefaultValue(HotSpotResolvedObjectTypeProxy.isInitializedMethod, false)
                .ensureRecorded(HotSpotResolvedObjectTypeProxy.isPrimaryTypeMethod, HotSpotResolvedObjectTypeProxy.isPrimaryTypeInvokable) // For InstanceOfSnippets after divergence
                .setDefaultValue(HotSpotResolvedObjectTypeProxy.superCheckOffsetMethod, 0) // For InstanceOfSnippets after divergence
                .provideMethodCallsToRecord((input) -> {
                    HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) input;
                    if (!type.isArray() && !type.isInterface()) {
                        return List.of(new MethodCallToRecord(input, HotSpotResolvedObjectTypeProxy.instanceSizeMethod, HotSpotResolvedObjectTypeProxy.instanceSizeInvokable, null));
                    }
                    return List.of();
                })
                .setFallbackInvocationHandler(HotSpotResolvedObjectTypeProxy.isInstanceMethod, CompilerInterfaceDeclarations::objectTypeIsInstanceFallback)
                .register(declarations);
        // Must come after HotSpotResolvedObjectType. Needed for HotSpotResolvedPrimitiveType.
        new RegistrationBuilder<>(HotSpotResolvedJavaType.class)
                .ensureRecorded(HotSpotResolvedJavaTypeProxy.getNameMethod, HotSpotResolvedJavaTypeProxy.getNameInvokable)
                .ensureRecorded(HotSpotResolvedJavaTypeProxy.getJavaKindMethod, HotSpotResolvedJavaTypeProxy.getJavaKindInvokable)
                .ensureRecorded(HotSpotResolvedObjectTypeProxy.getComponentTypeMethod, HotSpotResolvedObjectTypeProxy.getComponentTypeInvokable)
                .setDefaultValueStrategy(HotSpotResolvedJavaTypeProxy.isPrimitiveMethod, true)
                .setLocalMirrorLocator((proxy, metaAccess, constantReflection, jvmciRuntime) -> {
                    HotSpotResolvedJavaType type = (HotSpotResolvedJavaType) proxy;
                    return HotSpotResolvedPrimitiveType.forKind(type.getJavaKind());
                })
                .ensureRecorded(HotSpotResolvedJavaTypeProxy.getJavaMirrorMethod, HotSpotResolvedJavaTypeProxy.getJavaMirrorInvokable)
                .register(declarations);
        new RegistrationBuilder<>(ConstantPool.class).register(declarations);
        new RegistrationBuilder<>(HotSpotResolvedJavaMethod.class, Formattable.class)
                .ensureRecorded(HotSpotResolvedJavaMethodProxy.getNameMethod, HotSpotResolvedJavaMethodProxy.getNameInvokable)
                .ensureRecorded(HotSpotResolvedJavaMethodProxy.getModifiersMethod, HotSpotResolvedJavaMethodProxy.getModifiersInvokable)
                .ensureRecorded(HotSpotResolvedJavaMethodProxy.getSignatureMethod, HotSpotResolvedJavaMethodProxy.getSignatureInvokable)
                .ensureRecorded(HotSpotResolvedJavaMethodProxy.isConstructorMethod, HotSpotResolvedJavaMethodProxy.isConstructorInvokable)
                .ensureRecorded(HotSpotResolvedJavaMethodProxy.canBeStaticallyBoundMethod, HotSpotResolvedJavaMethodProxy.canBeStaticallyBoundInvokable)
                .ensureRecorded(HotSpotResolvedJavaMethodProxy.getCodeMethod, HotSpotResolvedJavaMethodProxy.getCodeInvokable)
                .setDefaultValue(HotSpotResolvedJavaMethodProxy.vtableEntryOffsetMethod, 0) // For LoadMethodNode lowering after divergence
                .setStrategy(HotSpotResolvedJavaMethodProxy.formatToMethod, MethodStrategy.DefaultValue)
                .setDefaultValueSupplier(HotSpotResolvedJavaMethodProxy.formatToMethod, (proxy, method, args, metaAccess) -> {
                    ResolvedJavaMethod receiver = (ResolvedJavaMethod) proxy;
                    Formatter formatter = (Formatter) args[0];
                    int flags = (int) args[1];
                    int width = (int) args[2];
                    String base = (flags & ALTERNATE) == ALTERNATE ? receiver.getName() : receiver.toString();
                    formatter.format(DebugContext.applyFormattingFlagsAndWidth(base, flags & ~ALTERNATE, width));
                    return null;
                })
                .ensureRecorded(HotSpotResolvedJavaMethodProxy.asStackTraceElementMethod, HotSpotResolvedJavaMethodProxy.asStackTraceElementInvokable, -1)
                .setFallbackInvocationHandler(HotSpotResolvedJavaMethodProxy.asStackTraceElementMethod, (proxy, method, args, metaAccess) -> {
                    ResolvedJavaMethod receiver = (ResolvedJavaMethod) proxy;
                    return receiver.asStackTraceElement(-1);
                })
                .setFallbackInvocationHandler(HotSpotResolvedJavaMethodProxy.getCodeSizeMethod, (proxy, method, args, metaAccess) -> {
                    byte[] code = ((ResolvedJavaMethod) proxy).getCode();
                    if (code == null) {
                        return 0;
                    } else {
                        return code.length;
                    }
                })
                .ensureRecorded(HotSpotResolvedJavaMethodProxy.getDeclaringClassMethod, HotSpotResolvedJavaMethodProxy.getDeclaringClassInvokable)
                .ensureRecorded(HotSpotResolvedJavaMethodProxy.getSignatureMethod, HotSpotResolvedJavaMethodProxy.getSignatureInvokable)
                .setLocalMirrorLocator((proxy, metaAccess, constantReflection, jvmciRuntime) -> {
                    ResolvedJavaMethod method = (ResolvedJavaMethod) proxy;
                    ResolvedJavaType holderMirror = findObjectTypeMirror(method.getDeclaringClass(), metaAccess, constantReflection, jvmciRuntime);
                    if (holderMirror == null) {
                        return null;
                    }
                    String methodName = method.getName();
                    String methodDescriptor = method.getSignature().toMethodDescriptor();
                    if (method.isConstructor()) {
                        for (ResolvedJavaMethod candidate : holderMirror.getDeclaredConstructors()) {
                            if (candidate.getSignature().toMethodDescriptor().equals(methodDescriptor)) {
                                return candidate;
                            }
                        }
                    } else {
                        for (ResolvedJavaMethod candidate : holderMirror.getDeclaredMethods()) {
                            if (candidate.getName().equals(methodName) && candidate.getSignature().toMethodDescriptor().equals(methodDescriptor)) {
                                return candidate;
                            }
                        }
                    }
                    return null;
                })
                .setStrategy(HotSpotResolvedJavaMethodProxy.getParametersMethod, MethodStrategy.Passthrough)
                .setStrategy(HotSpotResolvedJavaMethodProxy.getParameterAnnotationsMethod, MethodStrategy.Passthrough)
                .setStrategy(CompilationProxyBase.CompilationProxyAnnotatedBase.getAnnotationsMethod, MethodStrategy.Passthrough)
                .setStrategy(CompilationProxyBase.CompilationProxyAnnotatedBase.getDeclaredAnnotationsMethod, MethodStrategy.Passthrough)
                .setStrategy(CompilationProxyBase.CompilationProxyAnnotatedBase.getAnnotationMethod, MethodStrategy.Passthrough)
                .setFallbackInvocationHandler(CompilationProxyBase.CompilationProxyAnnotatedBase.getAnnotationMethod, (proxy, method, args, metaAccess) -> {
                    // The HostInliningPhase can query Truffle-related annotations during replay on jargraal. It is safe to return null.
                    return null;
                })
                .setStrategy(HotSpotResolvedJavaMethodProxy.getGenericParameterTypesMethod, MethodStrategy.Passthrough)
                .setDefaultValueStrategy(HotSpotResolvedJavaMethodProxy.hasCodeAtLevelMethod, false)
                .setDefaultValue(HotSpotResolvedJavaMethodProxy.isInVirtualMethodTableMethod, false)
                .setDefaultValue(HotSpotResolvedJavaMethodProxy.intrinsicIdMethod, 0)
                .setDefaultValue(HotSpotResolvedJavaMethodProxy.canBeInlinedMethod, false)
                .provideMethodCallsToRecord((receiver) -> {
                    // Record calls to be able to format stable lambda names during replay (using StableMethodNameFormatter).
                    HotSpotResolvedJavaMethod method = (HotSpotResolvedJavaMethod) receiver;
                    List<MethodCallToRecord> calls = new ArrayList<>();
                    if (LambdaUtils.isLambdaType(method.getDeclaringClass()) || isMethodHandle(method.getDeclaringClass())) {
                        ConstantPool constantPool = method.getConstantPool();
                        calls.add(new MethodCallToRecord(method, HotSpotResolvedJavaMethodProxy.getConstantPoolMethod, HotSpotResolvedJavaMethodProxy.getConstantPoolInvokable, null));
                        for (BytecodeStream stream = new BytecodeStream(method.getCode()); stream.currentBCI() < stream.endBCI(); stream.next()) {
                            int opcode = stream.currentBC();
                            int cpi;
                            switch (opcode) {
                                case INVOKEVIRTUAL: // fall through
                                case INVOKESPECIAL: // fall through
                                case INVOKESTATIC: // fall through
                                case INVOKEINTERFACE:
                                    cpi = stream.readCPI();
                                    calls.add(new MethodCallToRecord(constantPool, ConstantPoolProxy.lookupMethodMethod, ConstantPoolProxy.lookupMethodInvokable, new Object[]{cpi, opcode, method}));
                                    break;
                                case INVOKEDYNAMIC:
                                    cpi = stream.readCPI4();
                                    calls.add(new MethodCallToRecord(constantPool, ConstantPoolProxy.lookupMethodMethod, ConstantPoolProxy.lookupMethodInvokable, new Object[]{cpi, opcode, method}));
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                    return calls;
                })
                .register(declarations);

        new RegistrationBuilder<>(Signature.class)
                .ensureRecorded(SignatureProxy.getParameterCountMethod, SignatureProxy.getParameterCountInvokable, false)
                .ensureRecorded(SignatureProxy.getParameterCountMethod, SignatureProxy.getParameterCountInvokable, true)
                .ensureRecorded(SignatureProxy.getReturnTypeMethod, SignatureProxy.getReturnTypeInvokable, (Object) null)
                .provideMethodCallsToRecord((input) -> {
                    Signature signature = (Signature) input;
                    int count = signature.getParameterCount(false);
                    List<MethodCallToRecord> calls = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        calls.add(new MethodCallToRecord(input, SignatureProxy.getParameterTypeMethod, SignatureProxy.getParameterTypeInvokable, new Object[]{i, null}));
                    }
                    return calls;
                })
                .setLocalMirrorLocator((proxy, metaAccess, constantReflection, jvmciRuntime) -> {
                    Signature signature = (Signature) proxy;
                    return new HotSpotSignature(HotSpotJVMCIRuntime.runtime(), signature.toMethodDescriptor());
                })
                .register(declarations);
        new RegistrationBuilder<>(HotSpotResolvedJavaField.class)
                .ensureRecorded(HotSpotResolvedJavaFieldProxy.isStableMethod, HotSpotResolvedJavaFieldProxy.isStableInvokable)
                .ensureRecorded(HotSpotResolvedJavaFieldProxy.isSyntheticMethod, HotSpotResolvedJavaFieldProxy.isSyntheticInvokable)
                .ensureRecorded(HotSpotResolvedJavaFieldProxy.getOffsetMethod, HotSpotResolvedJavaFieldProxy.getOffsetInvokable)
                .ensureRecorded(HotSpotResolvedJavaFieldProxy.getDeclaringClassMethod, HotSpotResolvedJavaFieldProxy.getDeclaringClassInvokable)
                .ensureRecorded(HotSpotResolvedJavaFieldProxy.getNameMethod, HotSpotResolvedJavaFieldProxy.getNameInvokable)
                .ensureRecorded(HotSpotResolvedJavaFieldProxy.getModifiersMethod, HotSpotResolvedJavaFieldProxy.getModifiersInvokable) // For graph dumps (BinaryGraphPrinter)
                .ensureRecorded(HotSpotResolvedJavaFieldProxy.getTypeMethod, HotSpotResolvedJavaFieldProxy.getTypeInvokable) // For graph dumps (BinaryGraphPrinter)
                .setLocalMirrorLocator((proxy, metaAccess, constantReflection, jvmciRuntime) -> {
                    ResolvedJavaField field = (ResolvedJavaField) proxy;
                    ResolvedJavaType holderMirror = findObjectTypeMirror(field.getDeclaringClass(), metaAccess, constantReflection, jvmciRuntime);
                    if (holderMirror == null) {
                        return null;
                    }
                    String name = field.getName();
                    boolean isStatic = field.isStatic();
                    ResolvedJavaField[] fields = (isStatic) ? holderMirror.getStaticFields() : holderMirror.getInstanceFields(false);
                    for (ResolvedJavaField candidate : fields) {
                        if (name.equals(candidate.getName())) {
                            return candidate;
                        }
                    }
                    return null;
                })
                .register(declarations);
        new RegistrationBuilder<>(HotSpotObjectConstant.class)
                .ensureRecorded(HotSpotConstantProxy.toValueStringMethod, HotSpotConstantProxy.toValueStringInvokable) // For graph dumps (BinaryGraphPrinter)
                .ensureRecorded(HotSpotConstantProxy.isCompressibleMethod, HotSpotConstantProxy.isCompressibleInvokable)
                .ensureRecorded(HotSpotConstantProxy.isCompressedMethod, HotSpotConstantProxy.isCompressedInvokable)
                .ensureRecorded(HotSpotConstantProxy.compressMethod, HotSpotConstantProxy.compressInvokable)
                .ensureRecorded(HotSpotConstantProxy.uncompressMethod, HotSpotConstantProxy.uncompressInvokable)
                .setDefaultValueStrategy(HotSpotConstantProxy.isDefaultForKindMethod, false)
                .setDefaultValueStrategy(HotSpotObjectConstantProxy.isNullMethod, false)
                .setDefaultValueStrategy(HotSpotObjectConstantProxy.getJavaKindMethod, JavaKind.Object)
                .register(declarations);
        new RegistrationBuilder<>(HotSpotMetaspaceConstant.class)
                .ensureRecorded(HotSpotConstantProxy.toValueStringMethod, HotSpotConstantProxy.toValueStringInvokable) // For graph dumps (BinaryGraphPrinter)
                .ensureRecorded(HotSpotConstantProxy.isCompressibleMethod, HotSpotConstantProxy.isCompressibleInvokable)
                .ensureRecorded(HotSpotConstantProxy.isCompressedMethod, HotSpotConstantProxy.isCompressedInvokable)
                .ensureRecorded(HotSpotConstantProxy.compressMethod, HotSpotConstantProxy.compressInvokable)
                .ensureRecorded(HotSpotConstantProxy.uncompressMethod, HotSpotConstantProxy.uncompressInvokable)
                .setDefaultValueStrategy(HotSpotConstantProxy.isDefaultForKindMethod, false)
                .ensureRecorded(HotSpotMetaspaceConstantProxy.asResolvedJavaTypeMethod, HotSpotMetaspaceConstantProxy.asResolvedJavaTypeInvokable)
                .ensureRecorded(HotSpotMetaspaceConstantProxy.asResolvedJavaMethodMethod, HotSpotMetaspaceConstantProxy.asResolvedJavaMethodInvokable)
                .register(declarations);
        new RegistrationBuilder<>(HotSpotProfilingInfo.class)
                .setDefaultValueStrategy(ProfilingInfoProxy.setCompilerIRSizeMethod, false)
                .setDefaultValue(ProfilingInfoProxy.isMatureMethod, false)
                // For CompilationTask constructor.
                .ensureRecorded(HotSpotProfilingInfoProxy.getDecompileCountMethod, HotSpotProfilingInfoProxy.getDecompileCountInvokable)
                .setDefaultValue(HotSpotProfilingInfoProxy.getDeoptimizationCountMethod, -1)
                .register(declarations);
        new RegistrationBuilder<>(ProfilingInfo.class) // Needed for DefaultProfilingInfo, CachingProfilingInfo.
                .setDefaultValueStrategy(ProfilingInfoProxy.setCompilerIRSizeMethod, false)
                .setDefaultValue(ProfilingInfoProxy.isMatureMethod, false)
                .setDefaultValue(HotSpotProfilingInfoProxy.getDeoptimizationCountMethod, -1)
                .register(declarations);
        new RegistrationBuilder<>(SpeculationLog.class)
                .setDefaultValue(SpeculationLogProxy.maySpeculateMethod, true)
                .setDefaultValueSupplier(SpeculationLogProxy.speculateMethod, (Object proxy, CompilationProxy.SymbolicMethod method, Object[] args, MetaAccessProvider metaAccess) -> {
                    // Some callers expect to get an actual speculation rather than NO_SPECULATION.
                    SpeculationLog.SpeculationReason reason = (SpeculationLog.SpeculationReason) args[0];
                    JavaConstant id = JavaConstant.forLong(-1);
                    return new HotSpotSpeculationLog.HotSpotSpeculation(reason, id, new byte[0]);
                })
                .register(declarations);
        new RegistrationBuilder<>(Predicate.class).setSingleton(true)
                .register(declarations); // Intrinsification trust predicate.
        // @formatter:on
        return declarations;
    }

    /**
     * Finds a local mirror for an object type proxy by resolving the class name on the host JVM.
     *
     * @param proxy the object type proxy
     * @param metaAccess the host meta access
     * @param constantReflection the host constant reflection
     * @param jvmciRuntime the host JVMCI runtime
     * @return the object type mirror or {@code null} if it cannot be resolved
     */
    @SuppressWarnings("unused")
    private static HotSpotResolvedObjectType findObjectTypeMirror(Object proxy, MetaAccessProvider metaAccess, HotSpotConstantReflectionProvider constantReflection, HotSpotJVMCIRuntime jvmciRuntime) {
        HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) proxy;
        String typeName = type.getName();
        if (typeName.contains("$Proxy") || typeName.contains(".0x")) {
            // Skip dynamic proxies and hidden classes, which have unstable names.
            return null;
        }
        Class<?> accessingClass = (inRuntimeCode()) ? Object.class : CompilerInterfaceDeclarations.class;
        HotSpotResolvedObjectType accessingType = (HotSpotResolvedObjectType) metaAccess.lookupJavaType(accessingClass);
        try {
            JavaType result = jvmciRuntime.lookupType(typeName, accessingType, true);
            if (result instanceof HotSpotResolvedObjectType mirror) {
                /*
                 * Initialize the type to avoid reducing whole snippets to a deopt when replay
                 * diverges and depends on the mirrors (e.g., BoxingSnippets on libgraal).
                 */
                synchronized (CompilerInterfaceDeclarations.class) {
                    mirror.initialize();
                }
                return mirror;
            }
        } catch (LinkageError | Exception ignored) {
            // Ignore LinkageError or TranslatedException.
        }
        return null;
    }

    /**
     * Implements a fallback for {@link HotSpotResolvedObjectType#isInstance} calls performed by
     * {@link HotSpotGraalConstantFieldProvider} when replaying a libgraal compilation on jargraal.
     * <p>
     * The provider performs checks like {@code getHotSpotVMConfigType().isInstance(receiver)},
     * which use snippet types on libgraal and HotSpot types on jargraal. Replay on jargraal needs
     * to answer these queries when the receiver and argument are HotSpot proxies.
     */
    @SuppressWarnings("unused")
    @ExcludeFromJacocoGeneratedReport("related to replay of libgraal compilations on jargraal")
    private static boolean objectTypeIsInstanceFallback(Object proxy, CompilationProxy.SymbolicMethod method, Object[] args, MetaAccessProvider metaAccess) {
        HotSpotResolvedObjectType receiverType = (HotSpotResolvedObjectType) proxy;
        if (!(args[0] instanceof HotSpotObjectConstant objectConstant)) {
            return false;
        }
        HotSpotResolvedObjectType constantType = objectConstant.getType();
        if (isGraalClass(receiverType) && !isGraalClass(constantType)) {
            // Assumes that only a Graal class can subtype a Graal class.
            return false;
        }
        return receiverType.isAssignableFrom(constantType);
    }

    /**
     * Implements {@link HotSpotCodeCacheProvider#installCode} for replay compilation.
     */
    @SuppressWarnings("unused")
    private static InstalledCode installCodeReplacement(Object proxy, CompilationProxy.SymbolicMethod method, Object[] args, MetaAccessProvider metaAccess) {
        ResolvedJavaMethod installedCodeOwner = (ResolvedJavaMethod) args[0];
        CompiledCode compiledCode = (CompiledCode) args[1];
        InstalledCode installedCode = (InstalledCode) args[2];
        if (installedCodeOwner == null) {
            // This is an installation of a stub which was not compiled during recording.
            return new HotSpotRuntimeStub(compiledCode.toString()); // ExcludeFromJacocoGeneratedReport
        } else {
            // For method installations, the return value is not important.
            return installedCode;
        }
    }
}
