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
package com.oracle.truffle.espresso.substitutions;

import static com.oracle.truffle.espresso.classfile.Constants.ACC_STATIC;
import static com.oracle.truffle.espresso.classfile.Constants.REF_LIMIT;
import static com.oracle.truffle.espresso.classfile.Constants.REF_NONE;
import static com.oracle.truffle.espresso.classfile.Constants.REF_getField;
import static com.oracle.truffle.espresso.classfile.Constants.REF_getStatic;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeInterface;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeSpecial;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeStatic;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeVirtual;
import static com.oracle.truffle.espresso.classfile.Constants.REF_putField;
import static com.oracle.truffle.espresso.classfile.Constants.REF_putStatic;
import static com.oracle.truffle.espresso.meta.EspressoError.cat;
import static com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics.PolySigIntrinsics.None;
import static com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives.Constants.ALL_KINDS;
import static com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives.Constants.CONSTANTS;
import static com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives.Constants.CONSTANTS_BEFORE_16;
import static com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives.Constants.LM_UNCONDITIONAL;
import static com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives.Constants.MN_IS_CONSTRUCTOR;
import static com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives.Constants.MN_IS_FIELD;
import static com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives.Constants.MN_IS_METHOD;
import static com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives.Constants.MN_REFERENCE_KIND_MASK;
import static com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives.Constants.MN_REFERENCE_KIND_SHIFT;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.Pair;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.constantpool.MemberRefConstant;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics;
import com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics.PolySigIntrinsics;
import com.oracle.truffle.espresso.runtime.StaticObject;

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
                    @Inject Meta meta) {
        Klass targetKlass = ref.getKlass();

        if (targetKlass.getType() == Type.java_lang_reflect_Method) {
            // Actual planting
            Method target = Method.getHostReflectiveMethodRoot(ref, meta);
            plantResolvedMethod(self, target, target.getRefKind(), meta);
            // Finish the job
            meta.java_lang_invoke_MemberName_clazz.setObject(self, target.getDeclaringKlass().mirror());
        } else if (targetKlass.getType() == Type.java_lang_reflect_Field) {
            // Actual planting
            Field field = Field.getReflectiveFieldRoot(ref, meta);
            plantResolvedField(self, field, getRefKind(meta.java_lang_invoke_MemberName_flags.getInt(self)), meta);
            // Finish the job
            Klass fieldKlass = meta.java_lang_reflect_Field_class.getObject(ref).getMirrorKlass(meta);
            meta.java_lang_invoke_MemberName_clazz.setObject(self, fieldKlass.mirror());
        } else if (targetKlass.getType() == Type.java_lang_reflect_Constructor) {
            Method target = Method.getHostReflectiveConstructorRoot(ref, meta);
            plantResolvedMethod(self, target, target.getRefKind(), meta);
            meta.java_lang_invoke_MemberName_clazz.setObject(self, target.getDeclaringKlass().mirror());
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("invalid argument for MemberName.init: " + ref.getKlass());
        }
    }

    @Substitution
    public static void expand(@JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                    @Inject Meta meta,
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
                int slot = Target_sun_misc_Unsafe.safetyOffsetToSlot((long) meta.HIDDEN_VMINDEX.getHiddenObject(self));
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
                    if (Types.isPrimitive(f.getType())) {
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

    @Substitution(methodName = "resolve")
    abstract static class ResolveOverload8 extends EspressoNode {

        abstract @JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject execute(
                        @JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                        @JavaType(value = Class.class) StaticObject caller);

        @Specialization
        @JavaType(internalName = "Ljava/lang/invoke/MemberName;")
        StaticObject doCached(
                        @JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                        @JavaType(value = Class.class) StaticObject caller,
                        @Cached ResolveOverload11 resolve) {
            return resolve.execute(self, caller, false);
        }
    }

    @Substitution(methodName = "resolve")
    abstract static class ResolveOverload11 extends EspressoNode {

        abstract @JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject execute(
                        @JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                        @JavaType(value = Class.class) StaticObject caller,
                        boolean speculativeResolve);

        @Specialization
        @JavaType(internalName = "Ljava/lang/invoke/MemberName;")
        StaticObject doCached(
                        @JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                        @JavaType(value = Class.class) StaticObject caller,
                        boolean speculativeResolve,
                        @Cached ResolveNode resolve) {
            try {
                return resolve.execute(self, caller, LM_UNCONDITIONAL);
            } catch (EspressoException e) {
                if (speculativeResolve) {
                    return StaticObject.NULL;
                }
                throw e;
            }
        }
    }

    @Substitution(methodName = "resolve")
    abstract static class ResolveOverload17 extends SubstitutionNode {

        abstract @JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject execute(
                        @JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                        @JavaType(value = Class.class) StaticObject caller,
                        int lookupMode,
                        boolean speculativeResolve);

        @Specialization
        @JavaType(internalName = "Ljava/lang/invoke/MemberName;")
        StaticObject doCached(
                        @JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                        @JavaType(value = Class.class) StaticObject caller,
                        int lookupMode,
                        boolean speculativeResolve,
                        @Cached ResolveNode resolve) {
            StaticObject result = StaticObject.NULL;
            EspressoException error = null;
            try {
                return resolve.execute(self, caller, lookupMode);
            } catch (EspressoException e) {
                error = e;
            }
            Meta meta = getMeta();
            if (StaticObject.isNull(result)) {
                int refKind = getRefKind(meta.java_lang_invoke_MemberName_flags.getInt(self));
                if (!isValidRefKind(refKind)) {
                    throw meta.throwExceptionWithMessage(meta.java_lang_InternalError, "obsolete MemberName format");
                }
                if (!speculativeResolve && error != null) {
                    throw error;
                }
            }
            return result;
        }
    }

    abstract static class ResolveNode extends SubstitutionNode {

        /**
         * Complete resolution of a memberName, full with method lookup, flags overwriting and
         * planting target.
         *
         * @param memberName The memberName to resolve
         * @param caller the class that commands the resolution
         * @return The resolved memberName. Note that it should be the same reference as self
         */
        abstract @JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject execute(
                        @JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject memberName,
                        @JavaType(value = Class.class) StaticObject caller,
                        int lookupMode);

        @Specialization
        @JavaType(internalName = "Ljava/lang/invoke/MemberName;")
        StaticObject doCached(
                        @JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject memberName,
                        @JavaType(value = Class.class) StaticObject caller,
                        @SuppressWarnings("unused") int lookupMode,
                        @Bind("getMeta()") Meta meta,
                        @Cached("create(meta.java_lang_invoke_MemberName_getSignature.getCallTarget())") DirectCallNode getSignature,
                        @Cached BranchProfile isMethodProfile,
                        @Cached BranchProfile isFieldProfile,
                        @Cached BranchProfile isConstructorProfile,
                        @Cached BranchProfile noMethodNameProfile,
                        @Cached BranchProfile isSignaturePolymorphicIntrinsicProfile,
                        @Cached BranchProfile isInvokeStaticOrInterfaceProfile,
                        @Cached BranchProfile isInvokeVirtualOrSpecialProfile,
                        @Cached BranchProfile isHandleMethodProfile) {
            if (meta.HIDDEN_VMTARGET.getHiddenObject(memberName) != null) {
                return memberName; // Already planted
            }
            StaticObject clazz = meta.java_lang_invoke_MemberName_clazz.getObject(memberName);
            if (StaticObject.isNull(clazz)) {
                return StaticObject.NULL;
            }
            Klass resolutionKlass = clazz.getMirrorKlass(meta);

            Field flagField = meta.java_lang_invoke_MemberName_flags;
            int flags = flagField.getInt(memberName);
            int refKind = getRefKind(flags);

            StaticObject name = meta.java_lang_invoke_MemberName_name.getObject(memberName);
            if (StaticObject.isNull(name)) {
                return StaticObject.NULL;
            }
            Symbol<Name> methodName;
            try {
                methodName = meta.getNames().lookup(meta.toHostString(name));
            } catch (EspressoError e) {
                methodName = null;
            }
            if (methodName == null) {
                noMethodNameProfile.enter();
                if ((flags & ALL_KINDS) == MN_IS_FIELD) {
                    throw meta.throwException(meta.java_lang_NoSuchFieldException);
                } else {
                    throw meta.throwException(meta.java_lang_NoSuchMethodException);
                }
            }

            PolySigIntrinsics mhMethodId = None;
            if (((flags & ALL_KINDS) == MN_IS_METHOD) &&
                            (resolutionKlass.getType() == Type.java_lang_invoke_MethodHandle || resolutionKlass.getType() == Type.java_lang_invoke_VarHandle)) {
                isHandleMethodProfile.enter();
                if (refKind == REF_invokeVirtual ||
                                refKind == REF_invokeSpecial ||
                                refKind == REF_invokeStatic) {
                    PolySigIntrinsics iid = MethodHandleIntrinsics.getId(methodName, resolutionKlass);
                    if (iid != None &&
                                    ((refKind == REF_invokeStatic) == (iid.isStaticPolymorphicSignature()))) {
                        mhMethodId = iid;
                    }
                }
            }

            Klass callerKlass = StaticObject.isNull(caller) ? null : caller.getMirrorKlass(meta);

            boolean doAccessChecks = callerKlass != null;
            boolean doConstraintsChecks = (callerKlass != null && ((lookupMode & LM_UNCONDITIONAL) == 0));

            StaticObject type = (StaticObject) getSignature.call(memberName);
            if (StaticObject.isNull(type)) {
                return StaticObject.NULL;
            }
            String desc = meta.toHostString(type);
            switch (flags & ALL_KINDS) {
                case MN_IS_CONSTRUCTOR:
                    isConstructorProfile.enter();
                    Symbol<Signature> constructorSignature = meta.getLanguage().getSignatures().lookupValidSignature(desc);
                    plantMethodMemberName(memberName, resolutionKlass, methodName, constructorSignature, refKind, callerKlass, doAccessChecks, doConstraintsChecks, meta);
                    meta.HIDDEN_VMINDEX.setHiddenObject(memberName, -3_000_000L);
                    break;
                case MN_IS_METHOD:
                    isMethodProfile.enter();
                    Symbol<Signature> sig = meta.getSignatures().lookupValidSignature(desc);
                    if (refKind == REF_invokeStatic || refKind == REF_invokeInterface) {
                        // This branch will also handle MH.linkTo* methods.
                        isInvokeStaticOrInterfaceProfile.enter();
                        plantMethodMemberName(memberName, resolutionKlass, methodName, sig, refKind, callerKlass, doAccessChecks, doConstraintsChecks, meta);
                    } else if (mhMethodId != None) {
                        isSignaturePolymorphicIntrinsicProfile.enter();
                        assert (!mhMethodId.isStaticPolymorphicSignature()) : "Should have been handled by refKind == REF_invokeStatic";
                        EspressoError.guarantee(mhMethodId.isSignaturePolymorphicIntrinsic(), "Should never need to resolve invokeGeneric MemberName");
                        plantInvokeBasic(memberName, resolutionKlass, methodName, sig, callerKlass, refKind, meta);
                    } else if (refKind == REF_invokeVirtual || refKind == REF_invokeSpecial) {
                        isInvokeVirtualOrSpecialProfile.enter();
                        plantMethodMemberName(memberName, resolutionKlass, methodName, sig, refKind, callerKlass, doAccessChecks, doConstraintsChecks, meta);
                    }
                    flags = flagField.getInt(memberName);
                    refKind = getRefKind(flags);
                    meta.HIDDEN_VMINDEX.setHiddenObject(memberName, (refKind == REF_invokeInterface || refKind == REF_invokeVirtual) ? 1_000_000L : -1_000_000L);
                    break;
                case MN_IS_FIELD:
                    isFieldProfile.enter();
                    Symbol<Type> t = meta.getLanguage().getTypes().lookup(desc);
                    plantFieldMemberName(memberName, resolutionKlass, methodName, t, refKind, callerKlass, doConstraintsChecks, meta);
                    break;
                default:
                    throw meta.throwExceptionWithMessage(meta.java_lang_LinkageError, "Member name resolution failed");
            }

            return memberName;
        }
    }

    // region MemberName planting

    private static void plantInvokeBasic(StaticObject memberName, Klass resolutionKlass, Symbol<Name> name, Symbol<Signature> sig, Klass callerKlass, int refKind, Meta meta) {
        assert (name == Name.invokeBasic);
        Method target = resolutionKlass.lookupMethod(name, sig, callerKlass);
        meta.HIDDEN_VMTARGET.setHiddenObject(memberName, target);
        meta.java_lang_invoke_MemberName_flags.setInt(memberName, getMethodFlags(target, refKind));
    }

    private static void plantMethodMemberName(StaticObject memberName,
                    Klass resolutionKlass, Symbol<Name> name, Symbol<Signature> sig,
                    int refKind,
                    Klass callerKlass,
                    boolean accessCheck, boolean constraintsChecks,
                    Meta meta) {
        Method target = doMethodLookup(resolutionKlass, name, sig, callerKlass);
        if (target == null) {
            throw meta.throwExceptionWithMessage(meta.java_lang_NoSuchMethodError, cat("Failed lookup for method ", resolutionKlass.getName(), "#", name, ":", sig));
        }
        if (target.isStatic() != (refKind == REF_invokeStatic)) {
            String expected = (refKind == REF_invokeStatic) ? "Static" : "Instance";
            String actual = (refKind == REF_invokeStatic) ? "Instance" : "Static";
            throw meta.throwExceptionWithMessage(meta.java_lang_IncompatibleClassChangeError,
                            cat(expected, " method lookup resulted in ", actual, " resolution for method ", resolutionKlass.getName(), "#", name, ":", sig));
        }
        if (accessCheck) {
            MemberRefConstant.doAccessCheck(callerKlass, target.getDeclaringKlass(), target, meta);
        }
        if (constraintsChecks) {
            target.checkLoadingConstraints(callerKlass.getDefiningClassLoader(), resolutionKlass.getDefiningClassLoader());
        }
        plantResolvedMethod(memberName, target, refKind, meta);
    }

    // Exposed to StackWalk
    public static void plantResolvedMethod(StaticObject memberName, Method target, int refKind, Meta meta) {
        meta.HIDDEN_VMTARGET.setHiddenObject(memberName, target);
        meta.java_lang_invoke_MemberName_flags.setInt(memberName, getMethodFlags(target, refKind));
        meta.java_lang_invoke_MemberName_clazz.setObject(memberName, target.getDeclaringKlass().mirror());
    }

    private static void plantFieldMemberName(StaticObject memberName,
                    Klass resolutionKlass, Symbol<Name> name, Symbol<Type> type,
                    int refKind,
                    Klass callerKlass,
                    boolean constraintsCheck,
                    Meta meta) {
        Field field = doFieldLookup(resolutionKlass, name, type);
        if (field == null) {
            throw meta.throwExceptionWithMessage(meta.java_lang_NoSuchFieldError, cat("Failed lookup for field ", resolutionKlass.getName(), "#", name, ":", type));
        }
        if (constraintsCheck) {
            field.checkLoadingConstraints(callerKlass.getDefiningClassLoader(), resolutionKlass.getDefiningClassLoader());
        }
        plantResolvedField(memberName, field, refKind, meta);
    }

    private static void plantResolvedField(StaticObject memberName, Field field, int refKind, Meta meta) {
        meta.HIDDEN_VMTARGET.setHiddenObject(memberName, field.getDeclaringKlass());
        meta.HIDDEN_VMINDEX.setHiddenObject(memberName, Target_sun_misc_Unsafe.slotToSafetyOffset(field.getSlot(), field.isStatic()));
        meta.java_lang_invoke_MemberName_flags.setInt(memberName, getFieldFlags(refKind, field));
        meta.java_lang_invoke_MemberName_clazz.setObject(memberName, field.getDeclaringKlass().mirror());
    }

    private static int getMethodFlags(Method target, int refKind) {
        int res = target.getMethodModifiers();
        if (refKind == REF_invokeInterface) {
            res |= MN_IS_METHOD | (REF_invokeInterface << MN_REFERENCE_KIND_SHIFT);
        } else if (refKind == REF_invokeVirtual) {
            res |= MN_IS_METHOD | (REF_invokeVirtual << MN_REFERENCE_KIND_SHIFT);
        } else {
            if (target.isStatic()) {
                res |= MN_IS_METHOD | (REF_invokeStatic << MN_REFERENCE_KIND_SHIFT);
            } else if (target.isConstructor() || target.isClassInitializer()) {
                res |= MN_IS_CONSTRUCTOR | (REF_invokeSpecial << MN_REFERENCE_KIND_SHIFT);
            } else {
                res |= MN_IS_METHOD | (REF_invokeSpecial << MN_REFERENCE_KIND_SHIFT);
            }
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

    private static Method doMethodLookup(Klass resolutionKlass, Symbol<Name> name, Symbol<Signature> sig,
                    Klass callerKlass) {
        if (CompilerDirectives.isPartialEvaluationConstant(resolutionKlass)) {
            return lookupMethod(resolutionKlass, name, sig, callerKlass);
        } else {
            return lookupMethodBoundary(resolutionKlass, name, sig, callerKlass);
        }
    }

    private static Method lookupMethod(Klass resolutionKlass, Symbol<Name> name, Symbol<Signature> sig,
                    Klass callerKlass) {
        return resolutionKlass.lookupMethod(name, sig, callerKlass);
    }

    @TruffleBoundary
    private static Method lookupMethodBoundary(Klass resolutionKlass, Symbol<Name> name, Symbol<Signature> sig,
                    Klass callerKlass) {
        return lookupMethod(resolutionKlass, name, sig, callerKlass);
    }

    private static Field doFieldLookup(Klass resolutionKlass, Symbol<Name> name, Symbol<Type> sig) {
        if (CompilerDirectives.isPartialEvaluationConstant(resolutionKlass)) {
            return lookupField(resolutionKlass, name, sig);
        } else {
            return lookupFieldBoundary(resolutionKlass, name, sig);
        }
    }

    private static Field lookupField(Klass resolutionKlass, Symbol<Name> name, Symbol<Type> sig) {
        return resolutionKlass.lookupField(name, sig);
    }

    @TruffleBoundary
    private static Field lookupFieldBoundary(Klass resolutionKlass, Symbol<Name> name, Symbol<Type> sig) {
        return lookupField(resolutionKlass, name, sig);
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

    /**
     * Compile-time constants go here. This collection exists not only for reference from clients,
     * but also for ensuring the VM and JDK agree on the values of these constants. JDK verifies
     * that through {@code java.lang.invoke.MethodHandleNatives#verifyConstants()}
     */
    public static final class Constants {
        private Constants() {
        } // static only

        public static final int MN_IS_METHOD = 0x00010000; // method (not constructor)
        public static final int MN_IS_CONSTRUCTOR = 0x00020000; // constructor
        public static final int MN_IS_FIELD = 0x00040000; // field
        public static final int MN_IS_TYPE = 0x00080000; // nested type
        // @CallerSensitive annotation detected
        public static final int MN_CALLER_SENSITIVE = 0x00100000;
        public static final int MN_TRUSTED_FINAL = 0x00200000; // trusted final field
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
