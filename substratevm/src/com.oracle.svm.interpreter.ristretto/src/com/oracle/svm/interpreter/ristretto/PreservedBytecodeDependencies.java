/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.constraints.UnresolvedElementException;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.espresso.classfile.Constants;
import com.oracle.svm.espresso.classfile.ParserConstantPool;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.interpreter.BuildTimeInterpreterUniverse;
import com.oracle.svm.interpreter.metadata.BytecodeStream;
import com.oracle.svm.interpreter.metadata.Bytecodes;
import com.oracle.svm.interpreter.metadata.InterpreterConstantPool;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.util.GuestAnnotationAccess;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.JVMCIReflectionUtil;
import com.oracle.svm.util.OriginalMethodProvider;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.debug.DebugContext;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.UnresolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaMethod;
import jdk.vm.ci.meta.UnresolvedJavaType;

/** Registers selected-body dependencies and owns pending dynamic-call appendices for one image. */
@Platforms(Platform.HOSTED_ONLY.class)
final class PreservedBytecodeDependencies {
    private final Set<AnalysisMethod> defaultMethods;
    private final ResolvedJavaMethod unsafeAllocateInstance;
    private final ResolvedJavaMethod methodHandleIsCompileConstant;
    private final Set<String> reportedAdditionalReachability = new HashSet<>();

    PreservedBytecodeDependencies(Set<AnalysisMethod> defaultMethods) {
        this.defaultMethods = defaultMethods;
        GuestAccess guestAccess = GuestAccess.get();
        unsafeAllocateInstance = guestAccess.lookupMethod(guestAccess.lookupType("jdk.internal.misc.Unsafe"), "allocateInstance", Class.class);
        methodHandleIsCompileConstant = guestAccess.lookupMethod(guestAccess.lookupType("java.lang.invoke.MethodHandleImpl"), "isCompileConstant", Object.class);
    }

    /**
     * Dynamic-call sites discovered in newly retained bodies, including AOT-folded branches.
     * A site stays here until its appendix is available and registered as an image-heap root.
     * Removing registered sites avoids repeating root registration in later analysis iterations.
     */
    private final Set<InvokeDynamicAppendixSite> pendingInvokeDynamicAppendices = new HashSet<>();

    void registerExactRoots(FeatureImpl.DuringAnalysisAccessImpl access, List<AnalysisMethod> methods) {
        for (AnalysisMethod method : methods) {
            if (!method.isReachable()) {
                additionalReachability(access, method, -1, "method " + method.format("%H.%n(%p)"));
            }
            access.registerAsRoot(method, true, defaultMethods.contains(method) ? "Required Ristretto adapter bytecodes"
                            : "Method selected by " + RistrettoOptions.JITPreserveBytecodesForRuntime.getName());
        }
    }

    /**
     * Keeps pool classes reachable before RistrettoFeature.afterCompilation hydrates selected bodies.
     * Hydration creates pools and unresolved entries after analysis has sealed type reachability;
     * the interpreter and runtime compiler then read those objects from the image heap.
     */
    static void registerRuntimeMetadata(FeatureImpl.BeforeAnalysisAccessImpl access) {
        access.registerAsInHeap(InterpreterConstantPool.class, "Pool metadata hydrated by RistrettoFeature.afterCompilation");
        access.registerAsInHeap(ParserConstantPool.class, "Pool metadata hydrated by RistrettoFeature.afterCompilation");
        access.registerAsInHeap(UnresolvedJavaType.class, "Pool metadata hydrated by RistrettoFeature.afterCompilation");
        access.registerAsInHeap(UnresolvedJavaField.class, "Pool metadata hydrated by RistrettoFeature.afterCompilation");
        access.registerAsInHeap(UnresolvedJavaMethod.class, "Pool metadata hydrated by RistrettoFeature.afterCompilation");
        registerRuntimeMetadataFields(access);
        RistrettoUtils.forcePreserveType(access.getBigBang(), GraalDirectives.class);
    }

