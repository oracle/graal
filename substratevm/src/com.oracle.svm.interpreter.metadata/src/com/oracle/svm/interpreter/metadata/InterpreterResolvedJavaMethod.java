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
package com.oracle.svm.interpreter.metadata;

import static com.oracle.svm.espresso.classfile.Constants.ACC_CALLER_SENSITIVE;
import static com.oracle.svm.espresso.classfile.Constants.ACC_FINAL;
import static com.oracle.svm.espresso.classfile.Constants.ACC_NATIVE;
import static com.oracle.svm.espresso.classfile.Constants.ACC_SIGNATURE_POLYMORPHIC;
import static com.oracle.svm.espresso.classfile.Constants.ACC_STATIC;
import static com.oracle.svm.espresso.classfile.Constants.ACC_SYNTHETIC;
import static com.oracle.svm.espresso.classfile.Constants.ACC_VARARGS;
import static com.oracle.svm.espresso.classfile.Constants.JVM_RECOGNIZED_METHOD_MODIFIERS;
import static com.oracle.svm.interpreter.metadata.Bytecodes.BREAKPOINT;
import static com.oracle.svm.interpreter.metadata.CremaMethodAccess.toJVMCI;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.FunctionPointerHolder;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.hub.crema.CremaSupport;
import com.oracle.svm.core.hub.registry.SymbolsSupport;
import com.oracle.svm.core.invoke.ResolvedMember;
import com.oracle.svm.core.invoke.Target_java_lang_invoke_MemberName;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.JavaVersion;
import com.oracle.svm.espresso.classfile.ParserMethod;
import com.oracle.svm.espresso.classfile.attributes.CodeAttribute;
import com.oracle.svm.espresso.classfile.descriptors.ByteSequence;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.ParserSymbols;
import com.oracle.svm.espresso.classfile.descriptors.Signature;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.shared.meta.SignaturePolymorphicIntrinsic;
import com.oracle.svm.espresso.shared.vtable.PartialMethod;
import com.oracle.svm.interpreter.metadata.serialization.VisibleForSerialization;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Encapsulates resolved methods used under close-world assumptions, compiled and interpretable, but
 * also abstract methods for vtable calls.
 */
public class InterpreterResolvedJavaMethod implements ResolvedJavaMethod, CremaMethodAccess, ResolvedMember {
    @Platforms(Platform.HOSTED_ONLY.class)//
    @SuppressWarnings("unchecked") //
    private static final Class<? extends Annotation> CALLER_SENSITIVE_CLASS = (Class<? extends Annotation>) ReflectionUtil.lookupClass("jdk.internal.reflect.CallerSensitive");
    /**
     * This flag denotes a method that was originally native but was substituted by a non-native
     * method.
     */
    private static final int ACC_SUBSTITUTED_NATIVE = 0x80000000;

    public static final InterpreterResolvedJavaMethod[] EMPTY_ARRAY = new InterpreterResolvedJavaMethod[0];
    public static final LocalVariableTable EMPTY_LOCAL_VARIABLE_TABLE = new LocalVariableTable(new Local[0]);
    public static final ExceptionHandler[] EMPTY_EXCEPTION_HANDLERS = new ExceptionHandler[0];

    public static final int UNKNOWN_METHOD_ID = 0;

    private final Symbol<Signature> signatureSymbol;

    // Should be final (not its contents, it can be patched with BREAKPOINT).
    // These are the bytecodes executed by the interpreter e.g. can be patched with BREAKPOINT.
    private byte[] interpretedCode;
    private final Symbol<Name> name;
    private final int maxLocals;
    private final int maxStackSize;
    /**
     * Contains standard modifiers in the low 16 bits, and non-standard flags in the upper 16 bits.
     *
     * @see #ACC_SUBSTITUTED_NATIVE
     * @see com.oracle.svm.espresso.classfile.Constants
     */
    private final int flags;

    @Platforms(Platform.HOSTED_ONLY.class) //
    private ResolvedJavaMethod originalMethod;

    private final InterpreterResolvedObjectType declaringClass;
    private final InterpreterUnresolvedSignature signature;

    private final LineNumberTable lineNumberTable;

    protected ExceptionHandler[] exceptionHandlers;

    private LocalVariableTable localVariableTable;

