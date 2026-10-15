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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.guest.staging.core.heap.RestrictHeapAccess;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.bootstrap.BootstrapMethodConfiguration;
import com.oracle.svm.interpreter.InterpreterFeature;
import com.oracle.svm.hosted.code.RestrictHeapAccessCalleesImpl;
import com.oracle.svm.interpreter.metadata.BytecodeStream;
import com.oracle.svm.interpreter.metadata.Bytecodes;
import com.oracle.svm.util.GuestAnnotationAccess;
import com.oracle.svm.util.GuestAccess;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.UnresolvedJavaField;

/** Inspects whether raw preserved bytecodes can execute without registering analysis roots. */
@Platforms(Platform.HOSTED_ONLY.class)
final class PreservedBytecodeEligibility {
    private PreservedBytecodeEligibility() {
    }

    /**
     * Recognizes guest declarations and hosted adapter aliases without resolving the call site.
     * Guest declaration flags remain available when the target is unresolved or substituted;
     * inspecting only the target's native/varargs flags would miss cold VarHandle calls.
     */
    private static boolean isSignaturePolymorphicInvoke(JavaMethod method) {
        String name = method.getName();
        String holder = method.getDeclaringClass().toClassName();
        Class<?> declarationHolder = holder.equals("java.lang.invoke.MethodHandle") ? MethodHandle.class : holder.equals("java.lang.invoke.VarHandle") ? VarHandle.class : null;
        if (declarationHolder != null) {
            if (declarationHolder == MethodHandle.class && (name.equals("invoke_MT") || name.equals("invokeExact_MT"))) {
                return true;
            }
            for (ResolvedJavaMethod declaration : GuestAccess.get().lookupType(declarationHolder).getDeclaredMethods()) {
                if (declaration.getName().equals(name) && declaration.isNative() && declaration.isVarArgs()) {
                    return true;
                }
            }
            return false;
        }
        if (holder.equals("java.lang.invoke.VarHandleGuards") && name.startsWith("guard_")) {
            return true;
        }
        return (name.equals("invoke_MT") || name.equals("invokeExact_MT")) &&
                        (holder.equals("java.lang.invoke.Invokers$Holder") || holder.startsWith("java.lang.invoke.LambdaForm$MH"));
    }

    /**
     * Returns why the original bytecodes cannot execute after runtime inlining, or null if supported.
     * AOT compilation can rewrite fields and calls that the interpreter would execute literally;
     * deleted fields, injected accessors and unsupported calling conventions therefore reject the
     * whole body. Resolution can reveal such operations in later analysis iterations, so retained
     * methods are checked again after analysis.
     */
    static String rejectionReason(AnalysisMethod method, SVMHost hostVM, MetaAccessProvider metaAccess) {
        return rejectionReason(method, hostVM, metaAccess, false);
    }

    /** Internal adapters use the interpreter's basic MethodHandle intrinsics, not application linkage. */
    static String rejectionReason(AnalysisMethod method, SVMHost hostVM, MetaAccessProvider metaAccess, boolean defaultAdapter) {
        if (method.isClassInitializer()) {
            return "it is a class initializer";
        }
        if (method.getCode() == null) {
            return "it has no bytecodes";
        }
        RestrictHeapAccess.Access restriction = RestrictHeapAccessCalleesImpl.heapAccessRestriction(method);
        if (restriction != RestrictHeapAccess.Access.UNRESTRICTED) {
            return "heap access is restricted to " + restriction;
        }
        if (!InterpreterFeature.executableByInterpreter(method)) {
            return "it is not callable or executable by the interpreter";
        }
        if (hostVM.isCoreType(method.getDeclaringClass())) {
            return "its declaring type is an SVM core type";
        }
        if (!InterpreterFeature.callableByInterpreter(method, metaAccess)) {
            return "it is not callable or executable by the interpreter";
        }
        String unsupportedAllocation = findUnsupportedAllocation(method, hostVM);
        if (unsupportedAllocation != null) {
            return unsupportedAllocation;
        }
        String unsupportedDirectFieldAccess = findUnsupportedDirectFieldAccess(method);
        if (unsupportedDirectFieldAccess != null) {
            return unsupportedDirectFieldAccess;
        }
        String unsupportedDirectInvocation = findUnsupportedDirectInvocation(method, defaultAdapter);
        if (unsupportedDirectInvocation != null) {
            return unsupportedDirectInvocation;
        }
        return findRuntimeDeferredConstant(method);
    }

