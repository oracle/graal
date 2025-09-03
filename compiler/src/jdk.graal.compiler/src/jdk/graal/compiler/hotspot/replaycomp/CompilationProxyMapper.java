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

import java.lang.reflect.Array;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.hotspot.replaycomp.proxy.CompilationProxy;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.StackLockValue;
import jdk.vm.ci.code.VirtualObject;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.ImplicitExceptionDispatch;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.code.site.Reference;
import jdk.vm.ci.code.site.Site;
import jdk.vm.ci.hotspot.HotSpotCompiledNmethod;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.EncodedSpeculationReason;
import jdk.vm.ci.meta.InvokeTarget;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.UnresolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaMethod;
import jdk.vm.ci.meta.VMConstant;

/**
 * Recursively proxifies or unproxifies instances of registered classes in composite objects.
 * <p>
 * {@link CompilerInterfaceDeclarations} declares for which JVMCI objects proxies should be created.
 * When these objects are stored inside composite objects (e.g., inside arrays or
 * {@link DebugInfo}), this class can create deep copies of such composite objects, replacing the
 * registered instances with proxies. This mapping is performed by the {@link #proxifyRecursive}
 * method.
 * <p>
 * This class can also perform the reverse mapping, i.e., create a deep copy of an object and
 * replace the proxy with a local JVMCI object (by calling {@link CompilationProxy#unproxify()} on
 * the proxies). This is performed by the {@link #unproxifyRecursive}.
 */
public class CompilationProxyMapper {
    /**
     * Declares the registered classes, indicating which objects should be proxified.
     */
    private final CompilerInterfaceDeclarations declarations;

    /**
     * Function that proxifies instances of registered classes.
     */
    private final Function<Object, CompilationProxy> proxify;

    /**
     * Maps instances of proxified arrays to the component types of the original arrays.
     */
    private final Map<Object[], Class<?>> originalComponentType;

    /**
     * Constructs a new proxy mapper.
     *
     * @param declarations the compiler interface declarations
     * @param proxify the function used to proxify a single registered instance
     */
    public CompilationProxyMapper(CompilerInterfaceDeclarations declarations, Function<Object, CompilationProxy> proxify) {
        this.declarations = declarations;
        this.proxify = proxify;
        this.originalComponentType = new WeakHashMap<>();
    }

    /**
     * Proxifies the given object, i.e., creates proxies for the instances of registered types and
     * replaces them with proxies.
     *
     * @param input the input object
     * @return an object where the registered instances are replaced with proxies
     */
    public Object proxifyRecursive(Object input) {
        return proxifyInternal(input, null);
    }

    /**
     * Unproxifies the given object, i.e., removes proxies from the instances of registered types.
     *
     * @param input the input object
     * @return an object where the proxies are replaced with the original objects/local mirrors
     */
    public Object unproxifyRecursive(Object input) {
        return unproxifyInternal(input, null);
    }

    @FunctionalInterface
    private interface Mapper {
        Object apply(Object input, EconomicMap<Object, Object> cache);
    }

    private Object proxifyInternal(Object input, EconomicMap<Object, Object> cache) {
        if (input instanceof Object[] array) {
            Class<?> originalClazz = input.getClass().componentType();
            Class<?> adjustedClazz = declarations.findRegisteredSupertype(originalClazz);
            if (adjustedClazz == null) {
                adjustedClazz = originalClazz;
            }
            Object[] result = (Object[]) Array.newInstance(adjustedClazz, array.length);
            for (int i = 0; i < array.length; i++) {
                result[i] = proxifyInternal(array[i], null);
            }
            if (!adjustedClazz.equals(originalClazz)) {
                originalComponentType.put(result, originalClazz);
            }
            return result;
        } else if (declarations.isRegisteredClassInstance(input)) {
            return proxify.apply(input);
        } else {
            return map(input, this::proxifyInternal, cache);
        }
    }

    private Object unproxifyInternal(Object input, EconomicMap<Object, Object> cache) {
        if (input instanceof Object[] array) {
            Class<?> clazz = originalComponentType.get(input);
            if (clazz == null) {
                clazz = input.getClass().componentType();
            }
            Object[] result = (Object[]) Array.newInstance(clazz, array.length);
            for (int i = 0; i < array.length; i++) {
                result[i] = unproxifyInternal(array[i], cache);
            }
            return result;
        } else if (input instanceof CompilationProxy compilationProxy) {
            return compilationProxy.unproxify();
        } else {
            return map(input, this::unproxifyInternal, cache);
        }
    }

