/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.common.remote;

import static java.lang.reflect.Modifier.*;

import java.lang.reflect.*;
import java.nio.*;
import java.util.*;

import sun.awt.util.*;

import com.oracle.graal.api.code.Register.RegisterCategory;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;

/**
 * Manages a context for replay or remote compilation.
 */
public class Context implements AutoCloseable {

    private static final ThreadLocal<Context> currentContext = new ThreadLocal<>();

    private final Map<Class<?>, Fields> fieldsMap = new HashMap<>();

    public enum Mode {
        Capturing,
        Replaying
    }

    private Mode mode = Mode.Capturing;

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public static <K, V> HashMap<K, V> newMap() {
        return Context.getCurrent() == null ? new HashMap<>() : new LinkedHashMap<>();
    }

    public static <K, V> HashMap<K, V> newMap(Map<K, V> m) {
        return Context.getCurrent() == null ? new HashMap<>(m) : new LinkedHashMap<>(m);
    }

    public static <K, V> HashMap<K, V> newMap(int initialCapacity) {
        return Context.getCurrent() == null ? new HashMap<>(initialCapacity) : new LinkedHashMap<>(initialCapacity);
    }

    public static <K, V> Map<K, V> newIdentityMap() {
        return Context.getCurrent() == null ? new IdentityHashMap<>() : new LinkedIdentityHashMap<>();
    }

    public static <K, V> Map<K, V> newIdentityMap(int expectedMaxSize) {
        return Context.getCurrent() == null ? new IdentityHashMap<>(expectedMaxSize) : new LinkedIdentityHashMap<>();
    }

    public static <K, V> Map<K, V> newIdentityMap(Map<K, V> m) {
        return Context.getCurrent() == null ? new IdentityHashMap<>(m) : new LinkedIdentityHashMap<>(m);
    }

    /**
     * Gets a descriptor for the fields in a class that can be used for serialization.
     */
    private Fields fieldsFor(Class<?> c) {
        Fields fields = fieldsMap.get(c);
        if (fields == null) {
            fields = Fields.forClass(c, Object.class, true, null);
            fieldsMap.put(c, fields);
        }
        return fields;
    }

    /**
     * Classes whose values are subject to special serialization handling.
     */
    // @formatter:off
    private static final Set<Class<?>> DontCopyClasses = new HashSet<>(Arrays.asList(
        Enum.class,
        Integer.class,
        Boolean.class,
        Short.class,
        Byte.class,
        Character.class,
        Float.class,
        Long.class,
        Double.class,
        String.class,
        Method.class,
        Class.class,
        Field.class,
        Constructor.class,
        RegisterCategory.class,
        NamedLocationIdentity.class
    ));
    // @formatter:on

    private static void registerSharedGlobal(Class<?> declaringClass, String staticFieldName) {
        try {
            SharedGlobal global = new SharedGlobal(declaringClass.getDeclaredField(staticFieldName));
            SharedGlobals.put(global.get(), global);
        } catch (NoSuchFieldException e) {
            // ignore non-existing fields
        } catch (Exception e) {
            throw new GraalInternalError(e);
        }
    }

