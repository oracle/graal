/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.Reset;

import java.security.AccessControlContext;
import java.security.AccessControlException;
import java.security.MessageDigestSpi;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.SecureRandomSpi;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.snippets.KnownIntrinsics;

// Checkstyle: allow reflection

/*
 * All security checks are disabled.
 */

@TargetClass(java.security.AccessController.class)
@SuppressWarnings({"unused"})
final class Target_java_security_AccessController {

    @Substitute
    private static <T> T doPrivileged(PrivilegedAction<T> action) {
        return action.run();
    }

    @Substitute
    private static <T> T doPrivilegedWithCombiner(PrivilegedAction<T> action) {
        return action.run();
    }

    @Substitute
    private static <T> T doPrivileged(PrivilegedAction<T> action, AccessControlContext context) {
        return action.run();
    }

    @Substitute
    private static <T> T doPrivileged(PrivilegedExceptionAction<T> action) throws Exception {
        return action.run();
    }

    @Substitute
    private static <T> T doPrivilegedWithCombiner(PrivilegedExceptionAction<T> action) throws Exception {
        return action.run();
    }

    @Substitute
    private static <T> T doPrivileged(PrivilegedExceptionAction<T> action, AccessControlContext context) throws Exception {
        return action.run();
    }

    @Substitute
    private static void checkPermission(Permission perm) throws AccessControlException {
    }

    @Substitute
    private static AccessControlContext getContext() {
        AccessControlContext result = new AccessControlContext(new ProtectionDomain[0]);
        KnownIntrinsics.unsafeCast(result, Target_java_security_AccessControlContext.class).isPrivileged = true;
        return result;
    }
}

@TargetClass(java.security.AccessControlContext.class)
final class Target_java_security_AccessControlContext {

    @Alias protected boolean isPrivileged;
}

@Substitute
@TargetClass(java.security.ProtectionDomain.class)
final class Target_java_security_ProtectionDomain {
}

@Delete
@TargetClass(className = "java.util.jar.JarVerifier")
final class Target_java_util_jar_JarVerifier {
}

@TargetClass(java.security.SecureRandom.class)
@SuppressWarnings({"unused"})
final class Target_java_security_SecureRandom {
    @Alias
    Target_java_security_SecureRandom(SecureRandomSpi secureRandomSpi, Provider provider) {
    }

    @Substitute
    private static SecureRandom getInstance(String algorithm) throws NoSuchAlgorithmException {
        if (!algorithm.equals("SHA1PRNG")) {
            throw new NoSuchAlgorithmException(algorithm);
        }
        return new SecureRandom();
    }

    @Substitute
    private static SecureRandom getInstance(String algorithm, String provider) throws NoSuchAlgorithmException, NoSuchProviderException {
        if (!provider.equals("SUN")) {
            throw new NoSuchProviderException(provider);
        }
        return getInstance(algorithm);
    }

    @Substitute
    private static SecureRandom getInstance(String algorithm, Provider provider) throws NoSuchAlgorithmException {
        if (!algorithm.equals("SHA1PRNG")) {
            throw new NoSuchAlgorithmException(algorithm);
        }
        return KnownIntrinsics.unsafeCast(new Target_java_security_SecureRandom(new sun.security.provider.SecureRandom(), provider), SecureRandom.class);
    }

    @Fold
    @Substitute
    static String getPrngAlgorithm() {
        return null;
    }
}

@TargetClass(className = "sun.security.provider.SeedGenerator")
final class Target_sun_security_provider_SeedGenerator {
    @Alias @RecomputeFieldValue(kind = Reset)//
    private static Target_sun_security_provider_SeedGenerator instance;

    @Alias
    native void getSeedBytes(byte[] result);

    @Substitute
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static void generateSeed(byte[] result) {
        if (instance == null) {
            instance = KnownIntrinsics.unsafeCast(new Target_sun_security_provider_SeedGenerator_URLSeedGenerator("file:/dev/urandom"), Target_sun_security_provider_SeedGenerator.class);
        }
        instance.getSeedBytes(result);
    }
}

@TargetClass(className = "sun.security.provider.SecureRandom", innerClass = "SeederHolder")
final class Target_sun_security_provider_SecureRandom_SeederHolder {
    @Alias @InjectAccessors(SeederAccessors.class) //
    private static sun.security.provider.SecureRandom seeder;

