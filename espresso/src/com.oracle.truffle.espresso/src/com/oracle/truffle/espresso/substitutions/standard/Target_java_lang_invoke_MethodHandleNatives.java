/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions.standard;

import static com.oracle.truffle.espresso.classfile.Constants.ACC_STATIC;
import static com.oracle.truffle.espresso.classfile.Constants.REF_LIMIT;
import static com.oracle.truffle.espresso.classfile.Constants.REF_NONE;
import static com.oracle.truffle.espresso.classfile.Constants.REF_getField;
import static com.oracle.truffle.espresso.classfile.Constants.REF_getStatic;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeInterface;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeSpecial;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeStatic;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeVirtual;
import static com.oracle.truffle.espresso.classfile.Constants.REF_newInvokeSpecial;
import static com.oracle.truffle.espresso.classfile.Constants.REF_putField;
import static com.oracle.truffle.espresso.classfile.Constants.REF_putStatic;
import static com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics.PolySigIntrinsics.InvokeGeneric;
import static com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics.PolySigIntrinsics.None;
import static com.oracle.truffle.espresso.substitutions.standard.Target_java_lang_invoke_MethodHandleNatives.Constants.ALL_KINDS;
import static com.oracle.truffle.espresso.substitutions.standard.Target_java_lang_invoke_MethodHandleNatives.Constants.CONSTANTS;
import static com.oracle.truffle.espresso.substitutions.standard.Target_java_lang_invoke_MethodHandleNatives.Constants.CONSTANTS_BEFORE_16;
import static com.oracle.truffle.espresso.substitutions.standard.Target_java_lang_invoke_MethodHandleNatives.Constants.LM_UNCONDITIONAL;
import static com.oracle.truffle.espresso.substitutions.standard.Target_java_lang_invoke_MethodHandleNatives.Constants.MN_CALLER_SENSITIVE;
import static com.oracle.truffle.espresso.substitutions.standard.Target_java_lang_invoke_MethodHandleNatives.Constants.MN_HIDDEN_MEMBER;
import static com.oracle.truffle.espresso.substitutions.standard.Target_java_lang_invoke_MethodHandleNatives.Constants.MN_IS_CONSTRUCTOR;
import static com.oracle.truffle.espresso.substitutions.standard.Target_java_lang_invoke_MethodHandleNatives.Constants.MN_IS_FIELD;
import static com.oracle.truffle.espresso.substitutions.standard.Target_java_lang_invoke_MethodHandleNatives.Constants.MN_IS_METHOD;
import static com.oracle.truffle.espresso.substitutions.standard.Target_java_lang_invoke_MethodHandleNatives.Constants.MN_REFERENCE_KIND_MASK;
import static com.oracle.truffle.espresso.substitutions.standard.Target_java_lang_invoke_MethodHandleNatives.Constants.MN_REFERENCE_KIND_SHIFT;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.Pair;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Types;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoLinkResolver;
import com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics;
import com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics.PolySigIntrinsics;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.shared.resolver.CallSiteType;
import com.oracle.truffle.espresso.shared.resolver.ResolvedCall;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionProfiler;

@EspressoSubstitutions
public final class Target_java_lang_invoke_MethodHandleNatives {
    /**
     * Plants an already resolved target into a memberName.
     *
     * @param self the memberName
     * @param ref the target. Can be either a mathod or a field.
     */
    @Substitution
    public static void init(@JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject self, @JavaType(Object.class) StaticObject ref,
                    @Inject Meta meta, @Inject EspressoLanguage language) {
        Klass targetKlass = ref.getKlass();

        if (targetKlass.getType() == Types.java_lang_reflect_Method) {
            // Actual planting
            Method target = Method.getHostReflectiveMethodRoot(ref, meta);
            plantResolvedMethod(self, target, target.getRefKind(), meta);
        } else if (targetKlass.getType() == Types.java_lang_reflect_Field) {
            // Actual planting
            Field field = Field.getReflectiveFieldRoot(ref, meta);
            plantResolvedField(self, field, getRefKind(meta.java_lang_invoke_MemberName_flags.getInt(self)), meta, language);
        } else if (targetKlass.getType() == Types.java_lang_reflect_Constructor) {
            Method target = Method.getHostReflectiveConstructorRoot(ref, meta);
            plantResolvedMethod(self, target, target.getRefKind(), meta);
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("invalid argument for MemberName.init: " + ref.getKlass());
        }
    }

