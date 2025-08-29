/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter;

import static com.oracle.svm.interpreter.metadata.Bytecodes.ANEWARRAY;
import static com.oracle.svm.interpreter.metadata.Bytecodes.CHECKCAST;
import static com.oracle.svm.interpreter.metadata.Bytecodes.GETFIELD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.GETSTATIC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INSTANCEOF;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKEDYNAMIC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKEINTERFACE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKESPECIAL;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKESTATIC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKEVIRTUAL;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LDC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LDC2_W;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LDC_W;
import static com.oracle.svm.interpreter.metadata.Bytecodes.MULTIANEWARRAY;
import static com.oracle.svm.interpreter.metadata.Bytecodes.NEW;
import static com.oracle.svm.interpreter.metadata.Bytecodes.PUTFIELD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.PUTSTATIC;

import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.interpreter.classfile.ConstantPoolBuilder;
import com.oracle.svm.interpreter.metadata.BytecodeStream;
import com.oracle.svm.interpreter.metadata.Bytecodes;
import com.oracle.svm.interpreter.metadata.InterpreterConstantPool;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType;
import com.oracle.svm.interpreter.metadata.ReferenceConstant;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.UnresolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaMethod;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * Allows to incrementally build constant pools from JVMCI provided bytecodes. This constant pool
 * supports appending and de-duplicating constants, classes, fields, methods ...
 * <p>
 * Once the constant pool is finished e.g. all methods in the class are processed for the
 * interpreter, it must be transformed (via {@link #snapshot()}) into a runtime-ready
 * {@link InterpreterConstantPool}.
 */
@Platforms(Platform.HOSTED_ONLY.class)
final class BuildTimeConstantPool {

    private static final ExceptionHandler[] EMPTY_EXCEPTION_HANDLERS = new ExceptionHandler[0];

    private final ConstantPoolBuilder poolBuilder;

    /**
     * Creates runtime-ready constant pool for the interpreter.
     */
    public InterpreterConstantPool snapshot() {
        // Contains a partial parser/symbolic constant pool.
        return poolBuilder.build();
    }

    private BuildTimeConstantPool(InterpreterResolvedObjectType holder, int majorVersion, int minorVersion) {
        this.poolBuilder = new ConstantPoolBuilder(holder, majorVersion, minorVersion);
        // index 0 always contains an invalid entry
        int invalidIndex = this.poolBuilder.appendInvalid();
        assert invalidIndex == 0;
    }

    public int longConstant(long value) {
        PrimitiveConstant primitiveConstant = BuildTimeInterpreterUniverse.singleton().primitiveConstant(value);
        return poolBuilder.appendPrimitiveConstant(primitiveConstant);
    }

    public int intConstant(int value) {
        PrimitiveConstant primitiveConstant = BuildTimeInterpreterUniverse.singleton().primitiveConstant(value);
        return poolBuilder.appendPrimitiveConstant(primitiveConstant);
    }

    public int floatConstant(float value) {
        PrimitiveConstant primitiveConstant = BuildTimeInterpreterUniverse.singleton().primitiveConstant(value);
        return poolBuilder.appendPrimitiveConstant(primitiveConstant);
    }

    public int doubleConstant(double value) {
        PrimitiveConstant primitiveConstant = BuildTimeInterpreterUniverse.singleton().primitiveConstant(value);
        return poolBuilder.appendPrimitiveConstant(primitiveConstant);
    }

    public int stringConstant(String value) {
        String string = BuildTimeInterpreterUniverse.singleton().stringConstant(value);
        return poolBuilder.appendCachedString(string);
    }

    public int typeConstant(JavaType type) {
        if (!(type instanceof InterpreterResolvedJavaType || type instanceof UnresolvedJavaType)) {
            throw new IllegalArgumentException("Type must be either InterpreterResolvedJavaType or UnresolvedJavaType");
        }
        return poolBuilder.appendCachedType(type);
    }

    public int method(JavaMethod method) {
        if (!(method instanceof InterpreterResolvedJavaMethod || method instanceof UnresolvedJavaMethod)) {
            throw new IllegalArgumentException("Type must be either InterpreterResolvedJavaMethod or UnresolvedJavaMethod");
        }
        return poolBuilder.appendCachedMethod(false, method);
    }

    public int field(JavaField field) {
        if (!(field instanceof InterpreterResolvedJavaField || field instanceof UnresolvedJavaField)) {
            throw new IllegalArgumentException("Type must be either InterpreterResolvedJavaField or UnresolvedJavaField");
        }
        return poolBuilder.appendCachedField(field);
    }

    private int appendixConstant(JavaConstant appendix) {
        assert appendix instanceof ReferenceConstant || appendix.isNull();
        return poolBuilder.appendCachedAppendix(appendix);
    }

    public int weakObjectConstant(ImageHeapConstant imageHeapConstant) {
        JavaConstant javaConstant = BuildTimeInterpreterUniverse.singleton().weakObjectConstant(imageHeapConstant);
        // Can't put arbitrary objects on the CP, (ab)used INVOKEDYNAMIC tag as a workaround.
        return poolBuilder.appendCachedAppendix(javaConstant);
    }

    private int ldcConstant(Object javaConstantOrType) {
        if (javaConstantOrType instanceof JavaConstant javaConstant) {
            switch (javaConstant.getJavaKind()) {
                case Boolean, Byte, Short, Char, Int:
                    return intConstant(javaConstant.asInt());
                case Float:
                    return floatConstant(javaConstant.asFloat());
                case Long:
                    return longConstant(javaConstant.asLong());
                case Double:
                    return doubleConstant(javaConstant.asDouble());
                case Object:
                    if (javaConstant instanceof ImageHeapConstant imageHeapConstant) {
                        return weakObjectConstant(imageHeapConstant);
                    }
            }
        } else if (javaConstantOrType instanceof JavaType javaType) {
            JavaType interpreterType = BuildTimeInterpreterUniverse.singleton().typeOrUnresolved(javaType);
            return typeConstant(interpreterType);
        }
        throw VMError.shouldNotReachHereUnexpectedInput(javaConstantOrType);
    }

    public static BuildTimeConstantPool create(InterpreterResolvedObjectType type, int majorVersion, int minorVersion) {
        BuildTimeConstantPool btcp = new BuildTimeConstantPool(type, majorVersion, minorVersion);
        btcp.hydrate(type);
        return btcp;
    }

    private ExceptionHandler[] processExceptionHandlers(ExceptionHandler[] hostExceptionHandlers) {
        if (hostExceptionHandlers.length == 0) {
            return EMPTY_EXCEPTION_HANDLERS;
        }
        ExceptionHandler[] handlers = new ExceptionHandler[hostExceptionHandlers.length];
        for (int i = 0; i < handlers.length; i++) {
            ExceptionHandler host = hostExceptionHandlers[i];
            JavaType resolvedCatchType = null;
            JavaType interpreterCatchType = null;
            int catchTypeCPI = 0;
            if (!host.isCatchAll()) {
                resolvedCatchType = host.getCatchType();
                interpreterCatchType = BuildTimeInterpreterUniverse.singleton().typeOrUnresolved(resolvedCatchType);
                // catchTypeCPI must be patched.
                catchTypeCPI = typeConstant(interpreterCatchType);
            }

            handlers[i] = BuildTimeInterpreterUniverse.singleton()
                            .exceptionHandler(new ExceptionHandler(host.getStartBCI(), host.getEndBCI(), host.getHandlerBCI(), catchTypeCPI, interpreterCatchType));
        }
        return handlers;
    }

    public static boolean weedOut(InterpreterResolvedObjectType type, HostedUniverse hUniverse) {
        boolean chasingFixpoint = false;

        methodsLoop: for (InterpreterResolvedJavaMethod method : BuildTimeInterpreterUniverse.singleton().allDeclaredMethods(type)) {
            if (!method.needsMethodBody() || !method.isInterpreterExecutable()) {
                method.setCode(null);
            }

            byte[] code = method.getInterpretedCode();
            if (code == null || code.length == 0) {
                continue;
            }

            ResolvedJavaMethod originalMethod = method.getOriginalMethod();
            ConstantPool originalConstantPool = originalMethod.getConstantPool();
            for (int bci = 0; bci < BytecodeStream.endBCI(code); bci = BytecodeStream.nextBCI(code, bci)) {
                int bytecode = BytecodeStream.currentBC(code, bci);
                switch (bytecode) {
                    case INVOKEINTERFACE, INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEDYNAMIC -> {
                        int originalCPI;
                        if (bytecode == INVOKEDYNAMIC) {
                            originalCPI = BytecodeStream.readCPI4(code, bci);
                        } else {
                            originalCPI = BytecodeStream.readCPI(code, bci);
                        }
                        JavaMethod calleeOriginalJavaMethod = null;
                        try {
                            calleeOriginalJavaMethod = originalConstantPool.lookupMethod(originalCPI, bytecode);
                        } catch (UnsupportedFeatureException | UserError.UserException e) {
                            // ignore
                        }
                        if (calleeOriginalJavaMethod != null) {
                            JavaMethod calleeInterpreterMethod = BuildTimeInterpreterUniverse.singleton().methodOrUnresolved(calleeOriginalJavaMethod);
                            if (calleeInterpreterMethod instanceof InterpreterResolvedJavaMethod calleeInterpreterResolvedJavaMethod) {
                                if (!calleeInterpreterResolvedJavaMethod.isInterpreterExecutable()) {
                                    HostedMethod calleeHostedMethod = hUniverse.optionalLookup(calleeOriginalJavaMethod);
                                    if (calleeHostedMethod.isCompiled()) {
                                        InterpreterUtil.log("[weedout] good. Call from %s @ bci=%s (interp) to %s (compiled) possible", method, bci, calleeInterpreterResolvedJavaMethod);
                                    } else if (calleeHostedMethod.hasVTableIndex()) {
                                        InterpreterUtil.log("[weedout] good. Virtual call from %s @ bci=%s (interp) to %s (compiled) possible", method, bci, calleeInterpreterResolvedJavaMethod);
                                    } else if (calleeHostedMethod.getImplementations().length == 1) {
                                        HostedMethod impl = calleeHostedMethod.getImplementations()[0];
                                        if (!(impl.isCompiled() || isInterpreterExecutable(impl))) {
                                            weedOut(method, BuildTimeInterpreterUniverse.singleton().methodOrUnresolved(impl), impl, true);
                                            chasingFixpoint = true;
                                            continue methodsLoop;
                                        }
                                        assert impl.isCompiled() || isInterpreterExecutable(impl) : calleeHostedMethod + ", implementation: " + impl + ", ";
                                        InterpreterUtil.log("[weedout] good. Virtual call from %s @ bci=%s (interp) has exactly one implementation available %s", method, bci,
                                                        calleeHostedMethod.getImplementations()[0]);
                                    } else if (!DebuggerFeature.isReachable(calleeHostedMethod.getWrapped())) {
                                        /*
                                         * not reached during analysis, let it fail during runtime
                                         * if this call-site is reached
                                         */
                                    } else {
                                        weedOut(method, calleeInterpreterResolvedJavaMethod, calleeHostedMethod, false);
                                        chasingFixpoint = true;
                                        continue methodsLoop;
                                    }
                                } else {
                                    /* the interpreter can dispatch the callee */
                                }
                            } else {
                                /*
                                 * not reached during analysis, let it fail during runtime if this
                                 * call-site is reached
                                 */
                            }
                        } else {
                            InterpreterUtil.log("[weedout] ??? call from %s at bci=%s does not go anywhere", method, bci);
                        }
                    }
                }
            }
        }
        return chasingFixpoint;
    }

    private static boolean isInterpreterExecutable(HostedMethod impl) {
        JavaMethod method = BuildTimeInterpreterUniverse.singleton().methodOrUnresolved(impl);
        if (method instanceof InterpreterResolvedJavaMethod interpreterResolvedJavaMethod) {
            return interpreterResolvedJavaMethod.isInterpreterExecutable();
        }
        return false;
    }

    private static void weedOut(InterpreterResolvedJavaMethod method, JavaMethod calleeInterpreterResolvedJavaMethod, HostedMethod calleeHostedMethod, boolean singleCalleeImpl) {
        InterpreterUtil.log("[weedout] bad. %s downgraded to non-interpreter-executable.", method);
        if (singleCalleeImpl) {
            InterpreterUtil.log("          there is no way to call the single callee implementation %s, but it is considered reachable",
                            calleeInterpreterResolvedJavaMethod);
        } else {
            InterpreterUtil.log("          there is no way to call a compiled version of %s or to execute it in the interpreter, but it is considered reachable",
                            calleeInterpreterResolvedJavaMethod);
        }
        assert DebuggerFeature.isReachable(calleeHostedMethod.getWrapped()) : calleeHostedMethod;
        method.setCode(null);
    }

    public void hydrate(InterpreterResolvedObjectType type) {

        List<InterpreterResolvedJavaMethod> allDeclaredMethods = BuildTimeInterpreterUniverse.singleton().allDeclaredMethods(type);

        // LDC (single-byte CPI) bytecodes must be processed first.
        processLDC(allDeclaredMethods);

        for (InterpreterResolvedJavaMethod method : allDeclaredMethods) {
            ResolvedJavaMethod originalMethod = method.getOriginalMethod();
            method.setExceptionHandlers(processExceptionHandlers(originalMethod.getExceptionHandlers()));

            LocalVariableTable hostLocalVariableTable = method.getOriginalMethod().getLocalVariableTable();
            if (hostLocalVariableTable != null) {
                method.setLocalVariableTable(BuildTimeInterpreterUniverse.processLocalVariableTable(hostLocalVariableTable));
            }

            if (!method.needsMethodBody()) {
                VMError.guarantee(method.getInterpretedCode() == null);
            }

            byte[] code = method.getInterpretedCode();
            if (code == null || code.length == 0) {
                continue;
            }

            InterpreterUtil.log("[hydrate] processing method=%s", method);

            ConstantPool originalConstantPool = originalMethod.getConstantPool();
            for (int bci = 0; bci < BytecodeStream.endBCI(code); bci = BytecodeStream.nextBCI(code, bci)) {
                int bytecode = BytecodeStream.currentBC(code, bci);
                switch (bytecode) {
                    case LDC: // fall-through
                    case LDC_W: // fall-through
                    case LDC2_W: {
                        int originalCPI = BytecodeStream.readCPI(code, bci);
                        int newCPI = 0;
                        // GR-44571: Somehow obtain an unresolved type to print useful error
                        // at runtime.
                        try {
                            Object originalConstant = originalConstantPool.lookupConstant(originalCPI);
                            newCPI = ldcConstant(originalConstant);
                        } catch (UnsupportedFeatureException | AnalysisError.TypeNotFoundError e) {
                            // cannot resolve type, ignore
                        }
                        BytecodeStream.patchCPI(code, bci, newCPI);
                        break;
                    }
                    case GETSTATIC: // fall-through
                    case PUTSTATIC: // fall-through
                    case GETFIELD: // fall-through
                    case PUTFIELD: {
                        int originalCPI = BytecodeStream.readCPI(code, bci);
                        int newCPI = 0;
                        JavaField originalJavaField = null;
                        try {
                            originalJavaField = originalConstantPool.lookupField(originalCPI, originalMethod, bytecode);
                        } catch (UnsupportedFeatureException e) {
                            // ignore
                        }
                        // GR-44571: Somehow obtain an unresolved field to print useful error
                        // at runtime.
                        if (originalJavaField != null) {
                            JavaField interpreterField = BuildTimeInterpreterUniverse.singleton().fieldOrUnresolved(originalJavaField);
                            newCPI = field(interpreterField);
                        }
                        BytecodeStream.patchCPI(code, bci, newCPI);
                        break;
                    }
                    case ANEWARRAY: // fall-through
                    case MULTIANEWARRAY: // fall-through
                    case NEW: // fall-through
                    case INSTANCEOF: // fall-through
                    case CHECKCAST: {
                        int originalCPI = BytecodeStream.readCPI(code, bci);
                        int newCPI = 0;
                        JavaType originalJavaType = null;
                        try {
                            originalJavaType = originalConstantPool.lookupType(originalCPI, bytecode);
                        } catch (UnsupportedFeatureException | AnalysisError.TypeNotFoundError e) {
                            // GR-44571: Type has not been seen during analysis (e.g. path
                            // has not been reached).
                            // Will patch the CPI with 0.
                        }

                        // GR-44571: Somehow obtain an unresolved type to print useful error
                        // at runtime.
                        if (originalJavaType != null) {
                            JavaType interpreterType = BuildTimeInterpreterUniverse.singleton().typeOrUnresolved(originalJavaType);
                            newCPI = typeConstant(interpreterType);
                        }
                        BytecodeStream.patchCPI(code, bci, newCPI);
                        break;
                    }
                    case INVOKEINTERFACE: // fall-through
                    case INVOKEVIRTUAL: // fall-through
                    case INVOKESPECIAL: // fall-through
                    case INVOKESTATIC:
                    case INVOKEDYNAMIC: {
                        int originalCPI;
                        if (bytecode == INVOKEDYNAMIC) {
                            originalCPI = BytecodeStream.readCPI4(code, bci);
                        } else {
                            originalCPI = BytecodeStream.readCPI(code, bci);
                        }
                        JavaMethod originalJavaMethod = null;
                        int newCPI = 0;
                        try {
                            originalJavaMethod = originalConstantPool.lookupMethod(originalCPI, bytecode);
                        } catch (UnsupportedFeatureException | UserError.UserException e) {
                            // ignore
                        }
                        // GR-44571: Somehow obtain an unresolved method to print useful
                        // error at runtime.
                        if (originalJavaMethod != null) {
                            JavaMethod interpreterMethod = BuildTimeInterpreterUniverse.singleton().methodOrUnresolved(originalJavaMethod);
                            if (interpreterMethod instanceof InterpreterResolvedJavaMethod) {
                                ((InterpreterResolvedJavaMethod) interpreterMethod).setNativeEntryPoint(new MethodPointer((ResolvedJavaMethod) originalJavaMethod));
                                InterpreterUtil.log("[hydrate] setting method pointer for %s", interpreterMethod);
                            }
                            newCPI = method(interpreterMethod);
                        }

                        if (bytecode == INVOKEDYNAMIC) {
                            int newAppendixCPI = 0;
                            JavaConstant appendix = originalConstantPool.lookupAppendix(originalCPI, bytecode);
                            if (appendix != null) {
                                JavaConstant interpreterAppendix = BuildTimeInterpreterUniverse.singleton().appendix(appendix);
                                newAppendixCPI = appendixConstant(interpreterAppendix);
                            } else {
                                // The appendix may be null, in which case a NullConstant is stored
                                // in the CP.
                                newAppendixCPI = appendixConstant(JavaConstant.NULL_POINTER);
                            }
                            BytecodeStream.patchAppendixCPI(code, bci, newAppendixCPI);
                        }

                        BytecodeStream.patchCPI(code, bci, newCPI);
                        break;
                    }
                }
            }
        }
    }

    private void processLDC(List<InterpreterResolvedJavaMethod> allDeclaredMethods) {
        for (InterpreterResolvedJavaMethod method : allDeclaredMethods) {
            byte[] code = method.getInterpretedCode();
            if (code == null || code.length == 0) {
                continue;
            }
            ConstantPool originalConstantPool = method.getOriginalMethod().getConstantPool();
            for (int bci = 0; bci < BytecodeStream.endBCI(code); bci = BytecodeStream.nextBCI(code, bci)) {
                if (BytecodeStream.opcode(code, bci) == Bytecodes.LDC) {
                    try {
                        Object constant = originalConstantPool.lookupConstant(BytecodeStream.readCPI(code, bci));
                        ldcConstant(constant);
                    } catch (UnsupportedFeatureException | AnalysisError.TypeNotFoundError e) {
                        // constant cannot be resolved, ignore
                    }
                }
            }
        }
    }
}
