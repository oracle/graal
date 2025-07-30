/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.constantpool;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.logging.Level;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.ParserConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.BootstrapMethodsAttribute;
import com.oracle.truffle.espresso.classfile.descriptors.Descriptor;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.perf.DebugCounter;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.impl.ClassRegistry;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Member;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.methodhandle.MHInvokeGenericNode;
import com.oracle.truffle.espresso.nodes.methodhandle.MHLinkToNode;
import com.oracle.truffle.espresso.redefinition.ClassRedefinition;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoLinkResolver;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;

public final class RuntimeConstantPool extends ConstantPool {

    private final ObjectKlass holder;

    @CompilationFinal(dimensions = 1) //
    private final ResolvedConstant[] resolvedConstants;

    private final ParserConstantPool parserConstantPool;

    public RuntimeConstantPool(ParserConstantPool parserConstantPool, ObjectKlass holder) {
        super(parserConstantPool);
        this.holder = holder;
        this.resolvedConstants = new ResolvedConstant[parserConstantPool.length()];
        this.parserConstantPool = parserConstantPool;
    }

    @Override
    public ParserConstantPool getParserConstantPool() {
        return this.parserConstantPool;
    }

    public void preResolveMethod(Method m, int idx) {
        assert !m.getDeclaringKlass().isInterface();
        resolvedConstants[idx] = new ResolvedClassMethodRefConstant(m);
    }

    private ResolvedConstant outOfLockResolvedAt(ObjectKlass accessingKlass, int index) {
        ResolvedConstant c = resolvedConstants[index];
        if (c == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // double check: deopt is a heavy operation.
            c = resolvedConstants[index];
            if (c == null) {
                ResolvedConstant locallyResolved = resolve(index, accessingKlass);
                synchronized (this) {
                    // Triple check: non-trivial resolution
                    c = resolvedConstants[index];
                    if (c == null) {
                        resolvedConstants[index] = c = locallyResolved;
                    }
                }
            }
        }
        return c;
    }

    /**
     * Returns the resolved, non-primitive, constant pool entry.
     */
    public ResolvedConstant resolvedAt(ObjectKlass accessingKlass, int index) {
        return resolvedAt(accessingKlass, index, true);
    }

