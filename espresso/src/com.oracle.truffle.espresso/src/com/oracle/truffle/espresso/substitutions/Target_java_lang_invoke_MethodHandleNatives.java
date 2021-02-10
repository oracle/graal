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
import static com.oracle.truffle.espresso.classfile.Constants.REF_getField;
import static com.oracle.truffle.espresso.classfile.Constants.REF_getStatic;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeInterface;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeSpecial;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeStatic;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeVirtual;
import static com.oracle.truffle.espresso.classfile.Constants.REF_putField;
import static com.oracle.truffle.espresso.classfile.Constants.REF_putStatic;
import static com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics.PolySigIntrinsics.None;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;

import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.descriptors.Signatures;
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
    public static void init(@Host(typeName = "Ljava/lang/invoke/MemberName;") StaticObject self, @Host(Object.class) StaticObject ref,
                    @InjectMeta Meta meta) {
        Klass targetKlass = ref.getKlass();

        if (targetKlass.getType() == Type.java_lang_reflect_Method) {
            // Actual planting
            Method target = Method.getHostReflectiveMethodRoot(ref, meta);
            plantResolvedMethod(self, target, target.getRefKind(), meta);
            // Finish the job
            self.setObjectField(meta.java_lang_invoke_MemberName_clazz, target.getDeclaringKlass().mirror());
        } else if (targetKlass.getType() == Type.java_lang_reflect_Field) {
            // Actual planting
            Field field = Field.getReflectiveFieldRoot(ref, meta);
            plantResolvedField(self, field, getRefKind(self.getIntField(meta.java_lang_invoke_MemberName_flags)), meta);
            // Finish the job
            Klass fieldKlass = ref.getObjectField(meta.java_lang_reflect_Field_class).getMirrorKlass();
            self.setObjectField(meta.java_lang_invoke_MemberName_clazz, fieldKlass.mirror());
        } else if (targetKlass.getType() == Type.java_lang_reflect_Constructor) {
            Method target = Method.getHostReflectiveConstructorRoot(ref, meta);
            plantResolvedMethod(self, target, target.getRefKind(), meta);
            self.setObjectField(meta.java_lang_invoke_MemberName_clazz, target.getDeclaringKlass().mirror());
        } else {
            throw EspressoError.shouldNotReachHere("invalid argument for MemberName.init: ", ref.getKlass());
        }
    }

    @Substitution
    public static void expand(@Host(typeName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                    @InjectMeta Meta meta,
                    @InjectProfile SubstitutionProfiler profiler) {
        if (StaticObject.isNull(self)) {
            profiler.profile(0);
            throw Meta.throwExceptionWithMessage(meta.java_lang_InternalError, "MemberName is null");
        }
        boolean haveClazz = !StaticObject.isNull(self.getObjectField(meta.java_lang_invoke_MemberName_clazz));
        boolean haveName = !StaticObject.isNull(self.getObjectField(meta.java_lang_invoke_MemberName_name));
        boolean haveType = !StaticObject.isNull(self.getObjectField(meta.java_lang_invoke_MemberName_type));
        int flags = self.getIntField(meta.java_lang_invoke_MemberName_flags);

        switch (flags & ALL_KINDS) {
            case MN_IS_METHOD:
            case MN_IS_CONSTRUCTOR: {
                Method m = (Method) self.getHiddenField(meta.HIDDEN_VMTARGET);
                if (m == null) {
                    profiler.profile(2);
                    throw Meta.throwExceptionWithMessage(meta.java_lang_InternalError, "Nothing to expand");
                }
                if (!haveClazz) {
                    self.setObjectField(meta.java_lang_invoke_MemberName_clazz, m.getDeclaringKlass().mirror());
                }
                if (!haveName) {
                    self.setObjectField(meta.java_lang_invoke_MemberName_name, meta.toGuestString(m.getName()));
                }
                if (!haveType) {
                    self.setObjectField(meta.java_lang_invoke_MemberName_type, meta.toGuestString(m.getRawSignature()));
                }
                break;
            }
            case MN_IS_FIELD: {
                StaticObject clazz = self.getObjectField(meta.java_lang_invoke_MemberName_clazz);
                if (StaticObject.isNull(clazz)) {
                    profiler.profile(3);
                    throw Meta.throwExceptionWithMessage(meta.java_lang_InternalError, "Nothing to expand");
                }
                Klass holder = clazz.getMirrorKlass();
                int slot = (int) (((long) self.getHiddenField(meta.HIDDEN_VMINDEX)) - Target_sun_misc_Unsafe.SAFETY_FIELD_OFFSET);
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
                    throw Meta.throwExceptionWithMessage(meta.java_lang_InternalError, "Nothing to expand");
                }
                if (!haveName) {
                    self.setObjectField(meta.java_lang_invoke_MemberName_name, meta.toGuestString(f.getName()));
                }
                if (!haveType) {
                    if (Types.isPrimitive(f.getType())) {
                        Klass k = meta.resolvePrimitive(f.getType());
                        self.setObjectField(meta.java_lang_invoke_MemberName_type, k.mirror());
                    } else {
                        self.setObjectField(meta.java_lang_invoke_MemberName_type, meta.toGuestString(f.getType()));
                    }
                }
                break;
            }
            default:
                profiler.profile(1);
                throw Meta.throwExceptionWithMessage(meta.java_lang_InternalError, "MemberName is null");
        }
    }

    @SuppressWarnings("unused")
    @Substitution
    public static int getNamedCon(int which, @Host(Object[].class) StaticObject name) {
        return 0;
    }

    @Substitution
    public static void setCallSiteTargetNormal(@Host(CallSite.class) StaticObject site, @Host(MethodHandle.class) StaticObject target,
                    @InjectMeta Meta meta) {
        site.setObjectField(meta.java_lang_invoke_CallSite_target, target);
    }

    @Substitution
    public static void setCallSiteTargetVolatile(@Host(CallSite.class) StaticObject site, @Host(MethodHandle.class) StaticObject target,
                    @InjectMeta Meta meta) {
        site.setObjectFieldVolatile(meta.java_lang_invoke_CallSite_target, target);
    }

    // TODO(garcia) verifyConstants

    @Substitution
    public static int getMembers(
                    @Host(Class.class) StaticObject defc,
                    @Host(String.class) StaticObject matchName,
                    @Host(String.class) StaticObject matchSig,
                    int matchFlags,
                    @Host(Class.class) StaticObject originalCaller,
                    int skip,
                    @Host(typeName = "[Ljava/lang/invoke/MemberName;") StaticObject resultsArr,
                    @InjectMeta Meta meta) {
        if (StaticObject.isNull(defc) || StaticObject.isNull(resultsArr)) {
            return -1;
        }
        EspressoContext context = meta.getContext();
        StaticObject[] results = resultsArr.unwrap();
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
            caller = originalCaller.getMirrorKlass();
            if (caller == null) {
                return -1;
            }
        }

        return findMemberNames(defc.getMirrorKlass(), name, sig, matchFlags, caller, skip, results);
    }

    @SuppressWarnings("unused")
    private static int findMemberNames(Klass klass, Symbol<Name> name, String sig, int matchFlags, Klass caller, int skip, StaticObject[] results) {
        // TODO(garcia) this.
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
    public static long objectFieldOffset(@Host(typeName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                    @InjectMeta Meta meta) {
        return (long) self.getHiddenField(meta.HIDDEN_VMINDEX);
    }

    @Substitution
    public static long staticFieldOffset(@Host(typeName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                    @InjectMeta Meta meta) {
        return (long) self.getHiddenField(meta.HIDDEN_VMINDEX);
    }

    @Substitution
    public static @Host(Object.class) StaticObject staticFieldBase(@Host(typeName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                    @InjectMeta Meta meta) {
        return self.getObjectField(meta.java_lang_invoke_MemberName_clazz).getMirrorKlass().getStatics();
    }

    @Substitution
    public static @Host(Object.class) StaticObject getMemberVMInfo(@Host(typeName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                    @InjectMeta Meta meta) {
        Object vmtarget = self.getHiddenField(meta.HIDDEN_VMTARGET);
        Object vmindex = self.getHiddenField(meta.HIDDEN_VMINDEX);
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

        return StaticObject.createArray(meta.java_lang_Object_array, result);
    }

    @Substitution
    public static @Host(typeName = "Ljava/lang/invoke/MemberName;") StaticObject resolve(
                    @Host(typeName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                    @Host(value = Class.class) StaticObject caller,
                    boolean speculativeResolve,
                    // Checkstyle: stop
                    @GuestCall(target = "java_lang_invoke_MemberName_getSignature") DirectCallNode mnGetSignature,
                    // Checkstyle: resume
                    @InjectMeta Meta meta,
                    @InjectProfile SubstitutionProfiler profiler) {
        try {
            return resolve(self, caller, mnGetSignature, meta, profiler);
        } catch (EspressoException e) {
            if (speculativeResolve) {
                return StaticObject.NULL;
            }
            throw e;

        }
    }

    /**
     * Complete resolution of a memberName, full with method lookup, flags overwriting and planting
     * target.
     * 
     * @param memberName The memberName to resolve
     * @param caller the class that commands the resolution
     * @return The resolved memberName. Note that it should be the same reference as self
     */
    @Substitution
    public static @Host(typeName = "Ljava/lang/invoke/MemberName;") StaticObject resolve(
                    @Host(typeName = "Ljava/lang/invoke/MemberName;") StaticObject memberName,
                    @Host(value = Class.class) StaticObject caller,
                    // Checkstyle: stop
                    @GuestCall(target = "java_lang_invoke_MemberName_getSignature") DirectCallNode mnGetSignature,
                    // Checkstyle: resume
                    @InjectMeta Meta meta,
                    @InjectProfile SubstitutionProfiler profiler) {
        // TODO(Garcia) Perhaps perform access checks ?
        if (memberName.getHiddenField(meta.HIDDEN_VMTARGET) != null) {
            return memberName; // Already planted
        }
        StaticObject clazz = memberName.getObjectField(meta.java_lang_invoke_MemberName_clazz);
        if (StaticObject.isNull(clazz)) {
            return StaticObject.NULL;
        }
        Klass defKlass = clazz.getMirrorKlass();

        Field flagField = meta.java_lang_invoke_MemberName_flags;
        int flags = memberName.getIntField(flagField);
        int refKind = getRefKind(flags);

        StaticObject name = memberName.getObjectField(meta.java_lang_invoke_MemberName_name);
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
            profiler.profile(0);
            if ((flags & ALL_KINDS) == MN_IS_FIELD) {
                throw Meta.throwException(meta.java_lang_NoSuchFieldException);
            } else {
                throw Meta.throwException(meta.java_lang_NoSuchMethodException);
            }
        }

        PolySigIntrinsics mhMethodId = None;
        if (((flags & ALL_KINDS) == MN_IS_METHOD) &&
                        (defKlass.getType() == Type.java_lang_invoke_MethodHandle || defKlass.getType() == Type.java_lang_invoke_VarHandle)) {
            if (refKind == REF_invokeVirtual ||
                            refKind == REF_invokeSpecial ||
                            refKind == REF_invokeStatic) {
                PolySigIntrinsics iid = MethodHandleIntrinsics.getId(methodName, defKlass);
                if (iid != None &&
                                ((refKind == REF_invokeStatic) == (iid.isStaticPolymorphicSignature()))) {
                    mhMethodId = iid;
                }
            }
        }

        Klass callerKlass = StaticObject.isNull(caller) ? meta.java_lang_Object : caller.getMirrorKlass();

        StaticObject type = (StaticObject) mnGetSignature.call(memberName);
        if (StaticObject.isNull(type)) {
            return StaticObject.NULL;
        }
        String desc = meta.toHostString(type);
        switch (flags & ALL_KINDS) {
            case MN_IS_CONSTRUCTOR:
                profiler.profile(1);
                Symbol<Signature> constructorSignature = meta.getEspressoLanguage().getSignatures().lookupValidSignature(desc);
                plantMethodMemberName(memberName, constructorSignature, defKlass, callerKlass, methodName, refKind, meta);
                memberName.setHiddenField(meta.HIDDEN_VMINDEX, -3_000_000L);
                break;
            case MN_IS_METHOD:
                profiler.profile(2);
                Signatures signatures = meta.getEspressoLanguage().getSignatures();
                Symbol<Signature> sig = signatures.lookupValidSignature(desc);
                if (refKind == REF_invokeStatic || refKind == REF_invokeInterface) {
                    profiler.profile(4);
                    plantMethodMemberName(memberName, sig, defKlass, callerKlass, methodName, refKind, meta);

                } else if (mhMethodId != None) {
                    assert (!mhMethodId.isStaticPolymorphicSignature());
                    if (mhMethodId.isSignaturePolymorphicIntrinsic()) {
                        profiler.profile(5);
                        plantInvokeBasic(memberName, sig, defKlass, callerKlass, methodName, refKind, meta);
                    } else {
                        throw EspressoError.shouldNotReachHere("Should never need to resolve invokeGeneric MemberName");
                    }
                } else if (refKind == REF_invokeVirtual || refKind == REF_invokeSpecial) {
                    profiler.profile(6);
                    plantMethodMemberName(memberName, sig, defKlass, callerKlass, methodName, refKind, meta);
                }
                flags = memberName.getIntField(flagField);
                refKind = (flags >> MN_REFERENCE_KIND_SHIFT) & MN_REFERENCE_KIND_MASK;
                memberName.setHiddenField(meta.HIDDEN_VMINDEX, (refKind == REF_invokeInterface || refKind == REF_invokeVirtual) ? 1_000_000L : -1_000_000L);
                break;
            case MN_IS_FIELD:
                profiler.profile(3);
                Symbol<Type> t = meta.getEspressoLanguage().getTypes().lookup(desc);
                plantFieldMemberName(memberName, t, defKlass, methodName, refKind, meta);
                break;
            default:
                throw Meta.throwExceptionWithMessage(meta.java_lang_LinkageError, "Member name resolution failed");
        }

        return memberName;
    }

    // region MemberName planting

    private static void plantInvokeBasic(StaticObject memberName, Symbol<Signature> sig, Klass defKlass, Klass callerKlass, Symbol<Name> name, int refKind, Meta meta) {
        assert (name == Name.invokeBasic);
        Method target = defKlass.lookupMethod(name, sig, callerKlass);
        memberName.setHiddenField(meta.HIDDEN_VMTARGET, target);
        memberName.setIntField(meta.java_lang_invoke_MemberName_flags, getMethodFlags(target, refKind));
    }

    private static void plantMethodMemberName(StaticObject memberName, Symbol<Signature> sig, Klass defKlass, Klass callerKlass, Symbol<Name> name, int refKind, Meta meta) {
        Method target = defKlass.lookupMethod(name, sig, callerKlass);
        if (target == null) {
            throw Meta.throwException(meta.java_lang_NoSuchMethodError);
        }
        plantResolvedMethod(memberName, target, refKind, meta);
    }

    // Exposed to StackWalk
    public static void plantResolvedMethod(StaticObject memberName, Method target, int refKind, Meta meta) {
        memberName.setHiddenField(meta.HIDDEN_VMTARGET, target);
        memberName.setIntField(meta.java_lang_invoke_MemberName_flags, getMethodFlags(target, refKind));
    }

    private static void plantFieldMemberName(StaticObject memberName, Symbol<Type> type, Klass defKlass, Symbol<Name> name, int refKind, Meta meta) {
        Field field = defKlass.lookupField(name, type);
        if (field == null) {
            throw Meta.throwException(meta.java_lang_NoSuchFieldError);
        }
        plantResolvedField(memberName, field, refKind, meta);
    }

    private static void plantResolvedField(StaticObject memberName, Field field, int refKind, Meta meta) {
        memberName.setHiddenField(meta.HIDDEN_VMTARGET, field.getDeclaringKlass());
        memberName.setHiddenField(meta.HIDDEN_VMINDEX, (long) field.getSlot() + Target_sun_misc_Unsafe.SAFETY_FIELD_OFFSET);
        memberName.setIntField(meta.java_lang_invoke_MemberName_flags, getFieldFlags(refKind, field));
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

    // endregion MemberName planting

    // region Helper methods

    public static int getRefKind(int flags) {
        return (flags >> MN_REFERENCE_KIND_SHIFT) & MN_REFERENCE_KIND_MASK;
    }

    // endregion Helper methods

    // MemberName
    // The JVM uses values of -2 and above for vtable indexes.
    // Field values are simple positive offsets.
    // Ref: src/share/vm/oops/methodOop.hpp
    // This value is negative enough to avoid such numbers,
    // but not too negative.
    static final int MN_IS_METHOD = 0x00010000; // method (not constructor)
    static final int MN_IS_CONSTRUCTOR = 0x00020000; // constructor
    static final int MN_IS_FIELD = 0x00040000; // field
    static final int MN_IS_TYPE = 0x00080000; // nested type
    static final int MN_CALLER_SENSITIVE = 0x00100000; // @CallerSensitive annotation detected
    static final int MN_REFERENCE_KIND_SHIFT = 24; // refKind
    static final int MN_REFERENCE_KIND_MASK = 0x0F000000 >> MN_REFERENCE_KIND_SHIFT;
    // The SEARCH_* bits are not for MN.flags but for the matchFlags argument of
    // MHN.getMembers:
    static final int MN_SEARCH_SUPERCLASSES = 0x00100000;
    static final int MN_SEARCH_INTERFACES = 0x00200000;
    static final int ALL_KINDS = MN_IS_CONSTRUCTOR | MN_IS_FIELD | MN_IS_METHOD | MN_IS_TYPE;

    @Substitution
    @SuppressWarnings("unused")
    public static void clearCallSiteContext(@Host(typeName = "Ljava/lang/invoke/MethodHandleNatives$CallSiteContext;") StaticObject context) {
        /* nop */
    }

}
