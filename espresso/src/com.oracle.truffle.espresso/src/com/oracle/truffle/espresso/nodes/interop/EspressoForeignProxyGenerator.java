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
import com.oracle.truffle.api.impl.asm.ClassWriter;
import com.oracle.truffle.api.impl.asm.Label;
import com.oracle.truffle.api.impl.asm.MethodVisitor;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;

import static com.oracle.truffle.api.impl.asm.Opcodes.*;

/**
 * EspressoForeignProxyGenerator contains the code to generate a dynamic Espresso proxy class for
 * foreign objects.
 *
 * Large parts of this class is copied from java.lang.reflect.ProxyGenerator.
 */
public final class EspressoForeignProxyGenerator extends ClassWriter {

    private static final String JL_OBJECT = "java/lang/Object";
    private static final String JL_THROWABLE = "java/lang/Throwable";
    private static final String JLR_UNDECLARED_THROWABLE_EX = "java/lang/reflect/UndeclaredThrowableException";
    private static final int VARARGS = 0x00000080;

    /* name of the superclass of proxy classes */
    private static final String superclassName = "java/lang/Object";

    private final EspressoContext context;

    /* name of proxy class */
    private String className;

    /* proxy interfaces */
    private ObjectKlass[] interfaces;

    /* proxy class access flags */
    private int accessFlags;

    /*
     * Maps method signature string to list of ProxyMethod objects for proxy methods with that
     * signature.
     */
    private Map<String, List<ProxyMethod>> proxyMethods = new HashMap<>();

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
        super(ClassWriter.COMPUTE_FRAMES);
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
        visit(V1_8, accessFlags, dotToSlash(className), null,
                        superclassName, typeNames(interfaces));

        /*
         * Add proxy methods for the hashCode, equals, and toString methods of java.lang.Object.
         * This is done before the methods from the proxy interfaces so that the methods from
         * java.lang.Object take precedence over duplicate methods in the proxy interfaces.
         */
        // since interop protocol doesn't (yet) have methods for equals
        // and hashCode, we simply use standard invokeMember delegation
        addProxyMethod(context.getMeta().java_lang_Object_hashCode);
        addProxyMethod(context.getMeta().java_lang_Object_equals);

        // toString is implemented by interop protocol by means of
        // toDisplayString and asString, so we use those
        generateToStringMethod();

        /*
         * Accumulate all of the methods from the proxy interfaces.
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

        generateConstructor();

        for (List<ProxyMethod> sigmethods : proxyMethods.values()) {
            for (ProxyMethod pm : sigmethods) {
                // Generate code for proxy method
                pm.generateMethod(this);
            }
        }

        return toByteArray();
    }

    private void generateToStringMethod() {
        MethodVisitor mv = visitMethod(ACC_PUBLIC | ACC_FINAL,
                        "toString", "()Ljava/lang/String;", null,
                        null);

        mv.visitCode();
        Label L_startBlock = new Label();
        Label L_endBlock = new Label();
        Label L_RuntimeHandler = new Label();
        Label L_ThrowableHandler = new Label();

        mv.visitLabel(L_startBlock);

        mv.visitVarInsn(ALOAD, 0);

        mv.visitMethodInsn(INVOKESTATIC, "com/oracle/truffle/espresso/polyglot/Interop",
                        "toDisplayString",
                        "(Ljava/lang/Object;)Ljava/lang/Object;",
                        false);

        mv.visitMethodInsn(INVOKESTATIC, "com/oracle/truffle/espresso/polyglot/Interop",
                        "asString",
                        "(Ljava/lang/Object;)Ljava/lang/String;",
                        false);

        mv.visitInsn(ARETURN);

        mv.visitLabel(L_endBlock);

        // Generate exception handler
        mv.visitLabel(L_RuntimeHandler);
        mv.visitInsn(ATHROW);   // just rethrow the exception

        mv.visitLabel(L_ThrowableHandler);
        mv.visitVarInsn(ASTORE, 1);
        mv.visitTypeInsn(NEW, JLR_UNDECLARED_THROWABLE_EX);
        mv.visitInsn(DUP);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKESPECIAL, JLR_UNDECLARED_THROWABLE_EX,
                        "<init>", "(Ljava/lang/Throwable;)V", false);
        mv.visitInsn(ATHROW);
        // Maxs computed by ClassWriter.COMPUTE_FRAMES, these arguments ignored
        mv.visitMaxs(-1, -1);
        mv.visitEnd();
    }

    /**
     * Return an array of the class and interface names from an array of Classes.
     *
     * @param classes an array of classes or interfaces
     * @return the array of class and interface names; or null if classes is null or empty
     */
    private static String[] typeNames(Klass[] classes) {
        if (classes == null || classes.length == 0)
            return null;
        int size = classes.length;
        String[] ifaces = new String[size];
        for (int i = 0; i < size; i++)
            ifaces[i] = dotToSlash(classes[i].getNameAsString());
        return ifaces;
    }

