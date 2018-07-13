/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.Reset;

import java.net.URL;
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
import java.util.List;
import java.util.function.Predicate;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

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
    private static <T> T doPrivileged(PrivilegedAction<T> action, AccessControlContext context, Permission... perms) {
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
    private static <T> T doPrivilegedWithCombiner(PrivilegedExceptionAction<T> action, AccessControlContext context, Permission... perms) throws Exception {
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

@TargetClass(className = "sun.security.provider.NativePRNG")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_sun_security_provider_NativePRNG {

    /*
     * This is originally a static final field, i.e., the RandomIO instance would be created during
     * image generation. But it opens a file, which we must do at run time. Therefore, we intercept
     * fields loads and lazily create the RandomIO instance on first access at run time.
     */
    @Alias @InjectAccessors(NativePRNGInstanceAccessors.class) //
    static Target_sun_security_provider_NativePRNG_RandomIO INSTANCE;

    @Alias
    static native Target_sun_security_provider_NativePRNG_RandomIO initIO(Target_sun_security_provider_NativePRNG_Variant v);
}

@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
class NativePRNGInstanceAccessors {
    static volatile Target_sun_security_provider_NativePRNG_RandomIO INSTANCE;

    static Target_sun_security_provider_NativePRNG_RandomIO get() {
        Target_sun_security_provider_NativePRNG_RandomIO result = INSTANCE;
        if (result == null) {
            /* Lazy initialization on first access. */
            result = initializeOnce();
        }
        return result;
    }

    // Checkstyle: stop
    static synchronized Target_sun_security_provider_NativePRNG_RandomIO initializeOnce() {
        // Checkstyle: resume

        Target_sun_security_provider_NativePRNG_RandomIO result = INSTANCE;
        if (result != null) {
            /* Double-checked locking is OK because INSTANCE is volatile. */
            return result;
        }

        result = Target_sun_security_provider_NativePRNG.initIO(Target_sun_security_provider_NativePRNG_Variant.MIXED);
        INSTANCE = result;
        return result;
    }
}

@TargetClass(className = "sun.security.provider.NativePRNG", innerClass = "Variant")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_sun_security_provider_NativePRNG_Variant {
    @Alias static Target_sun_security_provider_NativePRNG_Variant MIXED;
}

@TargetClass(className = "sun.security.provider.NativePRNG", innerClass = "RandomIO")
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class Target_sun_security_provider_NativePRNG_RandomIO {
}

@TargetClass(className = "javax.crypto.JceSecurityManager")
@SuppressWarnings({"static-method", "unused"})
final class Target_javax_crypto_JceSecurityManager {
    @Substitute
    Object getCryptoPermission(String var1) {
        return Target_javax_crypto_CryptoAllPermission.INSTANCE;
    }
}

@TargetClass(className = "javax.crypto.CryptoAllPermission")
final class Target_javax_crypto_CryptoAllPermission {
    @Alias //
    static Target_javax_crypto_CryptoAllPermission INSTANCE;
}

@TargetClass(className = "javax.crypto.JceSecurity", onlyWith = JDK8OrEarlier.class)
@SuppressWarnings({"unused"})
final class Target_javax_crypto_JceSecurity {

    @Substitute
    static void verifyProviderJar(URL var0) {
        throw VMError.unimplemented();
    }

    @Substitute
    static Exception getVerificationResult(Provider var0) {
        throw VMError.unimplemented();
    }

    @Substitute
    static URL getCodeBase(final Class<?> var0) {
        throw VMError.unimplemented();
    }
}

@TargetClass(className = "javax.crypto.JarVerifier")
@SuppressWarnings({"static-method", "unused"})
final class Target_javax_crypto_JarVerifier {

    @Substitute
    @TargetElement(onlyWith = ContainsVerifyJars.class)
    private String verifySingleJar(URL var1) {
        throw VMError.unimplemented();
    }

    @Substitute
    @TargetElement(onlyWith = ContainsVerifyJars.class)
    private void verifyJars(URL var1, List<String> var2) {
        throw VMError.unimplemented();
    }
}

final class ContainsVerifyJars implements Predicate<Class<?>> {
    @Override
    public boolean test(Class<?> originalClass) {
        try {
            originalClass.getDeclaredMethod("verifyJars", URL.class, List.class);
            return true;
        } catch (NoSuchMethodException ex) {
            return false;
        }
    }
}

/** Dummy class to have a class with the file's name. */
public final class SecuritySubstitutions {
}