    private ReferenceConstant<FunctionPointerHolder> nativeEntryPoint;

    // Token set by the toggle of method enter/exit events.
    private volatile Object interpreterExecToken;

    public static class InlinedBy {
        public InterpreterResolvedJavaMethod holder;
        public Set<InterpreterResolvedJavaMethod> inliners;

        public InlinedBy(InterpreterResolvedJavaMethod holder, Set<InterpreterResolvedJavaMethod> inliners) {
            this.holder = holder;
            this.inliners = inliners;
        }
    }

    protected final InlinedBy inlinedBy;

    public static final int VTBL_NO_ENTRY = -1;
    public static final int VTBL_ONE_IMPL = -2;
    private int vtableIndex = VTBL_NO_ENTRY;
    private InterpreterResolvedJavaMethod oneImplementation;

    /* slot in GOT */
    private int gotOffset;

    public static final int EST_NO_ENTRY = -5;
    /* slot in EST (EnterStubTable) */
    private int enterStubOffset = EST_NO_ENTRY;

    /**
     * Unique identifier for methods in compiled frames.
     * <p>
     * Only valid if != 0, 0 means unknown. Allows to precisely extract the
     * {@link InterpreterResolvedJavaMethod interpreter method instance} from a compiled frame.
     */
    private int methodId;

    @Platforms(Platform.HOSTED_ONLY.class) public boolean needMethodBody;

    private final SignaturePolymorphicIntrinsic intrinsic;

    // Only called during universe building
    @Platforms(Platform.HOSTED_ONLY.class)
    private InterpreterResolvedJavaMethod(ResolvedJavaMethod originalMethod, Symbol<Name> name, int maxLocals, int maxStackSize, int flags,
                    InterpreterResolvedObjectType declaringClass, InterpreterUnresolvedSignature signature, Symbol<Signature> signatureSymbol,
                    byte[] code, ExceptionHandler[] exceptionHandlers, LineNumberTable lineNumberTable, LocalVariableTable localVariableTable,
                    ReferenceConstant<FunctionPointerHolder> nativeEntryPoint, int vtableIndex, int gotOffset, int enterStubOffset, int methodId) {
        this.name = MetadataUtil.requireNonNull(name);
        this.maxLocals = maxLocals;
        this.maxStackSize = maxStackSize;
        this.flags = flags;
        this.declaringClass = MetadataUtil.requireNonNull(declaringClass);
        this.signature = MetadataUtil.requireNonNull(signature);
        this.signatureSymbol = MetadataUtil.requireNonNull(signatureSymbol);
        this.interpretedCode = code;
        this.exceptionHandlers = exceptionHandlers;
        this.lineNumberTable = lineNumberTable;
        this.localVariableTable = localVariableTable;
        this.nativeEntryPoint = nativeEntryPoint;
        this.vtableIndex = vtableIndex;
        this.gotOffset = gotOffset;
        this.enterStubOffset = enterStubOffset;
        this.methodId = methodId;
        this.inlinedBy = new InlinedBy(this, new HashSet<>());
        this.intrinsic = null;

        this.originalMethod = originalMethod;
    }

    // Used at run-time for deserialization
    private InterpreterResolvedJavaMethod(Symbol<Name> name, int maxLocals, int maxStackSize, int flags,
                    InterpreterResolvedObjectType declaringClass, InterpreterUnresolvedSignature signature, Symbol<Signature> signatureSymbol,
                    byte[] code, ExceptionHandler[] exceptionHandlers, LineNumberTable lineNumberTable, LocalVariableTable localVariableTable,
                    ReferenceConstant<FunctionPointerHolder> nativeEntryPoint, int vtableIndex, int gotOffset, int enterStubOffset, int methodId) {
        this.name = MetadataUtil.requireNonNull(name);
        this.maxLocals = maxLocals;
        this.maxStackSize = maxStackSize;
        this.flags = flags;
        this.declaringClass = MetadataUtil.requireNonNull(declaringClass);
        this.signature = MetadataUtil.requireNonNull(signature);
        this.signatureSymbol = MetadataUtil.requireNonNull(signatureSymbol);
        this.interpretedCode = code;
        this.exceptionHandlers = exceptionHandlers;
        this.lineNumberTable = lineNumberTable;
        this.localVariableTable = localVariableTable;
        this.nativeEntryPoint = nativeEntryPoint;
        this.vtableIndex = vtableIndex;
        this.gotOffset = gotOffset;
        this.enterStubOffset = enterStubOffset;
        this.methodId = methodId;
        this.inlinedBy = new InlinedBy(this, new HashSet<>());
        this.intrinsic = null;
    }