    public ResolvedConstant resolvedAt(ObjectKlass accessingKlass, int index, boolean allowStickyFailures) {
        ResolvedConstant c = resolvedConstants[index];
        if (c == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                c = resolvedConstants[index];
                if (c == null) {
                    c = resolve(index, accessingKlass);
                    if (allowStickyFailures || c.isSuccess()) {
                        resolvedConstants[index] = c;
                    }
                }
            }
        }
        return c;
    }

    private ResolvedConstant resolvedAtNoCache(ObjectKlass accessingKlass, int index) {
        CompilerAsserts.neverPartOfCompilation();
        return resolve(index, accessingKlass);
    }

    public @JavaType(String.class) StaticObject resolvedStringAt(int index) {
        ResolvedConstant resolved = resolvedAt(null, index);
        return (StaticObject) resolved.value();
    }

    public Klass resolvedKlassAt(ObjectKlass accessingKlass, int index) {
        return resolvedKlassAt(accessingKlass, index, true);
    }

    public Klass resolvedKlassAt(ObjectKlass accessingKlass, int index, boolean allowStickyFailures) {
        ResolvedClassConstant resolved = (ResolvedClassConstant) resolvedAt(accessingKlass, index, allowStickyFailures);
        return (Klass) resolved.value();
    }

    public Field resolvedFieldAt(ObjectKlass accessingKlass, int index) {
        ResolvedConstant resolved = resolvedAt(accessingKlass, index);
        try {
            return ((Field) resolved.value());
        } catch (NeedsFreshResolutionException e) {
            // clear the constants cache and re-resolve
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                resolvedConstants[index] = null;
            }
            return resolvedFieldAt(accessingKlass, index);
        }
    }

    public Field resolveFieldAndUpdate(ObjectKlass accessingKlass, int index, Field field) {
        CompilerAsserts.neverPartOfCompilation();
        try {
            ResolvedConstant resolved = resolvedAtNoCache(accessingKlass, index);
            // a compatible field was found, so update the entry
            synchronized (this) {
                resolvedConstants[index] = resolved;
            }
            return ((Field) resolved.value());
        } catch (EspressoException e) {
            Field realField = field;
            if (realField.hasCompatibleField()) {
                realField = realField.getCompatibleField();
            }
            // A new compatible field was not found, but we still allow
            // obsolete code to use the latest known resolved field.
            // To avoid a de-opt loop here, we create a compatible delegation
            // field that actually uses the latest known resolved field
            // underneath.
            synchronized (this) {
                Field delegationField = getContext().getClassRedefinition().createDelegationFrom(realField);
                ResolvedConstant resolved = new ResolvedFieldRefConstant(delegationField);
                resolvedConstants[index] = resolved;
                return delegationField;
            }
        }
    }

    public boolean isResolutionSuccessAt(int index) {
        ResolvedConstant constant = resolvedConstants[index];
        return constant != null && constant.isSuccess();
    }

    public Method resolvedMethodAt(ObjectKlass accessingKlass, int index) {
        ResolvedConstant resolved = resolvedAt(accessingKlass, index);
        return (Method) resolved.value();
    }

    public Method resolveMethodAndUpdate(ObjectKlass accessingKlass, int index) {
        CompilerAsserts.neverPartOfCompilation();
        ResolvedConstant resolved = resolvedAtNoCache(accessingKlass, index);
        synchronized (this) {
            resolvedConstants[index] = resolved;
        }
        return ((Method) resolved.value());
    }

    public @JavaType(MethodHandle.class) StaticObject resolvedMethodHandleAt(ObjectKlass accessingKlass, int index) {
        ResolvedConstant resolved = resolvedAt(accessingKlass, index);
        return (StaticObject) resolved.value();
    }

    public @JavaType(MethodType.class) StaticObject resolvedMethodTypeAt(ObjectKlass accessingKlass, int index) {
        ResolvedConstant resolved = resolvedAt(accessingKlass, index);
        return (StaticObject) resolved.value();
    }

    public SuccessfulCallSiteLink linkInvokeDynamic(ObjectKlass accessingKlass, int index, Method method, int bci) {
        ResolvedInvokeDynamicConstant indy = (ResolvedInvokeDynamicConstant) resolvedAt(accessingKlass, index);
        CallSiteLink link = indy.link(this, accessingKlass, index, method, bci);
        if (link instanceof FailedCallSiteLink failed) {
            throw failed.fail();
        }
        return (SuccessfulCallSiteLink) link;
    }

    public ResolvedInvokeDynamicConstant peekResolvedInvokeDynamic(int index) {
        return (ResolvedInvokeDynamicConstant) resolvedConstants[index];
    }

    public ResolvedConstant peekResolvedOrNull(int index, Meta meta) {
        try {
            return resolvedConstants[index];
        } catch (IndexOutOfBoundsException e) {
            throw meta.throwIndexOutOfBoundsExceptionBoundary("Invalid constant pool index", index, resolvedConstants.length);
        }
    }

    public ResolvedDynamicConstant resolvedDynamicConstantAt(ObjectKlass accessingKlass, int index) {
        ResolvedDynamicConstant dynamicConstant = (ResolvedDynamicConstant) outOfLockResolvedAt(accessingKlass, index);
        return dynamicConstant;
    }

    public StaticObject getClassLoader() {
        return holder.getDefiningClassLoader();
    }

    public EspressoContext getContext() {
        return holder.getContext();
    }

    public ObjectKlass getHolder() {
        return holder;
    }

    public void setKlassAt(int index, ObjectKlass klass) {
        resolvedConstants[index] = new ResolvedFoundClassConstant(klass);
    }

    public StaticObject getMethodHandle(BootstrapMethodsAttribute.Entry entry, ObjectKlass accessingKlass) {
        return this.resolvedMethodHandleAt(accessingKlass, entry.getBootstrapMethodRef());
    }

    public StaticObject[] getStaticArguments(BootstrapMethodsAttribute.Entry entry, ObjectKlass accessingKlass) {
        Meta meta = accessingKlass.getMeta();
        StaticObject[] args = new StaticObject[entry.numBootstrapArguments()];
        for (int i = 0; i < entry.numBootstrapArguments(); i++) {
            args[i] = switch (tagAt(entry.argAt(i))) {
                case METHODHANDLE -> this.resolvedMethodHandleAt(accessingKlass, entry.argAt(i));
                case METHODTYPE -> this.resolvedMethodTypeAt(accessingKlass, entry.argAt(i));
                case DYNAMIC -> this.resolvedDynamicConstantAt(accessingKlass, entry.argAt(i)).guestBoxedValue(meta);
                case CLASS -> this.resolvedKlassAt(accessingKlass, entry.argAt(i)).mirror();
                case STRING -> this.resolvedStringAt(entry.argAt(i));
                case INTEGER -> meta.boxInteger(this.intAt(entry.argAt(i)));
                case LONG -> meta.boxLong(this.longAt(entry.argAt(i)));
                case DOUBLE -> meta.boxDouble(this.doubleAt(entry.argAt(i)));
                case FLOAT -> meta.boxFloat(this.floatAt(entry.argAt(i)));
                default -> {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere();
                }
            };
        }
        return args;
    }

    private ResolvedConstant resolve(int thisIndex, ObjectKlass accessingKlass) {
        return switch (tagAt(thisIndex)) {
            case STRING -> resolveStringConstant(thisIndex, accessingKlass);
            case FIELD_REF -> resolveFieldRefConstant(thisIndex, accessingKlass);
            case INTERFACE_METHOD_REF -> resolveInterfaceMethodRefConstant(thisIndex, accessingKlass);
            case METHOD_REF -> resolveClassMethodRefConstant(thisIndex, accessingKlass);
            case METHODTYPE -> resolveMethodTypeConstant(thisIndex, accessingKlass);
            case INVOKEDYNAMIC -> resolveInvokeDynamicConstant(thisIndex, accessingKlass);
            case DYNAMIC -> resolveDynamicConstant(thisIndex, accessingKlass);
            case METHODHANDLE -> resolveMethodHandleConstant(thisIndex, accessingKlass);
            case CLASS -> resolveClassConstant(thisIndex, accessingKlass);
            default -> throw EspressoError.shouldNotReachHere("Unexpected CP entry: " + toString(thisIndex));
        };
    }

    @Override
    @TruffleBoundary
    public @JavaType(ClassFormatError.class) EspressoException classFormatError(String message) {
        CompilerAsserts.neverPartOfCompilation();
        Meta meta = EspressoContext.get(null).getMeta();
        if (meta.java_lang_ClassFormatError == null) {
            throw EspressoError.fatal("ClassFormatError during early startup: ", message);
        }
        throw meta.throwExceptionWithMessage(meta.java_lang_ClassFormatError, message);
    }

    public void patchAt(int index, ResolvedConstant resolvedConstant) {
        assert resolvedConstant != null;
        assert resolvedConstant.value() != null;
        resolvedConstants[index] = resolvedConstant;
    }

    // Runtime constant pool resolution.

    private static final DebugCounter CLASS_RESOLVE_COUNT = DebugCounter.create("ClassConstant.resolve calls");
    private static final DebugCounter FIELDREF_RESOLVE_COUNT = DebugCounter.create("FieldRef.resolve calls");
    private static final DebugCounter METHODREF_RESOLVE_COUNT = DebugCounter.create("MethodRef.resolve calls");

    public static ResolvedConstant preResolvedConstant(@JavaType(Object.class) StaticObject resolved, ConstantPool.Tag tag) {
        return new PreResolvedConstant(resolved, tag);
    }

    public ResolvedStringConstant resolveStringConstant(int stringIndex, @SuppressWarnings("unused") ObjectKlass accessingKlass) {
        int utf8Index = this.stringUtf8Index(stringIndex);
        return new ResolvedStringConstant(this.getContext().getStrings().intern(this.utf8At(utf8Index)));
    }

    /**
     * <h3>5.4.3.1. Class and Interface Resolution</h3>
     * <p>
     * To resolve an unresolved symbolic reference from D to a class or interface C denoted by N,
     * the following steps are performed:
     * <ol>
     * <li>The defining class loader of D is used to create a class or interface denoted by N. This
     * class or interface is C. The details of the process are given in &sect;5.3. <b>Any exception
     * that can be thrown as a result of failure of class or interface creation can thus be thrown
     * as a result of failure of class and interface resolution.</b>
     * <li>If C is an array class and its element type is a reference type, then a symbolic
     * reference to the class or interface representing the element type is resolved by invoking the
     * algorithm in &sect;5.4.3.1 recursively.
     * <li>Finally, access permissions to C are checked.
     * <ul>
     * <li><b>If C is not accessible (&sect;5.4.4) to D, class or interface resolution throws an
     * IllegalAccessError.</b> This condition can occur, for example, if C is a class that was
     * originally declared to be public but was changed to be non-public after D was compiled.
     * </ul>
     * </ol>
     * If steps 1 and 2 succeed but step 3 fails, C is still valid and usable. Nevertheless,
     * resolution fails, and D is prohibited from accessing C.
     */
    @SuppressWarnings("try")
    public ResolvedClassConstant resolveClassConstant(int classIndex, ObjectKlass accessingKlass) {
        try (EspressoLanguage.DisableSingleStepping ignored = accessingKlass.getLanguage().disableStepping()) {
            CLASS_RESOLVE_COUNT.inc();
            assert accessingKlass != null;
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Symbol<Name> klassName = this.className(classIndex);
            EspressoContext context = this.getContext();
            Symbol<Type> type = context.getTypes().fromClassNameEntry(klassName);
            Klass klass = context.getMeta().resolveSymbolOrFail(type, accessingKlass.getDefiningClassLoader(), accessingKlass.protectionDomain());
            Klass checkedKlass = klass.getElementalType();
            if (!Klass.checkAccess(checkedKlass, accessingKlass)) {
                Meta meta = context.getMeta();
                context.getLogger().log(Level.FINE,
                                "Access check of: " + checkedKlass.getType() + " from " + accessingKlass.getType() + " throws IllegalAccessError");
                StringBuilder errorMessage = new StringBuilder("failed to access class ");
                errorMessage.append(checkedKlass.getExternalName()).append(" from class ").append(accessingKlass.getExternalName());
                ClassRegistry.appendModuleAndLoadersDetails(context.getClassLoadingEnv(), checkedKlass, accessingKlass, errorMessage, context);
                throw meta.throwExceptionWithMessage(meta.java_lang_IllegalAccessError, errorMessage.toString());
            }
            return new ResolvedFoundClassConstant(klass);
        } catch (EspressoException e) {
            CompilerDirectives.transferToInterpreter();
            Meta meta = this.getContext().getMeta();
            // Comment from Hotspot:
            // Just throw the exception and don't prevent these classes from being loaded for
            // virtual machine errors like StackOverflow and OutOfMemoryError, etc.
            // Needs clarification to section 5.4.3 of the JVM spec (see 6308271)
            if (meta.java_lang_LinkageError.isAssignableFrom(e.getGuestException().getKlass())) {
                return new ResolvedFailClassConstant(e);
            }
            throw e;
        }
    }

    public ResolvedConstant resolveFieldRefConstant(int fieldIndex, ObjectKlass accessingKlass) {
        FIELDREF_RESOLVE_COUNT.inc();
        Klass holderKlass = getResolvedHolderKlass(fieldIndex, accessingKlass);
        Symbol<Name> name = this.fieldName(fieldIndex);
        Symbol<Type> type = this.fieldType(fieldIndex);

        EspressoContext context = this.getContext();
        Field field;
        ClassRedefinition classRedefinition = null;
        try {
            try {
                field = EspressoLinkResolver.resolveFieldSymbolOrThrow(context, accessingKlass, name, type, holderKlass, true, true);
            } catch (EspressoException e) {
                classRedefinition = context.getClassRedefinition();
                if (classRedefinition != null) {
                    // could be due to ongoing redefinition
                    classRedefinition.check();
                    field = EspressoLinkResolver.resolveFieldSymbolOrThrow(context, accessingKlass, name, type, holderKlass, true, true);
                } else {
                    throw e;
                }
            }
        } catch (EspressoException e) {
            return new MissingFieldRefConstant(e, classRedefinition == null ? Assumption.ALWAYS_VALID : classRedefinition.getMissingFieldAssumption());
        }

        return new ResolvedFieldRefConstant(field);
    }

    public Klass getResolvedHolderKlass(int memberIndex, ObjectKlass accessingKlass) {
        int classIndex = this.memberClassIndex(memberIndex);
        return this.resolvedKlassAt(accessingKlass, classIndex);
    }

    /**
     * <h3>5.4.4. Access Control</h3>
     * <p>
     * A field or method R is accessible to a class or interface D if and only if any of the
     * following is true:
     * <ul>
     * <li>R is public.
     * <li>R is protected and is declared in a class C, and D is either a subclass of C or C itself.
     * Furthermore, if R is not static, then the symbolic reference to R must contain a symbolic
     * reference to a class T, such that T is either a subclass of D, a superclass of D, or D
     * itself.
     * <li>R is either protected or has default access (that is, neither public nor protected nor
     * private), and is declared by a class in the same run-time package as D.
     * <li>R is private and is declared in D.
     * </ul>
     */
    public static boolean memberCheckAccess(Klass accessingKlass, Klass resolvedKlass, Member<? extends Descriptor> member) {
        if (member.isPublic()) {
            return true;
        }
        Klass memberKlass = member.getDeclaringKlass();
        if (member instanceof Method && Names.clone.equals(member.getName()) && memberKlass.isJavaLangObject()) {
            if (resolvedKlass.isArray()) {
                return true;
            }
        }
        if (member.isProtected()) {
            if (!member.isStatic()) {
                if (resolvedKlass.isAssignableFrom(accessingKlass) || accessingKlass.isAssignableFrom(resolvedKlass)) {
                    return true;
                }
            } else {
                if (memberKlass.isAssignableFrom(accessingKlass)) {
                    return true;
                }
            }
        }
        if (member.isProtected() || member.isPackagePrivate()) {
            if (accessingKlass.sameRuntimePackage(memberKlass)) {
                return true;
            }
        }
        if (member.isPrivate() && nestMateTest(accessingKlass, memberKlass)) {
            return true;
        }
        // MagicAccessorImpl marks internal reflection classes that have access to everything.
        if (accessingKlass.isMagicAccessor()) {
            return true;
        }

        if (accessingKlass.getHostClass() != null) {
            CompilerAsserts.partialEvaluationConstant(accessingKlass);
            return memberCheckAccess(accessingKlass.getHostClass(), resolvedKlass, member);
        }
        return false;
    }

    static boolean nestMateTest(Klass k1, Klass k2) {
        return k1 == k2 || k1.nest() == k2.nest();
    }

    /**
     * <h3>5.4.3.4. Interface Method Resolution</h3>
     * <p>
     * To resolve an unresolved symbolic reference from D to an interface method in an interface C,
     * the symbolic reference to C given by the interface method reference is first resolved
     * (&sect;5.4.3.1). Therefore, any exception that can be thrown as a result of failure of
     * resolution of an interface reference can be thrown as a result of failure of interface method
     * resolution. If the reference to C can be successfully resolved, exceptions relating to the
     * resolution of the interface method reference itself can be thrown.
     * <p>
     * When resolving an interface method reference:
     * <ol>
     * <li><b>If C is not an interface, interface method resolution throws an
     * IncompatibleClassChangeError.</b>
     * <li>Otherwise, if C declares a method with the name and descriptor specified by the interface
     * method reference, method lookup succeeds.
     * <li>Otherwise, if the class Object declares a method with the name and descriptor specified
     * by the interface method reference, which has its ACC_PUBLIC flag set and does not have its
     * ACC_STATIC flag set, method lookup succeeds.
     * <li>Otherwise, if the maximally-specific superinterface methods (&sect;5.4.3.3) of C for the
     * name and descriptor specified by the method reference include exactly one method that does
     * not have its ACC_ABSTRACT flag set, then this method is chosen and method lookup succeeds.
     * <li>Otherwise, if any superinterface of C declares a method with the name and descriptor
     * specified by the method reference that has neither its ACC_PRIVATE flag nor its ACC_STATIC
     * flag set, one of these is arbitrarily chosen and method lookup succeeds.
     * <li>Otherwise, method lookup fails.
     * </ol>
     * <p>
     * The result of interface method resolution is determined by whether method lookup succeeds or
     * fails:
     * <ul>
     * <li><b>If method lookup fails, interface method resolution throws a NoSuchMethodError.</b>
     * <li><b>If method lookup succeeds and the referenced method is not accessible (&sect;5.4.4) to
     * D, interface method resolution throws an IllegalAccessError.</b>
     * <li>Otherwise, let < E, L1 > be the class or interface in which the referenced interface
     * method m is actually declared, and let L2 be the defining loader of D.
     * <li>Given that the return type of m is Tr, and that the formal parameter types of m are Tf1,
     * ..., Tfn, then:
     * <li>If Tr is not an array type, let T0 be Tr; otherwise, let T0 be the element type
     * (&sect;2.4) of Tr.
     * <li>For i = 1 to n: If Tfi is not an array type, let Ti be Tfi; otherwise, let Ti be the
     * element type (&sect;2.4) of Tfi.
     * <li>The Java Virtual Machine must impose the loading constraints TiL1 = TiL2 for i = 0 to n
     * (&sect;5.3.4).
     * </ul>
     * The clause about accessibility is necessary because interface method resolution may pick a
     * private method of interface C. (Prior to Java SE 8, the result of interface method resolution
     * could be a non-public method of class Object or a static method of class Object; such results
     * were not consistent with the inheritance model of the Java programming language, and are
     * disallowed in Java SE 8 and above.)
     */
    public ResolvedConstant resolveInterfaceMethodRefConstant(int interfaceMethodIndex, ObjectKlass accessingKlass) {
        METHODREF_RESOLVE_COUNT.inc();

        Klass holderInterface = getResolvedHolderKlass(interfaceMethodIndex, accessingKlass);
        EspressoContext context = this.getContext();

        Symbol<Name> name = this.methodName(interfaceMethodIndex);
        Symbol<Signature> signature = this.methodSignature(interfaceMethodIndex);

        Method method = EspressoLinkResolver.resolveMethodSymbol(context, accessingKlass, name, signature, holderInterface, true, true, true);

        return new ResolvedInterfaceMethodRefConstant(method);
    }

    /**
     * <h3>5.4.3.3. Method Resolution</h3>
     * <p>
     * To resolve an unresolved symbolic reference from D to a method in a class C, the symbolic
     * reference to C given by the method reference is first resolved (&sect;5.4.3.1). Therefore,
     * any exception that can be thrown as a result of failure of resolution of a class reference
     * can be thrown as a result of failure of method resolution. If the reference to C can be
     * successfully resolved, exceptions relating to the resolution of the method reference itself
     * can be thrown.
     * <p>
     * When resolving a method reference:
     * <ol>
     *
     * <li>If C is an interface, method resolution throws an IncompatibleClassChangeError.
     *
     * <li>Otherwise, method resolution attempts to locate the referenced method in C and its
     * superclasses:
     * <ul>
     *
     * <li>If C declares exactly one method with the name specified by the method reference, and the
     * declaration is a signature polymorphic method (&sect;2.9), then method lookup succeeds. All
     * the class names mentioned in the descriptor are resolved (&sect;5.4.3.1).
     *
     * <li>The resolved method is the signature polymorphic method declaration. It is not necessary
     * for C to declare a method with the descriptor specified by the method reference.
     *
     * <li>Otherwise, if C declares a method with the name and descriptor specified by the method
     * reference, method lookup succeeds.
     *
     * <li>Otherwise, if C has a superclass, step 2 of method resolution is recursively invoked on
     * the direct superclass of C.
     * </ul>
     *
     * <li>Otherwise, method resolution attempts to locate the referenced method in the
     * superinterfaces of the specified class C:
     * <ul>
     * <li>If the maximally-specific superinterface methods of C for the name and descriptor
     * specified by the method reference include exactly one method that does not have its
     * ACC_ABSTRACT flag set, then this method is chosen and method lookup succeeds.
     *
     * <li>Otherwise, if any superinterface of C declares a method with the name and descriptor
     * specified by the method reference that has neither its ACC_PRIVATE flag nor its ACC_STATIC
     * flag set, one of these is arbitrarily chosen and method lookup succeeds.
     *
     * <li>Otherwise, method lookup fails.
     * </ul>
     * </ol>
     * <p>
     * A maximally-specific superinterface method of a class or interface C for a particular method
     * name and descriptor is any method for which all of the following are true:
     *
     * <ul>
     * <li>The method is declared in a superinterface (direct or indirect) of C.
     *
     * <li>The method is declared with the specified name and descriptor.
     *
     * <li>The method has neither its ACC_PRIVATE flag nor its ACC_STATIC flag set.
     *
     * <li>Where the method is declared in interface I, there exists no other maximally-specific
     * superinterface method of C with the specified name and descriptor that is declared in a
     * subinterface of I.
     * </ul>
     * The result of method resolution is determined by whether method lookup succeeds or fails:
     * <ul>
     * <li>If method lookup fails, method resolution throws a NoSuchMethodError.
     *
     * <li>Otherwise, if method lookup succeeds and the referenced method is not accessible
     * (&sect;5.4.4) to D, method resolution throws an IllegalAccessError.
     * <p>
     * Otherwise, let < E, L1 > be the class or interface in which the referenced method m is
     * actually declared, and let L2 be the defining loader of D.
     * <p>
     * Given that the return type of m is Tr, and that the formal parameter types of m are Tf1, ...,
     * Tfn, then:
     * <p>
     * If Tr is not an array type, let T0 be Tr; otherwise, let T0 be the element type (&sect;2.4)
     * of Tr.
     * <p>
     * For i = 1 to n: If Tfi is not an array type, let Ti be Tfi; otherwise, let Ti be the element
     * type (&sect;2.4) of Tfi.
     * <p>
     * The Java Virtual Machine must impose the loading constraints TiL1 = TiL2 for i = 0 to n
     * (&sect;5.3.4).
     * </ul>
     * When resolution searches for a method in the class's superinterfaces, the best outcome is to
     * identify a maximally-specific non-abstract method. It is possible that this method will be
     * chosen by method selection, so it is desirable to add class loader constraints for it.
     * <p>
     * Otherwise, the result is nondeterministic. This is not new: The Java&reg; Virtual Machine
     * Specification has never identified exactly which method is chosen, and how "ties" should be
     * broken. Prior to Java SE 8, this was mostly an unobservable distinction. However, beginning
     * with Java SE 8, the set of interface methods is more heterogenous, so care must be taken to
     * avoid problems with nondeterministic behavior. Thus:
     *
     * <ul>
     * <li>Superinterface methods that are private and static are ignored by resolution. This is
     * consistent with the Java programming language, where such interface methods are not
     * inherited.
     *
     * <li>Any behavior controlled by the resolved method should not depend on whether the method is
     * abstract or not.
     * </ul>
     * Note that if the result of resolution is an abstract method, the referenced class C may be
     * non-abstract. Requiring C to be abstract would conflict with the nondeterministic choice of
     * superinterface methods. Instead, resolution assumes that the run time class of the invoked
     * object has a concrete implementation of the method.
     */
    public ResolvedConstant resolveClassMethodRefConstant(int classMethodIndex, ObjectKlass accessingKlass) {
        METHODREF_RESOLVE_COUNT.inc();

        EspressoContext context = this.getContext();

        Klass holderKlass = getResolvedHolderKlass(classMethodIndex, accessingKlass);

        Symbol<Name> name = this.methodName(classMethodIndex);
        Symbol<Signature> signature = this.methodSignature(classMethodIndex);

        Method method = EspressoLinkResolver.resolveMethodSymbol(context, accessingKlass, name, signature, holderKlass, false, true, true);

        if (method.isInvokeIntrinsic()) {
            MHInvokeGenericNode.MethodHandleInvoker invoker = MHInvokeGenericNode.linkMethod(context.getMeta().getLanguage(), context.getMeta(), accessingKlass, method, name, signature);
            return new ResolvedWithInvokerClassMethodRefConstant(method, invoker);
        }

        return new ResolvedClassMethodRefConstant(method);
    }

    public static StaticObject signatureToMethodType(Symbol<Type>[] signature, ObjectKlass accessingKlass, boolean failWithBME, Meta meta) {
        Symbol<Type> rt = SignatureSymbols.returnType(signature);
        int pcount = SignatureSymbols.parameterCount(signature);

        StaticObject[] ptypes = new StaticObject[pcount];
        StaticObject rtype;
        for (int i = 0; i < pcount; i++) {
            Symbol<Type> paramType = SignatureSymbols.parameterType(signature, i);
            ptypes[i] = meta.resolveSymbolAndAccessCheck(paramType, accessingKlass).mirror();
        }
        try {
            rtype = meta.resolveSymbolAndAccessCheck(rt, accessingKlass).mirror();
        } catch (EspressoException e) {
            EspressoException rethrow = e;
            if (failWithBME) {
                rethrow = EspressoException.wrap(Meta.initExceptionWithCause(meta.java_lang_BootstrapMethodError, rethrow.getGuestException()), meta);
            }
            throw rethrow;
        }

        return (StaticObject) meta.java_lang_invoke_MethodHandleNatives_findMethodHandleType.invokeDirectStatic(rtype, StaticObject.createArray(meta.java_lang_Class_array, ptypes, meta.getContext()));
    }

    public ResolvedMethodTypeConstant resolveMethodTypeConstant(int methodTypeIndex, ObjectKlass accessingKlass) {
        Symbol<Signature> sig = this.methodTypeSignature(methodTypeIndex);
        Meta meta = accessingKlass.getContext().getMeta();
        return new ResolvedMethodTypeConstant(signatureToMethodType(meta.getSignatures().parsed(sig), accessingKlass, false, meta));
    }

    public ResolvedConstant resolveInvokeDynamicConstant(int invokeDynamicIndex, ObjectKlass accessingKlass) {
        CompilerAsserts.neverPartOfCompilation();
        BootstrapMethodsAttribute bms = (BootstrapMethodsAttribute) accessingKlass.getAttribute(BootstrapMethodsAttribute.NAME);
        int bootstrapMethodAttrIndex = this.invokeDynamicBootstrapMethodAttrIndex(invokeDynamicIndex);
        BootstrapMethodsAttribute.Entry bsEntry = bms.at(bootstrapMethodAttrIndex);

        Meta meta = accessingKlass.getMeta();
        Symbol<Signature> invokeSignature = this.invokeDynamicSignature(invokeDynamicIndex);
        Symbol<Type>[] parsedInvokeSignature = meta.getSignatures().parsed(invokeSignature);
        return new ResolvedInvokeDynamicConstant(bsEntry, parsedInvokeSignature, this.invokeDynamicName(invokeDynamicIndex));
    }

    private static ResolvedDynamicConstant makeResolved(Klass type, StaticObject result) {
        JavaKind kind = type.getJavaKind();
        switch (kind) {
            case Boolean:
            case Byte:
            case Short:
            case Char: {
                int value = (int) MHLinkToNode.rebasic(type.getMeta().unboxGuest(result), kind);
                return new ResolvedIntDynamicConstant(value, kind);
            }
            case Int: {
                int value = type.getMeta().unboxInteger(result);
                return new ResolvedIntDynamicConstant(value, JavaKind.Int);
            }
            case Float: {
                float value = type.getMeta().unboxFloat(result);
                return new ResolvedFloatDynamicConstant(value);
            }
            case Long: {
                long value = type.getMeta().unboxLong(result);
                return new ResolvedLongDynamicConstant(value);
            }
            case Double: {
                double value = type.getMeta().unboxDouble(result);
                return new ResolvedDoubleDynamicConstant(value);
            }
            case Object:
                return new ResolvedObjectDynamicConstant(result);
        }
        throw EspressoError.shouldNotReachHere();
    }

    public ResolvedConstant resolveDynamicConstant(int dynamicIndex, ObjectKlass accessingKlass) {
        Meta meta = accessingKlass.getMeta();

        // Condy constant resolving.
        BootstrapMethodsAttribute bms = (BootstrapMethodsAttribute) accessingKlass.getAttribute(BootstrapMethodsAttribute.NAME);

        assert (bms != null);
        // TODO(garcia) cache bootstrap method resolution
        // Bootstrap method resolution
        try {
            int bootstrapMethodAttrIndex = this.dynamicBootstrapMethodAttrIndex(dynamicIndex);
            BootstrapMethodsAttribute.Entry bsEntry = bms.at(bootstrapMethodAttrIndex);

            StaticObject bootstrapmethodMethodHandle = this.getMethodHandle(bsEntry, accessingKlass);
            StaticObject[] args = this.getStaticArguments(bsEntry, accessingKlass);

            StaticObject fieldName = meta.toGuestString(this.dynamicName(dynamicIndex));
            Klass fieldType = meta.resolveSymbolOrFail(this.dynamicType(dynamicIndex),
                            accessingKlass.getDefiningClassLoader(),
                            accessingKlass.protectionDomain());

            Object result = null;
            if (!meta.getJavaVersion().java19OrLater()) {
                result = meta.java_lang_invoke_MethodHandleNatives_linkDynamicConstant.invokeDirectStatic(
                                accessingKlass.mirror(),
                                dynamicIndex,
                                bootstrapmethodMethodHandle,
                                fieldName, fieldType.mirror(),
                                StaticObject.wrap(args, meta));
            } else {
                result = meta.java_lang_invoke_MethodHandleNatives_linkDynamicConstant.invokeDirectStatic(
                                accessingKlass.mirror(),
                                bootstrapmethodMethodHandle,
                                fieldName, fieldType.mirror(),
                                StaticObject.wrap(args, meta));
            }
            try {
                return makeResolved(fieldType, (StaticObject) result);
            } catch (ClassCastException | NullPointerException e) {
                throw meta.throwException(meta.java_lang_BootstrapMethodError);
            } catch (EspressoException e) {
                if (meta.java_lang_NullPointerException.isAssignableFrom(e.getGuestException().getKlass()) ||
                                meta.java_lang_ClassCastException.isAssignableFrom(e.getGuestException().getKlass())) {
                    throw meta.throwExceptionWithCause(meta.java_lang_BootstrapMethodError, e.getGuestException());
                }
                throw e;
            }
        } catch (EspressoException e) {
            // Comment from Hotspot:
            // Just throw the exception and don't prevent these classes from being loaded for
            // virtual machine errors like StackOverflow and OutOfMemoryError, etc.
            // Needs clarification to section 5.4.3 of the JVM spec (see 6308271)
            if (meta.java_lang_LinkageError.isAssignableFrom(e.getGuestException().getKlass())) {
                return new ResolvedFailDynamicConstant(e);
            }
            throw e;
        }
    }

    public ResolvedConstant resolveMethodHandleConstant(int methodHandleIndex, ObjectKlass accessingKlass) {
        Meta meta = this.getContext().getMeta();
        if (meta.getLanguage().getSpecComplianceMode() == EspressoOptions.SpecComplianceMode.STRICT || meta.getJavaVersion().java9OrLater()) {
            return specCompliantResolution(methodHandleIndex, accessingKlass, meta);
        } else {
            return hotspotResolutionBehavior(methodHandleIndex, accessingKlass, meta);
        }
    }

    private ResolvedConstant specCompliantResolution(int methodHandleIndex, ObjectKlass accessingKlass, Meta meta) {
        StaticObject mtype;
        Klass mklass;
        Symbol<Name> refName;

        int memberIndex = this.methodHandleMemberIndex(methodHandleIndex);

        ConstantPool.Tag refTag = this.tagAt(memberIndex);
        if (refTag == ConstantPool.Tag.METHOD_REF || refTag == ConstantPool.Tag.INTERFACE_METHOD_REF) {
            Method target = this.resolvedMethodAt(accessingKlass, memberIndex);
            Symbol<Type>[] parsed = target.getParsedSignature();

            mtype = signatureToMethodType(parsed, accessingKlass, false, meta);
            /*
             * we should use the klass from the method ref here rather than the declaring klass of
             * the target this is because the resolved target might come from a default method and
             * have an interface as declaring klass however if the refKind is invokeVirtual, it
             * would be illegal to use the interface type
             */
            int holderIndex = this.memberClassIndex(memberIndex);
            mklass = this.resolvedKlassAt(accessingKlass, holderIndex);
            refName = target.getName();
        } else {
            assert refTag == ConstantPool.Tag.FIELD_REF;
            Field field = this.resolvedFieldAt(accessingKlass, memberIndex);
            mtype = meta.resolveSymbolAndAccessCheck(field.getType(), accessingKlass).mirror();
            mklass = field.getDeclaringKlass();
            refName = field.getName();
        }

        return linkMethodHandleConstant(methodHandleIndex, accessingKlass, meta, mtype, mklass, refName);
    }

    /**
     * Resolves a method handle without resolving the method reference, which is not the behavior
     * described in the specs {5.4.3.5. Method Type and Method Handle Resolution }
     * <p>
     * see {@code JDK-8188145}
     */
    private ResolvedConstant hotspotResolutionBehavior(int methodHandleIndex, ObjectKlass accessingKlass, Meta meta) {
        StaticObject mtype;
        Klass mklass;
        Symbol<Name> refName;

        int memberIndex = this.methodHandleMemberIndex(methodHandleIndex);

        ConstantPool.Tag refTag = this.tagAt(memberIndex);
        if (refTag == ConstantPool.Tag.METHOD_REF || refTag == ConstantPool.Tag.INTERFACE_METHOD_REF) {
            Symbol<Signature> signature = this.methodSignature(memberIndex);
            Symbol<Type>[] parsed = meta.getSignatures().parsed(signature);

            mtype = signatureToMethodType(parsed, accessingKlass, false, meta);
            int holderIndex = this.memberClassIndex(memberIndex);
            mklass = this.resolvedKlassAt(accessingKlass, holderIndex);
            refName = this.methodName(memberIndex);
        } else {
            assert refTag == ConstantPool.Tag.FIELD_REF;
            Symbol<Type> type = this.fieldType(memberIndex);
            mtype = meta.resolveSymbolAndAccessCheck(type, accessingKlass).mirror();
            mklass = getResolvedHolderKlass(memberIndex, accessingKlass);
            refName = this.fieldName(memberIndex);
        }

        return linkMethodHandleConstant(methodHandleIndex, accessingKlass, meta, mtype, mklass, refName);
    }

    private ResolvedConstant linkMethodHandleConstant(int methodHandleIndex, Klass accessingKlass, Meta meta, StaticObject mtype, Klass mklass, Symbol<Name> refName) {
        StaticObject mname = meta.toGuestString(refName);
        int refKind = this.methodHandleRefKind(methodHandleIndex);
        return new ResolvedMethodHandleConstant((StaticObject) meta.java_lang_invoke_MethodHandleNatives_linkMethodHandleConstant.invokeDirectStatic(
                        accessingKlass.mirror(), refKind,
                        mklass.mirror(), mname, mtype));
    }
}
