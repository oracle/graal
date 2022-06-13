/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.interop;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * EspressoForeignProxyGenerator contains the code to generate a dynamic Espresso proxy class for
 * foreign objects.
 *
 * Large parts of this class is copied from java.lang.reflect.ProxyGenerator.
 */
public class EspressoForeignProxyGenerator {
    /*
     * In the comments below, "JVMS" refers to The Java Virtual Machine Specification Second Edition
     * and "JLS" refers to the original version of The Java Language Specification, unless otherwise
     * specified.
     */

    /* generate 1.5-era class file version */
    private static final int CLASSFILE_MAJOR_VERSION = 49;
    private static final int CLASSFILE_MINOR_VERSION = 0;

    /*
     * beginning of constants copied from sun.tools.java.RuntimeConstants (which no longer exists):
     */

    /* constant pool tags */
    private static final int CONSTANT_UTF8 = 1;
    //private static final int CONSTANT_UNICODE = 2;
    private static final int CONSTANT_INTEGER = 3;
    private static final int CONSTANT_FLOAT = 4;
    private static final int CONSTANT_LONG = 5;
    private static final int CONSTANT_DOUBLE = 6;
    private static final int CONSTANT_CLASS = 7;
    private static final int CONSTANT_STRING = 8;
    private static final int CONSTANT_FIELD = 9;
    private static final int CONSTANT_METHOD = 10;
    private static final int CONSTANT_INTERFACEMETHOD = 11;
    private static final int CONSTANT_NAMEANDTYPE = 12;

    /* access and modifier flags */
    private static final int ACC_PUBLIC = 0x00000001;
    private static final int ACC_PRIVATE = 0x00000002;
    // private static final int ACC_PROTECTED = 0x00000004;
    //private static final int ACC_STATIC = 0x00000008;
    private static final int ACC_FINAL = 0x00000010;
    // private static final int ACC_SYNCHRONIZED = 0x00000020;
// private static final int ACC_VOLATILE = 0x00000040;
// private static final int ACC_TRANSIENT = 0x00000080;
// private static final int ACC_NATIVE = 0x00000100;
// private static final int ACC_INTERFACE = 0x00000200;
// private static final int ACC_ABSTRACT = 0x00000400;
    private static final int ACC_SUPER = 0x00000020;
// private static final int ACC_STRICT = 0x00000800;

    /* opcodes */
// private static final int opc_nop = 0;
    //private static final int opc_aconst_null = 1;
    // private static final int opc_iconst_m1 = 2;
    private static final int opc_iconst_0 = 3;
    // private static final int opc_iconst_1 = 4;
// private static final int opc_iconst_2 = 5;
// private static final int opc_iconst_3 = 6;
// private static final int opc_iconst_4 = 7;
// private static final int opc_iconst_5 = 8;
// private static final int opc_lconst_0 = 9;
// private static final int opc_lconst_1 = 10;
// private static final int opc_fconst_0 = 11;
// private static final int opc_fconst_1 = 12;
// private static final int opc_fconst_2 = 13;
// private static final int opc_dconst_0 = 14;
// private static final int opc_dconst_1 = 15;
    private static final int opc_bipush = 16;
    private static final int opc_sipush = 17;
    private static final int opc_ldc = 18;
    private static final int opc_ldc_w = 19;
    // private static final int opc_ldc2_w = 20;
    private static final int opc_iload = 21;
    private static final int opc_lload = 22;
    private static final int opc_fload = 23;
    private static final int opc_dload = 24;
    private static final int opc_aload = 25;
    private static final int opc_iload_0 = 26;
    // private static final int opc_iload_1 = 27;
// private static final int opc_iload_2 = 28;
// private static final int opc_iload_3 = 29;
    private static final int opc_lload_0 = 30;
    // private static final int opc_lload_1 = 31;
// private static final int opc_lload_2 = 32;
// private static final int opc_lload_3 = 33;
    private static final int opc_fload_0 = 34;
    // private static final int opc_fload_1 = 35;
// private static final int opc_fload_2 = 36;
// private static final int opc_fload_3 = 37;
    private static final int opc_dload_0 = 38;
    // private static final int opc_dload_1 = 39;
// private static final int opc_dload_2 = 40;
// private static final int opc_dload_3 = 41;
    private static final int opc_aload_0 = 42;
    // private static final int opc_aload_1 = 43;
// private static final int opc_aload_2 = 44;
// private static final int opc_aload_3 = 45;
// private static final int opc_iaload = 46;
// private static final int opc_laload = 47;
// private static final int opc_faload = 48;
// private static final int opc_daload = 49;
// private static final int opc_aaload = 50;
// private static final int opc_baload = 51;
// private static final int opc_caload = 52;
// private static final int opc_saload = 53;
// private static final int opc_istore = 54;
// private static final int opc_lstore = 55;
// private static final int opc_fstore = 56;
// private static final int opc_dstore = 57;
    private static final int opc_astore = 58;
    // private static final int opc_istore_0 = 59;
// private static final int opc_istore_1 = 60;
// private static final int opc_istore_2 = 61;
// private static final int opc_istore_3 = 62;
// private static final int opc_lstore_0 = 63;
// private static final int opc_lstore_1 = 64;
// private static final int opc_lstore_2 = 65;
// private static final int opc_lstore_3 = 66;
// private static final int opc_fstore_0 = 67;
// private static final int opc_fstore_1 = 68;
// private static final int opc_fstore_2 = 69;
// private static final int opc_fstore_3 = 70;
// private static final int opc_dstore_0 = 71;
// private static final int opc_dstore_1 = 72;
// private static final int opc_dstore_2 = 73;
// private static final int opc_dstore_3 = 74;
    private static final int opc_astore_0 = 75;
    // private static final int opc_astore_1 = 76;
// private static final int opc_astore_2 = 77;
// private static final int opc_astore_3 = 78;
// private static final int opc_iastore = 79;
// private static final int opc_lastore = 80;
// private static final int opc_fastore = 81;
// private static final int opc_dastore = 82;
    private static final int opc_aastore = 83;
    // private static final int opc_bastore = 84;
// private static final int opc_castore = 85;
// private static final int opc_sastore = 86;
    private static final int opc_pop = 87;
    // private static final int opc_pop2 = 88;
    private static final int opc_dup = 89;
    // private static final int opc_dup_x1 = 90;
// private static final int opc_dup_x2 = 91;
// private static final int opc_dup2 = 92;
// private static final int opc_dup2_x1 = 93;
// private static final int opc_dup2_x2 = 94;
// private static final int opc_swap = 95;
// private static final int opc_iadd = 96;
// private static final int opc_ladd = 97;
// private static final int opc_fadd = 98;
// private static final int opc_dadd = 99;
// private static final int opc_isub = 100;
// private static final int opc_lsub = 101;
// private static final int opc_fsub = 102;
// private static final int opc_dsub = 103;
// private static final int opc_imul = 104;
// private static final int opc_lmul = 105;
// private static final int opc_fmul = 106;
// private static final int opc_dmul = 107;
// private static final int opc_idiv = 108;
// private static final int opc_ldiv = 109;
// private static final int opc_fdiv = 110;
// private static final int opc_ddiv = 111;
// private static final int opc_irem = 112;
// private static final int opc_lrem = 113;
// private static final int opc_frem = 114;
// private static final int opc_drem = 115;
// private static final int opc_ineg = 116;
// private static final int opc_lneg = 117;
// private static final int opc_fneg = 118;
// private static final int opc_dneg = 119;
// private static final int opc_ishl = 120;
// private static final int opc_lshl = 121;
// private static final int opc_ishr = 122;
// private static final int opc_lshr = 123;
// private static final int opc_iushr = 124;
// private static final int opc_lushr = 125;
// private static final int opc_iand = 126;
// private static final int opc_land = 127;
// private static final int opc_ior = 128;
// private static final int opc_lor = 129;
// private static final int opc_ixor = 130;
// private static final int opc_lxor = 131;
// private static final int opc_iinc = 132;
// private static final int opc_i2l = 133;
// private static final int opc_i2f = 134;
// private static final int opc_i2d = 135;
// private static final int opc_l2i = 136;
// private static final int opc_l2f = 137;
// private static final int opc_l2d = 138;
// private static final int opc_f2i = 139;
// private static final int opc_f2l = 140;
// private static final int opc_f2d = 141;
// private static final int opc_d2i = 142;
// private static final int opc_d2l = 143;
// private static final int opc_d2f = 144;
// private static final int opc_i2b = 145;
// private static final int opc_i2c = 146;
// private static final int opc_i2s = 147;
// private static final int opc_lcmp = 148;
// private static final int opc_fcmpl = 149;
// private static final int opc_fcmpg = 150;
// private static final int opc_dcmpl = 151;
// private static final int opc_dcmpg = 152;
// private static final int opc_ifeq = 153;
// private static final int opc_ifne = 154;
// private static final int opc_iflt = 155;
// private static final int opc_ifge = 156;
// private static final int opc_ifgt = 157;
// private static final int opc_ifle = 158;
// private static final int opc_if_icmpeq = 159;
// private static final int opc_if_icmpne = 160;
// private static final int opc_if_icmplt = 161;
// private static final int opc_if_icmpge = 162;
// private static final int opc_if_icmpgt = 163;
// private static final int opc_if_icmple = 164;
// private static final int opc_if_acmpeq = 165;
// private static final int opc_if_acmpne = 166;
// private static final int opc_goto = 167;
// private static final int opc_jsr = 168;
// private static final int opc_ret = 169;
// private static final int opc_tableswitch = 170;
// private static final int opc_lookupswitch = 171;
    private static final int opc_ireturn = 172;
    private static final int opc_lreturn = 173;
    private static final int opc_freturn = 174;
    private static final int opc_dreturn = 175;
    private static final int opc_areturn = 176;
    private static final int opc_return = 177;
    //private static final int opc_getstatic = 178;
    //private static final int opc_putstatic = 179;
    private static final int opc_getfield = 180;
    // private static final int opc_putfield = 181;
    private static final int opc_invokevirtual = 182;
    private static final int opc_invokespecial = 183;
    private static final int opc_invokestatic = 184;
    //private static final int opc_invokeinterface = 185;
    private static final int opc_new = 187;
    // private static final int opc_newarray = 188;
    private static final int opc_anewarray = 189;
    // private static final int opc_arraylength = 190;
    private static final int opc_athrow = 191;
    private static final int opc_checkcast = 192;
    // private static final int opc_instanceof = 193;
// private static final int opc_monitorenter = 194;
// private static final int opc_monitorexit = 195;
    private static final int opc_wide = 196;
// private static final int opc_multianewarray = 197;
// private static final int opc_ifnull = 198;
// private static final int opc_ifnonnull = 199;
// private static final int opc_goto_w = 200;
// private static final int opc_jsr_w = 201;