    /**
     * Keeps metadata that is written after analysis but read while runtime-compiling preserved AOT
     * bytecodes reachable in the image heap.
     */
    private static void registerRuntimeMetadataFields(FeatureImpl.BeforeAnalysisAccessImpl access) {
        AnalysisField exceptionHandlers = (AnalysisField) JVMCIReflectionUtil.getUniqueDeclaredField(
                        access.getMetaAccess().lookupJavaType(InterpreterResolvedJavaMethod.class), "exceptionHandlers");
        access.registerAsAccessed(exceptionHandlers, "Read by Ristretto runtime compilation from hydrated interpreter metadata.");
    }

    /**
     * Resolves and roots the hosted invoker for every {@code invokedynamic} in a preserved AOT
     * body, retaining interpreter metadata but not the invoker bytecodes.
     *
     * String-concat call sites can initially expose an {@link UnresolvedJavaMethod}. Loading the
     * referenced type performs hosted linkage for such a site and publishes its invoker.
     * Eligibility screening has already inspected unresolved bootstrap arguments without resolving
     * them. Rooting the invoker gives constant-pool hydration a resolved interpreter method and an
     * existing image entry point. Appendix sites are recorded for the complete retained body and
     * rooted independently during analysis.
     *
     * @return an ineligibility reason when a hosted invoker cannot be resolved, otherwise
     *         {@code null}
     */
    String registerInvokeDynamicInvokers(FeatureImpl.DuringAnalysisAccessImpl access, AnalysisMethod method) {
        byte[] code = method.getCode();
        List<AnalysisMethod> analysisInvokers = new ArrayList<>();
        for (int bci = 0; bci < BytecodeStream.endBCI(code); bci = BytecodeStream.nextBCI(code, bci)) {
            if (BytecodeStream.currentBC(code, bci) != Bytecodes.INVOKEDYNAMIC) {
                continue;
            }
            int cpi = BytecodeStream.readCPI4(code, bci);
            JavaMethod invoker;
            try {
                invoker = method.getConstantPool().lookupMethod(cpi, Bytecodes.INVOKEDYNAMIC);
                if (invoker instanceof UnresolvedJavaMethod) {
                    method.getConstantPool().loadReferencedType(cpi, Bytecodes.INVOKEDYNAMIC);
                    invoker = method.getConstantPool().lookupMethod(cpi, Bytecodes.INVOKEDYNAMIC);
                }
            } catch (UnresolvedElementException e) {
                return "its invokedynamic at BCI " + bci + " has no resolved hosted invoker: " + e.getMessage();
            }
            if (!(invoker instanceof AnalysisMethod analysisInvoker)) {
                return "its invokedynamic at BCI " + bci + " has no resolved hosted invoker: " + invoker;
            }
            analysisInvokers.add(analysisInvoker);
        }
        /*
         * Root registration and interpreter-universe creation are not reversible. Delay both until
         * every call site has resolved so a wildcard-ineligible method leaves no partial metadata
         * behind when it is skipped.
         */
        for (AnalysisMethod analysisInvoker : analysisInvokers) {
            if (!analysisInvoker.isInvoked()) {
                additionalReachability(access, method, -1, "invokedynamic invoker " + analysisInvoker.format("%H.%n(%p)"));
            }
            access.registerAsRoot(analysisInvoker, false, "invokedynamic invoker for preserved AOT bytecodes");
            BuildTimeInterpreterUniverse.singleton().getOrCreateMethod(analysisInvoker, false);
        }
        return null;
    }