    @Substitution
    public static void expand(@JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                    @Inject Meta meta, @Inject EspressoLanguage language,
                    @Inject SubstitutionProfiler profiler) {
        if (StaticObject.isNull(self)) {
            profiler.profile(0);
            throw meta.throwExceptionWithMessage(meta.java_lang_InternalError, "MemberName is null");
        }
        boolean haveClazz = !StaticObject.isNull(meta.java_lang_invoke_MemberName_clazz.getObject(self));
        boolean haveName = !StaticObject.isNull(meta.java_lang_invoke_MemberName_name.getObject(self));
        boolean haveType = !StaticObject.isNull(meta.java_lang_invoke_MemberName_type.getObject(self));
        int flags = meta.java_lang_invoke_MemberName_flags.getInt(self);

        switch (flags & ALL_KINDS) {
            case MN_IS_METHOD:
            case MN_IS_CONSTRUCTOR: {
                Method m = (Method) meta.HIDDEN_VMTARGET.getHiddenObject(self);
                if (m == null) {
                    profiler.profile(2);
                    throw meta.throwExceptionWithMessage(meta.java_lang_InternalError, "Nothing to expand");
                }
                if (!haveClazz) {
                    meta.java_lang_invoke_MemberName_clazz.setObject(self, m.getDeclaringKlass().mirror());
                }
                if (!haveName) {
                    meta.java_lang_invoke_MemberName_name.setObject(self, meta.toGuestString(m.getName()));
                }
                if (!haveType) {
                    meta.java_lang_invoke_MemberName_type.setObject(self, meta.toGuestString(m.getRawSignature()));
                }
                break;
            }
            case MN_IS_FIELD: {
                StaticObject clazz = meta.java_lang_invoke_MemberName_clazz.getObject(self);
                if (StaticObject.isNull(clazz)) {
                    profiler.profile(3);
                    throw meta.throwExceptionWithMessage(meta.java_lang_InternalError, "Nothing to expand");
                }
                Klass holder = clazz.getMirrorKlass(meta);
                int slot = Target_sun_misc_Unsafe.guestOffsetToSlot((long) meta.HIDDEN_VMINDEX.getHiddenObject(self), language);
                boolean isStatic = (flags & ACC_STATIC) != 0;
                Field f;
                try {
                    if (isStatic) {
                        f = holder.lookupStaticFieldTable(slot);
                    } else {
                        f = holder.lookupFieldTable(slot);
                    }
                } catch (IndexOutOfBoundsException e) {
                    f = null;
                }
                if (f == null) {
                    profiler.profile(4);
                    throw meta.throwExceptionWithMessage(meta.java_lang_InternalError, "Nothing to expand");
                }
                if (!haveName) {
                    meta.java_lang_invoke_MemberName_name.setObject(self, meta.toGuestString(f.getName()));
                }
                if (!haveType) {
                    if (TypeSymbols.isPrimitive(f.getType())) {
                        Klass k = meta.resolvePrimitive(f.getType());
                        meta.java_lang_invoke_MemberName_type.setObject(self, k.mirror());
                    } else {
                        meta.java_lang_invoke_MemberName_type.setObject(self, meta.toGuestString(f.getType()));
                    }
                }
                break;
            }
            default:
                profiler.profile(1);
                throw meta.throwExceptionWithMessage(meta.java_lang_InternalError, "MemberName is null");
        }
    }

    @SuppressWarnings("unused")
    @Substitution
    public static int getNamedCon(int which, @JavaType(Object[].class) StaticObject name,
                    @Inject EspressoLanguage language, @Inject Meta meta) {
        if (name.getKlass() == meta.java_lang_Object_array && name.length(language) > 0) {
            if (which < CONSTANTS.size()) {
                if (which >= CONSTANTS_BEFORE_16 && !meta.getJavaVersion().java16OrLater()) {
                    return 0;
                }
                Pair<String, Integer> pair = CONSTANTS.get(which);
                meta.getInterpreterToVM().setArrayObject(language, meta.toGuestString(pair.getLeft()), 0, name);
                return pair.getRight();
            }
        }
        return 0;
    }