    static final class SeederAccessors {
        private static sun.security.provider.SecureRandom instance;

        static sun.security.provider.SecureRandom get() {
            if (instance == null) {
                sun.security.provider.SecureRandom obj = new sun.security.provider.SecureRandom();
                byte[] array = new byte[20];
                Target_sun_security_provider_SeedGenerator.generateSeed(array);
                obj.engineSetSeed(array);
                instance = obj;
            }
            return instance;
        }
    }
}

@TargetClass(className = "sun.security.provider.SeedGenerator", innerClass = "URLSeedGenerator")
final class Target_sun_security_provider_SeedGenerator_URLSeedGenerator {
    @SuppressWarnings("unused")
    @Alias
    Target_sun_security_provider_SeedGenerator_URLSeedGenerator(String egdurl) {
    }
}

@TargetClass(sun.security.jca.Providers.class)
final class Target_sun_security_jca_Providers {
    @Fold
    @Substitute
    static Provider getSunProvider() {
        return sun.security.jca.Providers.getSunProvider();
    }
}

@TargetClass(className = "sun.security.provider.SHA2", innerClass = "SHA224")
final class Target_sun_security_provider_SHA2_SHA224 {
    @Alias
    Target_sun_security_provider_SHA2_SHA224() {
    }
}

@TargetClass(className = "sun.security.provider.SHA2", innerClass = "SHA256")
final class Target_sun_security_provider_SHA2_SHA256 {
    @Alias
    Target_sun_security_provider_SHA2_SHA256() {
    }
}

@TargetClass(className = "sun.security.provider.SHA5", innerClass = "SHA384")
final class Target_sun_security_provider_SHA5_SHA384 {
    @Alias
    Target_sun_security_provider_SHA5_SHA384() {
    }
}

@TargetClass(className = "sun.security.provider.SHA5", innerClass = "SHA512")
final class Target_sun_security_provider_SHA5_SHA512 {
    @Alias
    Target_sun_security_provider_SHA5_SHA512() {
    }
}

@TargetClass(value = java.security.MessageDigest.class, innerClass = "Delegate")
final class Target_java_security_MessageDigest_Delegate {
    @SuppressWarnings("unused")
    @Alias
    Target_java_security_MessageDigest_Delegate(MessageDigestSpi digestSpi, String algorithm) {
    }
}

@TargetClass(java.security.MessageDigest.class)
final class Target_java_security_MessageDigest {
    @Substitute
    public static Target_java_security_MessageDigest getInstance(String algorithm) throws NoSuchAlgorithmException {
        MessageDigestSpi spi = null;
        switch (algorithm) {
            case "MD5":
                spi = new sun.security.provider.MD5();
                break;
            case "SHA":
            case "SHA1":
            case "SHA-1":
                spi = new sun.security.provider.SHA();
                break;
            case "SHA-224":
                spi = KnownIntrinsics.unsafeCast(new Target_sun_security_provider_SHA2_SHA224(), MessageDigestSpi.class);
                break;
            case "SHA-256":
                spi = KnownIntrinsics.unsafeCast(new Target_sun_security_provider_SHA2_SHA256(), MessageDigestSpi.class);
                break;
            case "SHA-384":
                spi = KnownIntrinsics.unsafeCast(new Target_sun_security_provider_SHA5_SHA384(), MessageDigestSpi.class);
                break;
            case "SHA-512":
                spi = KnownIntrinsics.unsafeCast(new Target_sun_security_provider_SHA5_SHA512(), MessageDigestSpi.class);
                break;
        }
        if (spi != null) {
            return KnownIntrinsics.unsafeCast(new Target_java_security_MessageDigest_Delegate(spi, algorithm), Target_java_security_MessageDigest.class);
        }

        throw new NoSuchAlgorithmException(algorithm);
    }

    @SuppressWarnings("unused")
    @Substitute
    public static Target_java_security_MessageDigest getInstance(String algorithm, String provider) throws NoSuchAlgorithmException {
        return getInstance(algorithm);
    }
}

/** Dummy class to have a class with the file's name. */
public final class SecuritySubstitutions {
}