    // end of constants copied from sun.tools.java.RuntimeConstants

    /** name of the superclass of proxy classes */
    private static final String superclassName = "java/lang/Object";

    private static final String foreignObjectFieldName = "foreign";

    private final EspressoContext context;

    /** name of proxy class */
    private String className;

    /** proxy interfaces */
    private ObjectKlass[] interfaces;

    /** proxy class access flags */
    private int accessFlags;

    /** constant pool of class being generated */
    private ConstantPool cp = new ConstantPool();

    /** FieldInfo struct for each field of generated class */
    private List<FieldInfo> fields = new ArrayList<>();

    /** MethodInfo struct for each method of generated class */
    private List<MethodInfo> methods = new ArrayList<>();

    /**
     * maps method signature string to list of ProxyMethod objects for proxy methods with that
     * signature
     */
    private Map<String, List<ProxyMethod>> proxyMethods = new HashMap<>();

    /** count of ProxyMethod objects added to proxyMethods */
    private int proxyMethodCount = 0;

    // next number to use for generation of unique proxy class names
    private static final AtomicLong nextUniqueNumber = new AtomicLong();

    private static final String proxyNamePrefix = "com.oracle.truffle.espresso.polyglot.Foreign$Proxy$";

    private static final HashMap<String, ObjectKlass> proxyCache = new HashMap<>();

    /**
     * Construct a ProxyGenerator to generate a proxy class with the specified name and for the
     * given interfaces.
     *
     * A ProxyGenerator object contains the state for the ongoing generation of a particular proxy
     * class.
     */
    private EspressoForeignProxyGenerator(EspressoContext context, ObjectKlass[] interfaces) {
        this.context = context;
        this.className = nextClassName();
        this.interfaces = interfaces;
        this.accessFlags = ACC_PUBLIC | ACC_FINAL | ACC_SUPER;
    }

    @TruffleBoundary
    public static synchronized ObjectKlass getProxyKlass(EspressoContext context, Object metaObject, InteropLibrary interop) {
        assert interop.isMetaObject(metaObject);
        String metaName;
        try {
            metaName = interop.asString(interop.getMetaQualifiedName(metaObject));
        } catch (UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere();
        }
        if (proxyCache.containsKey(metaName)) {
            // Note: this assumes stability of meta object parents, so upon
            // hitting a use-case where meta parents do change we need to
            // place guards before returning from cache.
            return proxyCache.get(metaName);
        }
        String[] parentNames = getParentNames(metaObject, interop);
        if (parentNames.length == 0) {
            proxyCache.put(metaName, null);
            return null;
        }
        ArrayList<ObjectKlass> guestInterfaces = new ArrayList<>(parentNames.length);

        for (String parentName : parentNames) {
            ObjectKlass guestInterface = context.getPolyglotInterfaceMappings().mapName(parentName);
            if (guestInterface != null) {
                guestInterfaces.add(guestInterface);
            }
        }
        if (guestInterfaces.isEmpty()) {
            proxyCache.put(metaName, null);
            return null;
        }

        EspressoForeignProxyGenerator generator = new EspressoForeignProxyGenerator(context, guestInterfaces.toArray(new ObjectKlass[guestInterfaces.size()]));
        ObjectKlass proxyKlass = context.getRegistries().getClassRegistry(context.getBindings().getBindingsLoader()).defineKlass(generator.getClassType(), generator.generateClassFile());
        proxyCache.put(metaName, proxyKlass);
        return proxyKlass;
    }

    private static String[] getParentNames(Object metaObject, InteropLibrary interop) throws ClassCastException {
        try {
            ArrayList<String> names = new ArrayList<>();
            if (interop.hasMetaParents(metaObject)) {
                Object metaParents = interop.getMetaParents(metaObject);

                long arraySize = interop.getArraySize(metaParents);
                for (long i = 0; i < arraySize; i++) {
                    Object parent = interop.readArrayElement(metaParents, i);
                    names.add(interop.asString(interop.getMetaQualifiedName(parent)));
                    names.addAll(Arrays.asList(getParentNames(parent, interop)));
                }
            }
            return names.toArray(new String[names.size()]);
        } catch (InvalidArrayIndexException | UnsupportedMessageException e) {
            throw new ClassCastException();
        }
    }

