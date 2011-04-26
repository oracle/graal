/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.runtime;

import java.io.*;
import java.util.*;

import com.sun.cri.ri.*;

/**
 * Implementation of RiConstantPool for HotSpot.
 *
 * @author Thomas Wuerthinger, Lukas Stadler
 */
public class HotSpotConstantPool extends CompilerObject implements RiConstantPool {

    private final long vmId;

    private final FastLRUIntCache<RiMethod> methodCache = new FastLRUIntCache<RiMethod>();
    private final FastLRUIntCache<RiField> fieldCache = new FastLRUIntCache<RiField>();
    private final FastLRUIntCache<RiType> typeCache = new FastLRUIntCache<RiType>();

    public static class FastLRUIntCache<T> implements Serializable {

        private static final int InitialCapacity = 4;
        private int lastKey;
        private T lastObject;

        private int[] keys;
        private Object[] objects;
        private int count;

        @SuppressWarnings("unchecked")
        private T access(int index) {
            return (T) objects[index];
        }

        public T get(int key) {
            if (key == lastKey) {
                return lastObject;
            } else if (count > 1) {
                for (int i = 0; i < count; ++i) {
                    if (keys[i] == key) {
                        lastObject = access(i);
                        lastKey = key;
                        return lastObject;
                    }
                }
            }
            return null;
        }

        public void add(int key, T object) {
            count++;
            if (count == 1) {
                lastKey = key;
                lastObject = object;
            } else {
                ensureSize();
                keys[count - 1] = key;
                objects[count - 1] = object;
                if (count == 2) {
                    keys[0] = lastKey;
                    objects[0] = lastObject;
                }
                lastKey = key;
                lastObject = object;
            }
        }

        private void ensureSize() {
            if (keys == null) {
                keys = new int[InitialCapacity];
                objects = new Object[InitialCapacity];
            } else if (count > keys.length) {
                keys = Arrays.copyOf(keys, keys.length * 2);
                objects = Arrays.copyOf(objects, objects.length * 2);
            }
        }
    }

    public HotSpotConstantPool(Compiler compiler, long vmId) {
        super(compiler);
        this.vmId = vmId;
    }

    @Override
    public Object lookupConstant(int cpi) {
        Object constant = compiler.getVMEntries().RiConstantPool_lookupConstant(vmId, cpi);
        return constant;
    }

    @Override
    public RiSignature lookupSignature(int cpi) {
        return compiler.getVMEntries().RiConstantPool_lookupSignature(vmId, cpi);
    }

    @Override
    public RiMethod lookupMethod(int cpi, int byteCode) {
        RiMethod result = methodCache.get(cpi);
        if (result == null) {
            result = compiler.getVMEntries().RiConstantPool_lookupMethod(vmId, cpi, (byte) byteCode);
            methodCache.add(cpi, result);
        }
        return result;
    }

    @Override
    public RiType lookupType(int cpi, int opcode) {
        RiType result = typeCache.get(cpi);
        if (result == null) {
            result = compiler.getVMEntries().RiConstantPool_lookupType(vmId, cpi);
            typeCache.add(cpi, result);
        }
        return result;
    }

    @Override
    public RiField lookupField(int cpi, int opcode) {
        RiField result = fieldCache.get(cpi);
        if (result == null) {
            result = compiler.getVMEntries().RiConstantPool_lookupField(vmId, cpi, (byte) opcode);
            fieldCache.add(cpi, result);
        }
        return result;
    }
}
