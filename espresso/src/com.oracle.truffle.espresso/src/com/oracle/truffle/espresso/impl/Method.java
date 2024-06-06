/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.impl;

import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.GETFIELD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.GETSTATIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.MONITORENTER;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.MONITOREXIT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.PUTFIELD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.PUTSTATIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.RETURN;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_CALLER_SENSITIVE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_FORCE_INLINE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_HIDDEN;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_NATIVE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_SCOPED;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_VARARGS;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeInterface;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeSpecial;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeStatic;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeVirtual;

import java.io.PrintStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.logging.Level;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.analysis.hierarchy.ClassHierarchyAssumption;
import com.oracle.truffle.espresso.analysis.hierarchy.ClassHierarchyOracle;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.CodeAttribute;
import com.oracle.truffle.espresso.classfile.attributes.ExceptionsAttribute;
import com.oracle.truffle.espresso.classfile.attributes.LineNumberTableAttribute;
import com.oracle.truffle.espresso.classfile.attributes.LocalVariableTable;
import com.oracle.truffle.espresso.classfile.attributes.SignatureAttribute;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.ffi.NativeAccess;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.ffi.Pointer;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.MethodHook;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jni.Mangle;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.ExceptionHandler;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.meta.ModifiersProvider;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.interop.AbstractLookupNode;
import com.oracle.truffle.espresso.nodes.methodhandle.MethodHandleIntrinsicNode;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.GuestAllocator;
import com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

public final class Method extends Member<Signature> implements TruffleObject, ContextAccess {

    public static final Method[] EMPTY_ARRAY = new Method[0];
    public static final MethodVersion[] EMPTY_VERSION_ARRAY = new MethodVersion[0];

    private static final byte GETTER_LENGTH = 5;
    private static final byte STATIC_GETTER_LENGTH = 4;

    private static final byte SETTER_LENGTH = 6;
    private static final byte STATIC_SETTER_LENGTH = 5;

    private final ObjectKlass declaringKlass;

    @CompilationFinal(dimensions = 1) //
    private final Symbol<Type>[] parsedSignature;

    private final Method proxy;
    private String genericSignature;

    // always null unless the raw signature exposed for this method should be
    // different from the one in the linkedKlass
    private final Symbol<Signature> rawSignature;

    // the parts of the method that can change when it's redefined
    // are encapsulated within the methodVersion
    @CompilationFinal private volatile MethodVersion methodVersion;

    private boolean removedByRedefinition;
    private final ClassHierarchyAssumption isLeaf;

    private MethodHook[] hooks = MethodHook.EMPTY;
    private final Field.StableBoolean hasActiveHook = new Field.StableBoolean(false);

    Method(Method method) {
        this(method, method.getCodeAttribute());
    }

