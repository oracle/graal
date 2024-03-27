/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.host;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.graalvm.collections.EconomicSet;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.host.HostAdapterFactory.AdapterResult;

final class HostInteropReflect {
    static final Object[] EMPTY = {};
    static final String STATIC_TO_CLASS = "class";
    static final String CLASS_TO_STATIC = "static";
    static final String ADAPTER_SUPER_MEMBER = "super";
    static final String ADAPTER_DELEGATE_MEMBER = "this";

    private HostInteropReflect() {
    }

    @CompilerDirectives.TruffleBoundary
    static Class<?> findInnerClass(Class<?> clazz, String name) {
        if (Modifier.isPublic(clazz.getModifiers())) {
            for (Class<?> t : clazz.getClasses()) {
                // no support for non-static type members now
                if (isStaticTypeOrInterface(t) && t.getSimpleName().equals(name)) {
                    return t;
                }
            }
        }
        return null;
    }

    private static boolean isSignature(String name) {
        return name.length() > 0 && name.charAt(name.length() - 1) == ')' && name.indexOf('(') != -1;
    }

    private static boolean isJNIName(String name) {
        return name.contains("__");
    }

    @CompilerDirectives.TruffleBoundary
    static HostMethodDesc findMethod(HostContext context, Class<?> clazz, String name, boolean onlyStatic) {
        HostClassDesc classDesc = HostClassDesc.forClass(context, clazz);
        HostMethodDesc foundMethod = classDesc.lookupMethod(name, onlyStatic);
        if (foundMethod == null && isSignature(name)) {
            foundMethod = classDesc.lookupMethodBySignature(name, onlyStatic);
        }
        if (foundMethod == null && isJNIName(name)) {
            foundMethod = classDesc.lookupMethodByJNIName(name, onlyStatic);
        }
        return foundMethod;
    }

    @CompilerDirectives.TruffleBoundary
    static HostFieldDesc findField(HostContext context, Class<?> clazz, String name, boolean onlyStatic) {
        HostClassDesc classDesc = HostClassDesc.forClass(context, clazz);
        return classDesc.lookupField(name, onlyStatic);
    }

    private static Method functionalInterfaceMethod(Class<?> functionalInterface) {
        if (!functionalInterface.isInterface()) {
            return null;
        }
        Method found = null;
        for (Method m : functionalInterface.getMethods()) {
            if (Modifier.isAbstract(m.getModifiers()) && !HostClassDesc.isObjectMethodOverride(m)) {
                if (found != null) {
                    return null;
                }
                found = m;
            }
        }
        return found;
    }

    @CompilerDirectives.TruffleBoundary
    static boolean isFunctionalInterface(Class<?> type) {
        if (!type.isInterface() || type == TruffleObject.class) {
            return false;
        }
        if (type.getAnnotation(FunctionalInterface.class) != null) {
            return true;
        } else if (functionalInterfaceMethod(type) != null) {
            return true;
        }
        return false;
    }

    @TruffleBoundary
    static boolean isReadable(HostObject object, Class<?> clazz, String name, boolean onlyStatic, boolean isClass) {
        HostClassDesc classDesc = HostClassDesc.forClass(object.context, clazz);
        HostMethodDesc foundMethod = classDesc.lookupMethod(name, onlyStatic);
        if (foundMethod != null) {
            return true;
        } else if (isSignature(name)) {
            foundMethod = classDesc.lookupMethodBySignature(name, onlyStatic);
            if (foundMethod != null) {
                return true;
            }
        } else if (isJNIName(name)) {
            foundMethod = classDesc.lookupMethodByJNIName(name, onlyStatic);
            if (foundMethod != null) {
                return true;
            }
        }

        HostFieldDesc foundField = classDesc.lookupField(name, onlyStatic);
        if (foundField != null) {
            return true;
        }

        if (onlyStatic) {
            if (STATIC_TO_CLASS.equals(name)) {
                return true;
            }
            Class<?> innerClass = findInnerClass(clazz, name);
            if (innerClass != null) {
                return true;
            }
        }
        if (isClass) {
            if (CLASS_TO_STATIC.equals(name)) {
                return true;
            }
        }
        return false;
    }

    @TruffleBoundary
    static boolean isModifiable(HostObject object, Class<?> clazz, String name, boolean onlyStatic) {
        HostClassDesc classDesc = HostClassDesc.forClass(object.context, clazz);
        HostFieldDesc foundField = classDesc.lookupField(name, onlyStatic);
        if (foundField != null && !foundField.isFinal()) {
            return true;
        }
        return false;
    }

    @TruffleBoundary
    static boolean isInvokable(HostObject object, Class<?> clazz, String name, boolean onlyStatic) {
        HostClassDesc classDesc = HostClassDesc.forClass(object.context, clazz);
        HostMethodDesc foundMethod = classDesc.lookupMethod(name, onlyStatic);
        if (foundMethod != null) {
            return true;
        } else if (isSignature(name)) {
            foundMethod = classDesc.lookupMethodBySignature(name, onlyStatic);
            if (foundMethod != null) {
                return true;
            }
        } else if (isJNIName(name)) {
            foundMethod = classDesc.lookupMethodByJNIName(name, onlyStatic);
            if (foundMethod != null) {
                return true;
            }
        }
        return false;
    }

