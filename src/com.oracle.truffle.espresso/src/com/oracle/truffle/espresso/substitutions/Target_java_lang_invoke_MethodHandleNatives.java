package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.max;

@EspressoSubstitutions
public final class Target_java_lang_invoke_MethodHandleNatives {

    /**
     * plants an already resolved target into a memberName
     * 
     * @param self the memberName
     * @param ref the target. Can be either a mathod or a field.
     */
    @Substitution
    public static void init(@Host(typeName = "Ljava/lang/invoke/MemberName;") StaticObjectImpl self, @Host(Object.class) StaticObject ref) {
        Klass mnKlass = self.getKlass();
        Meta meta = mnKlass.getContext().getMeta();

        Klass targetKlass = ref.getKlass();

        if (targetKlass.getType() == Type.Method) {
            // Actual planting
            Method target = Method.getHostReflectiveMethodRoot(ref);
            plantResolvedMethod(self, target, target.getRawSignature());
            // Finish the job
            Field flagField = meta.MNflags;
            int refKind = target.getRefKind();
            self.setField(flagField, getMethodFlags(target, refKind));
            self.setField(meta.MNclazz, target.getDeclaringKlass().mirror());
        } else {
            assert (targetKlass.getType() == Type.Field);
            // Actual planting
            Field field = Target_sun_misc_Unsafe.getReflectiveFieldRoot(ref);
            int refkind = getRefKind((int) self.getField(meta.MNflags));
            plantResolvedField(self, field);
            // Finish the job
            StaticObjectImpl guestField = (StaticObjectImpl) ref;
            Klass fieldKlass = ((StaticObjectClass) guestField.getField(meta.Field_class)).getMirrorKlass();
            self.setField(meta.MNflags, getFieldFlags(refkind, field));
            self.setField(meta.MNclazz, fieldKlass.mirror());
        }
    }

    @SuppressWarnings("unused")
    @Substitution
    public static int getNamedCon(int which, @Host(Object[].class) StaticObjectArray name) {
        return 0;
    }

    @Substitution
    public static void setCallSiteTargetNormal(@Host(CallSite.class) StaticObjectImpl site, @Host(MethodHandle.class) StaticObjectImpl target) {
        site.setField(site.getKlass().getMeta().CStarget, target);
    }

    @Substitution
    public static void setCallSiteTargetVolatile(@Host(CallSite.class) StaticObjectImpl site, @Host(MethodHandle.class) StaticObjectImpl target) {
        site.setFieldVolatile(site.getKlass().getMeta().CStarget, target);
    }

    // TODO(garcia) verifyConstants

    @Substitution
    public static int getMembers(
                    @Host(Class.class) StaticObject defc,
                    @Host(String.class) StaticObject matchName,
                    @Host(String.class) StaticObject matchSig,
                    int matchFlags,
                    @Host(Class.class) StaticObject _caller,
                    int skip,
                    @Host(typeName = "[Ljava/lang/invoke/MemberName;") StaticObject _results) {
        if (defc == StaticObject.NULL || _results == StaticObject.NULL) {
            return -1;
        }
        EspressoContext context = defc.getKlass().getContext();
        StaticObject[] results = ((StaticObjectArray) _results).unwrap();
        Symbol<Name> name = null;
        // Symbol<Signature> sig = null;
        if (matchName != StaticObject.NULL) {
            name = context.getNames().lookup(Meta.toHostString(matchName));
            if (name == null)
                return 0;
        }
        String sig = Meta.toHostString(matchSig);
        if (sig == null)
            return 0;

        Klass caller = null;
        if (_caller != StaticObject.NULL) {
            caller = ((StaticObjectClass) _caller).getMirrorKlass();
            if (caller == null)
                return -1;
        }

        if (defc == StaticObject.NULL) {
            return -1;
        }

        return findMemberNames(((StaticObjectClass) defc).getMirrorKlass(), name, sig, matchFlags, caller, skip, results);
    }