    /**
     * Registers calls in a preserved AOT body as direct or virtual analysis roots.
     *
     * <p>
     * Runtime-preserved bytecodes can execute a virtual or interface call directly in the
     * interpreter. Unlike a normal AOT call chain, that dispatch has no compiled caller from
     * which the compile queue can discover its implementations. A virtual root gives each
     * analysis-reachable implementation a native entry point without retaining its bytecodes.
     */
    void registerInvokeTargets(FeatureImpl.DuringAnalysisAccessImpl access, AnalysisMethod method) {
        byte[] code = method.getCode();
        Set<AnalysisMethod> directTargets = new HashSet<>();
        Set<AnalysisMethod> virtualTargets = new HashSet<>();
        for (int bci = 0; bci < BytecodeStream.endBCI(code); bci = BytecodeStream.nextBCI(code, bci)) {
            int opcode = BytecodeStream.currentBC(code, bci);
            if (opcode != Bytecodes.INVOKEVIRTUAL && opcode != Bytecodes.INVOKEINTERFACE && opcode != Bytecodes.INVOKESTATIC && opcode != Bytecodes.INVOKESPECIAL) {
                continue;
            }
            JavaMethod target;
            try {
                int cpi = BytecodeStream.readCPI(code, bci);
                target = method.getConstantPool().lookupMethod(cpi, opcode);
                if (target instanceof UnresolvedJavaMethod) {
                    method.getConstantPool().loadReferencedType(cpi, opcode);
                    target = method.getConstantPool().lookupMethod(cpi, opcode);
                }
            } catch (UnsupportedFeatureException | UserError.UserException e) {
                // Preserve the existing lazy runtime behavior for unresolved callees.
                continue;
            }
            if (target instanceof AnalysisMethod analysisTarget) {
                AnalysisType declaringClass = analysisTarget.getDeclaringClass();
                /*
                 * Primitive and word pseudo-methods cannot be virtual dispatch receivers. Their
                 * analysis type flow contains primitive state, which a virtual invoke cannot
                 * resolve into implementation methods. Core methods are already managed by the
                 * image build and must not receive artificial roots. Check the wrapped method
                 * because a substituted type can hide the core declaring class. The host's method
                 * eligibility check excludes guaranteed-folded methods and graph-only intrinsics
                 * that cannot be artificial roots; final executable-callee pruning handles them.
                 */
                if (!declaringClass.isPrimitive() && !declaringClass.isWordType() &&
                                (access.getHostVM().isSupportedAnalysisMethod(access.getBigBang(), analysisTarget) || isDefaultIntrinsicCallee(method, analysisTarget)) &&
                                !GuestAnnotationAccess.isAnnotationPresent(analysisTarget, Fold.class) &&
                                !access.getHostVM().isCoreType(analysisTarget.getWrapped().getDeclaringClass())) {
                    boolean direct = opcode == Bytecodes.INVOKESTATIC || opcode == Bytecodes.INVOKESPECIAL;
                    if ((direct ? directTargets : virtualTargets).add(analysisTarget)) {
                        if (direct ? !analysisTarget.isReachable() : !analysisTarget.isInvoked()) {
                            additionalReachability(access, method, bci, "callee " + analysisTarget.format("%H.%n(%p)"));
                        }
                        access.registerAsRoot(analysisTarget, direct, "invocation from preserved AOT bytecodes");
                    }
                }
            }
        }
    }

    /**
     * These default-adapter callees need interpreter-callable AOT entries even though the normal
     * host eligibility check excludes invocation plugins. Allocation has a standalone intrinsic
     * graph; isCompileConstant has real bytecodes returning false outside compilation.
     */
    private boolean isDefaultIntrinsicCallee(AnalysisMethod method, AnalysisMethod callee) {
        if (!defaultMethods.contains(method)) {
            return false;
        }
        ResolvedJavaMethod original = OriginalMethodProvider.getOriginalMethod(callee);
        return unsafeAllocateInstance.equals(original) || methodHandleIsCompileConstant.equals(original);
    }