    /**
     * Core implementation classes must not be instantiated from raw preserved bytecodes. AOT
     * parsing can remove their allocations behind configuration guards, such as a stack walk of
     * disabled continuations. Registering those allocations first can activate unsupported virtual
     * implementations before final executable-callee pruning can reject the preserved body.
     */
    private static String findUnsupportedAllocation(AnalysisMethod method, SVMHost hostVM) {
        byte[] code = method.getCode();
        for (int bci = 0; bci < BytecodeStream.endBCI(code); bci = BytecodeStream.nextBCI(code, bci)) {
            if (BytecodeStream.currentBC(code, bci) == Bytecodes.NEW &&
                            lookupBytecodeDependency(method, bci, Bytecodes.NEW) instanceof AnalysisType type && hostVM.isCoreType(type)) {
                return "it allocates SVM core type '" + type.toJavaName() + "' at BCI " + bci;
            }
        }
        return null;
    }

    /**
     * Rejects constants whose bootstrap the AOT parser would defer to runtime. Preservation cannot
     * represent that linkage yet, and its dependency scan and pool hydration use eager lookups.
     * Inspect the bootstrap and its nested constant arguments without invoking them, while allowing
     * already resolved and explicitly trusted constants. An invokedynamic with unavailable
     * metadata is safe only when its non-resolving method lookup is already resolved. A root
     * unresolved LDC otherwise keeps its existing lazy failure behavior. An integer bootstrap
     * argument can represent either an ordinary unresolved pool entry or a dynamic constant, so
     * it is rejected only when its non-resolving bootstrap metadata identifies it as dynamic.
     */
    private static String findRuntimeDeferredConstant(AnalysisMethod method) {
        return findRuntimeDeferredConstant(method.getCode(), method.getConstantPool());
    }

    /**
     * Scans one compiler-visible bytecode body without resolving its bootstrap operations.
     * Unavailable metadata or linkage failures reject the body; fatal VM errors propagate.
     * The private overload keeps the production caller unchanged while allowing direct probes of
     * the missing-metadata policy with a synthetic non-resolving constant pool.
     */
    private static String findRuntimeDeferredConstant(byte[] code, ConstantPool pool) {
        boolean inspectedCondyPool = false;
        for (int bci = 0; bci < BytecodeStream.endBCI(code); bci = BytecodeStream.nextBCI(code, bci)) {
            int opcode = BytecodeStream.currentBC(code, bci);
            if (opcode == Bytecodes.INVOKEDYNAMIC) {
                try {
                    if (!inspectedCondyPool) {
                        /*
                         * Indy metadata lookup can execute unresolved condy arguments. JVMCI
                         * cannot identify those arguments safely first, so inspect the loaded
                         * pool without resolving any entry. An unrelated unresolved condy also
                         * excludes this body. Recheck on later scans as constants may resolve.
                         */
                        List<ConstantPool.BootstrapMethodInvocation> constants = pool.lookupBootstrapMethodInvocations(false);
                        if (constants == null) {
                            return "its invokedynamic at BCI " + bci + " cannot be inspected safely because constant dynamic metadata is unavailable";
                        }
                        for (ConstantPool.BootstrapMethodInvocation constant : constants) {
                            if (constant.lookup() == null) {
                                return "its invokedynamic at BCI " + bci + " cannot be inspected safely while its constant pool contains an unresolved constant dynamic";
                            }
                        }
                        inspectedCondyPool = true;
                    }
                    int cpi = BytecodeStream.readCPI4(code, bci);
                    ConstantPool.BootstrapMethodInvocation bootstrap = pool.lookupBootstrapMethodInvocation(cpi, Bytecodes.INVOKEDYNAMIC);
                    if (bootstrap == null) {
                        if (pool.lookupMethod(cpi, Bytecodes.INVOKEDYNAMIC) instanceof ResolvedJavaMethod) {
                            continue;
                        }
                        return "its invokedynamic at BCI " + bci + " is unresolved but its non-resolving bootstrap metadata is unavailable";
                    }
                    for (JavaConstant argument : bootstrap.getStaticArguments()) {
                        /* Unresolved pool entries are represented by their integer pool index. */
                        if (argument.getJavaKind() == JavaKind.Int && hasRuntimeDeferredBootstrap(pool, argument.asInt())) {
                            return "it has a runtime-deferred constant dynamic invokedynamic bootstrap argument at BCI " + bci;
                        }
                    }
                    ResolvedJavaMethod bootstrapMethod = bootstrap.getMethod();
                    if (!BootstrapMethodConfiguration.singleton().isIndyAllowedAtBuildTime(bootstrapMethod)) {
                        return "its invokedynamic at BCI " + bci + " uses non-allowlisted bootstrap " + bootstrapMethod.format("%H.%n(%p)");
                    }
                } catch (LinkageError | UnsupportedOperationException | UnsupportedFeatureException | UserError.UserException | TypeNotPresentException e) {
                    return "its invokedynamic at BCI " + bci + " bootstrap arguments cannot be inspected safely: " + e.getMessage();
                }
                continue;
            }
            if (opcode != Bytecodes.LDC && opcode != Bytecodes.LDC_W && opcode != Bytecodes.LDC2_W) {
                continue;
            }
            try {
                int cpi = BytecodeStream.readCPI(code, bci);
                if (pool.lookupConstant(cpi, false) == null) {
                    if (hasRuntimeDeferredBootstrap(pool, cpi)) {
                        return "it loads a runtime-deferred constant dynamic at BCI " + bci;
                    }
                }
            } catch (LinkageError | UnsupportedOperationException | UnsupportedFeatureException | UserError.UserException | TypeNotPresentException e) {
                return "its constant at BCI " + bci + " bootstrap arguments cannot be inspected safely: " + e.getMessage();
            }
        }
        return null;
    }

