/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.logging.Level;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.attributes.BootstrapMethodsAttribute;
import com.oracle.truffle.espresso.classfile.constantpool.ClassConstant;
import com.oracle.truffle.espresso.classfile.constantpool.ClassMethodRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.DynamicConstant;
import com.oracle.truffle.espresso.classfile.constantpool.FieldRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.InterfaceMethodRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.InvokeDynamicConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MemberRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodHandleConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodTypeConstant;
import com.oracle.truffle.espresso.classfile.constantpool.Resolvable;
import com.oracle.truffle.espresso.classfile.constantpool.StringConstant;
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

public final class Resolution {
    static final DebugCounter CLASS_RESOLVE_COUNT = DebugCounter.create("ClassConstant.resolve calls");
    static final DebugCounter FIELDREF_RESOLVE_COUNT = DebugCounter.create("FieldRef.resolve calls");
    static final DebugCounter METHODREF_RESOLVE_COUNT = DebugCounter.create("MethodRef.resolve calls");

    public static ResolvedStringConstant resolveStringConstant(StringConstant.Index thiz, RuntimeConstantPool pool, @SuppressWarnings("unused") int thisIndex,
                    @SuppressWarnings("unused") ObjectKlass accessingKlass) {
        return new ResolvedStringConstant(pool.getContext().getStrings().intern(thiz.getSymbol(pool)));
    }

