/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package micro.benchmarks;

import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

// Copied from jdk/test/micro/org/openjdk/bench/javax/crypto/full/CryptoBase.java
@State(Scope.Thread)
public class CryptoBase extends BenchmarkBase {

    @Param({""}) private String provider;

    public Provider prov = null;

    @Setup
    public void setupProvider() {
        if (provider != null && !provider.isEmpty()) {
            prov = Security.getProvider(provider);
            if (prov == null) {
                throw new RuntimeException("Can't find prodiver \"" + provider + "\"");
            }
        }
    }

    public static Cipher makeCipher(Provider prov, String algorithm) throws NoSuchPaddingException, NoSuchAlgorithmException {
        return (prov == null) ? Cipher.getInstance(algorithm) : Cipher.getInstance(algorithm, prov);
    }

    public static byte[][] fillRandom(byte[][] data) {
        Random rnd = new Random();
        for (byte[] d : data) {
            rnd.nextBytes(d);
        }
        return data;
    }

    public static byte[] fillRandom(byte[] data) {
        Random rnd = new Random();
        rnd.nextBytes(data);
        return data;
    }

    public static byte[] fillSecureRandom(byte[] data) {
        SecureRandom rnd = new SecureRandom();
        rnd.nextBytes(data);
        return data;
    }

    public static byte[][] fillEncrypted(byte[][] data, Cipher encryptCipher) throws BadPaddingException, IllegalBlockSizeException {
        byte[][] encryptedData = new byte[data.length][];
        for (int i = 0; i < encryptedData.length; i++) {
            encryptedData[i] = encryptCipher.doFinal(data[i]);
        }
        return encryptedData;
    }
}