    private static String nextClassName() {
        return proxyNamePrefix + nextUniqueNumber.getAndIncrement();
    }

    /**
     * Generate a class file for the proxy class. This method drives the class file generation
     * process.
     */
    private byte[] generateClassFile() {

        // add field for the foreign object
        fields.add(new FieldInfo(foreignObjectFieldName,
                "Ljava/lang/Object;",
                ACC_PRIVATE));

        /*
         * ============================================================ Step 1: Assemble ProxyMethod
         * objects for all methods to generate proxy dispatching code for.
         */

        /*
         * Record that proxy methods are needed for the hashCode, equals, and toString methods of
         * java.lang.Object. This is done before the methods from the proxy interfaces so that the
         * methods from java.lang.Object take precedence over duplicate methods in the proxy
         * interfaces.
         */
        addProxyMethod(context.getMeta().java_lang_Object_toString);
        addProxyMethod(context.getMeta().java_lang_Object_equals);
        addProxyMethod(context.getMeta().java_lang_Object_hashCode);

        /*
         * Now record all of the methods from the proxy interfaces, giving earlier interfaces
         * precedence over later ones with duplicate methods.
         */
        for (ObjectKlass intf : interfaces) {
            for (Method m : intf.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) {
                    addProxyMethod(m);
                }
            }
        }

        /*
         * For each set of proxy methods with the same signature, verify that the methods' return
         * types are compatible.
         */
        for (List<ProxyMethod> sigmethods : proxyMethods.values()) {
            checkReturnTypes(sigmethods);
        }

        /*
         * ============================================================ Step 2: Assemble FieldInfo
         * and MethodInfo structs for all of fields and methods in the class we are generating.
         */
        try {
            methods.add(generateConstructor());

            for (List<ProxyMethod> sigmethods : proxyMethods.values()) {
                for (ProxyMethod pm : sigmethods) {
                    // generate code for proxy method and add it
                    methods.add(pm.generateMethod());
                }
            }

            //methods.add(generateStaticInitializer());

        } catch (IOException e) {
            throw new InternalError("unexpected I/O Exception", e);
        }

        if (methods.size() > 65535) {
            throw new IllegalArgumentException("method limit exceeded");
        }
        if (fields.size() > 65535) {
            throw new IllegalArgumentException("field limit exceeded");
        }

        /*
         * ============================================================ Step 3: Write the final
         * class file.
         */

        /*
         * Make sure that constant pool indexes are reserved for the following items before starting
         * to write the final class file.
         */
        cp.getClass(dotToSlash(className));
        cp.getClass(superclassName);
        for (Klass intf : interfaces) {
            cp.getClass(dotToSlash(intf.getNameAsString()));
        }