    // Used at run-time for signature-polymorphic instantiation
    private InterpreterResolvedJavaMethod(Symbol<Name> name, int maxLocals, int flags,
                    InterpreterResolvedObjectType declaringClass, InterpreterUnresolvedSignature signature, Symbol<Signature> signatureSymbol,
                    int vtableIndex, int gotOffset, int enterStubOffset, int methodId, SignaturePolymorphicIntrinsic intrinsic) {
        this.name = MetadataUtil.requireNonNull(name);
        this.maxLocals = maxLocals;
        this.maxStackSize = 0;
        this.flags = flags;
        this.declaringClass = MetadataUtil.requireNonNull(declaringClass);
        this.signature = MetadataUtil.requireNonNull(signature);
        this.signatureSymbol = MetadataUtil.requireNonNull(signatureSymbol);
        // not bytecode-interpretable
        this.interpretedCode = null;
        this.exceptionHandlers = null;
        this.lineNumberTable = null;
        this.localVariableTable = null;
        this.nativeEntryPoint = null;
        this.vtableIndex = vtableIndex;
        this.gotOffset = gotOffset;
        this.enterStubOffset = enterStubOffset;
        this.methodId = methodId;
        this.inlinedBy = null;
        this.intrinsic = MetadataUtil.requireNonNull(intrinsic);
    }

    // Used at run-time for the crema sub-class
    protected InterpreterResolvedJavaMethod(InterpreterResolvedObjectType declaringClass, ParserMethod m, int vtableIndex) {
        assert RuntimeClassLoading.isSupported();
        this.name = MetadataUtil.requireNonNull(m.getName());
        this.signatureSymbol = MetadataUtil.requireNonNull(m.getSignature());
        this.declaringClass = MetadataUtil.requireNonNull(declaringClass);
        this.flags = m.getFlags();
        assert (flags & ACC_SUBSTITUTED_NATIVE) == 0;
        CodeAttribute codeAttribute = (CodeAttribute) m.getAttribute(CodeAttribute.NAME);
        if (codeAttribute != null) {
            this.maxLocals = codeAttribute.getMaxLocals();
            this.maxStackSize = codeAttribute.getMaxStack() + 1;
            this.interpretedCode = codeAttribute.getOriginalCode();
            this.lineNumberTable = CremaMethodAccess.toJVMCI(codeAttribute.getLineNumberTableAttribute());
        } else {
            this.maxLocals = 0;
            this.maxStackSize = 0;
            this.interpretedCode = null;
            this.lineNumberTable = null;
        }
        this.signature = CremaMethodAccess.toJVMCI(m.getSignature(), SymbolsSupport.getTypes());

        this.vtableIndex = vtableIndex;
        this.nativeEntryPoint = null;

        this.gotOffset = -2 /* -GOT_NO_ENTRY */;
        this.enterStubOffset = EST_NO_ENTRY;
        this.methodId = UNKNOWN_METHOD_ID;
        this.inlinedBy = null;
        this.intrinsic = null;
    }

    @VisibleForSerialization
    public static InterpreterResolvedJavaMethod createForDeserialization(String name, int maxLocals, int maxStackSize, int flags, InterpreterResolvedObjectType declaringClass,
                    InterpreterUnresolvedSignature signature,
                    byte[] code, ExceptionHandler[] exceptionHandlers, LineNumberTable lineNumberTable, LocalVariableTable localVariableTable,
                    ReferenceConstant<FunctionPointerHolder> nativeEntryPoint, int vtableIndex, int gotOffset, int enterStubOffset, int methodId) {
        Symbol<Name> nameSymbol = SymbolsSupport.getNames().getOrCreate(name);
        Symbol<Signature> signatureSymbol = toSymbol(signature);
        return new InterpreterResolvedJavaMethod(nameSymbol, maxLocals, maxStackSize, flags, declaringClass, signature, signatureSymbol, code,
                        exceptionHandlers, lineNumberTable, localVariableTable, nativeEntryPoint, vtableIndex, gotOffset, enterStubOffset, methodId);
    }