    private static boolean hasRuntimeDeferredBootstrap(ConstantPool pool, int cpi) {
        List<Integer> pending = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        pending.add(cpi);
        while (!pending.isEmpty()) {
            int current = pending.removeLast();
            if (!visited.add(current) || pool.lookupConstant(current, false) != null) {
                continue;
            }
            ConstantPool.BootstrapMethodInvocation bootstrap = pool.lookupBootstrapMethodInvocation(current, -1);
            if (bootstrap == null) {
                continue;
            }
            if (!BootstrapMethodConfiguration.singleton().isCondyAllowedAtBuildTime(bootstrap.getMethod())) {
                return true;
            }
            for (JavaConstant argument : bootstrap.getStaticArguments()) {
                // JVMCI represents unresolved condy arguments as Int pool indices, boxing values.
                if (argument.getJavaKind() == JavaKind.Int) {
                    pending.add(argument.asInt());
                }
            }
        }
        return false;
    }

    /**
     * Returns the reason that a directly referenced field cannot be represented by runtime-preserved
     * bytecodes, or {@code null} when every resolved field access is supported. Unresolved fields
     * deliberately remain eligible so method-body retention can preserve their existing lazy
     * runtime failure behavior.
     */
    private static String findUnsupportedDirectFieldAccess(AnalysisMethod method) {
        byte[] code = method.getCode();
        for (int bci = 0; bci < BytecodeStream.endBCI(code); bci = BytecodeStream.nextBCI(code, bci)) {
            int opcode = BytecodeStream.opcode(code, bci);
            if (opcode != Bytecodes.GETFIELD && opcode != Bytecodes.GETSTATIC && opcode != Bytecodes.PUTFIELD && opcode != Bytecodes.PUTSTATIC) {
                continue;
            }
            try {
                int cpi = BytecodeStream.readCPI(code, bci);
                method.getConstantPool().loadReferencedType(cpi, opcode);
                JavaField field = method.getConstantPool().lookupField(cpi, method, opcode);
                if (field instanceof UnresolvedJavaField unresolved && unresolved.getCause() instanceof IllegalAccessError error) {
                    // JVMCI may return the linkage failure attached to an unresolved field.
                    throw error;
                }
                if (field instanceof AnalysisField analysisField) {
                    String unsupportedField = unsupportedPreservedBytecodeField(analysisField);
                    if (unsupportedField != null) {
                        return unsupportedField;
                    }
                }
            } catch (IllegalAccessError e) {
                return "its direct field access at BCI " + bci + " is not accessible: " + e.getMessage();
            } catch (UnsupportedFeatureException | UserError.UserException e) {
                // Keep the existing unresolved-field behavior; it is handled by method-body retention.
            }
        }
        return null;
    }

    /**
     * Returns the first reason why {@code field} cannot be represented by a preserved bytecode
     * body, or {@code null} when the field has ordinary runtime image semantics. Deleted fields lack
     * storage; guarantee-folded and injected-accessor fields depend on hosted rewriting. Word fields
     * have primitive storage despite their object descriptor, which the interpreter cannot access.
     */
    private static String unsupportedPreservedBytecodeField(AnalysisField field) {
        if (field.getType().isWordType()) {
            return "it directly accesses word-valued field '" + field.format("%H.%n") + "', whose storage cannot be accessed by the interpreter";
        }
        if (GuestAnnotationAccess.isAnnotationPresent(field, Delete.class)) {
            return "it directly accesses @Delete field '" + field.format("%H.%n") + "', which is not present in the image";
        }
        if (field.isGuaranteeFolded()) {
            return "it directly accesses guarantee-folded field '" + field.format("%H.%n") + "'";
        }
        if (GuestAnnotationAccess.isAnnotationPresent(field, InjectAccessors.class)) {
            return "it directly accesses @InjectAccessors field '" + field.format("%H.%n") + "', whose bytecode access requires hosted graph rewriting";
        }
        return null;
    }