    @Substitution
    public static void setCallSiteTargetNormal(@JavaType(CallSite.class) StaticObject site, @JavaType(MethodHandle.class) StaticObject target,
                    @Inject Meta meta) {
        meta.java_lang_invoke_CallSite_target.setObject(site, target);
    }

    @Substitution
    public static void setCallSiteTargetVolatile(@JavaType(CallSite.class) StaticObject site, @JavaType(MethodHandle.class) StaticObject target,
                    @Inject Meta meta) {
        meta.java_lang_invoke_CallSite_target.setObject(site, target, true);
    }

    // TODO(garcia) verifyConstants

    @Substitution
    public static int getMembers(
                    @JavaType(Class.class) StaticObject defc,
                    @JavaType(String.class) StaticObject matchName,
                    @JavaType(String.class) StaticObject matchSig,
                    int matchFlags,
                    @JavaType(Class.class) StaticObject originalCaller,
                    int skip,
                    @JavaType(internalName = "[Ljava/lang/invoke/MemberName;") StaticObject resultsArr,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta) {
        if (StaticObject.isNull(defc) || StaticObject.isNull(resultsArr)) {
            return -1;
        }
        EspressoContext context = meta.getContext();
        StaticObject[] results = resultsArr.unwrap(language);
        Symbol<Name> name = null;
        if (!StaticObject.isNull(matchName)) {
            name = context.getNames().lookup(meta.toHostString(matchName));
            if (name == null) {
                return 0;
            }
        }
        String sig = meta.toHostString(matchSig);
        if (sig == null) {
            return 0;
        }

        Klass caller = null;
        if (!StaticObject.isNull(originalCaller)) {
            caller = originalCaller.getMirrorKlass(meta);
            if (caller == null) {
                return -1;
            }
        }

        return findMemberNames(defc.getMirrorKlass(meta), name, sig, matchFlags, caller, skip, results);
    }

    @SuppressWarnings("unused")
    private static int findMemberNames(Klass klass, Symbol<Name> name, String sig, int matchFlags, Klass caller, int skip, StaticObject[] results) {
        // TODO(garcia) this.
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.unimplemented();
    }

    @Substitution
    public static void registerNatives() {
        /* nop */
    }

    @Substitution
    public static int getConstant(int which) {
        switch (which) {
            case 4:
                return 1;
            default:
                return 0;
        }
    }

    @Substitution
    public static long objectFieldOffset(@JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                    @Inject Meta meta) {
        return (long) meta.HIDDEN_VMINDEX.getHiddenObject(self);
    }

    @Substitution
    public static long staticFieldOffset(@JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                    @Inject Meta meta) {
        return (long) meta.HIDDEN_VMINDEX.getHiddenObject(self);
    }

    @Substitution
    public static @JavaType(Object.class) StaticObject staticFieldBase(@JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                    @Inject Meta meta) {
        return meta.java_lang_invoke_MemberName_clazz.getObject(self).getMirrorKlass(meta).getStatics();
    }

    @Substitution
    public static @JavaType(Object.class) StaticObject getMemberVMInfo(@JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                    @Inject Meta meta) {
        Object vmtarget = meta.HIDDEN_VMTARGET.getHiddenObject(self);
        Object vmindex = meta.HIDDEN_VMINDEX.getHiddenObject(self);
        StaticObject[] result = new StaticObject[2];
        if (vmindex == null) {
            // vmindex is not used in espresso. Spoof it so java is still happy.
            result[0] = meta.boxLong(-2_000_000);
        } else {
            result[0] = meta.boxLong((long) vmindex);
        }

        if (vmtarget == null) {
            result[1] = StaticObject.NULL;
        } else if (vmtarget instanceof Klass) {
            result[1] = ((Klass) vmtarget).mirror();
        } else {
            result[1] = self;
        }

        return StaticObject.createArray(meta.java_lang_Object_array, result, meta.getContext());
    }

    @Substitution
    public static @JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject resolve(@JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                    @JavaType(Class.class) StaticObject caller,
                    @Inject Meta meta) {
        return resolve(self, caller, false, meta);
    }

    @Substitution
    public static @JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject resolve(@JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                    @JavaType(Class.class) StaticObject caller, boolean speculativeResolve,
                    @Inject Meta meta) {
        try {
            return resolve(self, caller, LM_UNCONDITIONAL, meta);
        } catch (EspressoException e) {
            if (speculativeResolve) {
                return StaticObject.NULL;
            }
            throw e;
        }
    }