    /**
     * Visits dependencies in the original bytecodes, including branches eliminated from the AOT
     * graph. Types and constants must enter analysis before constant-pool hydration, which cannot
     * introduce reachability after the universe and image heap have been sealed. Field registration
     * itself remains with method-body retention; its policy check must precede that registration.
     */
    void registerBytecodeDependencies(FeatureImpl.DuringAnalysisAccessImpl access, AnalysisMethod method) {
        byte[] code = method.getCode();
        for (int bci = 0; bci < BytecodeStream.endBCI(code); bci = BytecodeStream.nextBCI(code, bci)) {
            int opcode = BytecodeStream.currentBC(code, bci);
            Object dependency = PreservedBytecodeEligibility.lookupBytecodeDependency(method, bci, opcode);
            switch (opcode) {
                case Bytecodes.INVOKEVIRTUAL, Bytecodes.INVOKEINTERFACE, Bytecodes.INVOKESTATIC, Bytecodes.INVOKESPECIAL -> {
                    /* Resolution may move an inherited callee to a different declaring type. */
                    if (dependency instanceof AnalysisType symbolicHolder) {
                        retainType(access, method, bci, symbolicHolder, false);
                    }
                }
                case Bytecodes.NEW, Bytecodes.ANEWARRAY, Bytecodes.MULTIANEWARRAY, Bytecodes.CHECKCAST, Bytecodes.INSTANCEOF -> {
                    if (dependency instanceof AnalysisType referencedType) {
                        AnalysisType type = opcode == Bytecodes.ANEWARRAY ? referencedType.getArrayClass() : referencedType;
                        boolean allocation = opcode == Bytecodes.NEW || opcode == Bytecodes.ANEWARRAY || opcode == Bytecodes.MULTIANEWARRAY;
                        int dimensions = opcode == Bytecodes.MULTIANEWARRAY ? BytecodeStream.readUByte(code, bci + 3) : 1;
                        for (int dimension = 0; dimension < dimensions; dimension++) {
                            retainType(access, method, bci, type, allocation);
                            type = type.getComponentType();
                        }
                    }
                }
                case Bytecodes.NEWARRAY -> {
                    Class<?> arrayClass = switch (BytecodeStream.readUByte(code, bci + 1)) {
                        case Constants.JVM_ArrayType_Boolean -> boolean[].class;
                        case Constants.JVM_ArrayType_Char -> char[].class;
                        case Constants.JVM_ArrayType_Float -> float[].class;
                        case Constants.JVM_ArrayType_Double -> double[].class;
                        case Constants.JVM_ArrayType_Byte -> byte[].class;
                        case Constants.JVM_ArrayType_Short -> short[].class;
                        case Constants.JVM_ArrayType_Int -> int[].class;
                        case Constants.JVM_ArrayType_Long -> long[].class;
                        default -> throw UserError.abort("Invalid newarray operand in preserved method '%s' at BCI %d.", method, bci);
                    };
                    retainType(access, method, bci, access.getMetaAccess().lookupJavaType(arrayClass), true);
                }
                case Bytecodes.LDC, Bytecodes.LDC_W, Bytecodes.LDC2_W -> {
                    if (dependency instanceof AnalysisType type) {
                        retainType(access, method, bci, type, false);
                    } else if (dependency instanceof ImageHeapConstant heapConstant) {
                        retainConstant(access, method, bci, heapConstant);
                    }
                }
                case Bytecodes.GETFIELD, Bytecodes.GETSTATIC, Bytecodes.PUTFIELD, Bytecodes.PUTSTATIC -> {
                    /* Hydration keeps the symbolic holder even when the field is inherited. */
                    if (PreservedBytecodeEligibility.lookupBytecodeReferencedType(method, bci, opcode) instanceof AnalysisType symbolicHolder) {
                        retainType(access, method, bci, symbolicHolder, false);
                    }
                    if (dependency instanceof AnalysisField field) {
                        boolean read = opcode == Bytecodes.GETFIELD || opcode == Bytecodes.GETSTATIC;
                        if (read ? !field.isRead() : !field.isWritten()) {
                            additionalReachability(access, method, bci, "field " + (read ? "read " : "write ") + field.format("%H.%n"));
                        }
                        /* A plain primitive instance store needs read reachability to retain its offset. */
                        if (opcode == Bytecodes.PUTFIELD && !field.isStatic() && !field.isAccessed() &&
                                        !Modifier.isVolatile(field.getModifiers()) && field.getStorageKind() != JavaKind.Object) {
                            additionalReachability(access, method, bci, "field read " + field.format("%H.%n"));
                        }
                    }
                }
            }
        }
        for (ExceptionHandler handler : method.getExceptionHandlers()) {
            if (handler.getCatchType() instanceof AnalysisType type) {
                retainType(access, method, handler.getHandlerBCI(), type, false);
            }
        }
    }

    private void retainType(FeatureImpl.DuringAnalysisAccessImpl access, AnalysisMethod method, int bci, AnalysisType type, boolean allocation) {
        if (allocation ? !type.isInstantiated() : !type.isReachable()) {
            additionalReachability(access, method, bci, (allocation ? "instantiated type " : "type ") + type.toJavaName());
        }
        String reason = "Dependency of preserved AOT bytecodes in " + method.format("%H.%n(%p)");
        if (allocation) {
            access.registerAsInHeap(type, reason);
        } else {
            access.registerAsUsed(type, reason);
        }
    }

