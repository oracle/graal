/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.oracle.svm.hosted.image;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.serviceprovider.BufferUtil;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.RelocatedPointer;

import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.hosted.meta.MethodPointer;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class RelocatableBuffer {

    public static RelocatableBuffer factory(final String name, final long size, final ByteOrder byteOrder) {
        return new RelocatableBuffer(name, size, byteOrder);
    }

    /*
     * Map methods.
     */

    public RelocatableBuffer.Info getInfo(final int key) {
        return getMap().get(key);
    }

    // The only place is is used is to add the ObjectHeader bits to a DynamicHub,
    // which is otherwise just a reference to a class.
    // In the future, this could be used for any offset from a relocated object reference.
    public RelocatableBuffer.Info addDirectRelocationWithAddend(int key, int relocationSize, Long explicitAddend, Object targetObject) {
        final RelocatableBuffer.Info info = infoFactory(ObjectFile.RelocationKind.DIRECT, relocationSize, explicitAddend, targetObject);
        final RelocatableBuffer.Info result = putInfo(key, info);
        return result;
    }

    public RelocatableBuffer.Info addDirectRelocationWithoutAddend(int key, int relocationSize, Object targetObject) {
        final RelocatableBuffer.Info info = infoFactory(ObjectFile.RelocationKind.DIRECT, relocationSize, null, targetObject);
        final RelocatableBuffer.Info result = putInfo(key, info);
        return result;
    }

    public RelocatableBuffer.Info addPCRelativeRelocationWithAddend(int key, int relocationSize, Long explicitAddend, Object targetObject) {
        final RelocatableBuffer.Info info = infoFactory(ObjectFile.RelocationKind.PC_RELATIVE, relocationSize, explicitAddend, targetObject);
        final RelocatableBuffer.Info result = putInfo(key, info);
        return result;
    }

    public RelocatableBuffer.Info addRelocation(int key, ObjectFile.RelocationKind relocationKind, int relocationSize, Long explicitAddend, Object targetObject) {
        final RelocatableBuffer.Info info = infoFactory(relocationKind, relocationSize, explicitAddend, targetObject);
        final RelocatableBuffer.Info result = putInfo(key, info);
        return result;
    }

    public int mapSize() {
        return getMap().size();
    }

    // TODO: Replace with a visitor pattern rather than exposing the entrySet.
    public Set<Map.Entry<Integer, RelocatableBuffer.Info>> entrySet() {
        return getMap().entrySet();
    }

    /** Raw map access. */
    private RelocatableBuffer.Info putInfo(final int key, final RelocatableBuffer.Info value) {
        return getMap().put(key, value);
    }

    /** Raw map access. */
    protected Map<Integer, RelocatableBuffer.Info> getMap() {
        return map;
    }

    /*
     * ByteBuffer methods.
     */

    public byte getByte(final int index) {
        return getBuffer().get(index);
    }

    public RelocatableBuffer putByte(final byte value) {
        getBuffer().put(value);
        return this;
    }

    public RelocatableBuffer putByte(final int index, final byte value) {
        getBuffer().put(index, value);
        return this;
    }

    public RelocatableBuffer putBytes(final byte[] source, final int offset, final int length) {
        getBuffer().put(source, offset, length);
        return this;
    }

    public RelocatableBuffer putInt(final int index, final int value) {
        getBuffer().putInt(index, value);
        return this;
    }

    public int getPosition() {
        return getBuffer().position();
    }

    public RelocatableBuffer setPosition(final int newPosition) {
        BufferUtil.asBaseBuffer(getBuffer()).position(newPosition);
        return this;
    }

    // TODO: Eliminate this method to avoid separating the byte[] from the RelocatableBuffer.
    protected byte[] getBytes() {
        return getBuffer().array();
    }

    // TODO: This should become a private method.
    protected ByteBuffer getBuffer() {
        return buffer;
    }

    /*
     * Info factory.
     */

    private Info infoFactory(ObjectFile.RelocationKind kind, int relocationSize, Long explicitAddend, Object targetObject) {
        return new Info(kind, relocationSize, explicitAddend, targetObject);
    }

    /*
     * Debugging.
     */

    public String getName() {
        return name;
    }

    protected static String targetObjectClassification(final Object targetObject) {
        final StringBuilder result = new StringBuilder();
        if (targetObject == null) {
            result.append("null");
        } else {
            if (targetObject instanceof CFunctionPointer) {
                result.append("pointer to function");
                if (targetObject instanceof MethodPointer) {
                    final MethodPointer mp = (MethodPointer) targetObject;
                    final ResolvedJavaMethod hm = mp.getMethod();
                    result.append("  name: ");
                    result.append(hm.getName());
                }
            } else {
                result.append("pointer to data");
            }
        }
        return result.toString();
    }

    /** Constructor. */
    private RelocatableBuffer(final String name, final long size, final ByteOrder byteOrder) {
        this.name = name;
        this.size = size;
        final int intSize = NumUtil.safeToInt(size);
        this.buffer = ByteBuffer.wrap(new byte[intSize]).order(byteOrder);
        this.map = new TreeMap<>();
    }

    // Immutable fields.

    /** For debugging. */
    protected final String name;
    /** The size of the ByteBuffer. */
    protected final long size;
    /** The ByteBuffer itself. */
    protected final ByteBuffer buffer;
    /** The map itself. */
    private final TreeMap<Integer, RelocatableBuffer.Info> map;

    // Constants.

    static final long serialVersionUID = 0;
    static final int WORD_SIZE = 8; // HACK: hard-code for now

    // Note: A non-static inner class.
    // Note: To keep the RelocatableBuffer from getting separated from this Info.
    public class Info {

        /*
         * Access methods.
         */

        public int getRelocationSize() {
            return relocationSize;
        }

        public ObjectFile.RelocationKind getRelocationKind() {
            return relocationKind;
        }

        public boolean hasExplicitAddend() {
            return (explicitAddend != null);
        }

        // May return null.
        public Long getExplicitAddend() {
            return explicitAddend;
        }

        public Object getTargetObject() {
            return targetObject;
        }

        // Protected constructor called only from RelocatableBuffer.infoFactory method.
        protected Info(ObjectFile.RelocationKind kind, int relocationSize, Long explicitAddend, Object targetObject) {
            this.relocationKind = kind;
            this.relocationSize = relocationSize;
            this.explicitAddend = explicitAddend;
            this.targetObject = targetObject;
        }

        // Immutable state.
        private final int relocationSize;
        private final ObjectFile.RelocationKind relocationKind;
        private final Long explicitAddend;
        /**
         * The referenced object on the heap. If this is an instance of a {@link RelocatedPointer},
         * than the relocation is not treated as a data relocation but has a special meaning, e.g. a
         * code (text section) or constants (rodata section) relocation.
         */
        private final Object targetObject;

        @Override
        public String toString() {
            return "RelocatableBuffer.Info(targetObject=" + targetObject + " relocationSize=" + relocationSize + " relocationKind=" + relocationKind + " explicitAddend=" + explicitAddend + ")";
        }
    }
}
