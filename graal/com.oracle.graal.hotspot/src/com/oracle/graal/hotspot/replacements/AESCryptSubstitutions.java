/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.hotspot.HotSpotBackend.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.word.Word.*;

import java.lang.reflect.*;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.common.*;
import jdk.internal.jvmci.meta.*;
import sun.misc.*;

import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.word.*;

// JaCoCo Exclude

/**
 * Substitutions for {@code com.sun.crypto.provider.AESCrypt} methods.
 */
public class AESCryptSubstitutions {

    static final long kOffset;
    static final Class<?> AESCryptClass;
    static final int AES_BLOCK_SIZE;

    static {
        try {
            // Need to use launcher class path as com.sun.crypto.provider.AESCrypt
            // is normally not on the boot class path
            ClassLoader cl = Launcher.getLauncher().getClassLoader();
            AESCryptClass = Class.forName("com.sun.crypto.provider.AESCrypt", true, cl);
            kOffset = UnsafeAccess.unsafe.objectFieldOffset(AESCryptClass.getDeclaredField("K"));
            Field aesBlockSizeField = Class.forName("com.sun.crypto.provider.AESConstants", true, cl).getDeclaredField("AES_BLOCK_SIZE");
            aesBlockSizeField.setAccessible(true);
            AES_BLOCK_SIZE = aesBlockSizeField.getInt(null);
        } catch (Exception ex) {
            throw new JVMCIError(ex);
        }
    }

    static void encryptBlock(Object rcvr, byte[] in, int inOffset, byte[] out, int outOffset) {
        crypt(rcvr, in, inOffset, out, outOffset, true);
    }

    static void decryptBlock(Object rcvr, byte[] in, int inOffset, byte[] out, int outOffset) {
        crypt(rcvr, in, inOffset, out, outOffset, false);
    }

    private static void crypt(Object rcvr, byte[] in, int inOffset, byte[] out, int outOffset, boolean encrypt) {
        checkArgs(in, inOffset, out, outOffset);
        Object realReceiver = PiNode.piCastNonNull(rcvr, AESCryptClass);
        Object kObject = UnsafeLoadNode.load(realReceiver, kOffset, Kind.Object, LocationIdentity.any());
        Word kAddr = fromWordBase(Word.fromObject(kObject).add(arrayBaseOffset(Kind.Byte)));
        Word inAddr = Word.unsigned(ComputeObjectAddressNode.get(in, arrayBaseOffset(Kind.Byte) + inOffset));
        Word outAddr = Word.unsigned(ComputeObjectAddressNode.get(out, arrayBaseOffset(Kind.Byte) + outOffset));
        if (encrypt) {
            encryptBlockStub(ENCRYPT_BLOCK, inAddr, outAddr, kAddr);
        } else {
            decryptBlockStub(DECRYPT_BLOCK, inAddr, outAddr, kAddr);
        }
    }

    /**
     * Perform null and array bounds checks for arguments to a cipher operation.
     */
    static void checkArgs(byte[] in, int inOffset, byte[] out, int outOffset) {
        if (UnsignedMath.aboveThan(inOffset + AES_BLOCK_SIZE, in.length) || UnsignedMath.aboveThan(outOffset + AES_BLOCK_SIZE, out.length)) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
    }

    @NodeIntrinsic(ForeignCallNode.class)
    public static native void encryptBlockStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word in, Word out, Word key);

    @NodeIntrinsic(ForeignCallNode.class)
    public static native void decryptBlockStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word in, Word out, Word key);
}