    // Only called during universe building
    @Platforms(Platform.HOSTED_ONLY.class)
    public static InterpreterResolvedJavaMethod createAtBuildTime(ResolvedJavaMethod originalMethod, String name, int maxLocals, int maxStackSize, int modifiers,
                    InterpreterResolvedObjectType declaringClass,
                    InterpreterUnresolvedSignature signature, boolean isSubstitutedNative,
                    byte[] code, ExceptionHandler[] exceptionHandlers, LineNumberTable lineNumberTable, LocalVariableTable localVariableTable,
                    ReferenceConstant<FunctionPointerHolder> nativeEntryPoint, int vtableIndex, int gotOffset, int enterStubOffset, int methodId) {
        Symbol<Name> nameSymbol = SymbolsSupport.getNames().getOrCreate(name);
        Symbol<Signature> signatureSymbol = toSymbol(signature);
        int flags = createFlags(modifiers, declaringClass, signatureSymbol, isSubstitutedNative, originalMethod);
        return new InterpreterResolvedJavaMethod(originalMethod, nameSymbol, maxLocals, maxStackSize, flags, declaringClass, signature, signatureSymbol, code,
                        exceptionHandlers, lineNumberTable, localVariableTable, nativeEntryPoint, vtableIndex, gotOffset, enterStubOffset, methodId);
    }