    private void retainConstant(FeatureImpl.DuringAnalysisAccessImpl access, AnalysisMethod method, int bci, ImageHeapConstant constant) {
        if (!constant.isReachable()) {
            additionalReachability(access, method, bci, "image-heap constant of type " + constant.getType().toJavaName());
        }
        access.getUniverse().registerEmbeddedRoot(constant, new BytecodePosition(null, method, bci));
    }

    /**
     * Records all sites in a newly preserved body, including branches folded out of its AOT
     * graph. Their appendices can still be reached through the interpreter constant pool and must
     * be registered during analysis, before reflection metadata is sealed.
     */
    void registerPreservedInvokeDynamicAppendixSites(AnalysisMethod method) {
        byte[] code = method.getCode();
        for (int bci = 0; bci < BytecodeStream.endBCI(code); bci = BytecodeStream.nextBCI(code, bci)) {
            if (BytecodeStream.currentBC(code, bci) == Bytecodes.INVOKEDYNAMIC) {
                int cpi = BytecodeStream.readCPI4(code, bci);
                InvokeDynamicAppendixSite site = new InvokeDynamicAppendixSite(method, bci, cpi);
                pendingInvokeDynamicAppendices.add(site);
            }
        }
    }

    /**
     * Roots available appendices and removes their pending sites. Missing appendices are retried
     * in later analysis iterations; a true result requests another iteration for the new roots.
     */
    boolean registerNewInvokeDynamicAppendices(FeatureImpl.DuringAnalysisAccessImpl access) {
        boolean registeredNewAppendix = false;
        BuildTimeInterpreterUniverse interpreterUniverse = BuildTimeInterpreterUniverse.singleton();
        for (var iterator = pendingInvokeDynamicAppendices.iterator(); iterator.hasNext();) {
            InvokeDynamicAppendixSite site = iterator.next();
            InterpreterResolvedJavaMethod interpreterMethod = interpreterUniverse.getMethod(site.method());
            if (interpreterMethod == null || interpreterMethod.getInterpretedCode() == null) {
                continue;
            }
            JavaConstant appendix = site.method().getConstantPool().lookupAppendix(site.cpi(), Bytecodes.INVOKEDYNAMIC);
            if (appendix == null) {
                continue;
            }
            if (appendix instanceof ImageHeapConstant heapConstant) {
                retainConstant(access, site.method(), site.bci(), heapConstant);
            } else {
                access.getUniverse().registerEmbeddedRoot(appendix, new BytecodePosition(null, site.method(), site.bci()));
            }
            iterator.remove();
            registeredNewAppendix = true;
        }
        return registeredNewAppendix;
    }

    private record InvokeDynamicAppendixSite(AnalysisMethod method, int bci, int cpi) {
    }

    /**
     * Checks original-body dependencies before mutating analysis; diagnostics describe
     * registration-time state, not a counterfactual analysis without preservation. The feature's
     * own metadata infrastructure is independent of this per-body policy.
     */
    private void additionalReachability(FeatureImpl.DuringAnalysisAccessImpl access, AnalysisMethod method, int bci, String dependency) {
        if (defaultMethods.contains(method)) {
            return;
        }
        DebugContext debug = access.getDebugContext();
        try (DebugContext.Scope _ = debug.scope(RistrettoPreserveBytecodeSupport.LOG_SCOPE)) {
            boolean failOnAdditionalReachability = RistrettoOptions.JITPreserveBytecodesFailOnAdditionalReachability.getValue();
            if (failOnAdditionalReachability || debug.isLogEnabled()) {
                String location = method.format("%H.%n(%p)") + (bci < 0 ? "" : " at BCI " + bci);
                if (failOnAdditionalReachability) {
                    throw UserError.abort("Preserving bytecodes for '%s' requires additional reachability: %s. Disable %s to retain these dependencies.",
                                    location, dependency, RistrettoOptions.JITPreserveBytecodesFailOnAdditionalReachability.getName());
                }
                if (reportedAdditionalReachability.add(dependency)) {
                    debug.log("Preserved bytecodes add reachability: %s (required by %s).", dependency, location);
                }
            }
        }
    }
}