    public static Resolvable.ResolvedConstant preResolvedConstant(@JavaType(Object.class) StaticObject resolved, Tag tag) {
        return new PreResolvedConstant(resolved, tag);
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
    public static ResolvedClassConstant resolveClassConstant(ClassConstant.Index thiz, RuntimeConstantPool pool, @SuppressWarnings("unused") int thisIndex, ObjectKlass accessingKlass) {
        try (EspressoLanguage.DisableSingleStepping ignored = accessingKlass.getLanguage().disableStepping()) {
            CLASS_RESOLVE_COUNT.inc();
            assert accessingKlass != null;
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Symbol<Name> klassName = thiz.getName(pool);
            EspressoContext context = pool.getContext();
            Symbol<Type> type = context.getTypes().fromClassNameEntry(klassName);
            Klass klass = context.getMeta().resolveSymbolOrFail(type, accessingKlass.getDefiningClassLoader(), accessingKlass.protectionDomain());
            Klass checkedKlass = klass.getElementalType();
            if (!Klass.checkAccess(checkedKlass, accessingKlass, false)) {
                Meta meta = context.getMeta();
                context.getLogger().log(Level.FINE,
                                "Access check of: " + checkedKlass.getType() + " from " + accessingKlass.getType() + " throws IllegalAccessError");
                StringBuilder errorMessage = new StringBuilder("failed to access class ");
                errorMessage.append(checkedKlass.getExternalName()).append(" from class ").append(accessingKlass.getExternalName());
                if (context.getJavaVersion().modulesEnabled()) {
                    errorMessage.append(" (");
                    if (accessingKlass.module() == checkedKlass.module()) {
                        errorMessage.append(checkedKlass.getExternalName());
                        errorMessage.append(" and ");
                        ClassRegistry.classInModuleOfLoader(accessingKlass, true, errorMessage, meta);
                    } else {
                        // checkedKlass is not an array type (getElementalType) nor a
                        // primitive type (it would have passed the access checks)
                        ClassRegistry.classInModuleOfLoader((ObjectKlass) checkedKlass, false, errorMessage, meta);
                        errorMessage.append("; ");
                        ClassRegistry.classInModuleOfLoader(accessingKlass, false, errorMessage, meta);
                    }
                    errorMessage.append(")");
                }
                throw meta.throwExceptionWithMessage(meta.java_lang_IllegalAccessError, errorMessage.toString());
            }
            return new ResolvedFoundClassConstant(klass);
        } catch (EspressoException e) {
            CompilerDirectives.transferToInterpreter();
            Meta meta = pool.getContext().getMeta();
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

    public static Resolvable.ResolvedConstant resolveFieldRefConstant(FieldRefConstant.Indexes thiz, RuntimeConstantPool pool, @SuppressWarnings("unused") int thisIndex, ObjectKlass accessingKlass) {
        FIELDREF_RESOLVE_COUNT.inc();
        Klass holderKlass = getResolvedHolderKlass(thiz, pool, accessingKlass);
        Symbol<Name> name = thiz.getName(pool);
        Symbol<Type> type = thiz.getType(pool);

        EspressoContext context = pool.getContext();
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

    public static Klass getResolvedHolderKlass(MemberRefConstant.Indexes thiz, RuntimeConstantPool pool, ObjectKlass accessingKlass) {
        return pool.resolvedKlassAt(accessingKlass, thiz.getHolderIndex());
    }

    /**
     * <h3>5.4.4. Access Control</h3>
     *
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
        if (accessingKlass.getMeta().sun_reflect_MagicAccessorImpl.isAssignableFrom(accessingKlass)) {
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
     *
     * To resolve an unresolved symbolic reference from D to an interface method in an interface C,
     * the symbolic reference to C given by the interface method reference is first resolved
     * (&sect;5.4.3.1). Therefore, any exception that can be thrown as a result of failure of
     * resolution of an interface reference can be thrown as a result of failure of interface method
     * resolution. If the reference to C can be successfully resolved, exceptions relating to the
     * resolution of the interface method reference itself can be thrown.
     *
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
     *
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
    public static Resolvable.ResolvedConstant resolveInterfaceMethodRefConstant(InterfaceMethodRefConstant.Indexes thiz, RuntimeConstantPool pool, @SuppressWarnings("unused") int thisIndex,
                    ObjectKlass accessingKlass) {
        METHODREF_RESOLVE_COUNT.inc();

        Klass holderInterface = getResolvedHolderKlass(thiz, pool, accessingKlass);
        EspressoContext context = pool.getContext();
        Symbol<Name> name = thiz.getName(pool);
        Symbol<Signature> signature = thiz.getSignature(pool);

        Method method = EspressoLinkResolver.resolveMethodSymbol(context, accessingKlass, name, signature, holderInterface, true, true, true);

        return new ResolvedInterfaceMethodRefConstant(method);
    }

    /**
     * <h3>5.4.3.3. Method Resolution</h3>
     *
     * To resolve an unresolved symbolic reference from D to a method in a class C, the symbolic
     * reference to C given by the method reference is first resolved (&sect;5.4.3.1). Therefore,
     * any exception that can be thrown as a result of failure of resolution of a class reference
     * can be thrown as a result of failure of method resolution. If the reference to C can be
     * successfully resolved, exceptions relating to the resolution of the method reference itself
     * can be thrown.
     *
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
     *
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
     *
     * Otherwise, let < E, L1 > be the class or interface in which the referenced method m is
     * actually declared, and let L2 be the defining loader of D.
     *
     * Given that the return type of m is Tr, and that the formal parameter types of m are Tf1, ...,
     * Tfn, then:
     *
     * If Tr is not an array type, let T0 be Tr; otherwise, let T0 be the element type (&sect;2.4)
     * of Tr.
     *
     * For i = 1 to n: If Tfi is not an array type, let Ti be Tfi; otherwise, let Ti be the element
     * type (&sect;2.4) of Tfi.
     *
     * The Java Virtual Machine must impose the loading constraints TiL1 = TiL2 for i = 0 to n
     * (&sect;5.3.4).
     * </ul>
     * When resolution searches for a method in the class's superinterfaces, the best outcome is to
     * identify a maximally-specific non-abstract method. It is possible that this method will be
     * chosen by method selection, so it is desirable to add class loader constraints for it.
     *
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
    public static Resolvable.ResolvedConstant resolveClassMethodRefConstant(ClassMethodRefConstant.Indexes thiz, RuntimeConstantPool pool, @SuppressWarnings("unused") int thisIndex,
                    ObjectKlass accessingKlass) {
        METHODREF_RESOLVE_COUNT.inc();

        EspressoContext context = pool.getContext();

        Klass holderKlass = getResolvedHolderKlass(thiz, pool, accessingKlass);
        Symbol<Name> name = thiz.getName(pool);
        Symbol<Signature> signature = thiz.getSignature(pool);

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

    public static ResolvedMethodTypeConstant resolveMethodTypeConstant(MethodTypeConstant.Index thiz, RuntimeConstantPool pool, @SuppressWarnings("unused") int thisIndex, ObjectKlass accessingKlass) {
        Symbol<Signature> sig = thiz.getSignature(pool);
        Meta meta = accessingKlass.getContext().getMeta();
        return new ResolvedMethodTypeConstant(signatureToMethodType(meta.getSignatures().parsed(sig), accessingKlass, false, meta));
    }

    public static Resolvable.ResolvedConstant resolveInvokeDynamicConstant(InvokeDynamicConstant.Indexes thiz, RuntimeConstantPool pool, @SuppressWarnings("unused") int thisIndex,
                    ObjectKlass accessingKlass) {
        CompilerAsserts.neverPartOfCompilation();
        BootstrapMethodsAttribute bms = (BootstrapMethodsAttribute) accessingKlass.getAttribute(BootstrapMethodsAttribute.NAME);
        BootstrapMethodsAttribute.Entry bsEntry = bms.at(thiz.getBootstrapMethodAttrIndex());

        Meta meta = accessingKlass.getMeta();
        Symbol<Signature> invokeSignature = thiz.getSignature(pool);
        Symbol<Type>[] parsedInvokeSignature = meta.getSignatures().parsed(invokeSignature);
        return new ResolvedInvokeDynamicConstant(bsEntry, parsedInvokeSignature, thiz.getName(pool));
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

    public static Resolvable.ResolvedConstant resolveDynamicConstant(DynamicConstant.Indexes thiz, RuntimeConstantPool pool, @SuppressWarnings("unused") int thisIndex, ObjectKlass accessingKlass) {
        Meta meta = accessingKlass.getMeta();

        // Condy constant resolving.
        BootstrapMethodsAttribute bms = (BootstrapMethodsAttribute) accessingKlass.getAttribute(BootstrapMethodsAttribute.NAME);

        assert (bms != null);
        // TODO(garcia) cache bootstrap method resolution
        // Bootstrap method resolution
        try {
            BootstrapMethodsAttribute.Entry bsEntry = bms.at(thiz.getBootstrapMethodAttrIndex());

            StaticObject bootstrapmethodMethodHandle = pool.getMethodHandle(bsEntry, accessingKlass);
            StaticObject[] args = pool.getStaticArguments(bsEntry, accessingKlass);

            StaticObject fieldName = meta.toGuestString(thiz.getName(pool));
            Klass fieldType = meta.resolveSymbolOrFail(thiz.getTypeSymbol(pool),
                            accessingKlass.getDefiningClassLoader(),
                            accessingKlass.protectionDomain());

            Object result = null;
            if (!meta.getJavaVersion().java19OrLater()) {
                result = meta.java_lang_invoke_MethodHandleNatives_linkDynamicConstant.invokeDirectStatic(
                                accessingKlass.mirror(),
                                thisIndex,
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

    public static Resolvable.ResolvedConstant resolveMethodHandleConstant(MethodHandleConstant.Index thiz, RuntimeConstantPool pool, @SuppressWarnings("unused") int thisIndex,
                    ObjectKlass accessingKlass) {
        Meta meta = pool.getContext().getMeta();
        if (meta.getLanguage().getSpecComplianceMode() == EspressoOptions.SpecComplianceMode.STRICT || meta.getJavaVersion().java9OrLater()) {
            return specCompliantResolution(thiz, pool, accessingKlass, meta);
        } else {
            return hotspotResolutionBehavior(thiz, pool, accessingKlass, meta);
        }
    }

    private static Resolvable.ResolvedConstant specCompliantResolution(MethodHandleConstant.Index thiz, RuntimeConstantPool pool, ObjectKlass accessingKlass, Meta meta) {
        StaticObject mtype;
        Klass mklass;
        Symbol<Name> refName;

        Tag refTag = pool.tagAt(thiz.getRefIndex());
        if (refTag == Tag.METHOD_REF || refTag == Tag.INTERFACE_METHOD_REF) {
            Method target = pool.resolvedMethodAt(accessingKlass, thiz.getRefIndex());
            Symbol<Type>[] parsed = target.getParsedSignature();

            mtype = signatureToMethodType(parsed, accessingKlass, false, meta);
            MethodRefConstant ref = pool.methodAt(thiz.getRefIndex());
            /*
             * we should use the klass from the method ref here rather than the declaring klass of
             * the target this is because the resolved target might come from a default method and
             * have an interface as declaring klass however if the refKind is invokeVirtual, it
             * would be illegal to use the interface type
             */
            mklass = pool.resolvedKlassAt(accessingKlass, ((MemberRefConstant.Indexes) ref).getHolderIndex());
            refName = target.getName();
        } else {
            assert refTag == Tag.FIELD_REF;
            Field field = pool.resolvedFieldAt(accessingKlass, thiz.getRefIndex());
            mtype = meta.resolveSymbolAndAccessCheck(field.getType(), accessingKlass).mirror();
            mklass = field.getDeclaringKlass();
            refName = field.getName();
        }

        return linkMethodHandleConstant(thiz, accessingKlass, meta, mtype, mklass, refName);
    }

    /**
     * Resolves a method handle without resolving the method reference, which is not the behavior
     * described in the specs {5.4.3.5. Method Type and Method Handle Resolution }
     * <p>
     * see {@code JDK-8188145}
     */
    private static Resolvable.ResolvedConstant hotspotResolutionBehavior(MethodHandleConstant.Index thiz, RuntimeConstantPool pool, ObjectKlass accessingKlass, Meta meta) {
        StaticObject mtype;
        Klass mklass;
        Symbol<Name> refName;

        Tag refTag = pool.tagAt(thiz.getRefIndex());
        if (refTag == Tag.METHOD_REF || refTag == Tag.INTERFACE_METHOD_REF) {
            MethodRefConstant.Indexes ref = pool.methodAt(thiz.getRefIndex());
            Symbol<Signature> signature = ref.getSignature(pool);
            Symbol<Type>[] parsed = meta.getSignatures().parsed(signature);

            mtype = signatureToMethodType(parsed, accessingKlass, false, meta);
            mklass = pool.resolvedKlassAt(accessingKlass, ref.getHolderIndex());
            refName = ref.getName(pool);
        } else {
            assert refTag == Tag.FIELD_REF;
            FieldRefConstant.Indexes ref = pool.fieldAt(thiz.getRefIndex());

            Symbol<Type> type = ref.getType(pool);
            mtype = meta.resolveSymbolAndAccessCheck(type, accessingKlass).mirror();
            mklass = getResolvedHolderKlass(ref, pool, accessingKlass);
            refName = ref.getName(pool);
        }

        return linkMethodHandleConstant(thiz, accessingKlass, meta, mtype, mklass, refName);
    }

    private static Resolvable.ResolvedConstant linkMethodHandleConstant(MethodHandleConstant.Index thiz, Klass accessingKlass, Meta meta, StaticObject mtype, Klass mklass, Symbol<Name> refName) {
        StaticObject mname = meta.toGuestString(refName);
        return new ResolvedMethodHandleConstant((StaticObject) meta.java_lang_invoke_MethodHandleNatives_linkMethodHandleConstant.invokeDirectStatic(
                        accessingKlass.mirror(), thiz.getRefKind(),
                        mklass.mirror(), mname, mtype));
    }
}