    @SuppressWarnings("unused")
    private static int findMemberNames(Klass klass, Symbol<Name> name, String sig, int matchFlags, Klass caller, int skip, StaticObject[] results) {
        // TODO(Garcia) this.
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
    public static long objectFieldOffset(@Host(typeName = "Ljava/lang/invoke/MemberName;") StaticObjectImpl self) {
        return (long) self.getHiddenField("vmindex");
    }

    @Substitution
    public static long staticFieldOffset(@Host(typeName = "Ljava/lang/invoke/MemberName;") StaticObjectImpl self) {
        return (long) self.getHiddenField("vmindex");
    }

    @Substitution
    public static @Host(Object.class) StaticObject staticFieldBase(@Host(typeName = "Ljava/lang/invoke/MemberName;") StaticObjectImpl self) {
        return (StaticObject) self.getField(self.getKlass().lookupField(Name.clazz, Type.Class));
    }

    @Substitution
    public static @Host(Object.class) StaticObject getMemberVMInfo(@Host(typeName = "Ljava/lang/invoke/MemberName;") StaticObjectImpl self) {
        Object vmtarget = self.getHiddenField("vmtarget");
        Object vmindex = self.getHiddenField("vmindex");
        StaticObject[] result = new StaticObject[2];

        Meta meta = self.getKlass().getMeta();
        if (vmindex == null) {
            result[0] = meta.boxLong(-2_000_000); // vmindex is not used in espresso
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

        return new StaticObjectArray(meta.Object_array, result);
    }

    /**
     * Complete resolution of a memberName, full with method lookup, flags overwriting and planting
     * target.
     * 
     * @param self The memberName to resolve
     * @param caller the class that commands the resolution
     * @return The resolved memberName. Note that it should be the same reference as @self
     */
    @Substitution
    public static @Host(typeName = "Ljava/lang/invoke/MemberName;") StaticObject resolve(
                    @Host(typeName = "Ljava/lang/invoke/MemberName;") StaticObject self,
                    @Host(value = Class.class) StaticObject caller) {
        // TODO(Garcia) Perhaps perform access checks ?
        Klass mnKlass = self.getKlass();
        Meta meta = mnKlass.getContext().getMeta();
        StaticObjectImpl memberName = (StaticObjectImpl) self;
        if (memberName.getHiddenField("vmtarget") != null) {
            return self; // Already planted
        }

        StaticObjectClass clazz = (StaticObjectClass) memberName.getField(mnKlass.lookupDeclaredField(Name.clazz, Type.Class));
        Klass defKlass = clazz.getMirrorKlass();

        StaticObject name = (StaticObject) memberName.getField(mnKlass.lookupDeclaredField(Name.name, Type.String));
        Symbol<Name> nSymbol = meta.getEspressoLanguage().getNames().lookup(Meta.toHostString(name));

        StaticObject type = (StaticObject) meta.getSignature.invokeDirect(self);

        Field flagField = mnKlass.lookupDeclaredField(Name.flags, Type._int);
        int flags = (int) memberName.getField(flagField);
        int refKind = getRefKind(flags);
        if (defKlass == null) {
            return StaticObject.NULL;
        }
        int mhMethodId = _none;
        if (((flags & ALL_KINDS) == MN_IS_METHOD) && (defKlass.getType() == Type.MethodHandle)) {
            if (refKind == REF_invokeVirtual ||
                            refKind == REF_invokeSpecial ||
                            refKind == REF_invokeStatic) {
                int iid = MHid(nSymbol);
                if (iid != _none &&
                                ((refKind == REF_invokeStatic) == isStaticSigPoly(iid))) {
                    mhMethodId = iid;
                }
            }
        }

        StaticObject callerKlass = (caller == StaticObject.NULL) ? meta.Object.mirror() : caller;

        String desc = Meta.toHostString(type);
        switch (flags & ALL_KINDS) {
            case MN_IS_CONSTRUCTOR:
                Symbol<Signature> constructorSignature = meta.getEspressoLanguage().getSignatures().lookupValidSignature(desc);
                plantMethodMemberName(memberName, constructorSignature, defKlass, nSymbol, flagField, refKind);
                memberName.setHiddenField("vmindex", -3_000_000L);
                break;
            case MN_IS_METHOD:
                Signatures signatures = meta.getEspressoLanguage().getSignatures();
                Symbol<Signature> sig = signatures.lookupValidSignature(desc);
                if (refKind == REF_invokeStatic || refKind == REF_invokeInterface) {
                    plantMethodMemberName(memberName, sig, defKlass, nSymbol, flagField, refKind);

                } else if (mhMethodId != _none) {
                    assert (!isStaticSigPoly(mhMethodId));
                    if (isIntrinsicPolySig(mhMethodId)) {
                        boolean keepLastArg = isStaticSigPoly(mhMethodId);
                        Symbol<Signature> basic = toBasic(signatures.parsed(sig), keepLastArg, signatures);
                        Method target = meta.invokeBasic;
                        plantInvokeBasic(memberName, target, basic, defKlass, nSymbol, flagField, refKind);
                    } else {
                        StaticObjectArray appendix = new StaticObjectArray(meta.Object_array, new StaticObject[1]);
                        StaticObjectImpl result = (StaticObjectImpl) meta.linkMethod.invokeDirect(
                                        null,
                                        callerKlass, (int) (REF_invokeVirtual),
                                        clazz, name, type,
                                        appendix);

                        StaticObject getAppendix = appendix.get(0);

                        // TODO(garcia)
                        throw EspressoError.unimplemented(result.toString() + getAppendix.toString());

                    }
                } else if (refKind == REF_invokeVirtual || refKind == REF_invokeSpecial) {
                    plantMethodMemberName(memberName, sig, defKlass, nSymbol, flagField, refKind);
                }
                flags = (int) memberName.getField(flagField);
                refKind = (flags >> MN_REFERENCE_KIND_SHIFT) & MN_REFERENCE_KIND_MASK;
                memberName.setHiddenField("vmindex", (refKind == REF_invokeInterface || refKind == REF_invokeVirtual) ? 1_000_000L : -1_000_000L);
                break;
            case MN_IS_FIELD:
                Symbol<Type> t = meta.getEspressoLanguage().getTypes().lookup(desc);
                plantFieldMemberName(memberName, t, defKlass, nSymbol, flagField, refKind);
                break;
            default:
                throw new EspressoError("Unrecognised member name");
        }

        return memberName;
    }

    /**
     * Converts a regular signature to a basic one.
     * 
     * @param sig Signature to convert
     * @param keepLastArg Whether or not to erase the last parameter.
     * @param signatures known signatures for the contexr.
     * @return A basic signature corresponding to @sig
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Symbol<Signature> toBasic(Symbol<Type>[] sig, boolean keepLastArg, Signatures signatures) {
        int pcount = Signatures.parameterCount(sig, false);
        int params = max(pcount - (keepLastArg ? 0 : 1), 0);
        List<Symbol<Type>> buf = new ArrayList<>();
        // Symbol<Type>[] params = new Symbol[max(pcount - (keepLastArg ? 0 : 1), 0)];

        for (int i = 0; i < params; i++) {
            Symbol<Type> t = Signatures.parameterType(sig, i);
            if (i == params - 1 && keepLastArg) {
                buf.add(t);
            } else {
                buf.add(toBasic(t));
            }
        }

        Symbol<Type> rtype = toBasic(Signatures.returnType(sig));
        return signatures.makeRaw(rtype, buf.toArray(new Symbol[params]));
    }

    private static Symbol<Type> toBasic(Symbol<Type> t) {
        if (t == Type.Object || t.toString().charAt(0) == '[') {
            return Type.Object;
        } else if (t == Type._int || t == Type._short || t == Type._boolean || t == Type._char) {
            return Type._int;
        } else {
            return t;
        }
    }

    // MemberName planting

    private static void plantInvokeBasic(StaticObjectImpl memberName, Method target, Symbol<Signature> basicSig, Klass defKlass, Symbol<Name> name, Field flagField, int refKind) {
        assert (name == Name.invokeBasic);
        assert (defKlass.getType() == target.getContext().getMeta().MethodHandle.getType() && target.getName() == target.getContext().getMeta().invokeBasic.getName());
        memberName.setHiddenField("vmtarget", target);
        memberName.setHiddenField("basicSignature", basicSig);
        memberName.setHiddenField("invocationSignature", basicSig);
        memberName.setField(flagField, getMethodFlags(target, refKind));
    }

    private static void plantMethodMemberName(StaticObjectImpl memberName, Symbol<Signature> sig, Klass defKlass, Symbol<Name> name, Field flagField, int refKind) {
        Method target = defKlass.lookupMethod(name, sig);
        if (target == null) {
            throw defKlass.getContext().getMeta().throwEx(NoSuchMethodException.class);
        }
        plantResolvedMethod(memberName, target, sig);
        memberName.setField(flagField, getMethodFlags(target, refKind));
    }

    private static void plantResolvedMethod(StaticObjectImpl memberName, Method target, Symbol<Signature> sig) {
        memberName.setHiddenField("vmtarget", target);
        memberName.setHiddenField("invocationSignature", sig);
    }

    private static void plantFieldMemberName(StaticObjectImpl memberName, Symbol<Type> type, Klass defKlass, Symbol<Name> name, Field flagField, int refKind) {
        Field field = defKlass.lookupField(name, type);
        if (field == null) {
            throw defKlass.getContext().getMeta().throwEx(NoSuchFieldException.class);
        }
        plantResolvedField(memberName, field);
        memberName.setField(flagField, getFieldFlags(refKind, field));
    }

    private static void plantResolvedField(StaticObjectImpl memberName, Field field) {
        memberName.setHiddenField("vmtarget", field.getDeclaringKlass());
        // Remember we are accessing a static field.
        long offset = Target_sun_misc_Unsafe.SAFETY_FIELD_OFFSET + (field.isStatic() ? Target_sun_misc_Unsafe.STATIC_FIELD_OFFSET : 0);
        memberName.setHiddenField("vmindex", (field.getSlot() + offset));
        memberName.setHiddenField("vmfield", field);
    }

    private static int getMethodFlags(Method target, int refKind) {
        int res = target.getModifiers();
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
        if (isSetter)
            res += ((REF_putField - REF_getField) << MN_REFERENCE_KIND_SHIFT);
        return res;

        // End MemberName planting
    }

    // Helping methods

    public static int MHid(Symbol<Name> name) {
        if (name == Name.invoke)
            return _invokeGeneric;
        if (name == Name.invokeExact)
            return _invokeGeneric;
        if (name == Name.invokeBasic)
            return _invokeBasic;
        if (name == Name.linkToVirtual)
            return _linkToVirtual;
        if (name == Name.linkToStatic)
            return _linkToStatic;
        if (name == Name.linkToInterface)
            return _linkToInterface;
        if (name == Name.linkToSpecial)
            return _linkToSpecial;
        return _none;
    }

    @SuppressWarnings("unused")
    private static boolean isMHinvoke(Klass klass, Symbol<Name> name) {
        if (klass == null) {
            return false;
        }
        if (!(klass.getType() == Type.MethodHandle)) {
            return false;
        }
        Symbol<Signature> sig = Signature.Object_ObjectArray;
        Method m = klass.lookupMethod(name, sig);
        if (m == null) {
            return false;
        }
        int required = ACC_NATIVE | ACC_VARARGS;
        int flags = m.getModifiers();
        return (flags & required) == required;
    }

    private static boolean isStaticSigPoly(int id) {
        return (id >= _firstStaticSigPoly) && (id <= _lastSigPoly);
    }

    private static boolean isIntrinsicPolySig(int id) {
        return (id != _invokeGeneric);
    }

    public static int getRefKind(int flags) {
        return (flags >> MN_REFERENCE_KIND_SHIFT) & MN_REFERENCE_KIND_MASK;
    }

    // End helping methods

    // Useful thingies.

    public static final int // intrinsics
    _none = 0,
                    _invokeGeneric = 1,
                    _invokeBasic = 2,
                    _linkToVirtual = 3,
                    _linkToStatic = 4,
                    _linkToSpecial = 5,
                    _linkToInterface = 6,

                    _firstStaticSigPoly = _linkToVirtual,
                    _lastSigPoly = _linkToInterface;

    static final int // for getConstant
    GC_COUNT_GWT = 4,
                    GC_LAMBDA_SUPPORT = 5;

    // MemberName
    // The JVM uses values of -2 and above for vtable indexes.
    // Field values are simple positive offsets.
    // Ref: src/share/vm/oops/methodOop.hpp
    // This value is negative enough to avoid such numbers,
    // but not too negative.
    static final int MN_IS_METHOD = 0x00010000, // method (not constructor)
                    MN_IS_CONSTRUCTOR = 0x00020000, // constructor
                    MN_IS_FIELD = 0x00040000, // field
                    MN_IS_TYPE = 0x00080000, // nested type
                    MN_CALLER_SENSITIVE = 0x00100000, // @CallerSensitive annotation detected
                    MN_REFERENCE_KIND_SHIFT = 24, // refKind
                    MN_REFERENCE_KIND_MASK = 0x0F000000 >> MN_REFERENCE_KIND_SHIFT,
                    // The SEARCH_* bits are not for MN.flags but for the matchFlags argument of
                    // MHN.getMembers:
                    MN_SEARCH_SUPERCLASSES = 0x00100000,
                    MN_SEARCH_INTERFACES = 0x00200000,
                    ALL_KINDS = MN_IS_CONSTRUCTOR | MN_IS_FIELD | MN_IS_METHOD | MN_IS_TYPE;

    /**
     * Access modifier flags.
     */
    static final char ACC_PUBLIC = 0x0001,
                    ACC_PRIVATE = 0x0002,
                    ACC_PROTECTED = 0x0004,
                    ACC_STATIC = 0x0008,
                    ACC_FINAL = 0x0010,
                    ACC_SYNCHRONIZED = 0x0020,
                    ACC_VOLATILE = 0x0040,
                    ACC_TRANSIENT = 0x0080,
                    ACC_NATIVE = 0x0100,
                    ACC_INTERFACE = 0x0200,
                    ACC_ABSTRACT = 0x0400,
                    ACC_STRICT = 0x0800,
                    ACC_SYNTHETIC = 0x1000,
                    ACC_ANNOTATION = 0x2000,
                    ACC_ENUM = 0x4000,
                    // aliases:
                    ACC_SUPER = ACC_SYNCHRONIZED,
                    ACC_BRIDGE = ACC_VOLATILE,
                    ACC_VARARGS = ACC_TRANSIENT;

    /**
     * Constant pool reference-kind codes, as used by CONSTANT_MethodHandle CP entries.
     */
    public static final byte REF_NONE = 0,  // null value
                    REF_getField = 1,
                    REF_getStatic = 2,
                    REF_putField = 3,
                    REF_putStatic = 4,
                    REF_invokeVirtual = 5,
                    REF_invokeStatic = 6,
                    REF_invokeSpecial = 7,
                    REF_newInvokeSpecial = 8,
                    REF_invokeInterface = 9,
                    REF_LIMIT = 10;

}
