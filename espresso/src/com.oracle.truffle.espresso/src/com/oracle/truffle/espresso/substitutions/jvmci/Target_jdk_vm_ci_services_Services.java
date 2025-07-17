/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions.jvmci;

import static com.oracle.truffle.espresso.substitutions.jvmci.Target_jdk_vm_ci_runtime_JVMCI.checkJVMCIAvailable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;

@EspressoSubstitutions
final class Target_jdk_vm_ci_services_Services {

    private Target_jdk_vm_ci_services_Services() {
    }

    private static final long NODE_STRUCT_SIZE = 8 * 3;
    private static final int NODE_STRUCT_KEY_OFFSET = 0;
    private static final int NODE_STRUCT_VALUE_OFFSET = 8;
    private static final int NODE_STRUCT_NEXT_OFFSET = 16;

    @Substitution
    @TruffleBoundary
    public static long readSystemPropertiesInfo(@JavaType(int[].class) StaticObject offsets,
                    @Inject EspressoLanguage language, @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        int[] unwrappedOffsets = offsets.unwrap(language);
        unwrappedOffsets[0] = NODE_STRUCT_NEXT_OFFSET;
        unwrappedOffsets[1] = NODE_STRUCT_KEY_OFFSET;
        unwrappedOffsets[2] = NODE_STRUCT_VALUE_OFFSET;

        Map<String, String> systemProperties = context.getVM().getSystemProperties();
        /*
         * The result must point at a struct with
         * @formatter:off
         * struct node {
         *   long key; // points to UTF_8, 0x00-terminated, string
         *   long value; // null or points to UTF_8, 0x00-terminated, string
         *   long next;  // null or points to another node
         * }
         * @formatter:on
         */
        // Convert the strings and find the allocation size
        long size = 0;
        byte[][] keys = new byte[systemProperties.size()][];
        byte[][] values = new byte[systemProperties.size()][];
        int i = 0;
        for (Map.Entry<String, String> entry : systemProperties.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            size += NODE_STRUCT_SIZE;
            keys[i] = key.getBytes(StandardCharsets.UTF_8);
            size += keys[i].length + 1;
            if (value != null) {
                values[i] = value.getBytes(StandardCharsets.UTF_8);
                size += values[i].length + 1;
            }
            i++;
        }

        // Allocate the buffer
        TruffleObject allocated = context.getNativeAccess().allocateMemory(size);
        long ptr = NativeUtils.interopAsPointer(allocated);
        ByteBuffer buffer = NativeUtils.directByteBuffer(ptr, size);
        for (i = 0; i < keys.length; i++) {
            /* Layout:
             * @formatter:off
             * this block:
             *   long key; -> key
             *   long value; 0 or -> value
             *   long next; 0 or -> next block
             *   utf8 key;
             *   utf8 value; (if != null)
             * next block:
             *   ...
             * @formatter:on
             */
            long nodePtr = ptr + buffer.position();
            long keyPtr = nodePtr + NODE_STRUCT_SIZE;
            long keyLen = keys[i].length + 1;
            long valuePtr;
            long valueLen;
            if (values[i] == null) {
                valuePtr = 0;
                valueLen = 0;
            } else {
                valuePtr = keyPtr + keyLen;
                valueLen = values[i].length + 1;
            }
            long nextPtr;
            if (i + 1 < keys.length) {
                nextPtr = nodePtr + NODE_STRUCT_SIZE + keyLen + valueLen;
                assert nextPtr == keyPtr + keyLen + valueLen;
            } else {
                nextPtr = 0;
            }
            buffer.putLong(keyPtr);
            buffer.putLong(valuePtr);
            buffer.putLong(nextPtr);
            buffer.put(keys[i]);
            buffer.put((byte) 0);
            if (values[i] != null) {
                buffer.put(values[i]);
                buffer.put((byte) 0);
            }
        }
        assert buffer.position() == size;

        /*
         * Note: the buffer is leaked, this is OK since there is usually only one context per
         * process using JVMCI. If there are more contexts needed, we should remember the pointer
         * and cleanup on context finalization.
         */
        return ptr;
    }
}