        /*
         * Disallow new constant pool additions beyond this point, since we are about to write the
         * final constant pool table.
         */
        cp.setReadOnly();

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);

        try {
            /*
             * Write all the items of the "ClassFile" structure. See JVMS section 4.1.
             */
            // u4 magic;
            dout.writeInt(0xCAFEBABE);
            // u2 minor_version;
            dout.writeShort(CLASSFILE_MINOR_VERSION);
            // u2 major_version;
            dout.writeShort(CLASSFILE_MAJOR_VERSION);

            cp.write(dout);             // (write constant pool)

            // u2 access_flags;
            dout.writeShort(accessFlags);
            // u2 this_class;
            dout.writeShort(cp.getClass(dotToSlash(className)));
            // u2 super_class;
            dout.writeShort(cp.getClass(superclassName));

            // u2 interfaces_count;
            dout.writeShort(interfaces.length);
            // u2 interfaces[interfaces_count];
            for (Klass intf : interfaces) {
                dout.writeShort(cp.getClass(
                                dotToSlash(intf.getNameAsString())));
            }

            // u2 fields_count;
            dout.writeShort(fields.size());
            // field_info fields[fields_count];
            for (FieldInfo f : fields) {
                f.write(dout);
            }

            // u2 methods_count;
            dout.writeShort(methods.size());
            // method_info methods[methods_count];
            for (MethodInfo m : methods) {
                m.write(dout);
            }

            // u2 attributes_count;
            dout.writeShort(0); // (no ClassFile attributes for proxy classes)

        } catch (IOException e) {
            throw new InternalError("unexpected I/O Exception", e);
        }

        return bout.toByteArray();
    }

    /**
     * Add another method to be proxied, either by creating a new ProxyMethod object or augmenting
     * an old one for a duplicate method.
     *
     * "fromClass" indicates the proxy interface that the method was found through, which may be
     * different from (a subinterface of) the method's "declaring class". Note that the first Method
     * object passed for a given name and descriptor identifies the Method object (and thus the
     * declaring class) that will be passed to the invocation handler's "invoke" method for a given
     * set of duplicate methods.
     */
    private void addProxyMethod(Method m) {
        String name = m.getNameAsString();
        Klass[] parameterTypes = m.resolveParameterKlasses();
        Klass returnType = m.resolveReturnKlass();
        ObjectKlass[] exceptionTypes = m.getCheckedExceptions();

        String sig = name + getParameterDescriptors(parameterTypes);
        List<ProxyMethod> sigmethods = proxyMethods.get(sig);
        if (sigmethods != null) {
            for (ProxyMethod pm : sigmethods) {
                if (returnType == pm.returnType) {
                    /*
                     * Found a match: reduce exception types to the greatest set of exceptions that
                     * can thrown compatibly with the throws clauses of both overridden methods.
                     */
                    List<Klass> legalExceptions = new ArrayList<>();
                    collectCompatibleTypes(
                                    exceptionTypes, pm.exceptionTypes, legalExceptions);
                    collectCompatibleTypes(
                                    pm.exceptionTypes, exceptionTypes, legalExceptions);
                    pm.exceptionTypes = new Klass[legalExceptions.size()];
                    pm.exceptionTypes = legalExceptions.toArray(pm.exceptionTypes);
                    return;
                }
            }
        } else {
            sigmethods = new ArrayList<>(3);
            proxyMethods.put(sig, sigmethods);
        }
        sigmethods.add(new ProxyMethod(name, parameterTypes, returnType,
                        exceptionTypes));
    }

    /**
     * For a given set of proxy methods with the same signature, check that their return types are
     * compatible according to the Proxy specification.
     *
     * Specifically, if there is more than one such method, then all of the return types must be
     * reference types, and there must be one return type that is assignable to each of the rest of
     * them.
     */
    private static void checkReturnTypes(List<ProxyMethod> methods) {
        /*
         * If there is only one method with a given signature, there cannot be a conflict. This is
         * the only case in which a primitive (or void) return type is allowed.
         */
        if (methods.size() < 2) {
            return;
        }

        /*
         * List of return types that are not yet known to be assignable from ("covered" by) any of
         * the others.
         */
        LinkedList<Klass> uncoveredReturnTypes = new LinkedList<>();

        nextNewReturnType: for (ProxyMethod pm : methods) {
            Klass newReturnType = pm.returnType;
            if (newReturnType.isPrimitive()) {
                throw new IllegalArgumentException(
                                "methods with same signature " +
                                                getFriendlyMethodSignature(pm.methodName,
                                                                pm.parameterTypes) +
                                                " but incompatible return types: " +
                                                newReturnType.getName() + " and others");
            }
            boolean added = false;

            /*
             * Compare the new return type to the existing uncovered return types.
             */
            ListIterator<Klass> liter = uncoveredReturnTypes.listIterator();
            while (liter.hasNext()) {
                Klass uncoveredReturnType = liter.next();

                /*
                 * If an existing uncovered return type is assignable to this new one, then we can
                 * forget the new one.
                 */
                if (newReturnType.isAssignableFrom(uncoveredReturnType)) {
                    assert !added;
                    continue nextNewReturnType;
                }

                /*
                 * If the new return type is assignable to an existing uncovered one, then should
                 * replace the existing one with the new one (or just forget the existing one, if
                 * the new one has already be put in the list).
                 */
                if (uncoveredReturnType.isAssignableFrom(newReturnType)) {
                    // (we can assume that each return type is unique)
                    if (!added) {
                        liter.set(newReturnType);
                        added = true;
                    } else {
                        liter.remove();
                    }
                }
            }

            /*
             * If we got through the list of existing uncovered return types without an
             * assignability relationship, then add the new return type to the list of uncovered
             * ones.
             */
            if (!added) {
                uncoveredReturnTypes.add(newReturnType);
            }
        }

        /*
         * We shouldn't end up with more than one return type that is not assignable from any of the
         * others.
         */
        if (uncoveredReturnTypes.size() > 1) {
            ProxyMethod pm = methods.get(0);
            throw new IllegalArgumentException(
                            "methods with same signature " +
                                            getFriendlyMethodSignature(pm.methodName, pm.parameterTypes) +
                                            " but incompatible return types: " + uncoveredReturnTypes);
        }
    }

    public Symbol<Symbol.Type> getClassType() {
        return context.getTypes().fromClassGetName(className);
    }

    /**
     * A FieldInfo object contains information about a particular field in the class being
     * generated. The class mirrors the data items of the "field_info" structure of the class file
     * format (see JVMS 4.5).
     */
    private class FieldInfo {
        public int accessFlags;
        public String name;
        public String descriptor;

        public FieldInfo(String name, String descriptor, int accessFlags) {
            this.name = name;
            this.descriptor = descriptor;
            this.accessFlags = accessFlags;

            /*
             * Make sure that constant pool indexes are reserved for the following items before
             * starting to write the final class file.
             */
            cp.getUtf8(name);
            cp.getUtf8(descriptor);
        }

        public void write(DataOutputStream out) throws IOException {
            /*
             * Write all the items of the "field_info" structure. See JVMS section 4.5.
             */
            // u2 access_flags;
            out.writeShort(accessFlags);
            // u2 name_index;
            out.writeShort(cp.getUtf8(name));
            // u2 descriptor_index;
            out.writeShort(cp.getUtf8(descriptor));
            // u2 attributes_count;
            out.writeShort(0);  // (no field_info attributes for proxy classes)
        }
    }

    /**
     * An ExceptionTableEntry object holds values for the data items of an entry in the
     * "exception_table" item of the "Code" attribute of "method_info" structures (see JVMS 4.7.3).
     */
    private static class ExceptionTableEntry {
        public short startPc;
        public short endPc;
        public short handlerPc;
        public short catchType;

        public ExceptionTableEntry(short startPc, short endPc,
                        short handlerPc, short catchType) {
            this.startPc = startPc;
            this.endPc = endPc;
            this.handlerPc = handlerPc;
            this.catchType = catchType;
        }
    }

    /**
     * A MethodInfo object contains information about a particular method in the class being
     * generated. This class mirrors the data items of the "method_info" structure of the class file
     * format (see JVMS 4.6).
     */
    private class MethodInfo {
        public int accessFlags;
        public String name;
        public String descriptor;
        public short maxStack;
        public short maxLocals;
        public ByteArrayOutputStream code = new ByteArrayOutputStream();
        public List<ExceptionTableEntry> exceptionTable = new ArrayList<>();
        public short[] declaredExceptions;

        public MethodInfo(String name, String descriptor, int accessFlags) {
            this.name = name;
            this.descriptor = descriptor;
            this.accessFlags = accessFlags;

            /*
             * Make sure that constant pool indexes are reserved for the following items before
             * starting to write the final class file.
             */
            cp.getUtf8(name);
            cp.getUtf8(descriptor);
            cp.getUtf8("Code");
            cp.getUtf8("Exceptions");
        }

        public void write(DataOutputStream out) throws IOException {
            /*
             * Write all the items of the "method_info" structure. See JVMS section 4.6.
             */
            // u2 access_flags;
            out.writeShort(accessFlags);
            // u2 name_index;
            out.writeShort(cp.getUtf8(name));
            // u2 descriptor_index;
            out.writeShort(cp.getUtf8(descriptor));
            // u2 attributes_count;
            out.writeShort(2);  // (two method_info attributes:)

            // Write "Code" attribute. See JVMS section 4.7.3.

            // u2 attribute_name_index;
            out.writeShort(cp.getUtf8("Code"));
            // u4 attribute_length;
            out.writeInt(12 + code.size() + 8 * exceptionTable.size());
            // u2 max_stack;
            out.writeShort(maxStack);
            // u2 max_locals;
            out.writeShort(maxLocals);
            // u2 code_length;
            out.writeInt(code.size());
            // u1 code[code_length];
            code.writeTo(out);
            // u2 exception_table_length;
            out.writeShort(exceptionTable.size());
            for (ExceptionTableEntry e : exceptionTable) {
                // u2 start_pc;
                out.writeShort(e.startPc);
                // u2 end_pc;
                out.writeShort(e.endPc);
                // u2 handler_pc;
                out.writeShort(e.handlerPc);
                // u2 catch_type;
                out.writeShort(e.catchType);
            }
            // u2 attributes_count;
            out.writeShort(0);

            // write "Exceptions" attribute. See JVMS section 4.7.4.

            // u2 attribute_name_index;
            out.writeShort(cp.getUtf8("Exceptions"));
            // u4 attributes_length;
            out.writeInt(2 + 2 * declaredExceptions.length);
            // u2 number_of_exceptions;
            out.writeShort(declaredExceptions.length);
            // u2 exception_index_table[number_of_exceptions];
            for (short value : declaredExceptions) {
                out.writeShort(value);
            }
        }

    }

    /**
     * A ProxyMethod object represents a proxy method in the proxy class being generated: a method
     * whose implementation will encode and dispatch invocations to the proxy instance's invocation
     * handler.
     */
    private class ProxyMethod {

        public String methodName;
        public Klass[] parameterTypes;
        public Klass returnType;
        public Klass[] exceptionTypes;
        public String methodFieldName;

        private ProxyMethod(String methodName, Klass[] parameterTypes,
                            Klass returnType, Klass[] exceptionTypes) {
            this.methodName = methodName;
            this.parameterTypes = parameterTypes;
            this.returnType = returnType;
            this.exceptionTypes = exceptionTypes;
            this.methodFieldName = "m" + proxyMethodCount++;
        }

        /**
         * Return a MethodInfo object for this method, including generating the code and exception
         * table entry.
         */
        private MethodInfo generateMethod() throws IOException {
            String desc = getMethodDescriptor(parameterTypes, returnType);
            MethodInfo minfo = new MethodInfo(methodName, desc,
                    ACC_PUBLIC | ACC_FINAL);

            int[] parameterSlot = new int[parameterTypes.length];
            int nextSlot = 1;
            for (int i = 0; i < parameterSlot.length; i++) {
                parameterSlot[i] = nextSlot;
                nextSlot += getWordsPerType(parameterTypes[i]);
            }
            int localSlot0 = nextSlot;
            short pc, tryBegin = 0, tryEnd;

            DataOutputStream out = new DataOutputStream(minfo.code);

            // return type int
            // return Interop.asInt(Interop.invokeMember(Object foreign, String methodName, Object[] args))

            // MyInterface return type
            // return (MyInterface) Interop.invokeMember(Object foreign, String methodName, Object[] args))

            code_aload(0, out);

            out.writeByte(opc_getfield);
            out.writeShort(cp.getFieldRef(
                    dotToSlash(className),
                    foreignObjectFieldName, "Ljava/lang/Object;"));

            code_ldc(cp.getString(methodName), out);

            if (parameterTypes.length > 0) {

                code_ipush(parameterTypes.length, out);

                out.writeByte(opc_anewarray);
                out.writeShort(cp.getClass("java/lang/Object"));

                for (int i = 0; i < parameterTypes.length; i++) {

                    out.writeByte(opc_dup);

                    code_ipush(i, out);

                    codeWrapArgument(parameterTypes[i], parameterSlot[i], out);

                    out.writeByte(opc_aastore);
                }
            } else {
                out.writeByte(opc_iconst_0);
                out.writeByte(opc_anewarray);
                out.writeShort(cp.getClass("java/lang/Object"));
            }

            out.writeByte(opc_invokestatic);
            out.writeShort(cp.getMethodRef(
                    "com/oracle/truffle/espresso/polyglot/Interop",
                    "invokeMember",
                    "(Ljava/lang/Object;Ljava/lang/String;" +
                            "[Ljava/lang/Object;)Ljava/lang/Object;"));

            if (returnType == context.getMeta()._void) {
                out.writeByte(opc_pop);
                out.writeByte(opc_return);

            } else {
                codeUnwrapReturnValue(returnType, out);
            }

            tryEnd = pc = (short) minfo.code.size();

            List<Klass> catchList = computeUniqueCatchList(exceptionTypes);
            if (catchList.size() > 0) {

                for (Klass ex : catchList) {
                    minfo.exceptionTable.add(new ExceptionTableEntry(
                            tryBegin, tryEnd, pc,
                            cp.getClass(dotToSlash(ex.getNameAsString()))));
                }

                out.writeByte(opc_athrow);

                pc = (short) minfo.code.size();

                minfo.exceptionTable.add(new ExceptionTableEntry(
                        tryBegin, tryEnd, pc, cp.getClass("java/lang/Throwable")));

                code_astore(localSlot0, out);

                out.writeByte(opc_new);
                out.writeShort(cp.getClass(
                        "java/lang/reflect/UndeclaredThrowableException"));

                out.writeByte(opc_dup);

                code_aload(localSlot0, out);

                out.writeByte(opc_invokespecial);

                out.writeShort(cp.getMethodRef(
                        "java/lang/reflect/UndeclaredThrowableException",
                        "<init>", "(Ljava/lang/Throwable;)V"));

                out.writeByte(opc_athrow);
            }

            if (minfo.code.size() > 65535) {
                throw new IllegalArgumentException("code size limit exceeded");
            }

            minfo.maxStack = 10;
            minfo.maxLocals = (short) (localSlot0 + 1);
            minfo.declaredExceptions = new short[exceptionTypes.length];
            for (int i = 0; i < exceptionTypes.length; i++) {
                minfo.declaredExceptions[i] = cp.getClass(
                        dotToSlash(exceptionTypes[i].getNameAsString()));
            }
            return minfo;
        }

        /**
         * Generate code for wrapping an argument of the given type whose value can be found at the
         * specified local variable index, in order for it to be passed (as an Object) to the
         * invocation handler's "invoke" method. The code is written to the supplied stream.
         */
        private void codeWrapArgument(Klass type, int slot,
                                      DataOutputStream out)
                throws IOException {
            if (type.isPrimitive()) {
                PrimitiveTypeInfo prim = PrimitiveTypeInfo.get(context, type);

                if (type == context.getMeta()._int ||
                        type == context.getMeta()._boolean ||
                        type == context.getMeta()._byte ||
                        type == context.getMeta()._char ||
                        type == context.getMeta()._short) {
                    code_iload(slot, out);
                } else if (type == context.getMeta()._long) {
                    code_lload(slot, out);
                } else if (type == context.getMeta()._float) {
                    code_fload(slot, out);
                } else if (type == context.getMeta()._double) {
                    code_dload(slot, out);
                } else {
                    throw new AssertionError();
                }

                out.writeByte(opc_invokestatic);
                out.writeShort(cp.getMethodRef(
                        prim.wrapperClassName,
                        "valueOf", prim.wrapperValueOfDesc));
            } else {
                code_aload(slot, out);
            }
        }

        /**
         * Generate code for unwrapping a return value of the given type from the invocation
         * handler's "invoke" method (as type Object) to its correct type. The code is written to
         * the supplied stream.
         */
        private void codeUnwrapReturnValue(Klass type, DataOutputStream out)
                throws IOException {
            if (type.isPrimitive()) {
                PrimitiveTypeInfo prim = PrimitiveTypeInfo.get(context, type);

                out.writeByte(opc_checkcast);
                out.writeShort(cp.getClass(prim.wrapperClassName));

                out.writeByte(opc_invokevirtual);
                out.writeShort(cp.getMethodRef(
                        prim.wrapperClassName,
                        prim.unwrapMethodName, prim.unwrapMethodDesc));

                if (type == context.getMeta()._int ||
                        type == context.getMeta()._boolean ||
                        type == context.getMeta()._byte ||
                        type == context.getMeta()._char ||
                        type == context.getMeta()._short) {
                    out.writeByte(opc_ireturn);
                } else if (type == context.getMeta()._long) {
                    out.writeByte(opc_lreturn);
                } else if (type == context.getMeta()._float) {
                    out.writeByte(opc_freturn);
                } else if (type == context.getMeta()._double) {
                    out.writeByte(opc_dreturn);
                } else {
                    throw new AssertionError();
                }
            } else {
                out.writeByte(opc_checkcast);
                out.writeShort(cp.getClass(dotToSlash(type.getNameAsString())));
                out.writeByte(opc_areturn);
            }
        }
    }

    /**
     * Generate the constructor method for the proxy class.
     */
    private MethodInfo generateConstructor() throws IOException {
        MethodInfo minfo = new MethodInfo(
                        "<init>", "()V",
                        ACC_PUBLIC);

        DataOutputStream out = new DataOutputStream(minfo.code);
        out.writeByte(opc_aload_0);
        out.writeByte(opc_invokespecial);
        out.writeShort(cp.getMethodRef(
                superclassName,
                "<init>", "()V"));

        out.writeByte(opc_return);

        minfo.maxStack = 1;
        minfo.maxLocals = 1;
        minfo.declaredExceptions = new short[0];

        return minfo;
    }

    /*
     * =============== Code Generation Utility Methods ===============
     */

    /*
     * The following methods generate code for the load or store operation indicated by their name
     * for the given local variable. The code is written to the supplied stream.
     */

    private void code_iload(int lvar, DataOutputStream out)
                    throws IOException {
        codeLocalLoadStore(lvar, opc_iload, opc_iload_0, out);
    }

    private void code_lload(int lvar, DataOutputStream out)
                    throws IOException {
        codeLocalLoadStore(lvar, opc_lload, opc_lload_0, out);
    }

    private void code_fload(int lvar, DataOutputStream out)
                    throws IOException {
        codeLocalLoadStore(lvar, opc_fload, opc_fload_0, out);
    }

    private void code_dload(int lvar, DataOutputStream out)
                    throws IOException {
        codeLocalLoadStore(lvar, opc_dload, opc_dload_0, out);
    }

    private void code_aload(int lvar, DataOutputStream out)
                    throws IOException {
        codeLocalLoadStore(lvar, opc_aload, opc_aload_0, out);
    }