    /**
     * Rejects the whole body before preservation registers its invoke targets or dependencies.
     * Scan every instruction, including branches AOT compilation can fold away. Signature-polymorphic
     * calls use a different AOT linkage than runtime-loaded calls, so their containing bodies cannot
     * be preserved: exact selections fail and wildcard selections keep ordinary AOT execution.
     * Required adapters are the narrow exception: their recognized MethodHandle dispatch entries are
     * hydrated as concrete-signature interpreter intrinsics.
     * Hosted linkage failures, including JFR-injected invocations, also reject the body; ordinary
     * unresolved entries keep their symbolic signatures and existing runtime behavior.
     */
    private static String findUnsupportedDirectInvocation(AnalysisMethod method, boolean defaultAdapter) {
        byte[] code = method.getCode();
        for (int bci = 0; bci < BytecodeStream.endBCI(code); bci = BytecodeStream.nextBCI(code, bci)) {
            int opcode = BytecodeStream.opcode(code, bci);
            if (opcode != Bytecodes.INVOKEVIRTUAL && opcode != Bytecodes.INVOKEINTERFACE && opcode != Bytecodes.INVOKESPECIAL && opcode != Bytecodes.INVOKESTATIC) {
                continue;
            }
            try {
                int cpi = BytecodeStream.readCPI(code, bci);
                JavaMethod callee = method.getConstantPool().lookupMethod(cpi, opcode);
                if (callee != null && isSignaturePolymorphicInvoke(callee) &&
                                !(defaultAdapter && callee instanceof AnalysisMethod analysisCallee && InterpreterFeature.isInterpreterMethodHandleIntrinsic(analysisCallee))) {
                    return "its signature-polymorphic invocation of '" + callee.format("%H.%n(%p)") + "' at BCI " + bci + " is unsupported for preserved AOT bytecodes";
                }
            } catch (IllegalAccessError | UnsupportedFeatureException | UserError.UserException e) {
                return "its direct invocation at BCI " + bci + " is not linkable by the hosted VM: " + e.getMessage();
            }
        }
        return null;
    }

    /**
     * Keeps existing lazy lookup failures separate from policy or registration failures. In
     * particular, a strict-policy UserError must not be mistaken for an unresolved pool entry.
     */
    static Object lookupBytecodeDependency(AnalysisMethod method, int bci, int opcode) {
        ConstantPool pool = method.getConstantPool();
        byte[] code = method.getCode();
        try {
            return switch (opcode) {
                case Bytecodes.NEW, Bytecodes.ANEWARRAY, Bytecodes.MULTIANEWARRAY, Bytecodes.CHECKCAST, Bytecodes.INSTANCEOF,
                                Bytecodes.INVOKEVIRTUAL, Bytecodes.INVOKEINTERFACE, Bytecodes.INVOKESTATIC, Bytecodes.INVOKESPECIAL -> lookupBytecodeReferencedType(method, bci, opcode);
                case Bytecodes.LDC, Bytecodes.LDC_W, Bytecodes.LDC2_W -> pool.lookupConstant(BytecodeStream.readCPI(code, bci));
                case Bytecodes.GETFIELD, Bytecodes.GETSTATIC, Bytecodes.PUTFIELD, Bytecodes.PUTSTATIC -> pool.lookupField(BytecodeStream.readCPI(code, bci), method, opcode);
                default -> null;
            };
        } catch (UnsupportedFeatureException | UserError.UserException | TypeNotPresentException e) {
            return null;
        }
    }

    /** Looks up the symbolic holder independently of the resolved member's declaring type. */
    static JavaType lookupBytecodeReferencedType(AnalysisMethod method, int bci, int opcode) {
        ConstantPool pool = method.getConstantPool();
        try {
            int cpi = BytecodeStream.readCPI(method.getCode(), bci);
            pool.loadReferencedType(cpi, opcode);
            return pool.lookupReferencedType(cpi, opcode);
        } catch (UnsupportedFeatureException | UserError.UserException | TypeNotPresentException e) {
            return null;
        }
    }
}