    @Substitution(methodName = "resolve")
    public static @JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject resolve(@JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                    @JavaType(Class.class) StaticObject caller, int lookupMode, boolean speculativeResolve, @Inject Meta meta) {
        EspressoException error;
        try {
            return resolve(self, caller, lookupMode, meta);
        } catch (EspressoException e) {
            error = e;
        }
        int refKind = getRefKind(meta.java_lang_invoke_MemberName_flags.getInt(self));
        if (!isValidRefKind(refKind)) {
            throw meta.throwExceptionWithMessage(meta.java_lang_InternalError, "obsolete MemberName format");
        }
        if (!speculativeResolve) {
            throw error;
        }
        return StaticObject.NULL;
    }

    @TruffleBoundary
    private static StaticObject resolve(StaticObject memberName, @JavaType(Class.class) StaticObject guestCaller, int lookupMode, Meta meta) {
        if (StaticObject.isNull(memberName)) {
            throw meta.throwExceptionWithMessage(meta.java_lang_InternalError, "Member Name is null.");
        }
        // JDK code should have already checked that 'caller' has access to 'memberName.clazz'.

        if (meta.HIDDEN_VMTARGET.getHiddenObject(memberName) != null) {
            return memberName; // Already planted
        }
        StaticObject clazz = meta.java_lang_invoke_MemberName_clazz.getObject(memberName);
        StaticObject type = meta.java_lang_invoke_MemberName_type.getObject(memberName);
        StaticObject guestName = meta.java_lang_invoke_MemberName_name.getObject(memberName);

        if (StaticObject.isNull(guestName) || StaticObject.isNull(type) || StaticObject.isNull(clazz)) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Nothing to resolve.");
        }
        // Extract resolution information from member name.
        final int flags = meta.java_lang_invoke_MemberName_flags.getInt(memberName);
        if (Integer.bitCount(flags & ALL_KINDS) != 1) {
            // Ensure the flags field is not ill-formed.
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Invalid MemberName flag format.");
        }

        // Determine the holder klass
        Klass resolutionKlass = clazz.getMirrorKlass(meta);
        if (!(resolutionKlass instanceof ObjectKlass)) {
            // Non-standard behavior: behave as HotSpot.
            if (resolutionKlass.isArray()) {
                resolutionKlass = meta.java_lang_Object;
            } else if (resolutionKlass.isPrimitive()) {
                throw defaultResolutionFailure(meta, flags);
            }
        }

        // Determine caller klass
        Klass callerKlass = StaticObject.isNull(guestCaller) ? null : guestCaller.getMirrorKlass(meta);
        if (callerKlass != null && callerKlass.isPrimitive()) {
            // HotSpot behavior: primitive caller klass skip checks.
            callerKlass = null;
        }

        EspressoContext ctx = meta.getContext();
        ByteSequence desc = asSignature(type, meta);
        Symbol<Name> name = lookupName(meta, meta.toHostString(guestName), (Constants.flagHas(flags, MN_IS_FIELD)) ? meta.java_lang_NoSuchFieldException : meta.java_lang_NoSuchMethodException);

        boolean doAccessChecks = callerKlass != null;
        boolean doConstraintsChecks = (callerKlass != null && ((lookupMode & LM_UNCONDITIONAL) == 0));
        int refKind = getRefKind(flags);

        if (Constants.flagHas(flags, MN_IS_FIELD)) {
            Symbol<Type> t = lookupType(meta, desc);
            // Field member name resolution skips several checks:
            // - Access checks
            // - Static fields are accessed statically
            // - Final fields and ref_put*
            // These are done when needed by JDK code.
            Field f = EspressoLinkResolver.resolveFieldSymbolOrThrow(ctx, callerKlass, name, t, resolutionKlass, false, doConstraintsChecks);
            plantResolvedField(memberName, f, refKind, meta, meta.getLanguage());
            return memberName;
        }

        if (Constants.flagHas(flags, MN_IS_CONSTRUCTOR)) {
            if (name != Names._init_) {
                throw meta.throwException(meta.java_lang_LinkageError);
            }
            // Ignores refKind
            refKind = REF_invokeSpecial;
        } else if (!Constants.flagHas(flags, MN_IS_METHOD)) {
            throw meta.throwExceptionWithMessage(meta.java_lang_InternalError, "Unrecognized MemberName format");
        }