    @TruffleBoundary
    static boolean isInternal(HostObject object, Class<?> clazz, String name, boolean onlyStatic) {
        HostClassDesc classDesc = HostClassDesc.forClass(object.context, clazz);
        HostMethodDesc foundMethod = classDesc.lookupMethod(name, onlyStatic);
        if (foundMethod != null) {
            return false;
        } else if (isSignature(name)) {
            foundMethod = classDesc.lookupMethodBySignature(name, onlyStatic);
            if (foundMethod != null) {
                return true;
            }
        } else if (isJNIName(name)) {
            foundMethod = classDesc.lookupMethodByJNIName(name, onlyStatic);
            if (foundMethod != null) {
                return true;
            }
        }
        return false;
    }

    @TruffleBoundary
    static Object newAdapterInstance(Node node, HostContext hostContext, Class<?> clazz, Object obj) throws IllegalArgumentException {
        if (TruffleOptions.AOT) {
            throw HostEngineException.unsupported(hostContext.access, "Unsupported target type.");
        }
        HostClassDesc classDesc = HostClassDesc.forClass(hostContext, clazz);
        AdapterResult adapter = classDesc.getAdapter(hostContext);
        if (!adapter.isAutoConvertible()) {
            throw HostEngineException.illegalArgument(hostContext.access, "Cannot convert to " + clazz);
        }
        HostMethodDesc.SingleMethod adapterConstructor = adapter.getValueConstructor();
        Object[] arguments = new Object[]{obj};
        try {
            return ((HostObject) HostExecuteNodeGen.getUncached().execute(node, adapterConstructor, null, arguments, hostContext)).obj;
        } catch (UnsupportedTypeException e) {
            throw HostInteropErrors.invalidExecuteArgumentType(hostContext, null, e.getSuppliedValues());
        } catch (ArityException e) {
            throw HostInteropErrors.invalidExecuteArity(hostContext, null, arguments, e.getExpectedMinArity(), e.getExpectedMaxArity(), e.getActualArity());
        }
    }

    private static boolean isStaticTypeOrInterface(Class<?> t) {
        // anonymous classes are private, they should be eliminated elsewhere
        return Modifier.isPublic(t.getModifiers()) && (t.isInterface() || t.isEnum() || Modifier.isStatic(t.getModifiers()));
    }

    static boolean isAbstractType(Class<?> targetType) {
        return targetType.isInterface() ||
                        (!TruffleOptions.AOT && (Modifier.isAbstract(targetType.getModifiers()) && !targetType.isArray() && !targetType.isPrimitive() && !Number.class.isAssignableFrom(targetType)));
    }

    static boolean isExtensibleType(Class<?> targetType) {
        return targetType.isInterface() ||
                        (!TruffleOptions.AOT && (!Modifier.isFinal(targetType.getModifiers()) && !targetType.isArray() && !targetType.isPrimitive() && !Number.class.isAssignableFrom(targetType)));
    }

    @CompilerDirectives.TruffleBoundary
    static String[] findUniquePublicMemberNames(HostContext context, Class<?> clazz, boolean isStatic, boolean isClass, boolean includeInternal) throws SecurityException {
        HostClassDesc classDesc = HostClassDesc.forClass(context, clazz);
        EconomicSet<String> names = EconomicSet.create();
        names.addAll(classDesc.getFieldNames(isStatic));
        names.addAll(classDesc.getMethodNames(isStatic, includeInternal));
        if (isStatic) {
            names.add(STATIC_TO_CLASS);
            if (Modifier.isPublic(clazz.getModifiers())) {
                // no support for non-static member types now
                for (Class<?> t : clazz.getClasses()) {
                    if (!isStaticTypeOrInterface(t)) {
                        continue;
                    }
                    names.add(t.getSimpleName());
                }
            }
        } else if (isClass) {
            names.add(CLASS_TO_STATIC);
        }
        return names.toArray(new String[names.size()]);
    }

    @SuppressWarnings({"unchecked"})
    static <E extends Throwable> RuntimeException rethrow(Throwable ex) throws E {
        throw (E) ex;
    }

    static String toNameAndSignature(Method m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.getName());
        sb.append('(');
        Class<?>[] arr = m.getParameterTypes();
        for (int i = 0; i < arr.length; i++) {
            if (i != 0) {
                sb.append(',');
            }
            sb.append(arr[i].getTypeName());
        }
        sb.append(')');
        return sb.toString();
    }

    static String jniName(Method m) {
        StringBuilder sb = new StringBuilder();
        noUnderscore(sb, m.getName()).append("__");
        appendType(sb, m.getReturnType());
        Class<?>[] arr = m.getParameterTypes();
        for (int i = 0; i < arr.length; i++) {
            appendType(sb, arr[i]);
        }
        return sb.toString();
    }

    private static StringBuilder noUnderscore(StringBuilder sb, String name) {
        return sb.append(name.replace("_", "_1").replace('.', '_'));
    }

    private static void appendType(StringBuilder sb, Class<?> type) {
        if (type == Integer.TYPE) {
            sb.append('I');
            return;
        }
        if (type == Long.TYPE) {
            sb.append('J');
            return;
        }
        if (type == Double.TYPE) {
            sb.append('D');
            return;
        }
        if (type == Float.TYPE) {
            sb.append('F');
            return;
        }
        if (type == Byte.TYPE) {
            sb.append('B');
            return;
        }
        if (type == Boolean.TYPE) {
            sb.append('Z');
            return;
        }
        if (type == Short.TYPE) {
            sb.append('S');
            return;
        }
        if (type == Void.TYPE) {
            sb.append('V');
            return;
        }
        if (type == Character.TYPE) {
            sb.append('C');
            return;
        }
        if (type.isArray()) {
            sb.append("_3");
            appendType(sb, type.getComponentType());
            return;
        }
        noUnderscore(sb.append('L'), type.getName());
        sb.append("_2");
    }
}
