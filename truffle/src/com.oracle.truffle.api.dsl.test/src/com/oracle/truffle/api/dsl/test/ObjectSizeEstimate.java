/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

/**
 * Calculates approximate estimates of the size of an object graph.
 *
 * The result contains number of object headers {@link #getHeaderCount()}, number of pointers
 * {@link #getPointerCount()} and size of the primitive data {@link #getPrimitiveByteSize()}.
 *
 * The methods {@link #getTotalBytes()} and {@link #getCompressedTotalBytes()} estimate the total
 * number of bytes occupied. The real number of bytes occupied may vary due to different alignment
 * or different header sizes on different virtual machines.
 */
public final class ObjectSizeEstimate {

    private static final int UNCOMPRESSED_POINTER_SIZE = 8;
    private static final int UNCOMPRESSED_HEADER_SIZE = 16;
    private static final int COMPRESSED_POINTER_SIZE = 4;
    private static final int COMPRESSED_HEADER_SIZE = 12;

    /**
     * Collect the size occupied by the object graph reachable from the given root object.
     *
     * @param root the starting point of the object graph traversal
     */
    public static ObjectSizeEstimate forObject(Object root) {
        return forObject(root, Integer.MAX_VALUE);
    }

    /**
     * Collect the size occupied by the object graph reachable from the given root object.
     *
     * @param root the starting point of the object graph traversal
     * @param maxDepth the maximum depth of the traversal
     */
    public static ObjectSizeEstimate forObject(Object root, int maxDepth) {
        return forObjectHelper(root, maxDepth);
    }

    private int headerCount;
    private int pointerCount;
    private int primitiveByteSize;

    private ObjectSizeEstimate() {
    }

    public ObjectSizeEstimate add(ObjectSizeEstimate other) {
        ObjectSizeEstimate result = new ObjectSizeEstimate();
        result.headerCount = headerCount + other.headerCount;
        result.primitiveByteSize = primitiveByteSize + other.primitiveByteSize;
        result.pointerCount = pointerCount + other.pointerCount;
        return result;
    }

    public ObjectSizeEstimate subtract(ObjectSizeEstimate other) {
        ObjectSizeEstimate result = new ObjectSizeEstimate();
        result.headerCount = headerCount - other.headerCount;
        result.primitiveByteSize = primitiveByteSize - other.primitiveByteSize;
        result.pointerCount = pointerCount - other.pointerCount;
        return result;
    }

    public int getHeaderCount() {
        return headerCount;
    }

    public int getPointerCount() {
        return pointerCount;
    }

    public int getPrimitiveByteSize() {
        return primitiveByteSize;
    }

    @Override
    public String toString() {
        return String.format("(#headers=%s, #pointers=%s, #primitiveBytes=%s, totalCompressed=%s, totalNonCompressed=%s)", headerCount, pointerCount, primitiveByteSize,
                        getCompressedTotalBytes(), getTotalBytes());
    }

    public int getCompressedTotalBytes() {
        return headerCount * COMPRESSED_HEADER_SIZE + pointerCount * COMPRESSED_POINTER_SIZE + primitiveByteSize;
    }

    public int getTotalBytes() {
        return headerCount * UNCOMPRESSED_HEADER_SIZE + pointerCount * UNCOMPRESSED_POINTER_SIZE + primitiveByteSize;
    }

    private void recordHeader() {
        headerCount++;
    }

    private void recordPointer() {
        pointerCount++;
    }

    private void recordPrimitiveBytes(int size) {
        primitiveByteSize += size;
    }

