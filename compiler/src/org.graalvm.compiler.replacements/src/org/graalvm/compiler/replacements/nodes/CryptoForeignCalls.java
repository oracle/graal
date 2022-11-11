/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

public class CryptoForeignCalls {

    public static final ForeignCallDescriptor STUB_AES_ENCRYPT = foreignCallDescriptor("aesEncrypt", AESNode.KILLED_LOCATIONS, Pointer.class, Pointer.class, Pointer.class);
    public static final ForeignCallDescriptor STUB_AES_DECRYPT = foreignCallDescriptor("aesDecrypt", AESNode.KILLED_LOCATIONS, Pointer.class, Pointer.class, Pointer.class);

    public static final ForeignCallDescriptor STUB_GHASH_PROCESS_BLOCKS = foreignCallDescriptor("ghashProcessBlocks", GHASHProcessBlocksNode.KILLED_LOCATIONS,
                    Pointer.class, Pointer.class, Pointer.class, int.class);
    public static final ForeignCallDescriptor STUB_CTR_AES_CRYPT = foreignCallDescriptorWithReturnType("ctrAESCrypt", CounterModeAESNode.KILLED_LOCATIONS,
                    int.class, Pointer.class, Pointer.class, Pointer.class, Pointer.class, int.class, Pointer.class, Pointer.class);

    public static final ForeignCallDescriptor[] AES_STUBS = {
                    STUB_AES_ENCRYPT,
                    STUB_AES_DECRYPT,
                    STUB_CTR_AES_CRYPT,
    };

    private static ForeignCallDescriptor foreignCallDescriptor(String name, LocationIdentity[] killLocations, Class<?>... argTypes) {
        return new ForeignCallDescriptor(name, void.class, argTypes, false, killLocations, false, false);
    }

    private static ForeignCallDescriptor foreignCallDescriptorWithReturnType(String name, LocationIdentity[] killLocations, Class<?> returnType, Class<?>... argTypes) {
        return new ForeignCallDescriptor(name, returnType, argTypes, false, killLocations, false, false);
    }
}
