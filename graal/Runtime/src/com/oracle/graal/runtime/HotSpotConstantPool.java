/*
 * Copyright (c) 2010 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.oracle.graal.runtime;

import java.io.*;
import java.util.*;

import com.sun.c1x.debug.*;
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