    private static void registerSharedGlobals(Class<?> declaringClass, Class<?> staticFieldType) {
        assert !staticFieldType.isPrimitive();
        try {
            for (Field f : declaringClass.getDeclaredFields()) {
                if (isStatic(f.getModifiers()) && isFinal(f.getModifiers()) && !f.getType().isPrimitive()) {
                    SharedGlobal global = new SharedGlobal(f);
                    if (staticFieldType.isAssignableFrom(f.getType())) {
                        SharedGlobals.put(global.get(), global);
                    } else {
                        Class<?> componentType = f.getType().getComponentType();
                        if (componentType != null && staticFieldType.isAssignableFrom(componentType)) {
                            Object[] vals = global.get();
                            for (int i = 0; i < vals.length; i++) {
                                SharedGlobal g = new SharedGlobal(f, i);
                                Object obj = g.get();
                                SharedGlobals.put(obj, g);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new GraalInternalError(e);
        }
    }

    /**
     * A shared global is a non-primitive value in a static final variable whose identity is
     * important to the compiler. That is, equality tests against these values are performed with
     * {@code ==} or these values are keys in identity hash maps.
     */
    static class SharedGlobal {
        final Field staticField;
        final Integer index;

        public SharedGlobal(Field staticField) {
            this(staticField, null);
        }

        public SharedGlobal(Field staticField, Integer index) {
            int mods = staticField.getModifiers();
            assert isStatic(mods) && isFinal(mods) && !staticField.getType().isPrimitive() : staticField;
            staticField.setAccessible(true);
            this.staticField = staticField;
            this.index = index;
        }

        @SuppressWarnings("unchecked")
        public <T> T get() {
            try {
                Object value = staticField.get(null);
                if (index != null) {
                    value = ((Object[]) value)[index.intValue()];
                }
                return (T) value;
            } catch (Exception e) {
                throw new GraalInternalError(e);
            }
        }

        @Override
        public String toString() {
            String res = staticField.getDeclaringClass().getName() + "." + staticField.getName();
            if (index != null) {
                res += "[" + index + "]";
            }
            return res;
        }
    }

    /**
     * Objects that should not be copied but retrieved from final static fields.
     */
    private static final Map<Object, SharedGlobal> SharedGlobals = new IdentityHashMap<>();
    static {
        registerSharedGlobal(ByteOrder.class, "BIG_ENDIAN");
        registerSharedGlobal(ByteOrder.class, "LITTLE_ENDIAN");
        registerSharedGlobal(ArrayList.class, "EMPTY_ELEMENTDATA");
        registerSharedGlobal(ArrayList.class, "DEFAULTCAPACITY_EMPTY_ELEMENTDATA");
        registerSharedGlobals(StampFactory.class, Stamp.class);
    }

    /**
     * Determines if a given class is a subclass of any class in a given collection of classes.
     */
    private static boolean isAssignableTo(Class<?> from, Collection<Class<?>> to) {
        return to.stream().anyMatch(c -> c.isAssignableFrom(from));
    }

    /**
     * Gets a string representing the identity of an object.
     */
    private static String id(Object o) {
        if (o == null) {
            return "null";
        }
        return String.format("%s@%x", MetaUtil.getSimpleName(o.getClass(), true), System.identityHashCode(o));
    }

    /**
     * Process an object graph copy operation iteratively using a worklist.
     */
    private void copy0(Deque<Object> worklist, Map<Object, Object> copies) {
        // 1. Traverse object graph, making uninitialized copies of
        // objects that need to be copied (other object values are transferred 'as is')
        while (!worklist.isEmpty()) {
            Object obj = worklist.pollFirst();
            // System.out.printf("worklist-: %s%n", s(obj));
            assert copies.get(obj) == copies : id(obj) + " -> " + id(copies.get(obj));
            assert pool.get(obj) == null;
            Class<? extends Object> clazz = obj.getClass();
            Class<?> componentType = clazz.getComponentType();
            if (componentType != null) {
                if (componentType.isPrimitive()) {
                    if (obj instanceof int[]) {
                        copies.put(obj, ((int[]) obj).clone());
                    } else if (obj instanceof byte[]) {
                        copies.put(obj, ((byte[]) obj).clone());
                    } else if (obj instanceof char[]) {
                        copies.put(obj, ((char[]) obj).clone());
                    } else if (obj instanceof short[]) {
                        copies.put(obj, ((short[]) obj).clone());
                    } else if (obj instanceof float[]) {
                        copies.put(obj, ((float[]) obj).clone());
                    } else if (obj instanceof long[]) {
                        copies.put(obj, ((long[]) obj).clone());
                    } else if (obj instanceof double[]) {
                        copies.put(obj, ((double[]) obj).clone());
                    } else {
                        copies.put(obj, ((boolean[]) obj).clone());
                    }
                } else {
                    Object[] o = (Object[]) obj;
                    Object[] c = o.clone();
                    copies.put(obj, c);
                    // System.out.printf("m+: %s%n", s(obj));
                    for (int i = 0; i < c.length; i++) {
                        c[i] = copyFieldOrElement(worklist, copies, o[i]);
                    }
                }
            } else {
                assert !isAssignableTo(clazz, DontCopyClasses);
                Object c;
                try {
                    c = UnsafeAccess.unsafe.allocateInstance(clazz);
                    copies.put(obj, c);
                    // System.out.printf("m+: %s%n", s(obj));

                    Fields fields = fieldsFor(clazz);
                    fields.copy(obj, c, (i, o) -> copyFieldOrElement(worklist, copies, o));
                } catch (InstantiationException e) {
                    throw new GraalInternalError(e);
                }
            }
        }

        // 2. Initialize fields of copied objects
        for (Map.Entry<Object, Object> e : copies.entrySet()) {
            Object src = e.getKey();
            Object dst = e.getValue();
            assert dst != copies : id(src);
            pool.put(src, dst);
            if (src instanceof Object[]) {
                Object[] srcArr = (Object[]) src;
                Object[] dstArr = (Object[]) dst;
                for (int i = 0; i < srcArr.length; i++) {
                    Object dstElement = copies.get(srcArr[i]);
                    if (dstElement != null) {
                        dstArr[i] = dstElement;
                    }
                }
            } else {
                Fields fields = fieldsFor(src.getClass());
                assert !Proxy.isProxyClass(dst.getClass());
                for (int index = 0; index < fields.getCount(); index++) {
                    if (!fields.getType(index).isPrimitive()) {
                        Object value = copies.get(fields.getObject(src, index));
                        if (value != null) {
                            fields.set(dst, index, value);
                        }
                    }
                }
            }
        }
    }

    /**
     * Given the value of an object field or object array element, returns the value that should be
     * written into the copy of the object or array.
     */
    private Object copyFieldOrElement(Deque<Object> worklist, Map<Object, Object> copies, Object srcValue) {
        Object dstValue = srcValue;
        if (srcValue != null && !Proxy.isProxyClass(srcValue.getClass())) {
            if (isAssignableTo(srcValue.getClass(), DontCopyClasses) || SharedGlobals.containsKey(srcValue)) {
                pool.put(srcValue, srcValue);
                return srcValue;
            }
            dstValue = pool.get(srcValue);
            if (dstValue == null) {
                if (srcValue instanceof Remote) {
                    dstValue = get(srcValue);
                } else {
                    dstValue = copies.get(srcValue);
                    if (dstValue == null) {
                        assert !worklist.contains(srcValue) : id(srcValue);
                        // System.out.printf("worklist+: %s%n", s(srcValue));
                        worklist.add(srcValue);
                        copies.put(srcValue, copies);
                    } else if (dstValue == copies) {
                        dstValue = null;
                    }
                }
            }
        }
        return dstValue;
    }

    /**
     * Copies an object graph. This operation does not copy:
     * <ul>
     * <li>objects whose type is assignable to one of {@link #DontCopyClasses}</li>
     * <li>proxy objects</li>
     * </ul>
     * In addition, copies in {@link #pool} are re-used.
     */
    private Object copy(Object root) {
        assert !(isAssignableTo(root.getClass(), DontCopyClasses) || SharedGlobals.containsKey(root));
        // System.out.printf("----- %s ------%n", s(obj));
        assert pool.get(root) == null;
        Deque<Object> worklist = new IdentityLinkedList<>();
        worklist.add(root);
        IdentityHashMap<Object, Object> copies = new IdentityHashMap<>();
        copies.put(root, copies);
        copy0(worklist, copies);
        return pool.get(root);
    }

    /**
     * Creates an opens a context for a remote compilation request or a replay compilation
     * capturing. This should be used in conjunction with the try-finally statement:
     *
     * <pre>
     * try (Context c = new Context()) {
     *     ...
     * }
     * </pre>
     *
     * Open one context can be active at any time for a thread.
     */
    public Context() {
        assert currentContext.get() == null : currentContext.get();
        currentContext.set(this);
    }

    private final Map<Object, Object> proxies = new IdentityHashMap<>();
    private final Map<Object, Object> pool = new IdentityHashMap<>();

    /**
     * Gets the value of a given object within this context.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Object obj) {
        if (obj == null || Proxy.isProxyClass(obj.getClass())) {
            return (T) obj;
        }
        if (obj instanceof Remote) {
            Object proxy = proxies.get(obj);
            if (proxy == null) {
                Class<?>[] interfaces = ProxyUtil.getAllInterfaces(obj.getClass());
                proxy = Proxy.newProxyInstance(obj.getClass().getClassLoader(), interfaces, new Handler<>(obj, this));
                proxies.put(obj, proxy);
            }
            return (T) proxy;
        } else {
            Object value;
            if (isAssignableTo(obj.getClass(), DontCopyClasses) || SharedGlobals.containsKey(obj)) {
                value = obj;
            } else {
                value = pool.get(obj);
                if (value == null) {
                    if (mode == Mode.Capturing) {
                        value = copy(obj);
                    } else {
                        throw new GraalInternalError("No captured state for %s [class=%s]", obj, obj.getClass());
                    }
                }
            }
            return (T) value;
        }
    }

    public void close() {
        assert currentContext.get() == this : currentContext.get();
        currentContext.set(null);
    }

    /**
     * Checks that a given value is valid within the {@linkplain #getCurrent() current context} (if
     * any).
     */
    public static boolean check(Object o) {
        if (o != null) {
            Context c = currentContext.get();
            if (c != null) {
                if (o instanceof Remote) {
                    if (!Proxy.isProxyClass(o.getClass())) {
                        throw new GraalInternalError("Expecting proxy, found instance of %s", o.getClass());
                    }
                } else {
                    if (!Proxy.isProxyClass(o.getClass())) {
                        throw new GraalInternalError("Expecting instance of %s, found proxy", o.getClass());
                    }
                }
            }
        }
        return true;
    }

    /**
     * Gets the currently active context for the calling thread.
     *
     * @return {@code null} if there is no context active on the calling thread
     */
    public static Context getCurrent() {
        return currentContext.get();
    }
}