    private static ObjectSizeEstimate forObjectHelper(Object object, int maxDepth) {
        EconomicMap<Object, Object> identityHashMap = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        EconomicMap<Class<?>, Field[]> fieldsMap = EconomicMap.create();
        ObjectSizeEstimate size = new ObjectSizeEstimate();

        ArrayList<Object> stack = new ArrayList<>();
        ArrayList<Integer> depthStack = new ArrayList<>();
        stack.add(object);
        depthStack.add(0);
        identityHashMap.put(object, object);

        while (!stack.isEmpty()) {
            Object o = stack.remove(stack.size() - 1);
            int depth = depthStack.remove(depthStack.size() - 1);
            size.recordHeader();
            Class<?> c = o.getClass();
            if (c.isArray()) {
                size.recordPrimitiveBytes(Integer.BYTES);
                if (o instanceof byte[]) {
                    size.recordPrimitiveBytes(Byte.BYTES * ((byte[]) o).length);
                } else if (o instanceof boolean[]) {
                    size.recordPrimitiveBytes(Byte.BYTES * ((boolean[]) o).length);
                } else if (o instanceof char[]) {
                    size.recordPrimitiveBytes(Character.BYTES * ((char[]) o).length);
                } else if (o instanceof short[]) {
                    size.recordPrimitiveBytes(Short.BYTES * ((short[]) o).length);
                } else if (o instanceof int[]) {
                    size.recordPrimitiveBytes(Integer.BYTES * ((int[]) o).length);
                } else if (o instanceof long[]) {
                    size.recordPrimitiveBytes(Long.BYTES * ((long[]) o).length);
                } else if (o instanceof float[]) {
                    size.recordPrimitiveBytes(Float.BYTES * ((float[]) o).length);
                } else if (o instanceof double[]) {
                    size.recordPrimitiveBytes(Byte.BYTES * ((double[]) o).length);
                } else {
                    for (Object element : (Object[]) o) {
                        size.recordPointer();
                        if (element != null) {
                            if (depth < maxDepth && !identityHashMap.containsKey(element)) {
                                identityHashMap.put(element, null);
                                stack.add(element);
                                depthStack.add(depth + 1);
                            }
                        }
                    }
                }
            } else {
                while (c != null) {

                    Field[] fields = fieldsMap.get(c);
                    if (fields == null) {
                        fields = c.getDeclaredFields();
                        fieldsMap.put(c, fields);
                    }
                    for (Field f : fields) {
                        if (!Modifier.isStatic(f.getModifiers())) {
                            Class<?> type = f.getType();
                            if (type == Byte.TYPE) {
                                size.recordPrimitiveBytes(Byte.BYTES);
                            } else if (type == Boolean.TYPE) {
                                size.recordPrimitiveBytes(Byte.BYTES);
                            } else if (type == Character.TYPE) {
                                size.recordPrimitiveBytes(Character.BYTES);
                            } else if (type == Short.TYPE) {
                                size.recordPrimitiveBytes(Short.BYTES);
                            } else if (type == Integer.TYPE) {
                                size.recordPrimitiveBytes(Integer.BYTES);
                            } else if (type == Long.TYPE) {
                                size.recordPrimitiveBytes(Long.BYTES);
                            } else if (type == Float.TYPE) {
                                size.recordPrimitiveBytes(Float.BYTES);
                            } else if (type == Double.TYPE) {
                                size.recordPrimitiveBytes(Double.BYTES);
                            } else {
                                size.recordPointer();
                                if (maxDepth > 1 && type != Class.class) {
                                    try {
                                        if (!f.canAccess(o)) {
                                            f.setAccessible(true);
                                        }
                                        Object inner = f.get(o);
                                        if (inner != null) {
                                            if (depth < maxDepth && !identityHashMap.containsKey(inner)) {
                                                identityHashMap.put(inner, null);
                                                stack.add(inner);
                                                depthStack.add(depth + 1);
                                            }
                                        }
                                    } catch (IllegalArgumentException | IllegalAccessException e) {
                                        throw new UnsupportedOperationException("Must have access privileges to traverse object graph");
                                    } catch (RuntimeException e) {
                                        if ("java.lang.reflect.InaccessibleObjectException".equals(e.getClass().getName())) {
                                            // This is a newly introduced exception in JDK9 and thus
                                            // cannot be declared in the catch clause.
                                            throw new UnsupportedOperationException("Target class is not exported to the current module.", e);
                                        } else {
                                            throw e;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    c = c.getSuperclass();
                }
            }
        }
        return size;
    }

    public static ObjectSizeEstimate zero() {
        return new ObjectSizeEstimate();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        return headerCount + prime * (pointerCount + prime * primitiveByteSize);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof ObjectSizeEstimate) {
            ObjectSizeEstimate other = (ObjectSizeEstimate) obj;
            return headerCount == other.headerCount && pointerCount == other.pointerCount && primitiveByteSize == other.primitiveByteSize;
        }
        return false;
    }
}