    private Method(Method method, CodeAttribute split) {
        this.rawSignature = method.rawSignature;
        this.declaringKlass = method.declaringKlass;
        this.methodVersion = new MethodVersion(method.getMethodVersion().klassVersion, method.getRuntimeConstantPool(), method.getLinkedMethod(),
                        method.getMethodVersion().poisonPill, split);

        try {
            this.parsedSignature = getSignatures().parsed(this.getRawSignature());
        } catch (IllegalArgumentException | ClassFormatError e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Meta meta = getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_ClassFormatError, e.getMessage());
        }
        // Proxy the method, so that we have the same callTarget if it is not yet initialized.
        // Allows for not duplicating the codeAttribute
        this.proxy = method.proxy;
        this.isLeaf = method.isLeaf;
    }

    Method(ObjectKlass.KlassVersion klassVersion, LinkedMethod linkedMethod, RuntimeConstantPool pool) {
        this(klassVersion, linkedMethod, linkedMethod.getRawSignature(), pool);
    }

    Method(ObjectKlass.KlassVersion klassVersion, LinkedMethod linkedMethod, Symbol<Signature> rawSignature, RuntimeConstantPool pool) {
        this.declaringKlass = klassVersion.getKlass();
        this.rawSignature = rawSignature;
        this.methodVersion = new MethodVersion(klassVersion, pool, linkedMethod, false, (CodeAttribute) linkedMethod.getAttribute(CodeAttribute.NAME));

        try {
            this.parsedSignature = getSignatures().parsed(this.getRawSignature());
        } catch (IllegalArgumentException | ClassFormatError e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Meta meta = getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_ClassFormatError, e.getMessage());
        }
        this.proxy = this;
        this.isLeaf = getContext().getClassHierarchyOracle().createLeafAssumptionForNewMethod(this);
    }

    public Method identity() {
        return proxy;
    }

    @Override
    public Symbol<Name> getName() {
        return getMethodVersion().getName();
    }

    // can have a different constant pool than it's declaring class
    public ConstantPool getConstantPool() {
        return getRuntimeConstantPool();
    }

    public RuntimeConstantPool getRuntimeConstantPool() {
        return getMethodVersion().pool;
    }

    public LinkedMethod getLinkedMethod() {
        return getMethodVersion().linkedMethod;
    }

    public CodeAttribute getCodeAttribute() {
        return getMethodVersion().codeAttribute;
    }

    @Override
    @Idempotent
    public ObjectKlass getDeclaringKlass() {
        return declaringKlass;
    }

    public Symbol<Signature> getRawSignature() {
        if (rawSignature != null) {
            return rawSignature;
        }
        return getLinkedMethod().getRawSignature();
    }

    public Symbol<Type>[] getParsedSignature() {
        assert parsedSignature != null;
        return parsedSignature;
    }

    public ClassHierarchyAssumption getLeafAssumption(ClassHierarchyOracle.ClassHierarchyAccessor accessor) {
        Objects.requireNonNull(accessor);
        return isLeaf;
    }

    public int getRefKind() {
        return getMethodVersion().getRefKind();
    }

    public Attribute getAttribute(Symbol<Name> attrName) {
        return getLinkedMethod().getAttribute(attrName);
    }

    @TruffleBoundary
    public int bciToLineNumber(int atBCI) {
        return getMethodVersion().bciToLineNumber(atBCI);
    }

    @Override
    public EspressoContext getContext() {
        return declaringKlass.getContext();
    }

    public byte[] getOriginalCode() {
        return getCodeAttribute().getOriginalCode();
    }

    public ExceptionHandler[] getExceptionHandlers() {
        return getCodeAttribute().getExceptionHandlers();
    }

    public int[] getSOEHandlerInfo() {
        ArrayList<Integer> toArray = new ArrayList<>();
        for (ExceptionHandler handler : getExceptionHandlers()) {
            if (handler.isCatchAll() //
                            || handler.getCatchType() == Type.java_lang_StackOverflowError //
                            || handler.getCatchType() == Type.java_lang_VirtualMachineError //
                            || handler.getCatchType() == Type.java_lang_Error //
                            || handler.getCatchType() == Type.java_lang_Throwable) {
                toArray.add(handler.getStartBCI());
                toArray.add(handler.getEndBCI());
                toArray.add(handler.getHandlerBCI());
            }
        }
        if (toArray.isEmpty()) {
            return null;
        }
        int[] res = new int[toArray.size()];
        int pos = 0;
        for (Integer i : toArray) {
            res[pos++] = i;
        }
        return res;
    }

    public static NativeSignature buildJniNativeSignature(Symbol<Type>[] signature) {
        NativeType returnType = NativeAccess.kindToNativeType(Signatures.returnKind(signature));
        int argCount = Signatures.parameterCount(signature);

        // Prepend JNIEnv* and class|receiver.
        NativeType[] parameterTypes = new NativeType[argCount + 2];

        // Prepend JNIEnv*.
        parameterTypes[0] = NativeType.POINTER;

        // Receiver for instance methods, class for static methods.
        parameterTypes[1] = NativeType.OBJECT;

        for (int i = 0; i < argCount; ++i) {
            parameterTypes[i + 2] = NativeAccess.kindToNativeType(Signatures.parameterKind(signature, i));
        }

        return NativeSignature.create(returnType, parameterTypes);
    }

    public TruffleObject lookupAndBind(@Pointer TruffleObject library, String mangledName) {
        NativeSignature signature = buildJniNativeSignature(getParsedSignature());
        return getNativeAccess().lookupAndBindSymbol(library, mangledName, signature);
    }

    private TruffleObject bind(@Pointer TruffleObject symbol) {
        NativeSignature signature = buildJniNativeSignature(getParsedSignature());
        return getNativeAccess().bindSymbol(symbol, signature);
    }

    /**
     * Ensure any callTarget is called immediately before a BCI is advanced, or it could violate the
     * specs on class init.
     */
    public CallTarget getCallTarget() {
        return getMethodVersion().getCallTarget();
    }

    /**
     * Exposed to Interop API, to ensure that VM calls to guest classes properly initialize guest
     * classes prior to the calls.
     */
    public CallTarget getCallTargetForceInit() {
        getDeclaringKlass().safeInitialize();
        return getCallTarget();
    }

    /**
     * Obtains the original call target for the method, ignoring espresso substitutions. Note that
     * this completely ignores the call target cache, therefore, all calls to this method will
     * generate a new CallTarget. This is fine, as this method is not intended to be used outside of
     * the substitutions themselves.
     */
    public CallTarget getCallTargetNoSubstitution() {
        return getMethodVersion().getCallTargetNoSubstitution();
    }

    public boolean usesMonitors() {
        return getMethodVersion().usesMonitors();
    }

    private CallTarget lookupLibJavaCallTarget() {
        // If the loader is null we have a system class, so we attempt a lookup
        // in the native Java library.
        if (StaticObject.isNull(getDeclaringKlass().getDefiningClassLoader())) {
            for (boolean withSignature : new boolean[]{false, true}) {
                String mangledName = Mangle.mangleMethod(this, withSignature);
                // Look in libjava
                TruffleObject nativeMethod = lookupAndBind(getVM().getJavaLibrary(), mangledName);
                if (nativeMethod != null) {
                    return EspressoRootNode.createNative(getMethodVersion(), nativeMethod).getCallTarget();
                }
            }
        }
        return null;
    }

    private CallTarget lookupAgents() {
        // Look in agents
        for (boolean withSignature : new boolean[]{false, true}) {
            String mangledName = Mangle.mangleMethod(this, withSignature);
            TruffleObject nativeMethod = getContext().bindToAgent(this, mangledName);
            if (nativeMethod != null) {
                return EspressoRootNode.createNative(getMethodVersion(), nativeMethod).getCallTarget();
            }
        }
        return null;
    }

    private CallTarget lookupJniCallTarget() {
        CallTarget target;
        Method findNative = getMeta().java_lang_ClassLoader_findNative;
        // Lookup the short name first, otherwise lookup the long name (with
        // signature).
        target = lookupJniCallTarget(findNative, false);
        if (target == null) {
            target = lookupJniCallTarget(findNative, true);
        }
        return target;
    }

    private CallTarget lookupJniCallTarget(Method findNative, boolean fullSignature) {
        String mangledName = Mangle.mangleMethod(this, fullSignature);
        long handle = (long) findNative.invokeWithConversions(null, getDeclaringKlass().getDefiningClassLoader(), mangledName);
        if (handle == 0) { // not found
            return null;
        }
        TruffleObject symbol = getVM().getFunction(handle);
        TruffleObject nativeMethod = bind(symbol);
        return EspressoRootNode.createNative(this.getMethodVersion(), nativeMethod).getCallTarget();
    }

    public boolean isConstructor() {
        return Name._init_.equals(getName());
    }

    public boolean isDefault() {
        if (isConstructor()) {
            return false;
        }
        // Copied from java.lang.Method.isDefault()
        int mask = Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC;
        return ((getModifiers() & mask) == Modifier.PUBLIC) && getDeclaringKlass().isInterface();
    }

    public boolean canOverride(Method other) {
        if (other.isPrivate() || other.isStatic() || isPrivate() || isStatic()) {
            return false;
        }
        if (other.isPublic() || other.isProtected()) {
            return true;
        }
        return getDeclaringKlass().sameRuntimePackage(other.getDeclaringKlass());
    }

    public ObjectKlass[] getCheckedExceptions() {
        return getMethodVersion().getCheckedExceptions();
    }

    /**
     * Checks whether the method has bytecodes associated with it. Methods without bytecodes are
     * either abstract or native methods.
     *
     * @return whether the definition of this method is Java bytecodes
     */
    public boolean hasBytecodes() {
        return isConcrete() && !isNative();
    }

    /**
     * Checks whether the method has a receiver parameter - i.e., whether it is not static.
     *
     * @return whether the method has a receiver parameter
     */
    public boolean hasReceiver() {
        return !isStatic();
    }

    /**
     * Determines if this method is {@link java.lang.Object#Object()}.
     */
    public boolean isJavaLangObjectInit() {
        return getDeclaringKlass().isJavaLangObject() && Name._init_.equals(getName());
    }

    // region Meta.Method

    /**
     * Invoke guest method, parameters and return value are converted to host world. Primitives,
     * primitive arrays are shared, and are passed verbatim, conversions are provided for String and
     * StaticObject.NULL/null. There's no parameter casting based on the method's signature,
     * widening nor narrowing.
     */
    @TruffleBoundary
    public Object invokeWithConversions(Object self, Object... args) {
        getContext().getJNI().clearPendingException();
        assert args.length == Signatures.parameterCount(getParsedSignature());
        // assert !isStatic() || ((StaticObject) self).isStatic();

        final Object[] filteredArgs;
        if (isStatic()) {
            getDeclaringKlass().safeInitialize();
            filteredArgs = new Object[args.length];
            for (int i = 0; i < filteredArgs.length; ++i) {
                filteredArgs[i] = getMeta().toGuestBoxed(args[i]);
            }
        } else {
            filteredArgs = new Object[args.length + 1];
            filteredArgs[0] = getMeta().toGuestBoxed(self);
            for (int i = 1; i < filteredArgs.length; ++i) {
                filteredArgs[i] = getMeta().toGuestBoxed(args[i - 1]);
            }
        }
        return getMeta().toHostBoxed(getCallTarget().call(filteredArgs));
    }

    /**
     * Invokes a guest method without parameter/return type conversions. There's no parameter
     * casting, widening nor narrowing based on the method signature.
     * <p>
     * e.g. Host (boxed) Integer represents int, guest Integer doesn't.
     */
    @TruffleBoundary
    public Object invokeDirect(Object self, Object... args) {
        getContext().getJNI().clearPendingException();
        if (isStatic()) {
            assert args.length == Signatures.parameterCount(getParsedSignature());
            getDeclaringKlass().safeInitialize();
            return getCallTarget().call(args);
        } else {
            assert args.length + 1 /* self */ == Signatures.parameterCount(getParsedSignature()) + (isStatic() ? 0 : 1);
            Object[] fullArgs = new Object[args.length + 1];
            System.arraycopy(args, 0, fullArgs, 1, args.length);
            fullArgs[0] = self;
            return getCallTarget().call(fullArgs);
        }
    }

    public boolean isClassInitializer() {
        return Name._clinit_.equals(getName()) && isStatic();
    }

    @Override
    public int getModifiers() {
        return getMethodVersion().getModifiers();
    }

    public boolean isCallerSensitive() {
        return (getModifiers() & ACC_CALLER_SENSITIVE) != 0;
    }

    /**
     * The {@code @ForceInline} annotation only takes effect for methods or constructors of classes
     * loaded by the boot loader. Annotations on methods or constructors of classes loaded outside
     * of the boot loader are ignored.
     */
    public boolean isForceInline() {
        return (getModifiers() & ACC_FORCE_INLINE) != 0 && StaticObject.isNull(getDeclaringKlass().getDefiningClassLoader());
    }

    public boolean isHidden() {
        return (getModifiers() & ACC_HIDDEN) != 0;
    }

    public boolean isScoped() {
        return (getModifiers() & ACC_SCOPED) != 0;
    }

    public int getMethodModifiers() {
        return getMethodVersion().getModifiers() & Constants.JVM_RECOGNIZED_METHOD_MODIFIERS;
    }

    @Override
    public String toString() {
        return getMethodVersion().toString();
    }

    public JavaKind getReturnKind() {
        return Signatures.returnKind(getParsedSignature());
    }

    public Klass[] resolveParameterKlasses() {
        // TODO(peterssen): Use resolved signature.
        final Symbol<Type>[] signature = getParsedSignature();
        int paramCount = Signatures.parameterCount(signature);
        Klass[] paramsKlasses = paramCount > 0 ? new Klass[paramCount] : Klass.EMPTY_ARRAY;
        for (int i = 0; i < paramCount; ++i) {
            Symbol<Type> paramType = Signatures.parameterType(signature, i);
            paramsKlasses[i] = getMeta().resolveSymbolOrFail(paramType,
                            getDeclaringKlass().getDefiningClassLoader(),
                            getDeclaringKlass().protectionDomain());
        }
        return paramsKlasses;
    }

    public Klass resolveReturnKlass() {
        // TODO(peterssen): Use resolved signature.
        Symbol<Type> returnType = Signatures.returnType(getParsedSignature());
        return getMeta().resolveSymbolOrFail(returnType,
                        getDeclaringKlass().getDefiningClassLoader(),
                        getDeclaringKlass().protectionDomain());
    }

    @Idempotent
    public int getParameterCount() {
        return Signatures.parameterCount(getParsedSignature());
    }

    public int getArgumentCount() {
        return getParameterCount() + (isStatic() ? 0 : 1);
    }

    public static Method getHostReflectiveMethodRoot(StaticObject seed, Meta meta) {
        assert seed.getKlass().getMeta().java_lang_reflect_Method.isAssignableFrom(seed.getKlass());
        StaticObject curMethod = seed;
        while (curMethod != null && StaticObject.notNull(curMethod)) {
            Method target = (Method) meta.HIDDEN_METHOD_KEY.getHiddenObject(curMethod);
            if (target != null) {
                return target;
            }
            curMethod = meta.java_lang_reflect_Method_root.getObject(curMethod);
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("Could not find HIDDEN_METHOD_KEY");
    }

    public static Method getHostReflectiveConstructorRoot(StaticObject seed, Meta meta) {
        assert seed.getKlass().getMeta().java_lang_reflect_Constructor.isAssignableFrom(seed.getKlass());
        StaticObject curMethod = seed;
        while (curMethod != null && StaticObject.notNull(curMethod)) {
            Method target = (Method) meta.HIDDEN_CONSTRUCTOR_KEY.getHiddenObject(curMethod);
            if (target != null) {
                return target;
            }
            curMethod = meta.java_lang_reflect_Constructor_root.getObject(curMethod);
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("Could not find HIDDEN_CONSTRUCTOR_KEY");
    }

    // Polymorphic signature method 'creation'

    Method findIntrinsic(Symbol<Signature> signature) {
        return getContext().getMethodHandleIntrinsics().findIntrinsic(this, signature);
    }

    public boolean isSignaturePolymorphicDeclared() {
        // JVM 2.9 Special Methods:
        // A method is signature polymorphic if and only if all of the following conditions hold :
        // * It is declared in the java.lang.invoke.MethodHandle or java.lang.invoke.VarHandle
        // class.
        // * It has a single formal parameter of type Object[].
        // * It has the ACC_VARARGS and ACC_NATIVE flags set.
        // * ONLY JAVA <= 8: It has a return type of Object.
        if (!(Type.java_lang_invoke_MethodHandle.equals(getDeclaringKlass().getType()) ||
                        Type.java_lang_invoke_VarHandle.equals(getDeclaringKlass().getType()))) {
            return false;
        }
        Symbol<Type>[] signature = getParsedSignature();
        if (Signatures.parameterCount(signature) != 1) {
            return false;
        }
        if (Signatures.parameterType(signature, 0) != Type.java_lang_Object_array) {
            return false;
        }
        if (getJavaVersion().java8OrEarlier()) {
            if (Signatures.returnType(signature) != Type.java_lang_Object) {
                return false;
            }
        }
        int required = ACC_NATIVE | ACC_VARARGS;
        int flags = getModifiers();
        return (flags & required) == required;
    }

    void setVTableIndex(int i) {
        getMethodVersion().setVTableIndex(i);
    }

    void setVTableIndex(int i, boolean isRedefinition) {
        getMethodVersion().setVTableIndex(i, isRedefinition);
    }

    public int getVTableIndex() {
        return getMethodVersion().getVTableIndex();
    }

    void setITableIndex(int i) {
        getMethodVersion().setITableIndex(i);
    }

    public int getITableIndex() {
        return getMethodVersion().getITableIndex();
    }

    public boolean hasCode() {
        return getCodeAttribute() != null || isNative();
    }

    public boolean isVirtualCall() {
        return !isStatic() && !isConstructor() && !isPrivate() && !getDeclaringKlass().isInterface();
    }

    public void setPoisonPill() {
        getMethodVersion().poisonPill = true;
    }

    public boolean hasSourceFileAttribute() {
        return declaringKlass.getAttribute(Name.SourceFile) != null;
    }

    public String report(int curBCI) {
        String sourceFile = getDeclaringKlass().getSourceFile();
        if (sourceFile == null) {
            sourceFile = "unknown source";
        }
        return "at " + MetaUtil.internalNameToJava(getDeclaringKlass().getType().toString(), true, false) + "." + getName() + "(" + sourceFile + ":" + bciToLineNumber(curBCI) + ")";
    }

    public String report() {
        return "at " + MetaUtil.internalNameToJava(getDeclaringKlass().getType().toString(), true, false) + "." + getName() + "(unknown source)";
    }

    public boolean isInvokeIntrinsic() {
        return isNative() && MethodHandleIntrinsics.getId(this) == MethodHandleIntrinsics.PolySigIntrinsics.InvokeGeneric;
    }

    public boolean isPolySignatureIntrinsic() {
        return isNative() && MethodHandleIntrinsics.getId(this) != MethodHandleIntrinsics.PolySigIntrinsics.None;
    }

    public boolean isInlinableGetter() {
        if (!getSubstitutions().hasSubstitutionFor(this)) {
            if (getParameterCount() == 0 && !isAbstract() && !isNative() && !isSynchronized()) {
                return hasGetterBytecodes();
            }
        }
        return false;
    }

    private boolean hasGetterBytecodes() {
        byte[] code = getOriginalCode();
        if (isStatic()) {
            if (code.length == STATIC_GETTER_LENGTH && getExceptionHandlers().length == 0) {
                return (code[0] == (byte) GETSTATIC) && (Bytecodes.isReturn(code[3])) && code[3] != (byte) RETURN;
            }
        } else {
            if (code.length == GETTER_LENGTH && getExceptionHandlers().length == 0) {
                return (code[0] == (byte) ALOAD_0) && (code[1] == (byte) GETFIELD) && (Bytecodes.isReturn(code[4])) && code[4] != (byte) RETURN;
            }
        }
        return false;
    }

    public boolean isInlinableSetter() {
        if (!getSubstitutions().hasSubstitutionFor(this)) {
            if (getParameterCount() == 1 && !isAbstract() && !isNative() && !isSynchronized()) {
                return hasSetterBytecodes();
            }
        }
        return false;
    }

    private boolean hasSetterBytecodes() {
        byte[] code = getOriginalCode();
        if (isStatic()) {
            if (code.length == STATIC_SETTER_LENGTH && getExceptionHandlers().length == 0) {
                return (code[0] == (byte) ALOAD_0) && (code[1] == (byte) PUTSTATIC) && (code[4] == (byte) RETURN);
            }
        } else {
            if (code.length == SETTER_LENGTH && getExceptionHandlers().length == 0) {
                return (code[0] == (byte) ALOAD_0) && (Bytecodes.isLoad1(code[1])) && (code[2] == (byte) PUTFIELD) && (code[5] == (byte) RETURN);
            }
        }
        return false;
    }

    public void unregisterNative() {
        assert isNative();
        if (getMethodVersion().callTarget != null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getMethodVersion().callTarget = null;
        }
    }

    @SuppressWarnings("unused")
    public void printBytecodes(PrintStream out) {
        new BytecodeStream(getOriginalCode()).printBytecode(declaringKlass, out);
    }

    public LocalVariableTable getLocalVariableTable() {
        return getMethodVersion().getLocalVariableTable();
    }

    /**
     * @return the source object associated with this method
     */

    public Source getSource() {
        return getDeclaringKlass().getSource();
    }

    @TruffleBoundary
    public void checkLoadingConstraints(StaticObject loader1, StaticObject loader2) {
        for (Symbol<Type> type : getParsedSignature()) {
            getContext().getRegistries().checkLoadingConstraint(type, loader1, loader2);
        }
    }

    public int getCatchLocation(int bci, StaticObject ex) {
        ExceptionHandler[] handlers = getExceptionHandlers();
        ExceptionHandler resolved = null;
        for (ExceptionHandler toCheck : handlers) {
            if (bci >= toCheck.getStartBCI() && bci < toCheck.getEndBCI()) {
                Klass catchType = null;
                if (!toCheck.isCatchAll()) {
                    catchType = getRuntimeConstantPool().resolvedKlassAt(getDeclaringKlass(), toCheck.catchTypeCPI());
                }
                if (catchType == null || InterpreterToVM.instanceOf(ex, catchType)) {
                    // the first found exception handler is our exception handler
                    resolved = toCheck;
                    break;
                }
            }
        }
        if (resolved != null) {
            return resolved.getHandlerBCI();
        } else {
            return -1;
        }
    }

    // Spawns a placeholder method for MH intrinsics
    public Method createIntrinsic(Symbol<Signature> polymorphicRawSignature) {
        assert isPolySignatureIntrinsic();
        return new Method(declaringKlass.getKlassVersion(), getLinkedMethod(), polymorphicRawSignature, getRuntimeConstantPool());
    }

    public MethodHandleIntrinsicNode spawnIntrinsicNode(EspressoLanguage language, Meta meta, Klass accessingKlass, Symbol<Name> mname, Symbol<Signature> signature) {
        assert isPolySignatureIntrinsic();
        return MethodHandleIntrinsics.createIntrinsicNode(language, meta, this, accessingKlass, mname, signature);
    }

    public Method forceSplit() {
        Method result = new Method(this, getCodeAttribute());
        EspressoRootNode root = EspressoRootNode.createForBytecodes(result.getMethodVersion());
        result.getMethodVersion().callTarget = root.getCallTarget();
        return result;
    }

    // region jdwp-specific
    public long getBCIFromLine(int line) {
        return getMethodVersion().getBCIFromLine(line);
    }

    public boolean hasLine(int lineNumber) {
        return getMethodVersion().hasLine(lineNumber);
    }

    public String getNameAsString() {
        return getName().toString();
    }

    @TruffleBoundary
    public String getInteropString() {
        return getNameAsString() + AbstractLookupNode.METHOD_SELECTION_SEPARATOR + getRawSignature();
    }

    public String getSignatureAsString() {
        return getRawSignature().toString();
    }

    public KlassRef[] getParameters() {
        return resolveParameterKlasses();
    }

    @TruffleBoundary
    public Object invokeMethod(Object callee, Object[] args) {
        if (isConstructor()) {
            GuestAllocator.AllocationChecks.checkCanAllocateNewReference(getMeta(), getDeclaringKlass(), false);
            Object theCallee = getAllocator().createNew(getDeclaringKlass());
            invokeWithConversions(theCallee, args);
            return theCallee;
        }
        return invokeWithConversions(callee, args);
    }

    public String getGenericSignatureAsString() {
        if (genericSignature == null) {
            SignatureAttribute attr = (SignatureAttribute) getLinkedMethod().getAttribute(SignatureAttribute.NAME);
            if (attr == null) {
                genericSignature = ""; // if no generics, the generic signature is empty
            } else {
                genericSignature = getRuntimeConstantPool().symbolAt(attr.getSignatureIndex()).toString();
            }
        }
        return genericSignature;
    }

    public boolean hasActiveHook() {
        return hasActiveHook.get();
    }

    public synchronized MethodHook[] getMethodHooks() {
        return Arrays.copyOf(hooks, hooks.length);
    }

    public synchronized void addMethodHook(MethodHook info) {
        hasActiveHook.set(true);
        hooks = Arrays.copyOf(hooks, hooks.length + 1);
        hooks[hooks.length - 1] = info;
    }

    private void expectActiveHooks() {
        if (hooks.length == 0) {
            throw new RuntimeException("Method: " + getNameAsString() + " expected to contain method hook");
        }
    }

    public synchronized void removeActiveHook(int requestId) {
        expectActiveHooks();
        boolean removed = false;
        // shrink the array to avoid null values
        if (hooks.length == 1) {
            // make sure it's the right hook
            if (hooks[0].getRequestId() == requestId) {
                hooks = MethodHook.EMPTY;
                hasActiveHook.set(false);
                removed = true;
            }
        } else {
            int removeIndex = -1;
            for (int i = 0; i < hooks.length; i++) {
                if (hooks[i].getRequestId() == requestId) {
                    removeIndex = i;
                    break;
                }
            }
            if (removeIndex != -1) {
                MethodHook[] temp = new MethodHook[hooks.length - 1];
                for (int i = 0; i < temp.length; i++) {
                    temp[i] = i < removeIndex ? hooks[i] : hooks[i + 1];
                }
                hooks = temp;
                removed = true;
            }
        }
        if (!removed) {
            throw new RuntimeException("Method: " + getNameAsString() + " should contain method hook");
        }
    }

    public synchronized void removeActiveHook(MethodHook hook) {
        expectActiveHooks();
        boolean removed = false;
        // shrink the array to avoid null values
        if (hooks.length == 1) {
            // make sure it's the right hook
            if (hooks[0] == hook) {
                hooks = MethodHook.EMPTY;
                hasActiveHook.set(false);
                removed = true;
            }
        } else {
            int removeIndex = -1;
            for (int i = 0; i < hooks.length; i++) {
                if (hooks[i] == hook) {
                    removeIndex = i;
                    break;
                }
            }
            if (removeIndex != -1) {
                MethodHook[] temp = new MethodHook[hooks.length - 1];
                for (int i = 0; i < temp.length; i++) {
                    temp[i] = i < removeIndex ? hooks[i] : hooks[i + 1];
                }
                hooks = temp;
                removed = true;
            }
        }
        if (!removed) {
            throw new RuntimeException("Method: " + getNameAsString() + " should contain method hook");
        }
    }

    public SharedRedefinitionContent redefine(ObjectKlass.KlassVersion klassVersion, ParserMethod newMethod, ParserKlass newKlass, Ids<Object> ids) {
        // install the new method version immediately
        LinkedMethod newLinkedMethod = new LinkedMethod(newMethod);
        RuntimeConstantPool runtimePool = new RuntimeConstantPool(getContext(), newKlass.getConstantPool(), getDeclaringKlass().getDefiningClassLoader());
        CodeAttribute newCodeAttribute = (CodeAttribute) newMethod.getAttribute(Name.Code);
        MethodVersion oldVersion = methodVersion;
        methodVersion = oldVersion.replace(klassVersion, runtimePool, newLinkedMethod, newCodeAttribute);
        ids.replaceObject(oldVersion, methodVersion);
        return new SharedRedefinitionContent(methodVersion, newLinkedMethod, runtimePool, newCodeAttribute);
    }

    public void redefine(ObjectKlass.KlassVersion klassVersion, SharedRedefinitionContent content, Ids<Object> ids) {
        // install the new method version immediately
        MethodVersion oldVersion = methodVersion;
        methodVersion = oldVersion.replace(klassVersion, content.getPool(), content.getLinkedMethod(), content.codeAttribute);
        ids.replaceObject(oldVersion, methodVersion);
    }

    public MethodVersion swapMethodVersion(ObjectKlass.KlassVersion klassVersion, Ids<Object> ids) {
        MethodVersion oldVersion = methodVersion;
        CodeAttribute codeAttribute = oldVersion.getCodeAttribute();
        // create a copy of the code attribute using the original
        // code of the old version. An obsolete method could be
        // running quickened bytecode and we can't safely patch
        // the bytecodes back to the original.
        CodeAttribute newCodeAttribute = codeAttribute != null ? new CodeAttribute(codeAttribute) : null;
        methodVersion = oldVersion.replace(klassVersion, oldVersion.pool, oldVersion.linkedMethod, newCodeAttribute);
        ids.replaceObject(oldVersion, methodVersion);
        return methodVersion;
    }

    public MethodVersion getMethodVersion() {
        MethodVersion version = methodVersion;
        if (!version.getRedefineAssumption().isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // we block until class redefinition is done
            // unless we're the redefine thread
            getContext().getClassRedefinition().check();
            if (isRemovedByRedefinition()) {
                // for a removed method, we return the latest known
                // method version in case active frames try to
                // retrieve information for obsolete methods
                return version;
            }
            do {
                version = methodVersion;
            } while (!version.getRedefineAssumption().isValid());
        }
        return version;
    }

    public void removedByRedefinition() {
        removedByRedefinition = true;
    }

    public boolean isRemovedByRedefinition() {
        return removedByRedefinition;
    }

    public StaticObject makeMirror(Meta meta) {
        Attribute rawRuntimeVisibleAnnotations = getAttribute(Name.RuntimeVisibleAnnotations);
        StaticObject runtimeVisibleAnnotations = rawRuntimeVisibleAnnotations != null
                        ? StaticObject.wrap(rawRuntimeVisibleAnnotations.getData(), meta)
                        : StaticObject.NULL;

        Attribute rawRuntimeVisibleParameterAnnotations = getAttribute(Name.RuntimeVisibleParameterAnnotations);
        StaticObject runtimeVisibleParameterAnnotations = rawRuntimeVisibleParameterAnnotations != null
                        ? StaticObject.wrap(rawRuntimeVisibleParameterAnnotations.getData(), meta)
                        : StaticObject.NULL;

        Attribute rawRuntimeVisibleTypeAnnotations = getAttribute(Name.RuntimeVisibleTypeAnnotations);
        StaticObject runtimeVisibleTypeAnnotations = rawRuntimeVisibleTypeAnnotations != null
                        ? StaticObject.wrap(rawRuntimeVisibleTypeAnnotations.getData(), meta)
                        : StaticObject.NULL;

        Attribute rawAnnotationDefault = getAttribute(Name.AnnotationDefault);
        StaticObject annotationDefault = rawAnnotationDefault != null
                        ? StaticObject.wrap(rawAnnotationDefault.getData(), meta)
                        : StaticObject.NULL;
        final Klass[] rawParameterKlasses = resolveParameterKlasses();
        StaticObject parameterTypes = meta.java_lang_Class.allocateReferenceArray(
                        getParameterCount(),
                        new IntFunction<StaticObject>() {
                            @Override
                            public StaticObject apply(int j) {
                                return rawParameterKlasses[j].mirror();
                            }
                        });

        final Klass[] rawCheckedExceptions = getCheckedExceptions();
        StaticObject guestCheckedExceptions = meta.java_lang_Class.allocateReferenceArray(rawCheckedExceptions.length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int j) {
                return rawCheckedExceptions[j].mirror();
            }
        });

        SignatureAttribute signatureAttribute = (SignatureAttribute) getAttribute(Name.Signature);
        StaticObject guestGenericSignature = StaticObject.NULL;
        if (signatureAttribute != null) {
            String sig = getConstantPool().symbolAt(signatureAttribute.getSignatureIndex(), "signature").toString();
            guestGenericSignature = meta.toGuestString(sig);
        }

        StaticObject instance = meta.java_lang_reflect_Method.allocateInstance(meta.getContext());

        meta.java_lang_reflect_Method_init.invokeDirect(
                        /* this */ instance,
                        /* declaringClass */ getDeclaringKlass().mirror(),
                        /* name */ getContext().getStrings().intern(getName()),
                        /* parameterTypes */ parameterTypes,
                        /* returnType */ resolveReturnKlass().mirror(),
                        /* checkedExceptions */ guestCheckedExceptions,
                        /* modifiers */ getMethodModifiers(),
                        /* slot */ getVTableIndex(),
                        /* signature */ guestGenericSignature,
                        /* annotations */ runtimeVisibleAnnotations,
                        /* parameterAnnotations */ runtimeVisibleParameterAnnotations,
                        /* annotationDefault */ annotationDefault);
        meta.HIDDEN_METHOD_KEY.setHiddenObject(instance, this);
        meta.HIDDEN_METHOD_RUNTIME_VISIBLE_TYPE_ANNOTATIONS.setHiddenObject(instance, runtimeVisibleTypeAnnotations);
        return instance;
    }

    public final class MethodVersion implements MethodRef, ModifiersProvider {
        private final ObjectKlass.KlassVersion klassVersion;
        private final RuntimeConstantPool pool;
        private final LinkedMethod linkedMethod;
        private final CodeAttribute codeAttribute;
        private final ExceptionsAttribute exceptionsAttribute;

        @CompilationFinal private CallTarget callTarget;

        @CompilationFinal private int vtableIndex = -1;
        @CompilationFinal private int itableIndex = -1;

        // Whether we need to use an additional frame slot for monitor unlock on kill.
        @CompilationFinal private byte usesMonitors = -1;

        @CompilationFinal(dimensions = 1) //
        private volatile byte[] code = null;

        @CompilationFinal private int refKind;

        @CompilationFinal(dimensions = 1) //
        private ObjectKlass[] checkedExceptions;

        // Multiple maximally-specific interface methods. Fail on call.
        @CompilationFinal private boolean poisonPill;

        private MethodVersion(ObjectKlass.KlassVersion klassVersion, RuntimeConstantPool pool, LinkedMethod linkedMethod, boolean poisonPill,
                        CodeAttribute codeAttribute) {
            this.klassVersion = klassVersion;
            this.pool = pool;
            this.linkedMethod = linkedMethod;
            this.codeAttribute = codeAttribute;
            this.exceptionsAttribute = (ExceptionsAttribute) linkedMethod.getAttribute(ExceptionsAttribute.NAME);
            this.poisonPill = poisonPill;
            initRefKind();
        }

        public SourceSection getWholeMethodSourceSection() {
            Source s = getSource();
            if (s == null) {
                return null;
            }

            LineNumberTableAttribute lineNumberTable = getLineNumberTableAttribute();

            if (lineNumberTable != LineNumberTableAttribute.EMPTY) {
                List<LineNumberTableAttribute.Entry> entries = lineNumberTable.getEntries();
                int startLine = Integer.MAX_VALUE;
                int endLine = 0;

                for (int i = 0; i < entries.size(); i++) {
                    int line = entries.get(i).getLineNumber();
                    if (line > endLine) {
                        endLine = line;
                    }
                    if (line < startLine) {
                        startLine = line;
                    }
                }

                if (startLine >= 1 && endLine >= 1 && startLine <= endLine) {
                    return s.createSection(startLine, -1, endLine, -1);
                } // (else) Most likely generated bytecodes with dummy LineNumberTable attribute.
            }
            return s.createUnavailableSection();
        }

        public void initRefKind() {
            if (Modifier.isStatic(linkedMethod.getFlags())) {
                this.refKind = REF_invokeStatic;
            } else if (Modifier.isPrivate(linkedMethod.getFlags()) || Name._init_.equals(linkedMethod.getName())) {
                this.refKind = REF_invokeSpecial;
            } else if (klassVersion.isInterface()) {
                this.refKind = REF_invokeInterface;
            } else {
                assert !declaringKlass.isPrimitive();
                this.refKind = REF_invokeVirtual;
            }
        }

        public MethodVersion replace(ObjectKlass.KlassVersion version, RuntimeConstantPool constantPool, LinkedMethod newLinkedMethod, CodeAttribute newCodeAttribute) {
            MethodVersion result = new MethodVersion(version, constantPool, newLinkedMethod, false, newCodeAttribute);
            // make sure the table indices are copied
            result.vtableIndex = vtableIndex;
            result.itableIndex = itableIndex;
            return result;
        }

        public LinkedMethod getLinkedMethod() {
            return linkedMethod;
        }

        public byte[] getCode() {
            if (code == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                synchronized (this) {
                    if (code == null) {
                        byte[] originalCode = getCodeAttribute().getOriginalCode();
                        code = Arrays.copyOf(originalCode, originalCode.length);
                    }
                }
            }
            return code;
        }

        @Idempotent
        public Method getMethod() {
            return Method.this;
        }

        public Symbol<Name> getName() {
            return linkedMethod.getName();
        }

        public Symbol<Signature> getRawSignature() {
            return getMethod().getRawSignature();
        }

        public Assumption getRedefineAssumption() {
            return klassVersion.getAssumption();
        }

        public CodeAttribute getCodeAttribute() {
            return codeAttribute;
        }

        void setVTableIndex(int i) {
            setVTableIndex(i, false);
        }

        void setVTableIndex(int i, boolean isRedefinition) {
            assert (vtableIndex == -1 || vtableIndex == i || isRedefinition);
            assert itableIndex == -1;
            CompilerAsserts.neverPartOfCompilation();
            this.vtableIndex = i;
        }

        public int getVTableIndex() {
            return vtableIndex;
        }

        void setITableIndex(int i) {
            assert (itableIndex == -1 || itableIndex == i);
            assert vtableIndex == -1;
            CompilerAsserts.neverPartOfCompilation();
            this.itableIndex = i;
        }

        public int getITableIndex() {
            return itableIndex;
        }

        public ExceptionHandler[] getExceptionHandlers() {
            return codeAttribute.getExceptionHandlers();
        }

        public RuntimeConstantPool getPool() {
            return pool;
        }

        private CallTarget getCallTargetNoSubstitution() {
            CompilerAsserts.neverPartOfCompilation();
            EspressoError.guarantee(getSubstitutions().hasSubstitutionFor(getMethod()),
                            "Using 'getCallTargetNoSubstitution' should be done only to bypass the substitution mechanism.");
            return findCallTarget();
        }

        public CallTarget getCallTarget() {
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, callTarget == null)) {
                if (CompilerDirectives.isCompilationConstant(this)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                resolveCallTarget();
            }
            return callTarget;
        }

        @TruffleBoundary
        private void resolveCallTarget() {
            Meta meta = getMeta();
            checkPoisonPill(meta);
            synchronized (this) {
                if (callTarget != null) {
                    return;
                }
                if (proxy != Method.this) {
                    this.callTarget = proxy.getCallTarget();
                    return;
                }

                /*
                 * The substitution factory does the validation e.g. some substitutions only apply
                 * for classes/methods in the boot or platform class loaders. A warning is logged if
                 * the validation fails.
                 */
                EspressoRootNode redirectedMethod = getSubstitutions().get(getMethod());
                if (redirectedMethod != null) {
                    callTarget = redirectedMethod.getCallTarget();
                    return;
                }

                CallTarget target = findCallTarget();
                if (target != null) {
                    callTarget = target;
                    return;
                }
            }
        }

        private CallTarget findCallTarget() {
            CallTarget target;
            if (isNative()) {
                // Bind native method.
                target = lookupLibJavaCallTarget();
                if (target == null) {
                    target = lookupAgents();
                }
                if (target == null) {
                    target = lookupJniCallTarget();
                }

                // TODO(peterssen): Search JNI methods with OS prefix/suffix
                // (print_jni_name_suffix_on ...)

                if (target == null && isSignaturePolymorphicDeclared()) {
                    /*
                     * Happens only when trying to obtain call target of
                     * MethodHandle.invoke(Object... args), or MethodHandle.invokeExact(Object...
                     * args).
                     *
                     * The method was obtained through a regular lookup (since it is in the declared
                     * methods). Delegate it to a polysignature method lookup.
                     */
                    target = declaringKlass.lookupPolysigMethod(getName(), getRawSignature(), Klass.LookupMode.ALL).getCallTarget();
                }

                if (target == null) {
                    getContext().getLogger().log(Level.WARNING, "Failed to link native method: {0}", toString());
                    Meta meta = getMeta();
                    throw meta.throwException(meta.java_lang_UnsatisfiedLinkError);
                }
            } else {
                if (codeAttribute == null) {
                    Meta meta = getMeta();
                    throw meta.throwExceptionWithMessage(meta.java_lang_AbstractMethodError,
                                    "Calling abstract method: " + getMethod().getDeclaringKlass().getType() + "." + getName() + " -> " + getRawSignature());
                }
                EspressoRootNode rootNode = EspressoRootNode.createForBytecodes(this);
                target = rootNode.getCallTarget();
            }
            return target;
        }

        private void checkPoisonPill(Meta meta) {
            if (poisonPill) {
                // Conflicting Maximally-specific non-abstract interface methods.
                if (getJavaVersion().java9OrLater() && getSpecComplianceMode() == EspressoOptions.SpecComplianceMode.HOTSPOT) {
                    /*
                     * Supposed to be IncompatibleClassChangeError (see jvms-6.5.invokeinterface),
                     * but HotSpot throws AbstractMethodError.
                     */
                    throw meta.throwExceptionWithMessage(meta.java_lang_AbstractMethodError, "Conflicting default methods: " + getName());
                }
                throw meta.throwExceptionWithMessage(meta.java_lang_IncompatibleClassChangeError, "Conflicting default methods: " + getName());
            }
        }

        public int getCodeSize() {
            return getCode() != null ? getCode().length : 0;
        }

        public LineNumberTableAttribute getLineNumberTableAttribute() {
            if (codeAttribute != null) {
                LineNumberTableAttribute lineNumberTable = codeAttribute.getLineNumberTableAttribute();
                return lineNumberTable != null ? lineNumberTable : LineNumberTableAttribute.EMPTY;
            }
            return LineNumberTableAttribute.EMPTY;
        }

        @Override
        public long getBCIFromLine(int line) {
            return getLineNumberTable().getBCI(line);
        }

        @Override
        public Source getSource() {
            return getMethod().getSource();
        }

        @Override
        public boolean hasLine(int lineNumber) {
            return getLineNumberTable().getBCI(lineNumber) != -1;
        }

        @Override
        public String getSourceFile() {
            return getDeclaringKlass().getSourceFile();
        }

        @Override
        public String getNameAsString() {
            return getName().toString();
        }

        @Override
        public String getSignatureAsString() {
            return getMethod().getSignatureAsString();
        }

        @Override
        public String getGenericSignatureAsString() {
            return getMethod().getGenericSignatureAsString();
        }

        @Override
        public int getModifiers() {
            return linkedMethod.getFlags();
        }

        @Override
        public int bciToLineNumber(int bci) {
            if (bci < 0) {
                return bci;
            }
            return getCodeAttribute().bciToLineNumber(bci);
        }

        @Override
        public boolean isMethodNative() {
            return isNative();
        }

        @Override
        public byte[] getOriginalCode() {
            return getCodeAttribute().getOriginalCode();
        }

        @Override
        public LocalVariableTable getLocalVariableTable() {
            if (codeAttribute != null) {
                return codeAttribute.getLocalvariableTable();
            }
            return LocalVariableTable.EMPTY_LVT;
        }

        @Override
        public LocalVariableTable getLocalVariableTypeTable() {
            if (codeAttribute != null) {
                return codeAttribute.getLocalvariableTypeTable();
            }
            return LocalVariableTable.EMPTY_LVTT;
        }

        @Override
        public LineNumberTableAttribute getLineNumberTable() {
            return getLineNumberTableAttribute();
        }

        @Override
        public Object invokeMethod(Object callee, Object[] args) {
            if (getMethod().isRemovedByRedefinition()) {
                Meta meta = getMeta();
                throw meta.throwExceptionWithMessage(meta.java_lang_NoSuchMethodError,
                                meta.toGuestString(getMethod().getDeclaringKlass().getNameAsString() + "." + getName() + getRawSignature()));
            }
            return getMethod().invokeMethod(callee, args);
        }

        @Override
        public boolean hasSourceFileAttribute() {
            return getMethod().hasSourceFileAttribute();
        }

        @Override
        public boolean isLastLine(long codeIndex) {
            LineNumberTableAttribute table = getLineNumberTable();
            int lastLine = table.getLastLine();
            int lineAt = table.getLineNumber((int) codeIndex);
            return lastLine == lineAt;
        }

        @Override
        public KlassRef getDeclaringKlassRef() {
            return getMethod().getDeclaringKlass();
        }

        @Override
        public int getFirstLine() {
            return getLineNumberTable().getFirstLine();
        }

        @Override
        public int getLastLine() {
            return getLineNumberTable().getLastLine();
        }

        @Override
        public MethodHook[] getMethodHooks() {
            return getMethod().getMethodHooks();
        }

        @Override
        public void addMethodHook(MethodHook info) {
            getMethod().addMethodHook(info);
        }

        @Override
        public void removedMethodHook(int requestId) {
            getMethod().removeActiveHook(requestId);
        }

        @Override
        public void removedMethodHook(MethodHook hook) {
            getMethod().removeActiveHook(hook);
        }

        @Override
        public boolean hasActiveHook() {
            return getMethod().hasActiveHook();
        }

        @Override
        public boolean isObsolete() {
            return !klassVersion.getAssumption().isValid();
        }

        @Override
        public long getLastBCI() {
            int bci = 0;
            BytecodeStream bs = new BytecodeStream(getCode());
            int end = bs.endBCI();

            while (bci < end) {
                int nextBCI = bs.nextBCI(bci);
                if (nextBCI >= end || nextBCI == bci) {
                    return bci;
                } else {
                    bci = nextBCI;
                }
            }
            return bci;
        }

        @Override
        public String toString() {
            return "EspressoMethod<" + getDeclaringKlass().getType() + "." + getName() + getRawSignature() + ">";
        }

        public boolean usesMonitors() {
            if (usesMonitors != -1) {
                return usesMonitors != 0;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (isSynchronized()) {
                    return (usesMonitors = 1) != 0;
                }
                if (codeAttribute != null) {
                    BytecodeStream bs = new BytecodeStream(codeAttribute.getOriginalCode());
                    int bci = 0;
                    while (bci < bs.endBCI()) {
                        int opcode = bs.currentBC(bci);
                        if (opcode == MONITORENTER || opcode == MONITOREXIT) {
                            return (usesMonitors = 1) != 0;
                        }
                        bci = bs.nextBCI(bci);
                    }
                    return (usesMonitors = 0) != 0;
                }
                return false;
            }
        }

        public int getRefKind() {
            return refKind;
        }

        public ObjectKlass[] getCheckedExceptions() {
            if (checkedExceptions == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                createCheckedExceptions();
            }
            return checkedExceptions;
        }

        private synchronized void createCheckedExceptions() {
            if (checkedExceptions == null) {
                if (exceptionsAttribute == null) {
                    checkedExceptions = ObjectKlass.EMPTY_ARRAY;
                    return;
                }
                final int[] entries = exceptionsAttribute.getCheckedExceptionsCPI();
                ObjectKlass[] tmpchecked = new ObjectKlass[entries.length];
                for (int i = 0; i < entries.length; ++i) {
                    // TODO(peterssen): Resolve and cache CP entries.
                    tmpchecked[i] = (ObjectKlass) pool.resolvedKlassAt(declaringKlass, entries[i]);
                }
                checkedExceptions = tmpchecked;
            }
        }

        public ObjectKlass.KlassVersion getKlassVersion() {
            return klassVersion;
        }

        public ObjectKlass getDeclaringKlass() {
            return declaringKlass;
        }

        public void checkLoadingConstraints(StaticObject loader1, StaticObject loader2) {
            getMethod().checkLoadingConstraints(loader1, loader2);
        }

        public int getMaxLocals() {
            return codeAttribute.getMaxLocals();
        }

        public int getMaxStackSize() {
            return codeAttribute.getMaxStack();
        }
    }

    static class SharedRedefinitionContent {

        private final MethodVersion version;
        private final LinkedMethod linkedMethod;
        private final RuntimeConstantPool pool;
        private final CodeAttribute codeAttribute;

        SharedRedefinitionContent(MethodVersion version, LinkedMethod linkedMethod, RuntimeConstantPool pool, CodeAttribute codeAttribute) {
            this.version = version;
            this.linkedMethod = linkedMethod;
            this.pool = pool;
            this.codeAttribute = codeAttribute;
        }

        public MethodVersion getMethodVersion() {
            return version;
        }

        public LinkedMethod getLinkedMethod() {
            return linkedMethod;
        }

        public RuntimeConstantPool getPool() {
            return pool;
        }

        public CodeAttribute getCodeAttribute() {
            return codeAttribute;
        }
    }
    // endregion jdwp-specific

}