// private void code_istore(int lvar, DataOutputStream out)
// throws IOException
// {
// codeLocalLoadStore(lvar, opc_istore, opc_istore_0, out);
// }

// private void code_lstore(int lvar, DataOutputStream out)
// throws IOException
// {
// codeLocalLoadStore(lvar, opc_lstore, opc_lstore_0, out);
// }

// private void code_fstore(int lvar, DataOutputStream out)
// throws IOException
// {
// codeLocalLoadStore(lvar, opc_fstore, opc_fstore_0, out);
// }

// private void code_dstore(int lvar, DataOutputStream out)
// throws IOException
// {
// codeLocalLoadStore(lvar, opc_dstore, opc_dstore_0, out);
// }

    private void code_astore(int lvar, DataOutputStream out)
                    throws IOException {
        codeLocalLoadStore(lvar, opc_astore, opc_astore_0, out);
    }

    /**
     * Generate code for a load or store instruction for the given local variable. The code is
     * written to the supplied stream.
     *
     * "opcode" indicates the opcode form of the desired load or store instruction that takes an
     * explicit local variable index, and "opcode_0" indicates the corresponding form of the
     * instruction with the implicit index 0.
     */
    private static void codeLocalLoadStore(int lvar, int opcode, int opcode_0,
                    DataOutputStream out)
                    throws IOException {
        assert lvar >= 0 && lvar <= 0xFFFF;
        if (lvar <= 3) {
            out.writeByte(opcode_0 + lvar);
        } else if (lvar <= 0xFF) {
            out.writeByte(opcode);
            out.writeByte(lvar & 0xFF);
        } else {
            /*
             * Use the "wide" instruction modifier for local variable indexes that do not fit into
             * an unsigned byte.
             */
            out.writeByte(opc_wide);
            out.writeByte(opcode);
            out.writeShort(lvar & 0xFFFF);
        }
    }

    /**
     * Generate code for an "ldc" instruction for the given constant pool index (the "ldc_w"
     * instruction is used if the index does not fit into an unsigned byte). The code is written to
     * the supplied stream.
     */
    private static void code_ldc(int index, DataOutputStream out)
                    throws IOException {
        assert index >= 0 && index <= 0xFFFF;
        if (index <= 0xFF) {
            out.writeByte(opc_ldc);
            out.writeByte(index & 0xFF);
        } else {
            out.writeByte(opc_ldc_w);
            out.writeShort(index & 0xFFFF);
        }
    }

    /**
     * Generate code to push a constant integer value on to the operand stack, using the
     * "iconst_<i>", "bipush", or "sipush" instructions depending on the size of the value. The code
     * is written to the supplied stream.
     */
    private void code_ipush(int value, DataOutputStream out)
                    throws IOException {
        if (value >= -1 && value <= 5) {
            out.writeByte(opc_iconst_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            out.writeByte(opc_bipush);
            out.writeByte(value & 0xFF);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            out.writeByte(opc_sipush);
            out.writeShort(value & 0xFFFF);
        } else {
            throw new AssertionError();
        }
    }

    /*
     * ==================== General Utility Methods ====================
     */

    /**
     * Convert a fully qualified class name that uses '.' as the package separator, the external
     * representation used by the Java language and APIs, to a fully qualified class name that uses
     * '/' as the package separator, the representation used in the class file format (see JVMS
     * section 4.2).
     */
    private static String dotToSlash(String name) {
        return name.replace('.', '/');
    }

    /**
     * Return the "method descriptor" string for a method with the given parameter types and return
     * type. See JVMS section 4.3.3.
     */
    private String getMethodDescriptor(Klass[] parameterTypes,
                    Klass returnType) {
        return getParameterDescriptors(parameterTypes) +
                        ((returnType == context.getMeta()._void) ? "V" : getFieldType(returnType));
    }

    /**
     * Return the list of "parameter descriptor" strings enclosed in parentheses corresponding to
     * the given parameter types (in other words, a method descriptor without a return descriptor).
     * This string is useful for constructing string keys for methods without regard to their return
     * type.
     */
    private String getParameterDescriptors(Klass[] parameterTypes) {
        StringBuilder desc = new StringBuilder("(");
        for (int i = 0; i < parameterTypes.length; i++) {
            desc.append(getFieldType(parameterTypes[i]));
        }
        desc.append(')');
        return desc.toString();
    }

    /**
     * Return the "field type" string for the given type, appropriate for a field descriptor, a
     * parameter descriptor, or a return descriptor other than "void". See JVMS section 4.3.2.
     */
    private String getFieldType(Klass type) {
        if (type.isPrimitive()) {
            return PrimitiveTypeInfo.get(context, type).baseTypeString;
        } else if (type.isArray()) {
            /*
             * According to JLS 20.3.2, the getName() method on Class does return the VM type
             * descriptor format for array classes (only); using that should be quicker than the
             * otherwise obvious code:
             *
             * return "[" + getTypeDescriptor(type.getComponentType());
             */
            return type.getNameAsString().replace('.', '/');
        } else {
            return "L" + dotToSlash(type.getNameAsString()) + ";";
        }
    }

    /**
     * Returns a human-readable string representing the signature of a method with the given name
     * and parameter types.
     */
    private static String getFriendlyMethodSignature(String name,
                    Klass[] parameterTypes) {
        StringBuilder sig = new StringBuilder(name);
        sig.append('(');
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                sig.append(',');
            }
            Klass parameterType = parameterTypes[i];
            int dimensions = 0;
            while (parameterType.isArray()) {
                parameterType = parameterType.getElementalType();
                dimensions++;
            }
            sig.append(parameterType.getName());
            while (dimensions-- > 0) {
                sig.append("[]");
            }
        }
        sig.append(')');
        return sig.toString();
    }

    /**
     * Return the number of abstract "words", or consecutive local variable indexes, required to
     * contain a value of the given type. See JVMS section 3.6.1.
     *
     * Note that the original version of the JVMS contained a definition of this abstract notion of
     * a "word" in section 3.4, but that definition was removed for the second edition.
     */
    private int getWordsPerType(Klass type) {
        if (type == context.getMeta()._long || type == context.getMeta()._double) {
            return 2;
        } else {
            return 1;
        }
    }

    /**
     * Add to the given list all of the types in the "from" array that are not already contained in
     * the list and are assignable to at least one of the types in the "with" array.
     *
     * This method is useful for computing the greatest common set of declared exceptions from
     * duplicate methods inherited from different interfaces.
     */
    private static void collectCompatibleTypes(Klass[] from,
                    Klass[] with,
                    List<Klass> list) {
        for (Klass fc : from) {
            if (!list.contains(fc)) {
                for (Klass wc : with) {
                    if (wc.isAssignableFrom(fc)) {
                        list.add(fc);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Given the exceptions declared in the throws clause of a proxy method, compute the exceptions
     * that need to be caught from the invocation handler's invoke method and rethrown intact in the
     * method's implementation before catching other Throwables and wrapping them in
     * UndeclaredThrowableExceptions.
     *
     * The exceptions to be caught are returned in a List object. Each exception in the returned
     * list is guaranteed to not be a subclass of any of the other exceptions in the list, so the
     * catch blocks for these exceptions may be generated in any order relative to each other.
     *
     * Error and RuntimeException are each always contained by the returned list (if none of their
     * superclasses are contained), since those unchecked exceptions should always be rethrown
     * intact, and thus their subclasses will never appear in the returned list.
     *
     * The returned List will be empty if java.lang.Throwable is in the given list of declared
     * exceptions, indicating that no exceptions need to be caught.
     */
    private List<Klass> computeUniqueCatchList(Klass[] exceptions) {
        List<Klass> uniqueList = new ArrayList<>();
        // unique exceptions to catch

        uniqueList.add(context.getMeta().java_lang_Error);            // always catch/rethrow these
        uniqueList.add(context.getMeta().java_lang_RuntimeException);

        nextException: for (Klass ex : exceptions) {
            if (ex.isAssignableFrom(context.getMeta().java_lang_Throwable)) {
                /*
                 * If Throwable is declared to be thrown by the proxy method, then no catch blocks
                 * are necessary, because the invoke can, at most, throw Throwable anyway.
                 */
                uniqueList.clear();
                break;
            } else if (!context.getMeta().java_lang_Throwable.isAssignableFrom(ex)) {
                /*
                 * Ignore types that cannot be thrown by the invoke method.
                 */
                continue;
            }
            /*
             * Compare this exception against the current list of exceptions that need to be caught:
             */
            for (int j = 0; j < uniqueList.size();) {
                Klass ex2 = uniqueList.get(j);
                if (ex2.isAssignableFrom(ex)) {
                    /*
                     * if a superclass of this exception is already on the list to catch, then
                     * ignore this one and continue;
                     */
                    continue nextException;
                } else if (ex.isAssignableFrom(ex2)) {
                    /*
                     * if a subclass of this exception is on the list to catch, then remove it;
                     */
                    uniqueList.remove(j);
                } else {
                    j++;        // else continue comparing.
                }
            }
            // This exception is unique (so far): add it to the list to catch.
            uniqueList.add(ex);
        }
        return uniqueList;
    }

    /**
     * A PrimitiveTypeInfo object contains assorted information about a primitive type in its public
     * fields. The struct for a particular primitive type can be obtained using the static "get"
     * method.
     */
    private static class PrimitiveTypeInfo {

        /** "base type" used in various descriptors (see JVMS section 4.3.2) */
        public String baseTypeString;

        /** name of corresponding wrapper class */
        public String wrapperClassName;

        /** method descriptor for wrapper class "valueOf" factory method */
        public String wrapperValueOfDesc;

        /** name of wrapper class method for retrieving primitive value */
        public String unwrapMethodName;

        /** descriptor of same method */
        public String unwrapMethodDesc;

        private static Map<EspressoContext, Map<Klass, PrimitiveTypeInfo>> map = new HashMap<>();

        private static Map<Klass, PrimitiveTypeInfo> buildTable(EspressoContext context) {
            Map<Klass, PrimitiveTypeInfo> table = new HashMap<>(8);
            table.put(context.getMeta()._byte, new PrimitiveTypeInfo(byte.class, context.getMeta().java_lang_Byte));
            table.put(context.getMeta()._char, new PrimitiveTypeInfo(char.class, context.getMeta().java_lang_Character));
            table.put(context.getMeta()._double, new PrimitiveTypeInfo(double.class, context.getMeta().java_lang_Double));
            table.put(context.getMeta()._float, new PrimitiveTypeInfo(float.class, context.getMeta().java_lang_Float));
            table.put(context.getMeta()._int, new PrimitiveTypeInfo(int.class, context.getMeta().java_lang_Integer));
            table.put(context.getMeta()._long, new PrimitiveTypeInfo(long.class, context.getMeta().java_lang_Long));
            table.put(context.getMeta()._short, new PrimitiveTypeInfo(short.class, context.getMeta().java_lang_Short));
            table.put(context.getMeta()._boolean, new PrimitiveTypeInfo(boolean.class, context.getMeta().java_lang_Boolean));
            return table;
        }

        private PrimitiveTypeInfo(Class<?> primitiveClass, Klass wrapperClass) {
            assert primitiveClass.isPrimitive();

            baseTypeString = Array.newInstance(primitiveClass, 0).getClass().getName().substring(1);
            wrapperClassName = dotToSlash(wrapperClass.getNameAsString());
            wrapperValueOfDesc = "(" + baseTypeString + ")L" + wrapperClassName + ";";
            unwrapMethodName = primitiveClass.getName() + "Value";
            unwrapMethodDesc = "()" + baseTypeString;
        }

        @TruffleBoundary
        private static PrimitiveTypeInfo get(EspressoContext context, Klass cl) {
            if (map.containsKey(context)) {
                return map.get(context).get(cl);
            }
            return buildTable(context).get(cl);
        }
    }

    /**
     * A ConstantPool object represents the constant pool of a class file being generated. This
     * representation of a constant pool is designed specifically for use by ProxyGenerator; in
     * particular, it assumes that constant pool entries will not need to be resorted (for example,
     * by their type, as the Java compiler does), so that the final index value can be assigned and
     * used when an entry is first created.
     *
     * Note that new entries cannot be created after the constant pool has been written to a class
     * file. To prevent such logic errors, a ConstantPool instance can be marked "read only", so
     * that further attempts to add new entries will fail with a runtime exception.
     *
     * See JVMS section 4.4 for more information about the constant pool of a class file.
     */
    private static class ConstantPool {

        /**
         * list of constant pool entries, in constant pool index order.
         *
         * This list is used when writing the constant pool to a stream and for assigning the next
         * index value. Note that element 0 of this list corresponds to constant pool index 1.
         */
        private List<ConstantPool.Entry> pool = new ArrayList<>(32);

        /**
         * maps constant pool data of all types to constant pool indexes.
         *
         * This map is used to look up the index of an existing entry for values of all types.
         */
        private Map<Object, Integer> map = new HashMap<>(16);

        /** true if no new constant pool entries may be added */
        private boolean readOnly = false;

        /**
         * Get or assign the index for a CONSTANT_Utf8 entry.
         */
        public short getUtf8(String s) {
            if (s == null) {
                throw new NullPointerException();
            }
            return getValue(s);
        }

        /**
         * Get or assign the index for a CONSTANT_Class entry.
         */
        public short getClass(String name) {
            short utf8Index = getUtf8(name);
            return getIndirect(new ConstantPool.IndirectEntry(
                            CONSTANT_CLASS, utf8Index));
        }

        /**
         * Get or assign the index for a CONSTANT_String entry.
         */
        public short getString(String s) {
            short utf8Index = getUtf8(s);
            return getIndirect(new ConstantPool.IndirectEntry(
                            CONSTANT_STRING, utf8Index));
        }

        /**
         * Get or assign the index for a CONSTANT_FieldRef entry.
         */
        public short getFieldRef(String className,
                        String name, String descriptor) {
            short classIndex = getClass(className);
            short nameAndTypeIndex = getNameAndType(name, descriptor);
            return getIndirect(new ConstantPool.IndirectEntry(
                            CONSTANT_FIELD, classIndex, nameAndTypeIndex));
        }

        /**
         * Get or assign the index for a CONSTANT_MethodRef entry.
         */
        public short getMethodRef(String className,
                        String name, String descriptor) {
            short classIndex = getClass(className);
            short nameAndTypeIndex = getNameAndType(name, descriptor);
            return getIndirect(new ConstantPool.IndirectEntry(
                            CONSTANT_METHOD, classIndex, nameAndTypeIndex));
        }

        /**
         * Get or assign the index for a CONSTANT_NameAndType entry.
         */
        public short getNameAndType(String name, String descriptor) {
            short nameIndex = getUtf8(name);
            short descriptorIndex = getUtf8(descriptor);
            return getIndirect(new ConstantPool.IndirectEntry(
                            CONSTANT_NAMEANDTYPE, nameIndex, descriptorIndex));
        }

        /**
         * Set this ConstantPool instance to be "read only".
         *
         * After this method has been called, further requests to get an index for a non-existent
         * entry will cause an InternalError to be thrown instead of creating of the entry.
         */
        public void setReadOnly() {
            readOnly = true;
        }

        /**
         * Write this constant pool to a stream as part of the class file format.
         *
         * This consists of writing the "constant_pool_count" and "constant_pool[]" items of the
         * "ClassFile" structure, as described in JVMS section 4.1.
         */
        public void write(OutputStream out) throws IOException {
            DataOutputStream dataOut = new DataOutputStream(out);

            // constant_pool_count: number of entries plus one
            dataOut.writeShort(pool.size() + 1);

            for (ConstantPool.Entry e : pool) {
                e.write(dataOut);
            }
        }

        /**
         * Add a new constant pool entry and return its index.
         */
        private short addEntry(ConstantPool.Entry entry) {
            pool.add(entry);
            /*
             * Note that this way of determining the index of the added entry is wrong if this pool
             * supports CONSTANT_Long or CONSTANT_Double entries.
             */
            if (pool.size() >= 65535) {
                throw new IllegalArgumentException(
                                "constant pool size limit exceeded");
            }
            return (short) pool.size();
        }

        /**
         * Get or assign the index for an entry of a type that contains a direct value. The type of
         * the given object determines the type of the desired entry as follows:
         *
         * java.lang.String CONSTANT_Utf8 java.lang.Integer CONSTANT_Integer java.lang.Float
         * CONSTANT_Float java.lang.Long CONSTANT_Long java.lang.Double CONSTANT_DOUBLE
         */
        private short getValue(Object key) {
            Integer index = map.get(key);
            if (index != null) {
                return index.shortValue();
            } else {
                if (readOnly) {
                    throw new InternalError(
                                    "late constant pool addition: " + key);
                }
                short i = addEntry(new ConstantPool.ValueEntry(key));
                map.put(key, (int) i);
                return i;
            }
        }

        /**
         * Get or assign the index for an entry of a type that contains references to other constant
         * pool entries.
         */
        private short getIndirect(ConstantPool.IndirectEntry e) {
            Integer index = map.get(e);
            if (index != null) {
                return index.shortValue();
            } else {
                if (readOnly) {
                    throw new InternalError("late constant pool addition");
                }
                short i = addEntry(e);
                map.put(e, (int) i);
                return i;
            }
        }

        /**
         * Entry is the abstact superclass of all constant pool entry types that can be stored in
         * the "pool" list; its purpose is to define a common method for writing constant pool
         * entries to a class file.
         */
        private abstract static class Entry {
            public abstract void write(DataOutputStream out)
                            throws IOException;
        }

        /**
         * ValueEntry represents a constant pool entry of a type that contains a direct value (see
         * the comments for the "getValue" method for a list of such types).
         *
         * ValueEntry objects are not used as keys for their entries in the Map "map", so no useful
         * hashCode or equals methods are defined.
         */
        private static class ValueEntry extends ConstantPool.Entry {
            private Object value;

            public ValueEntry(Object value) {
                this.value = value;
            }

            @Override
            public void write(DataOutputStream out) throws IOException {
                if (value instanceof String) {
                    out.writeByte(CONSTANT_UTF8);
                    out.writeUTF((String) value);
                } else if (value instanceof Integer) {
                    out.writeByte(CONSTANT_INTEGER);
                    out.writeInt(((Integer) value).intValue());
                } else if (value instanceof Float) {
                    out.writeByte(CONSTANT_FLOAT);
                    out.writeFloat(((Float) value).floatValue());
                } else if (value instanceof Long) {
                    out.writeByte(CONSTANT_LONG);
                    out.writeLong(((Long) value).longValue());
                } else if (value instanceof Double) {
                    out.writeDouble(CONSTANT_DOUBLE);
                    out.writeDouble(((Double) value).doubleValue());
                } else {
                    throw new InternalError("bogus value entry: " + value);
                }
            }
        }

        /**
         * IndirectEntry represents a constant pool entry of a type that references other constant
         * pool entries, i.e., the following types:
         *
         * CONSTANT_Class, CONSTANT_String, CONSTANT_Fieldref, CONSTANT_Methodref,
         * CONSTANT_InterfaceMethodref, and CONSTANT_NameAndType.
         *
         * Each of these entry types contains either one or two indexes of other constant pool
         * entries.
         *
         * IndirectEntry objects are used as the keys for their entries in the Map "map", so the
         * hashCode and equals methods are overridden to allow matching.
         */
        private static class IndirectEntry extends ConstantPool.Entry {
            private int tag;
            private short index0;
            private short index1;

            /**
             * Construct an IndirectEntry for a constant pool entry type that contains one index of
             * another entry.
             */
            public IndirectEntry(int tag, short index) {
                this.tag = tag;
                this.index0 = index;
                this.index1 = 0;
            }

            /**
             * Construct an IndirectEntry for a constant pool entry type that contains two indexes
             * for other entries.
             */
            public IndirectEntry(int tag, short index0, short index1) {
                this.tag = tag;
                this.index0 = index0;
                this.index1 = index1;
            }

            @Override
            public void write(DataOutputStream out) throws IOException {
                out.writeByte(tag);
                out.writeShort(index0);
                /*
                 * If this entry type contains two indexes, write out the second, too.
                 */
                if (tag == CONSTANT_FIELD ||
                                tag == CONSTANT_METHOD ||
                                tag == CONSTANT_INTERFACEMETHOD ||
                                tag == CONSTANT_NAMEANDTYPE) {
                    out.writeShort(index1);
                }
            }

            @Override
            public int hashCode() {
                return tag + index0 + index1;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof ConstantPool.IndirectEntry) {
                    ConstantPool.IndirectEntry other = (ConstantPool.IndirectEntry) obj;
                    if (tag == other.tag &&
                                    index0 == other.index0 && index1 == other.index1) {
                        return true;
                    }
                }
                return false;
            }
        }
    }
}