    /**
     * Generate the constructor method for the proxy class.
     */
    private void generateConstructor() {
        MethodVisitor ctor = visitMethod(Modifier.PUBLIC, "<init>",
                        "()V", null, null);
        ctor.visitParameter(null, 0);
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, superclassName, "<init>",
                        "()V", false);
        ctor.visitInsn(RETURN);

        // Maxs computed by ClassWriter.COMPUTE_FRAMES, these arguments ignored
        ctor.visitMaxs(-1, -1);
        ctor.visitEnd();
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
                        exceptionTypes, isVarArgs(m.getModifiers())));
    }

    private static boolean isVarArgs(int modifiers) {
        return (modifiers & VARARGS) != 0;
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
     * A ProxyMethod object represents a proxy method in the proxy class being generated: a method
     * whose implementation will encode and dispatch invocations to the proxy instance's invocation
     * handler.
     */
    private final class ProxyMethod {

        public String methodName;
        public Klass[] parameterTypes;
        public Klass returnType;
        public Klass[] exceptionTypes;
        boolean isVarArgs;

        private ProxyMethod(String methodName, Klass[] parameterTypes,
                        Klass returnType, Klass[] exceptionTypes, boolean isVarArgs) {
            this.methodName = methodName;
            this.parameterTypes = parameterTypes;
            this.returnType = returnType;
            this.exceptionTypes = exceptionTypes;
            this.isVarArgs = isVarArgs;
        }

        private void generateMethod(ClassWriter cw) {
            String desc = getMethodDescriptor(parameterTypes, returnType);
            int methodAccess = ACC_PUBLIC | ACC_FINAL;

            if (isVarArgs) {
                methodAccess |= ACC_VARARGS;
            }

            MethodVisitor mv = cw.visitMethod(methodAccess,
                            methodName, desc, null,
                            typeNames(exceptionTypes));

            int[] parameterSlot = new int[parameterTypes.length];
            int nextSlot = 1;
            for (int i = 0; i < parameterSlot.length; i++) {
                parameterSlot[i] = nextSlot;
                nextSlot += getWordsPerType(parameterTypes[i]);
            }

            mv.visitCode();
            Label L_startBlock = new Label();
            Label L_endBlock = new Label();
            Label L_RuntimeHandler = new Label();
            Label L_ThrowableHandler = new Label();

            List<Klass> catchList = computeUniqueCatchList(exceptionTypes);
            if (catchList.size() > 0) {
                for (Klass ex : catchList) {
                    mv.visitTryCatchBlock(L_startBlock, L_endBlock, L_RuntimeHandler,
                                    dotToSlash(ex.getNameAsString()));
                }

                mv.visitTryCatchBlock(L_startBlock, L_endBlock, L_ThrowableHandler,
                                JL_THROWABLE);
            }
            mv.visitLabel(L_startBlock);

            mv.visitVarInsn(ALOAD, 0);
            mv.visitLdcInsn(methodName);

            if (parameterTypes.length > 0) {
                // Create an array and fill with the parameters converting primitives to wrappers
                emitIconstInsn(mv, parameterTypes.length);
                mv.visitTypeInsn(ANEWARRAY, JL_OBJECT);
                for (int i = 0; i < parameterTypes.length; i++) {
                    mv.visitInsn(DUP);
                    emitIconstInsn(mv, i);
                    codeWrapArgument(mv, parameterTypes[i], parameterSlot[i]);
                    mv.visitInsn(AASTORE);
                }
            } else {
                mv.visitInsn(ICONST_0);
                mv.visitTypeInsn(ANEWARRAY, JL_OBJECT);
            }

            mv.visitMethodInsn(INVOKESTATIC, "com/oracle/truffle/espresso/polyglot/Interop",
                            "invokeMember",
                            "(Ljava/lang/Object;Ljava/lang/String;" +
                                            "[Ljava/lang/Object;)Ljava/lang/Object;",
                            false);

            if (returnType == context.getMeta()._void) {
                mv.visitInsn(POP);
                mv.visitInsn(RETURN);
            } else {
                codeUnwrapReturnValue(mv, returnType);
            }

            mv.visitLabel(L_endBlock);

            // Generate exception handler
            mv.visitLabel(L_RuntimeHandler);
            mv.visitInsn(ATHROW);   // just rethrow the exception

            mv.visitLabel(L_ThrowableHandler);
            mv.visitVarInsn(ASTORE, 1);
            mv.visitTypeInsn(NEW, JLR_UNDECLARED_THROWABLE_EX);
            mv.visitInsn(DUP);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKESPECIAL, JLR_UNDECLARED_THROWABLE_EX,
                            "<init>", "(Ljava/lang/Throwable;)V", false);
            mv.visitInsn(ATHROW);
            // Maxs computed by ClassWriter.COMPUTE_FRAMES, these arguments ignored
            mv.visitMaxs(-1, -1);
            mv.visitEnd();
        }

        /**
         * Generate code for wrapping an argument of the given type whose value can be found at the
         * specified local variable index, in order for it to be passed (as an Object) to the
         * invocation handler's "invoke" method.
         */
        private void codeWrapArgument(MethodVisitor mv, Klass type, int slot) {
            if (type.isPrimitive()) {
                PrimitiveTypeInfo prim = PrimitiveTypeInfo.get(context, type);

                if (type == context.getMeta()._int ||
                                type == context.getMeta()._boolean ||
                                type == context.getMeta()._byte ||
                                type == context.getMeta()._char ||
                                type == context.getMeta()._short) {
                    mv.visitVarInsn(ILOAD, slot);
                } else if (type == context.getMeta()._long) {
                    mv.visitVarInsn(LLOAD, slot);
                } else if (type == context.getMeta()._float) {
                    mv.visitVarInsn(FLOAD, slot);
                } else if (type == context.getMeta()._double) {
                    mv.visitVarInsn(DLOAD, slot);
                } else {
                    throw new AssertionError();
                }
                mv.visitMethodInsn(INVOKESTATIC, prim.wrapperClassName, "valueOf",
                                prim.wrapperValueOfDesc, false);
            } else {
                mv.visitVarInsn(ALOAD, slot);
            }
        }

        /**
         * Generate code for unwrapping a return value of the given type from the invocation
         * handler's "invoke" method (as type Object) to its correct type.
         */
        private void codeUnwrapReturnValue(MethodVisitor mv, Klass type) {
            if (type.isPrimitive()) {
                PrimitiveTypeInfo prim = PrimitiveTypeInfo.get(context, type);

                mv.visitTypeInsn(CHECKCAST, prim.wrapperClassName);
                mv.visitMethodInsn(INVOKEVIRTUAL,
                                prim.wrapperClassName,
                                prim.unwrapMethodName, prim.unwrapMethodDesc, false);

                if (type == context.getMeta()._int ||
                                type == context.getMeta()._boolean ||
                                type == context.getMeta()._byte ||
                                type == context.getMeta()._char ||
                                type == context.getMeta()._short) {
                    mv.visitInsn(IRETURN);
                } else if (type == context.getMeta()._long) {
                    mv.visitInsn(LRETURN);
                } else if (type == context.getMeta()._float) {
                    mv.visitInsn(FRETURN);
                } else if (type == context.getMeta()._double) {
                    mv.visitInsn(DRETURN);
                } else {
                    throw new AssertionError();
                }
            } else {
                mv.visitTypeInsn(CHECKCAST, dotToSlash(type.getNameAsString()));
                mv.visitInsn(ARETURN);
            }
        }

        /*
         * =============== Code Generation Utility Methods ===============
         */

        /**
         * Visit a bytecode for a constant.
         *
         * @param mv The MethodVisitor
         * @param cst The constant value
         */
        private void emitIconstInsn(MethodVisitor mv, final int cst) {
            if (cst >= -1 && cst <= 5) {
                mv.visitInsn(ICONST_0 + cst);
            } else if (cst >= Byte.MIN_VALUE && cst <= Byte.MAX_VALUE) {
                mv.visitIntInsn(BIPUSH, cst);
            } else if (cst >= Short.MIN_VALUE && cst <= Short.MAX_VALUE) {
                mv.visitIntInsn(SIPUSH, cst);
            } else {
                mv.visitLdcInsn(cst);
            }
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
    private static final class PrimitiveTypeInfo {

        /* "base type" used in various descriptors (see JVMS section 4.3.2) */
        public String baseTypeString;

        /* name of corresponding wrapper class */
        public String wrapperClassName;

        /* method descriptor for wrapper class "valueOf" factory method */
        public String wrapperValueOfDesc;

        /* name of wrapper class method for retrieving primitive value */
        public String unwrapMethodName;

        /* descriptor of same method */
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
}