    private static Object map(Object input, Mapper mapper, EconomicMap<Object, Object> cache) {
        switch (input) {
            case SpecialResultMarker.ExceptionThrownMarker marker -> {
                return new SpecialResultMarker.ExceptionThrownMarker((Throwable) mapper.apply(marker.getThrown(), cache));
            }
            case HotSpotCompiledNmethod nmethod -> {
                return new HotSpotCompiledNmethod(nmethod.getName(), nmethod.getTargetCode(), nmethod.getTargetCodeSize(),
                                (Site[]) mapper.apply(nmethod.getSites(), cache), (Assumptions.Assumption[]) mapper.apply(nmethod.getAssumptions(), cache),
                                (ResolvedJavaMethod[]) mapper.apply(nmethod.getMethods(), cache), nmethod.getComments(),
                                nmethod.getDataSection(), nmethod.getDataSectionAlignment(), (DataPatch[]) mapper.apply(nmethod.getDataSectionPatches(), cache), nmethod.isImmutablePIC(),
                                nmethod.getTotalFrameSize(), nmethod.getDeoptRescueSlot(), (HotSpotResolvedJavaMethod) mapper.apply(nmethod.getMethod(), cache), nmethod.getEntryBCI(),
                                nmethod.getId(), nmethod.getCompileState(), nmethod.hasUnsafeAccess());
            }
            case Call call -> {
                return new Call((InvokeTarget) mapper.apply(call.target, cache), call.pcOffset, call.size, call.direct, (DebugInfo) mapper.apply(call.debugInfo, cache));
            }
            case ImplicitExceptionDispatch dispatch -> {
                return new ImplicitExceptionDispatch(dispatch.pcOffset, dispatch.dispatchOffset, (DebugInfo) mapper.apply(dispatch.debugInfo, cache));
            }
            case Infopoint infopoint -> {
                return new Infopoint(infopoint.pcOffset, (DebugInfo) mapper.apply(infopoint.debugInfo, cache), infopoint.reason);
            }
            case DataPatch dataPatch -> {
                return new DataPatch(dataPatch.pcOffset, (Reference) mapper.apply(dataPatch.reference, cache), mapper.apply(dataPatch.note, cache));
            }
            case ConstantReference constantReference -> {
                return new ConstantReference((VMConstant) mapper.apply(constantReference.getConstant(), cache));
            }
            case HotSpotSpeculationLog.HotSpotSpeculation speculation -> {
                return new HotSpotSpeculationLog.HotSpotSpeculation((SpeculationLog.SpeculationReason) mapper.apply(speculation.getReason(), cache),
                                (JavaConstant) mapper.apply(speculation.getEncoding(), cache), speculation.getReasonEncoding());
            }
            case SpeculationLog.Speculation speculation -> {
                return new SpeculationLog.Speculation((SpeculationLog.SpeculationReason) mapper.apply(speculation.getReason(), cache));
            }
            case EncodedSpeculationReason speculationReason -> {
                return new EncodedSpeculationReason(speculationReason.getGroupId(), speculationReason.getGroupName(), (Object[]) mapper.apply(speculationReason.getContext(), cache));
            }
            case BytecodeFrame bytecodeFrame -> {
                return new BytecodeFrame((BytecodeFrame) mapper.apply(bytecodeFrame.caller(), cache), (ResolvedJavaMethod) mapper.apply(bytecodeFrame.getMethod(), cache),
                                bytecodeFrame.getBCI(), bytecodeFrame.rethrowException, bytecodeFrame.duringCall, (JavaValue[]) mapper.apply(bytecodeFrame.values, cache),
                                bytecodeFrame.getSlotKinds(), bytecodeFrame.numLocals, bytecodeFrame.numStack, bytecodeFrame.numLocks);
            }
            case BytecodePosition bytecodePosition -> {
                return new BytecodePosition((BytecodePosition) mapper.apply(bytecodePosition.getCaller(), cache), (ResolvedJavaMethod) mapper.apply(bytecodePosition.getMethod(), cache),
                                bytecodePosition.getBCI());
            }
            case StackLockValue value -> {
                return new StackLockValue((JavaValue) mapper.apply(value.getOwner(), cache), value.getSlot(), value.isEliminated());
            }
            case JavaTypeProfile typeProfile -> {
                return new JavaTypeProfile(typeProfile.getNullSeen(), typeProfile.getNotRecordedProbability(), (JavaTypeProfile.ProfiledType[]) mapper.apply(typeProfile.getTypes(), cache));
            }
            case JavaTypeProfile.ProfiledType profiledType -> {
                return new JavaTypeProfile.ProfiledType((ResolvedJavaType) mapper.apply(profiledType.getType(), cache), profiledType.getProbability());
            }
            case JavaMethodProfile methodProfile -> {
                return new JavaMethodProfile(methodProfile.getNotRecordedProbability(), (JavaMethodProfile.ProfiledMethod[]) mapper.apply(methodProfile.getMethods(), cache));
            }
            case JavaMethodProfile.ProfiledMethod profiledMethod -> {
                return new JavaMethodProfile.ProfiledMethod((ResolvedJavaMethod) mapper.apply(profiledMethod.getMethod(), cache), profiledMethod.getProbability());
            }
            case DebugInfo debugInfo -> {
                DebugInfo newDebugInfo = new DebugInfo((BytecodePosition) mapper.apply(debugInfo.frame(), cache), (VirtualObject[]) mapper.apply(debugInfo.getVirtualObjectMapping(), cache));
                newDebugInfo.setCalleeSaveInfo(debugInfo.getCalleeSaveInfo());
                newDebugInfo.setReferenceMap(debugInfo.getReferenceMap());
                return newDebugInfo;
            }
            case VirtualObject virtualObject -> {
                VirtualObject newVirtualObject = VirtualObject.get((ResolvedJavaType) mapper.apply(virtualObject.getType(), cache), virtualObject.getId(), virtualObject.isAutoBox());
                EconomicMap<Object, Object> selectedCache = cache;
                if (selectedCache == null) {
                    selectedCache = EconomicMap.create(Equivalence.IDENTITY);
                } else {
                    Object found = selectedCache.get(virtualObject);
                    if (found != null) {
                        return found;
                    }
                }
                selectedCache.put(virtualObject, newVirtualObject);
                if (virtualObject.getValues() != null) {
                    newVirtualObject.setValues((JavaValue[]) mapper.apply(virtualObject.getValues(), selectedCache), virtualObject.getSlotKinds());
                }
                return newVirtualObject;
            }
            case Assumptions.AssumptionResult<?> result -> {
                Assumptions assumptions = new Assumptions();
                result.recordTo(assumptions);
                return new Assumptions.AssumptionResult<>(mapper.apply(result.getResult(), cache), (Assumptions.Assumption[]) mapper.apply(assumptions.toArray(), cache));
            }
            case Assumptions.NoFinalizableSubclass assumption -> {
                return new Assumptions.NoFinalizableSubclass((ResolvedJavaType) mapper.apply(assumption.receiverType, cache));
            }
            case Assumptions.ConcreteSubtype assumption -> {
                return new Assumptions.ConcreteSubtype((ResolvedJavaType) mapper.apply(assumption.context, cache), (ResolvedJavaType) mapper.apply(assumption.subtype, cache));
            }
            case Assumptions.LeafType assumption -> {
                return new Assumptions.LeafType((ResolvedJavaType) mapper.apply(assumption.context, cache));
            }
            case Assumptions.ConcreteMethod assumption -> {
                return new Assumptions.ConcreteMethod((ResolvedJavaMethod) mapper.apply(assumption.method, cache), (ResolvedJavaType) mapper.apply(assumption.context, cache),
                                (ResolvedJavaMethod) mapper.apply(assumption.impl, cache));
            }
            case Assumptions.CallSiteTargetValue assumption -> {
                return new Assumptions.CallSiteTargetValue((JavaConstant) mapper.apply(assumption.callSite, cache), (JavaConstant) mapper.apply(assumption.methodHandle, cache));
            }
            case jdk.vm.ci.meta.ExceptionHandler exceptionHandler -> {
                return new jdk.vm.ci.meta.ExceptionHandler(exceptionHandler.getStartBCI(), exceptionHandler.getEndBCI(),
                                exceptionHandler.getHandlerBCI(), exceptionHandler.catchTypeCPI(), (JavaType) mapper.apply(exceptionHandler.getCatchType(), cache));
            }
            case UnresolvedJavaMethod method -> {
                return new UnresolvedJavaMethod(method.getName(), (Signature) mapper.apply(method.getSignature(), cache), (JavaType) mapper.apply(method.getDeclaringClass(), cache));
            }
            case UnresolvedJavaField field -> {
                return new UnresolvedJavaField((JavaType) mapper.apply(field.getDeclaringClass(), cache), field.getName(), (JavaType) mapper.apply(field.getType(), cache));
            }
            case DelayedDeserializationObject delayedDeserialization -> {
                return mapper.apply(delayedDeserialization.deserialize(), cache);
            }
            case null, default -> {
                return input;
            }
        }
    }
}