        // Check if we got a polymorphic signature method, in which case we may need to force
        // the creation of a new signature symbol.
        PolySigIntrinsics mhMethodId = getPolysignatureIntrinsicID(flags, resolutionKlass, refKind, name);

        if (mhMethodId == InvokeGeneric) {
            // Can not resolve InvokeGeneric, as we would miss the invoker and appendix.
            throw meta.throwException(meta.java_lang_InternalError);
        }

        Symbol<Signature> sig = lookupSignature(meta, desc, mhMethodId);
        Method m = EspressoLinkResolver.resolveMethodSymbol(ctx, callerKlass, name, sig, resolutionKlass, resolutionKlass.isInterface(), doAccessChecks, doConstraintsChecks);
        ResolvedCall<Klass, Method, Field> resolvedCall = EspressoLinkResolver.resolveCallSiteOrThrow(ctx, callerKlass, m, SiteTypes.callSiteFromRefKind(refKind), resolutionKlass);

        plantResolvedMethod(memberName, resolvedCall, meta);

        return memberName;
    }

    private static RuntimeException defaultResolutionFailure(Meta meta, int flags) {
        if (Constants.flagHas(flags, MN_IS_FIELD)) {
            throw meta.throwExceptionWithMessage(meta.java_lang_NoSuchFieldError, "Field resolution failed");
        } else if (Constants.flagHas(flags, MN_IS_METHOD) || Constants.flagHas(flags, MN_IS_CONSTRUCTOR)) {
            throw meta.throwExceptionWithMessage(meta.java_lang_NoSuchMethodError, "Method resolution failed");
        } else {
            throw meta.throwExceptionWithMessage(meta.java_lang_LinkageError, "resolution failed");
        }
    }

    private static PolySigIntrinsics getPolysignatureIntrinsicID(int flags, Klass resolutionKlass, int refKind, Symbol<Name> name) {
        PolySigIntrinsics mhMethodId = None;
        if (Constants.flagHas(flags, MN_IS_METHOD) &&
                        Meta.isSignaturePolymorphicHolderType(resolutionKlass.getType())) {
            if (refKind == REF_invokeVirtual ||
                            refKind == REF_invokeSpecial ||
                            refKind == REF_invokeStatic) {
                PolySigIntrinsics iid = MethodHandleIntrinsics.getId(name, resolutionKlass);
                if (iid != None &&
                                ((refKind == REF_invokeStatic) == (iid.isStaticPolymorphicSignature()))) {
                    mhMethodId = iid;
                }
            }
        }
        return mhMethodId;
    }

    @TruffleBoundary
    private static Symbol<Name> lookupName(Meta meta, String name, ObjectKlass exceptionKlass) {
        Symbol<Name> methodName;
        try {
            methodName = meta.getNames().lookup(name);
        } catch (EspressoError e) {
            methodName = null;
        }
        if (methodName == null) {
            throw meta.throwExceptionWithMessage(exceptionKlass, name);
        }
        return methodName;
    }

    @TruffleBoundary
    private static Symbol<Type> lookupType(Meta meta, ByteSequence desc) {
        Symbol<Type> t = meta.getLanguage().getTypes().lookupValidType(desc);
        if (t == null) {
            throw meta.throwException(meta.java_lang_NoSuchFieldException);
        }
        return t;
    }

    @TruffleBoundary
    private static Symbol<Signature> lookupSignature(Meta meta, ByteSequence desc, PolySigIntrinsics iid) {
        Symbol<Signature> signature;
        if (iid != None) {
            signature = meta.getSignatures().getOrCreateValidSignature(desc);
        } else {
            signature = meta.getSignatures().lookupValidSignature(desc);
        }
        if (signature == null) {
            throw meta.throwException(meta.java_lang_NoSuchMethodException);
        }
        return signature;
    }

    private static ByteSequence asSignature(StaticObject typeObject, Meta meta) {
        Klass typeKlass = typeObject.getKlass();
        if (meta.java_lang_invoke_MethodType.isAssignableFrom(typeKlass)) {
            return methodTypeAsSignature(typeObject, meta);
        } else if (meta.java_lang_Class.isAssignableFrom(typeKlass)) {
            return typeObject.getMirrorKlass(meta).getType();
        } else if (meta.java_lang_String.isAssignableFrom(typeKlass)) {
            return ByteSequence.create(meta.toHostString(typeObject));
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere();
        }
    }

    private static ByteSequence methodTypeAsSignature(StaticObject methodType, Meta meta) {
        StaticObject ptypes = meta.java_lang_invoke_MethodType_ptypes.getObject(methodType);
        StaticObject rtype = meta.java_lang_invoke_MethodType_rtype.getObject(methodType);
        return Method.getSignatureFromGuestDescription(ptypes, rtype, meta);
    }

    private static long refKindToVMIndex(int refKind) {
        switch (refKind) {
            case REF_invokeStatic:
                return Constants.STATIC_INDEX;
            case REF_invokeVirtual:
                return Constants.VIRTUAL_INDEX;
            case REF_invokeInterface:
                return Constants.INTERFACE_INDEX;
            case REF_invokeSpecial: // fallthrough
            case REF_newInvokeSpecial:
                return Constants.SPECIAL_INDEX;
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere();
    }

    // region MemberName planting

    // Exposed to StackWalk
    public static void plantResolvedMethod(StaticObject memberName, Method target, int refKind, Meta meta) {
        int methodFlags = getMethodFlags(target, refKind);
        plant(memberName, target, meta, methodFlags);
    }

    public static void plantResolvedMethod(StaticObject memberName, ResolvedCall<Klass, Method, Field> resolvedCall, Meta meta) {
        int methodFlags = getMethodFlags(resolvedCall);
        plant(memberName, resolvedCall.getResolvedMethod(), meta, methodFlags);
    }

    private static void plant(StaticObject memberName, Method target, Meta meta, int methodFlags) {
        meta.HIDDEN_VMTARGET.setHiddenObject(memberName, target);
        meta.HIDDEN_VMINDEX.setHiddenObject(memberName, refKindToVMIndex(getRefKind(methodFlags)));
        meta.java_lang_invoke_MemberName_flags.setInt(memberName, methodFlags);
        meta.java_lang_invoke_MemberName_clazz.setObject(memberName, target.getDeclaringKlass().mirror());
    }

    private static void plantResolvedField(StaticObject memberName, Field field, int refKind, Meta meta, EspressoLanguage language) {
        meta.HIDDEN_VMTARGET.setHiddenObject(memberName, field.getDeclaringKlass());
        meta.HIDDEN_VMINDEX.setHiddenObject(memberName, Target_sun_misc_Unsafe.slotToGuestOffset(field.getSlot(), field.isStatic(), language));
        meta.java_lang_invoke_MemberName_flags.setInt(memberName, getFieldFlags(refKind, field));
        meta.java_lang_invoke_MemberName_clazz.setObject(memberName, field.getDeclaringKlass().mirror());
    }

    private static int getMethodFlags(ResolvedCall<Klass, Method, Field> call) {
        int flags = call.getResolvedMethod().getMethodModifiers();
        if (call.getResolvedMethod().isCallerSensitive()) {
            flags |= MN_CALLER_SENSITIVE;
        }
        if (call.getResolvedMethod().isConstructor() || call.getResolvedMethod().isClassInitializer()) {
            flags |= MN_IS_CONSTRUCTOR;
            flags |= (REF_newInvokeSpecial << MN_REFERENCE_KIND_SHIFT);
            return flags;
        }
        flags |= MN_IS_METHOD;
        switch (call.getCallKind()) {
            case STATIC:
                flags |= (REF_invokeStatic << MN_REFERENCE_KIND_SHIFT);
                break;
            case DIRECT:
                flags |= (REF_invokeSpecial << MN_REFERENCE_KIND_SHIFT);
                break;
            case VTABLE_LOOKUP:
                flags |= (REF_invokeVirtual << MN_REFERENCE_KIND_SHIFT);
                break;
            case ITABLE_LOOKUP:
                flags |= (REF_invokeInterface << MN_REFERENCE_KIND_SHIFT);
                break;
        }
        return flags;
    }

    private static int getMethodFlags(Method target, int refKind) {
        int res = target.getMethodModifiers();
        if (refKind == REF_invokeInterface) {
            if (target.isPrivate() || target.isFinalFlagSet() || target.getDeclaringKlass().isFinalFlagSet()) {
                res |= MN_IS_METHOD | (REF_invokeSpecial << MN_REFERENCE_KIND_SHIFT);
            } else if (target.getDeclaringKlass().isJavaLangObject()) {
                assert target.getVTableIndex() >= 0;
                res |= MN_IS_METHOD | (REF_invokeVirtual << MN_REFERENCE_KIND_SHIFT);
            } else {
                assert target.getITableIndex() >= 0;
                res |= MN_IS_METHOD | (REF_invokeInterface << MN_REFERENCE_KIND_SHIFT);
            }
        } else if (refKind == REF_invokeVirtual) {
            if (target.isPrivate() || target.isFinalFlagSet() || target.getDeclaringKlass().isFinalFlagSet()) {
                res |= MN_IS_METHOD | (REF_invokeSpecial << MN_REFERENCE_KIND_SHIFT);
            } else {
                assert target.getVTableIndex() >= 0;
                res |= MN_IS_METHOD | (REF_invokeVirtual << MN_REFERENCE_KIND_SHIFT);
            }
        } else {
            if (target.isStatic()) {
                res |= MN_IS_METHOD | (REF_invokeStatic << MN_REFERENCE_KIND_SHIFT);
            } else if (target.isConstructor() || target.isClassInitializer()) {
                res |= MN_IS_CONSTRUCTOR | (REF_invokeSpecial << MN_REFERENCE_KIND_SHIFT);
            } else {
                res |= MN_IS_METHOD | (REF_invokeSpecial << MN_REFERENCE_KIND_SHIFT);
            }
        }
        if (target.isCallerSensitive()) {
            res |= MN_CALLER_SENSITIVE;
        }
        if (target.isHidden()) {
            res |= MN_HIDDEN_MEMBER;
        }
        return res;
    }

    private static int getFieldFlags(int refKind, Field fd) {
        int res = fd.getModifiers();
        boolean isSetter = (refKind <= REF_putStatic) && !(refKind <= REF_getStatic);
        res |= MN_IS_FIELD | ((fd.isStatic() ? REF_getStatic : REF_getField) << MN_REFERENCE_KIND_SHIFT);
        if (isSetter) {
            res += ((REF_putField - REF_getField) << MN_REFERENCE_KIND_SHIFT);
        }
        return res;
    }

    // endregion MemberName planting

    // region Helper methods

    public static int getRefKind(int flags) {
        return (flags >> MN_REFERENCE_KIND_SHIFT) & MN_REFERENCE_KIND_MASK;
    }

    public static boolean isValidRefKind(int flags) {
        return flags > REF_NONE && flags < REF_LIMIT;
    }

    // endregion Helper methods

    public static final class SiteTypes {
        public static CallSiteType callSiteFromOpCode(int opcode) {
            return CallSiteType.fromOpCode(opcode);
        }

        public static CallSiteType callSiteFromRefKind(int refKind) {
            switch (refKind) {
                case REF_invokeVirtual:
                    return CallSiteType.Virtual;
                case REF_invokeStatic:
                    return CallSiteType.Static;
                case REF_invokeSpecial: // fallthrough
                case REF_newInvokeSpecial:
                    return CallSiteType.Special;
                case REF_invokeInterface:
                    return CallSiteType.Interface;
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere("refKind: " + refKind);
            }
        }

    }

    /**
     * Compile-time constants go here. This collection exists not only for reference from clients,
     * but also for ensuring the VM and JDK agree on the values of these constants. JDK verifies
     * that through {@code java.lang.invoke.MethodHandleNatives#verifyConstants()}
     */
    public static final class Constants {
        private Constants() {
        } // static only

        // VM_Index spoofs
        static final long NONE_INDEX = -3_000_000L;
        static final long VIRTUAL_INDEX = 1_000_000L;
        static final long INTERFACE_INDEX = 2_000_000L;
        static final long STATIC_INDEX = -1_000_000L;
        static final long SPECIAL_INDEX = -2_000_000L;

        public static final int MN_IS_METHOD = 0x00010000; // method (not constructor)
        public static final int MN_IS_CONSTRUCTOR = 0x00020000; // constructor
        public static final int MN_IS_FIELD = 0x00040000; // field
        public static final int MN_IS_TYPE = 0x00080000; // nested type
        // @CallerSensitive annotation detected
        public static final int MN_CALLER_SENSITIVE = 0x00100000;
        public static final int MN_TRUSTED_FINAL = 0x00200000; // trusted final field
        public static final int MN_HIDDEN_MEMBER = 0x00400000; /*- members defined in a hidden class or with @Hidden */
        public static final int MN_REFERENCE_KIND_SHIFT = 24; // refKind
        public static final int MN_REFERENCE_KIND_MASK = 0x0F000000 >> MN_REFERENCE_KIND_SHIFT;
        // The SEARCH_* bits are not for MN.flags but for the matchFlags argument of MHN.getMembers:
        public static final int MN_SEARCH_SUPERCLASSES = 0x00100000;
        public static final int MN_SEARCH_INTERFACES = 0x00200000;

        /**
         * Flags for Lookup.ClassOptions.
         */
        public static final int NESTMATE_CLASS = 0x00000001;
        public static final int HIDDEN_CLASS = 0x00000002;
        public static final int STRONG_LOADER_LINK = 0x00000004;
        public static final int ACCESS_VM_ANNOTATIONS = 0x00000008;

        /**
         * Lookup modes.
         */
        public static final int LM_MODULE = 0x00000008 << 1;
        public static final int LM_UNCONDITIONAL = 0x00000008 << 2;
        public static final int LM_TRUSTED = -1;

        /**
         * Additional Constants.
         */
        public static final int ALL_KINDS = MN_IS_CONSTRUCTOR | MN_IS_FIELD | MN_IS_METHOD | MN_IS_TYPE;

        static final List<Pair<String, Integer>> CONSTANTS;
        static final int CONSTANTS_BEFORE_16;

        public static boolean flagHas(int flags, int status) {
            return (flags & status) != 0;
        }

        static {
            CONSTANTS = new ArrayList<>();
            CONSTANTS.add(Pair.create("MN_IS_METHOD", MN_IS_METHOD));
            CONSTANTS.add(Pair.create("MN_IS_CONSTRUCTOR", MN_IS_CONSTRUCTOR));
            CONSTANTS.add(Pair.create("MN_IS_FIELD", MN_IS_FIELD));
            CONSTANTS.add(Pair.create("MN_IS_TYPE", MN_IS_TYPE));
            CONSTANTS.add(Pair.create("MN_CALLER_SENSITIVE", MN_CALLER_SENSITIVE));
            CONSTANTS.add(Pair.create("MN_TRUSTED_FINAL", MN_TRUSTED_FINAL));
            CONSTANTS.add(Pair.create("MN_SEARCH_SUPERCLASSES", MN_SEARCH_SUPERCLASSES));
            CONSTANTS.add(Pair.create("MN_SEARCH_INTERFACES", MN_SEARCH_INTERFACES));
            CONSTANTS.add(Pair.create("MN_REFERENCE_KIND_SHIFT", MN_REFERENCE_KIND_SHIFT));
            CONSTANTS.add(Pair.create("MN_REFERENCE_KIND_MASK", MN_REFERENCE_KIND_MASK));

            CONSTANTS_BEFORE_16 = CONSTANTS.size();

            CONSTANTS.add(Pair.create("NESTMATE_CLASS", NESTMATE_CLASS));
            CONSTANTS.add(Pair.create("HIDDEN_CLASS", HIDDEN_CLASS));
            CONSTANTS.add(Pair.create("STRONG_LOADER_LINK", STRONG_LOADER_LINK));
            CONSTANTS.add(Pair.create("ACCESS_VM_ANNOTATIONS", ACCESS_VM_ANNOTATIONS));
            CONSTANTS.add(Pair.create("LM_MODULE", LM_MODULE));
            CONSTANTS.add(Pair.create("LM_UNCONDITIONAL", LM_UNCONDITIONAL));
            CONSTANTS.add(Pair.create("LM_TRUSTED", LM_TRUSTED));
        }
    }

    @Substitution
    @SuppressWarnings("unused")
    public static void clearCallSiteContext(@JavaType(internalName = "Ljava/lang/invoke/MethodHandleNatives$CallSiteContext;") StaticObject context) {
        /* nop */
    }

}