    private static Symbol<Signature> toSymbol(InterpreterUnresolvedSignature jvmciSignature) {
        // hidden classes and SVM stable proxy name contain a `.`, replace with a `+`
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (int i = 0; i < jvmciSignature.getParameterCount(false); i++) {
            sb.append(jvmciSignature.getParameterType(i, null).getName().replace('.', '+'));
        }
        sb.append(')');
        sb.append(jvmciSignature.getReturnType(null).getName().replace('.', '+'));
        Symbol<Signature> symbol = SymbolsSupport.getSignatures().getOrCreateValidSignature(ByteSequence.create(sb.toString()));
        assert symbol != null : jvmciSignature;
        return symbol;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static int createFlags(int modifiers, InterpreterResolvedObjectType declaringClass, Symbol<Signature> signatureSymbol, boolean isSubstitutedNative, ResolvedJavaMethod originalMethod) {
        int newModifiers = modifiers;
        if (ParserMethod.isDeclaredSignaturePolymorphic(declaringClass.getSymbolicType(), signatureSymbol, getOriginalModifiers(modifiers, isSubstitutedNative), JavaVersion.HOST_VERSION)) {
            newModifiers |= ACC_SIGNATURE_POLYMORPHIC;
        }
        if (isSubstitutedNative) {
            newModifiers |= ACC_SUBSTITUTED_NATIVE;
        }
        if (AnnotationAccess.isAnnotationPresent(originalMethod, CALLER_SENSITIVE_CLASS)) {
            newModifiers |= ACC_CALLER_SENSITIVE;
        }
        return newModifiers;
    }

    public final boolean isCallerSensitive() {
        return (flags & ACC_CALLER_SENSITIVE) != 0;
    }

    @Override
    public InterpreterResolvedJavaMethod findSignaturePolymorphicIntrinsic(Symbol<Signature> methodSignature) {
        return (InterpreterResolvedJavaMethod) CremaSupport.singleton().findMethodHandleIntrinsic(this, methodSignature);
    }

    @Override
    public final boolean isDeclaredSignaturePolymorphic() {
        // Note: might not be true for the instantiation of polymorphic signature intrinsics.
        return (flags & ACC_SIGNATURE_POLYMORPHIC) != 0;
    }

    @Override
    public final InterpreterResolvedJavaMethod createSignaturePolymorphicIntrinsic(Symbol<Signature> newSignature) {
        SignaturePolymorphicIntrinsic iid = SignaturePolymorphicIntrinsic.getId(this);
        assert iid != null;
        assert intrinsic == null;
        int newModifiers;
        boolean isSubstitutedNative = (flags & ACC_SUBSTITUTED_NATIVE) != 0;
        if (iid == SignaturePolymorphicIntrinsic.InvokeGeneric) {
            newModifiers = getOriginalModifiers(flags, isSubstitutedNative) & ~(ACC_VARARGS | ACC_SUBSTITUTED_NATIVE | ACC_SIGNATURE_POLYMORPHIC);
        } else {
            newModifiers = ACC_NATIVE | ACC_SYNTHETIC | ACC_FINAL;
            if (iid.isStaticSignaturePolymorphic()) {
                newModifiers |= ACC_STATIC;
            }
        }
        assert Modifier.isNative(newModifiers);
        InterpreterUnresolvedSignature jvmciSignature = CremaMethodAccess.toJVMCI(newSignature, SymbolsSupport.getTypes());
        return new InterpreterResolvedJavaMethod(name, jvmciSignature.getParameterCount(true), newModifiers, declaringClass, jvmciSignature, newSignature,
                        vtableIndex, gotOffset, enterStubOffset, methodId, iid);
    }

    private static int getOriginalModifiers(int modifiers, boolean isSubstitutedNative) {
        return modifiers | (isSubstitutedNative ? Modifier.NATIVE : 0);
    }

    public final SignaturePolymorphicIntrinsic getSignaturePolymorphicIntrinsic() {
        return intrinsic;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public final boolean needsMethodBody() {
        return needMethodBody;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public final ResolvedJavaMethod getOriginalMethod() {
        return originalMethod;
    }

    /**
     * Returns the bytecodes executed by the interpreter, may include BREAKPOINT and other
     * non-standard bytecodes used by the interpreter. For a spec-compliant, without BREAKPOINT and
     * non-standard bytecodes use {@link #getCode()}
     */
    public final byte[] getInterpretedCode() {
        return interpretedCode;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public final void setCode(byte[] code) {
        VMError.guarantee(originalCode == null);
        this.interpretedCode = code;
    }

    private volatile byte[] originalCode;

    public final int getOriginalOpcodeAt(int bci) {
        return getCode()[bci] & 0xFF;
    }

    @Override
    public final byte[] getCode() {
        if (interpretedCode == null) {
            return null;
        }
        byte[] result = originalCode;
        if (result == null) {
            synchronized (this) {
                result = originalCode;
                if (result == null) {
                    originalCode = result = getInterpretedCode().clone();
                    verifySanitizedCode(result); // assert
                }
            }
        }
        return result;
    }

    private static void verifySanitizedCode(byte[] code) {
        for (int bci = 0; bci < BytecodeStream.endBCI(code); bci = BytecodeStream.nextBCI(code, bci)) {
            int currentBC = BytecodeStream.currentBC(code, bci);
            VMError.guarantee(Bytecodes.BREAKPOINT != currentBC);
        }
    }

    @Override
    public final int getCodeSize() {
        if (interpretedCode == null) {
            return 0;
        }
        return interpretedCode.length;
    }

    @Override
    public final Symbol<Name> getSymbolicName() {
        return name;
    }

    @Override
    public final Symbol<Signature> getSymbolicSignature() {
        return signatureSymbol;
    }

    @Override
    public final String getName() {
        return name.toString();
    }

    @Override
    public final InterpreterResolvedObjectType getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public final InterpreterUnresolvedSignature getSignature() {
        return signature;
    }

    @Override
    public final int getMaxLocals() {
        return maxLocals;
    }

    @Override
    public final int getMaxStackSize() {
        return maxStackSize;
    }

    @Override
    public final boolean isDeclared() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final boolean isClassInitializer() {
        return ParserSymbols.ParserNames._clinit_ == getSymbolicName() && isStatic();
    }

    @Override
    public final boolean isConstructor() {
        return ParserSymbols.ParserNames._init_ == getSymbolicName() && !isStatic();
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        ExceptionHandler[] result = exceptionHandlers;
        VMError.guarantee(result != null);
        return result;
    }

    @Override
    public final InterpreterConstantPool getConstantPool() {
        return declaringClass.getConstantPool();
    }

    @Override
    public final LineNumberTable getLineNumberTable() {
        return lineNumberTable;
    }

    @Override
    public final LocalVariableTable getLocalVariableTable() {
        return localVariableTable;
    }

    @Override
    public final int getModifiers() {
        return flags & JVM_RECOGNIZED_METHOD_MODIFIERS;
    }

    public int getFlags() {
        return flags;
    }

    @Override
    public final String toString() {
        return "InterpreterResolvedJavaMethod<holder=" + getDeclaringClass().getName() + " name=" + getName() + " descriptor=" + getSignature().toMethodDescriptor() + ">";
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public final void setExceptionHandlers(ExceptionHandler[] exceptionHandlers) {
        this.exceptionHandlers = MetadataUtil.requireNonNull(exceptionHandlers);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public final void setLocalVariableTable(LocalVariableTable localVariableTable) {
        this.localVariableTable = MetadataUtil.requireNonNull(localVariableTable);
    }

    private void patchOpcode(int bci, int newOpcode) {
        BytecodeStream.patchOpcodeOpaque(interpretedCode, bci, newOpcode);
    }

    public final void ensureCanSetBreakpointAt(int bci) {
        if (!hasBytecodes()) {
            throw new IllegalArgumentException("Cannot set breakpoint: method " + name + " doesn't have bytecodes");
        }
        if (bci < 0 || getCodeSize() <= bci) {
            throw new IllegalArgumentException("Cannot set breakpoint: BCI out of bounds");
        }
        if (!isValidBCI(getCode(), bci)) {
            throw new IllegalArgumentException("Cannot set breakpoint: targetBCI is not a valid first opcode");
        }
    }

    public final void toggleBreakpoint(int bci, boolean enabled) {
        ensureCanSetBreakpointAt(bci);
        if (enabled) {
            patchOpcode(bci, BREAKPOINT);
        } else {
            int originalOpcode = getOriginalOpcodeAt(bci);
            patchOpcode(bci, originalOpcode);
        }
    }

    public static boolean isValidBCI(byte[] code, int targetBCI) {
        for (int bci = 0; bci < BytecodeStream.endBCI(code); bci = BytecodeStream.nextBCI(code, bci)) {
            if (bci == targetBCI) {
                return true;
            }
        }
        return false;
    }

    /**
     * Unique identifier for methods in compiled frames.
     * <p>
     * Only valid if != 0, 0 means unknown. Allows to precisely extract the
     * {@link InterpreterResolvedJavaMethod interpreter method instance} from a compiled frame.
     */
    public final int getMethodId() {
        return methodId;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public final void setMethodId(int methodId) {
        assert methodId >= 0;
        this.methodId = methodId;
    }

    public final void setGOTOffset(int gotOffset) {
        this.gotOffset = gotOffset;
    }

    public final int getGotOffset() {
        return gotOffset;
    }

    public final void setEnterStubOffset(int offset) {
        this.enterStubOffset = offset;
    }

    public final int getEnterStubOffset() {
        return enterStubOffset;
    }

    public final boolean hasNativeEntryPoint() {
        return nativeEntryPoint != null;
    }

    public final MethodPointer getNativeEntryPoint() {
        if (nativeEntryPoint == null) {
            return Word.nullPointer();
        }
        return (MethodPointer) nativeEntryPoint.getReferent().functionPointer;
    }

    public final ReferenceConstant<FunctionPointerHolder> getNativeEntryPointHolderConstant() {
        return nativeEntryPoint;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public final void setNativeEntryPoint(MethodPointer nativeEntryPoint) {
        if (this.nativeEntryPoint != null && nativeEntryPoint != null) {
            /* already set, verify if it's the same */
            ResolvedJavaMethod setMethod = ((MethodPointer) this.nativeEntryPoint.getReferent().functionPointer).getMethod();
            VMError.guarantee(setMethod.equals(nativeEntryPoint.getMethod()));
            return;
        }

        if (nativeEntryPoint == null) {
            this.nativeEntryPoint = null;
        } else {
            this.nativeEntryPoint = ReferenceConstant.createFromNonNullReference(new FunctionPointerHolder(nativeEntryPoint));
        }
    }

    public final int getVTableIndex() {
        return vtableIndex;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public final void setVTableIndex(int vtableIndex) {
        VMError.guarantee(vtableIndex == VTBL_NO_ENTRY || (!isStatic() && !isConstructor()));
        if (vtableIndex >= 0) {
            VMError.guarantee(!isFinal());
        }
        this.vtableIndex = vtableIndex;
    }

    @Override
    public final boolean hasVTableIndex() {
        return vtableIndex != VTBL_NO_ENTRY && vtableIndex != VTBL_ONE_IMPL;
    }

    public final void setOneImplementation(InterpreterResolvedJavaMethod oneImplementation) {
        this.oneImplementation = oneImplementation;
    }

    public final InterpreterResolvedJavaMethod getOneImplementation() {
        /* if VTBL_ONE_IMPL is set, oneImplementation must have an assignment */
        VMError.guarantee(vtableIndex != VTBL_ONE_IMPL || oneImplementation != null);
        return oneImplementation;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InterpreterResolvedJavaMethod)) {
            return false;
        }
        InterpreterResolvedJavaMethod that = (InterpreterResolvedJavaMethod) other;
        return name.equals(that.name) && declaringClass.equals(that.declaringClass) && signature.equals(that.signature);
    }

    @Override
    public final int hashCode() {
        int result = MetadataUtil.hashCode(name);
        result = 31 * result + MetadataUtil.hashCode(declaringClass);
        result = 31 * result + MetadataUtil.hashCode(signature);
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public final boolean isInterpreterExecutable() {
        return hasBytecodes();
    }

    public final Set<InterpreterResolvedJavaMethod> getInlinedBy() {
        if (inlinedBy == null) {
            return Set.of();
        }
        return inlinedBy.inliners;
    }

    public final void addInliner(InterpreterResolvedJavaMethod inliner) {
        inlinedBy.inliners.add(inliner);
    }

    public final Object getInterpreterExecToken() {
        return interpreterExecToken;
    }

    public final void setInterpreterExecToken(Object interpreterExecToken) {
        this.interpreterExecToken = interpreterExecToken;
    }

    @Override
    public final InterpreterResolvedJavaMethod asMethodAccess() {
        return this;
    }

    @Override
    public final PartialMethod<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField> withVTableIndex(int index) {
        assert vtableIndex == VTBL_NO_ENTRY;
        vtableIndex = index;
        return this;
    }

    // region Unimplemented methods

    @Override
    public final boolean shouldSkipLoadingConstraints() {
        throw VMError.unimplemented("shouldSkipLoadingConstraints");
    }

    @Override
    public final CodeAttribute getCodeAttribute() {
        throw VMError.unimplemented("getCodeAttribute");
    }

    @Override
    public final boolean accessChecks(InterpreterResolvedJavaType accessingClass, InterpreterResolvedJavaType holderClass) {
        throw VMError.unimplemented("accessChecks");
    }

    @Override
    public final void loadingConstraints(InterpreterResolvedJavaType accessingClass, Function<String, RuntimeException> errorHandler) {
        throw VMError.unimplemented("loadingConstraints");
    }

    @Override
    public final com.oracle.svm.espresso.classfile.ExceptionHandler[] getSymbolicExceptionHandlers() {
        throw VMError.unimplemented("getSymbolicExceptionHandlers");
    }

    @Override
    public final Annotation[][] getParameterAnnotations() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final Type[] getGenericParameterTypes() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final boolean canBeInlined() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final boolean hasNeverInlineDirective() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final boolean shouldBeInlined() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final Constant getEncoding() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final SpeculationLog getSpeculationLog() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final Annotation[] getAnnotations() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final Annotation[] getDeclaredAnnotations() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final boolean isSynthetic() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final boolean isVarArgs() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final boolean isBridge() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final boolean isDefault() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final boolean canBeStaticallyBound() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final StackTraceElement asStackTraceElement(int bci) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final void reprofile() {
        throw VMError.intentionallyUnimplemented();
    }

    // endregion Unimplemented methods

    public static InterpreterResolvedJavaMethod fromMemberName(Target_java_lang_invoke_MemberName memberName) {
        InterpreterResolvedJavaMethod invoker = (InterpreterResolvedJavaMethod) memberName.resolved;
        if (invoker == null) {
            Executable reflectInvoker = (Executable) memberName.reflectAccess;
            if (reflectInvoker == null) {
                /*
                 * This should only happen on first use of image-heap MemberNames. Those don't have
                 * a `resolved` target and their `reflectAccess` is reset to null. Unfortunately we
                 * don't have a caller class to use for access checks.
                 */
                CremaSupport.singleton().resolveMemberName(memberName, null);
                invoker = (InterpreterResolvedJavaMethod) memberName.resolved;
                assert invoker != null;
            } else {
                invoker = toJVMCI(reflectInvoker);
                memberName.resolved = invoker;
            }
        }
        return invoker;
    }
}
