/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.meta;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;
import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;
import static jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect.DESTROYS_ALL_CALLER_SAVE_REGISTERS;
import static jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect.KILLS_NO_REGISTERS;
import static jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.LEAF_NO_VZERO;
import static jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.SAFEPOINT;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.MARK_WORD_LOCATION;
import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.JavaCall;
import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.JavaCallee;
import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.NativeCall;
import static org.graalvm.word.LocationIdentity.any;

import java.util.function.BiConsumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect;
import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkageImpl;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition;
import jdk.graal.compiler.hotspot.replaycomp.ReplayCompilationSupport;
import jdk.graal.compiler.hotspot.stubs.ForeignCallStub;
import jdk.graal.compiler.hotspot.stubs.InvokeJavaMethodStub;
import jdk.graal.compiler.hotspot.stubs.Stub;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.word.Word;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * HotSpot implementation of {@link HotSpotForeignCallsProvider}.
 */
public abstract class HotSpotForeignCallsProviderImpl implements HotSpotForeignCallsProvider {

    public static final LocationIdentity[] NO_LOCATIONS = {};

    public static final HotSpotForeignCallDescriptor OSR_MIGRATION_END = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, NO_LOCATIONS, "OSR_migration_end", void.class, long.class);
    public static final HotSpotForeignCallDescriptor IDENTITY_HASHCODE = new HotSpotForeignCallDescriptor(SAFEPOINT, HAS_SIDE_EFFECT, MARK_WORD_LOCATION, "identity_hashcode", int.class,
                    Object.class);
    public static final HotSpotForeignCallDescriptor VERIFY_OOP = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NO_SIDE_EFFECT, NO_LOCATIONS, "verify_oop", Object.class,
                    Object.class);
    public static final HotSpotForeignCallDescriptor LOAD_AND_CLEAR_EXCEPTION = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NO_SIDE_EFFECT, any(), "load_and_clear_exception", Object.class,
                    Word.class);

    public static final HotSpotForeignCallDescriptor TEST_DEOPTIMIZE_CALL_INT = new HotSpotForeignCallDescriptor(SAFEPOINT, NO_SIDE_EFFECT, any(), "test_deoptimize_call_int", int.class, int.class);

    protected final HotSpotJVMCIRuntime jvmciRuntime;
    protected final HotSpotGraalRuntimeProvider runtime;

    protected final EconomicMap<ForeignCallSignature, HotSpotForeignCallLinkage> foreignCalls = EconomicMap.create();
    protected final EconomicMap<ForeignCallSignature, HotSpotForeignCallDescriptor> signatureMap = EconomicMap.create();
    protected final MetaAccessProvider metaAccess;
    protected final CodeCacheProvider codeCache;
    protected final WordTypes wordTypes;

    public HotSpotForeignCallsProviderImpl(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, CodeCacheProvider codeCache,
                    WordTypes wordTypes) {
        this.jvmciRuntime = jvmciRuntime;
        this.runtime = runtime;
        this.metaAccess = metaAccess;
        this.codeCache = codeCache;
        this.wordTypes = wordTypes;
    }

    public HotSpotGraalRuntimeProvider getRuntime() {
        return runtime;
    }

    public HotSpotJVMCIRuntime getJVMCIRuntime() {
        return jvmciRuntime;
    }

    /**
     * Registers a foreign call signature that may subsequently have linkage
     * {@linkplain #register(HotSpotForeignCallLinkage) registered}.
     *
     * This exists to support foreign calls who linkage is not generated eagerly. Libgraal needs to
     * know the signature of such calls during image building.
     */
    public void register(ForeignCallSignature sig) {
        if (!foreignCalls.containsKey(sig)) {
            foreignCalls.put(sig, null);
        }
    }

    /**
     * Registers the linkage for a foreign call.
     */
    public HotSpotForeignCallLinkage register(HotSpotForeignCallLinkage linkage) {
        ForeignCallSignature key = linkage.getDescriptor().getSignature();
        HotSpotForeignCallLinkage existing = foreignCalls.put(key, linkage);
        GraalError.guarantee(existing == null, "already registered linkage for %s: %s", key, existing);
        return linkage;
    }

    /**
     * Creates and registers the details for linking a foreign call to a {@link Stub}.
     *
     * @param descriptor the signature of the call to the stub
     */
    public HotSpotForeignCallLinkage registerStubCall(
                    HotSpotForeignCallDescriptor descriptor,
                    RegisterEffect effect) {
        return register(HotSpotForeignCallLinkageImpl.create(metaAccess,
                        codeCache,
                        wordTypes,
                        this,
                        descriptor,
                        0L, effect,
                        JavaCall,
                        JavaCallee));
    }

    public HotSpotForeignCallLinkage registerStubCall(
                    ForeignCallSignature signature,
                    Transition transition,
                    CallSideEffect callSideEffect,
                    RegisterEffect effect,
                    LocationIdentity... killedLocations) {
        HotSpotForeignCallDescriptor descriptor = new HotSpotForeignCallDescriptor(signature, transition, callSideEffect, killedLocations);
        signatureMap.put(signature, descriptor);
        return registerStubCall(descriptor, effect);
    }

    /**
     * Creates and registers the linkage for a foreign call. All foreign calls are assumed to have
     * the effect {@link RegisterEffect#DESTROYS_ALL_CALLER_SAVE_REGISTERS} since they are outside
     * of Graal's knowledge.
     *
     * @param descriptor the signature of the foreign call
     * @param address the address of the code to call (must be non-zero)
     * @param outgoingCcType outgoing (caller) calling convention type
     */
    public HotSpotForeignCallLinkage registerForeignCall(
                    HotSpotForeignCallDescriptor descriptor,
                    long address,
                    CallingConvention.Type outgoingCcType) {
        if (address == 0) {
            throw new IllegalArgumentException("address must be non-zero");
        }
        Class<?> resultType = descriptor.getResultType();
        GraalError.guarantee(descriptor.getTransition() != SAFEPOINT || resultType.isPrimitive() || Word.class.isAssignableFrom(resultType),
                        "non-leaf foreign calls must return objects in thread local storage: %s", descriptor);
        GraalError.guarantee(outgoingCcType.equals(NativeCall), "only NativeCall");
        return register(HotSpotForeignCallLinkageImpl.create(metaAccess,
                        codeCache,
                        wordTypes,
                        this,
                        descriptor,
                        address,
                        DESTROYS_ALL_CALLER_SAVE_REGISTERS,
                        outgoingCcType,
                        null // incomingCcType
        ));
    }

    /**
     * Creates a {@linkplain ForeignCallStub stub} for the foreign call described by
     * {@code descriptor} if {@code address != 0}.
     *
     * @param descriptor the signature of the call to the stub
     * @param address the address of the foreign code to call
     * @param prependThread true if the JavaThread value for the current thread is to be prepended
     *            to the arguments for the call to {@code address}
     */
    public void linkForeignCall(OptionValues options,
                    HotSpotProviders providers,
                    HotSpotForeignCallDescriptor descriptor,
                    long address,
                    boolean prependThread) {
        if (address == 0) {
            throw new IllegalArgumentException("Can't link foreign call with zero address");
        }
        ForeignCallStub stub = new ForeignCallStub(options, jvmciRuntime, providers, address, descriptor, prependThread);
        HotSpotForeignCallLinkage linkage = stub.getLinkage();
        HotSpotForeignCallLinkage targetLinkage = stub.getTargetLinkage();
        linkage.setCompiledStub(stub);
        register(linkage);
        register(targetLinkage);
    }

    public void linkStackOnlyForeignCall(boolean enabled, OptionValues options,
                    HotSpotProviders providers,
                    HotSpotForeignCallDescriptor descriptor,
                    long address,
                    boolean prependThread) {
        if (enabled) {
            linkStackOnlyForeignCall(options, providers, descriptor, address, prependThread);
        } else {
            register(descriptor.getSignature());
        }
    }

    public void linkStackOnlyForeignCall(OptionValues options,
                    HotSpotProviders providers,
                    HotSpotForeignCallDescriptor descriptor,
                    long address,
                    boolean prependThread) {
        if (address == 0) {
            throw new IllegalArgumentException("Can't link foreign call with zero address");
        }
        ForeignCallStub stub = new ForeignCallStub(options, jvmciRuntime, providers, address, descriptor, prependThread, KILLS_NO_REGISTERS);
        HotSpotForeignCallLinkage linkage = stub.getLinkage();
        HotSpotForeignCallLinkage targetLinkage = stub.getTargetLinkage();
        linkage.setCompiledStub(stub);
        register(linkage);
        register(targetLinkage);
    }

    public void invokeJavaMethodStub(OptionValues options,
                    HotSpotProviders providers,
                    HotSpotForeignCallDescriptor descriptor,
                    long address) {
        if (address == 0) {
            throw new IllegalArgumentException("Can't link foreign call with zero address");
        }
        InvokeJavaMethodStub stub = new InvokeJavaMethodStub(options, jvmciRuntime, providers, address, descriptor);
        HotSpotForeignCallLinkage linkage = stub.getLinkage();
        HotSpotForeignCallLinkage targetLinkage = stub.getTargetLinkage();
        linkage.setCompiledStub(stub);
        register(linkage);
        HotSpotForeignCallLinkage registeredTargetLinkage = foreignCalls.get(targetLinkage.getDescriptor().getSignature());
        GraalError.guarantee(registeredTargetLinkage != null, "%s should already be registered", targetLinkage);
    }

    public static final boolean PREPEND_THREAD = true;
    public static final boolean DONT_PREPEND_THREAD = !PREPEND_THREAD;

    @Override
    @SuppressWarnings("try")
    public HotSpotForeignCallLinkage lookupForeignCall(ForeignCallSignature signature) {
        GraalError.guarantee(foreignCalls != null, "%s", signature);
        HotSpotForeignCallLinkage callTarget = foreignCalls.get(signature);
        if (callTarget == null) {
            throw GraalError.shouldNotReachHere("Missing implementation for runtime call: " + signature); // ExcludeFromJacocoGeneratedReport
        }
        if (callTarget.hasAddress()) {
            return callTarget;
        }
        ReplayCompilationSupport support = getRuntime().getReplayCompilationSupport();
        if (support != null && support.finalizeForeignCallLinkage(signature, callTarget)) {
            return callTarget;
        }
        try (DebugCloseable ignored = ReplayCompilationSupport.enterSnippetContext(support)) {
            callTarget.finalizeAddress(runtime.getHostBackend());
        }
        return callTarget;
    }

    @Override
    public HotSpotForeignCallLinkage lookupForeignCall(ForeignCallDescriptor descriptor) {
        return lookupForeignCall(descriptor.getSignature());
    }

    @Override
    public HotSpotForeignCallDescriptor getDescriptor(ForeignCallSignature signature) {
        HotSpotForeignCallDescriptor descriptor = signatureMap.get(signature);
        GraalError.guarantee(descriptor != null, "%s", signature);
        return descriptor;
    }

    HotSpotForeignCallDescriptor createDescriptor(ForeignCallSignature signature, Transition transition, CallSideEffect callSideEffect, LocationIdentity... killLocations) {
        GraalError.guarantee(!signatureMap.containsKey(signature), "%s", signature);
        HotSpotForeignCallDescriptor descriptor = new HotSpotForeignCallDescriptor(signature, transition, callSideEffect, killLocations);
        signatureMap.put(signature, descriptor);
        return descriptor;
    }

    @Override
    public LIRKind getValueKind(JavaKind javaKind) {
        return LIRKind.fromJavaKind(codeCache.getTarget().arch, javaKind);
    }

    /**
     * Performs {@code action} for each registered foreign call. The second parameter to
     * {@code action} is {@code null} when linkage is not yet available for the call.
     */
    public void forEachForeignCall(BiConsumer<ForeignCallSignature, HotSpotForeignCallLinkage> action) {
        MapCursor<ForeignCallSignature, HotSpotForeignCallLinkage> cursor = foreignCalls.getEntries();
        while (cursor.advance()) {
            action.accept(cursor.getKey(), cursor.getValue());
        }
    }
}
