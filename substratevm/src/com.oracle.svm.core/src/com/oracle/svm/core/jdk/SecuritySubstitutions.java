/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.snippets.KnownIntrinsics.readCallerStackPointer;

import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.security.Provider;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.thread.Target_java_lang_ThreadLocal;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import sun.security.util.SecurityConstants;

/*
 * All security checks are disabled.
 */

@TargetClass(SecurityManager.class)
@Platforms(InternalPlatform.NATIVE_ONLY.class)
@SuppressWarnings({"static-method", "unused"})
final class Target_java_lang_SecurityManager {
    @Substitute
    @NeverInline("Starting a stack walk in the caller frame")
    protected Class<?>[] getClassContext() {
        final Pointer startSP = readCallerStackPointer();
        return StackTraceUtils.getClassContext(0, startSP);
    }
}

@TargetClass(className = "javax.crypto.JceSecurityManager")
@SuppressWarnings({"static-method", "unused"})
final class Target_javax_crypto_JceSecurityManager {
    @Substitute
    Target_javax_crypto_CryptoPermission getCryptoPermission(String var1) {
        return SubstrateUtil.cast(Target_javax_crypto_CryptoAllPermission.INSTANCE, Target_javax_crypto_CryptoPermission.class);
    }
}

@TargetClass(className = "javax.crypto.CryptoPermission")
final class Target_javax_crypto_CryptoPermission {
}

@TargetClass(className = "javax.crypto.CryptoAllPermission")
final class Target_javax_crypto_CryptoAllPermission {
    @Alias //
    static Target_javax_crypto_CryptoAllPermission INSTANCE;
}

@TargetClass(value = java.security.Provider.class, innerClass = "ServiceKey")
final class Target_java_security_Provider_ServiceKey {

}

@TargetClass(value = java.security.Provider.class)
final class Target_java_security_Provider {
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ThreadLocalServiceKeyComputer.class) //
    private static Target_java_lang_ThreadLocal previousKey;
}

@TargetClass(value = java.security.Provider.class, innerClass = "Service")
final class Target_java_security_Provider_Service {

    /**
     * The field is lazily initialized on first access. We already have the necessary reflection
     * configuration for the reflective lookup at image run time.
     */
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private Object constructorCache;
}

class ServiceKeyProvider {
    static Object getNewServiceKey() {
        Class<?> serviceKey = ReflectionUtil.lookupClass("java.security.Provider$ServiceKey");
        Constructor<?> constructor = ReflectionUtil.lookupConstructor(serviceKey, String.class, String.class, boolean.class);
        return ReflectionUtil.newInstance(constructor, "", "", false);
    }

    /**
     * Originally the thread local creates a new default service key each time. Here we always
     * return the singleton default service key. This default key will be replaced with an actual
     * key in {@code java.security.Provider.parseLegacy}
     */
    static Supplier<Object> getNewServiceKeySupplier() {
        final Object singleton = ServiceKeyProvider.getNewServiceKey();
        return () -> singleton;
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ServiceKeyComputer implements FieldValueTransformer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        return ServiceKeyProvider.getNewServiceKey();
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
class ThreadLocalServiceKeyComputer implements FieldValueTransformer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        // Originally the thread local creates a new default service key each time.
        // Here we always return the singleton default service key. This default key
        // will be replaced with an actual key in Provider.parseLegacy
        return ThreadLocal.withInitial(ServiceKeyProvider.getNewServiceKeySupplier());
    }
}

@Platforms(InternalPlatform.WINDOWS_BASE.class)
@TargetClass(value = java.security.Provider.class)
final class Target_java_security_Provider_Windows {

    @Alias //
    private transient boolean initialized;

    @Alias //
    String name;

    /*
     * `Provider.checkInitialized` is called from all other Provider API methods, before any
     * computation, so it is a convenient location to do our own initialization, e.g., to ensure
     * that the required native libraries are loaded.
     */
    @Substitute
    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException();
        }
        /* Do our own initialization. */
        ProviderUtil.initialize(this);
    }
}

final class ProviderUtil {
    private static volatile boolean initialized = false;

    @SuppressWarnings("restricted")
    static void initialize(Target_java_security_Provider_Windows provider) {
        if (initialized) {
            return;
        }

        if ("SunMSCAPI".equals(provider.name)) {
            try {
                System.loadLibrary("sunmscapi");
            } catch (Throwable ignored) {
                /*
                 * If the loading fails, later calls to native methods will also fail. So, in order
                 * not to confuse users with unexpected stack traces, we ignore the exceptions here.
                 */
            }
            initialized = true;
        }
    }
}

@TargetClass(className = "javax.crypto.ProviderVerifier")
@SuppressWarnings({"unused"})
final class Target_javax_crypto_ProviderVerifier {

    @TargetElement(onlyWith = ProviderVerifierJavaHomeFieldPresent.class) //
    @Alias @InjectAccessors(ProviderVerifierJavaHomeAccessors.class) //
    static String javaHome;

}

class ProviderVerifierJavaHomeFieldPresent implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        Class<?> providerVerifier = Objects.requireNonNull(ReflectionUtil.lookupClass(false, "javax.crypto.ProviderVerifier"));
        return ReflectionUtil.lookupField(true, providerVerifier, "javaHome") != null;
    }
}

@SuppressWarnings("unused")
class ProviderVerifierJavaHomeAccessors {
    private static String javaHome;

    private static String getJavaHome() {
        if (javaHome == null) {
            javaHome = System.getProperty("java.home", "");
        }
        return javaHome;
    }

    private static void setJavaHome(String newJavaHome) {
        javaHome = newJavaHome;
    }
}

/**
 * The {@code javax.crypto.JceSecurity#verificationResults} cache is initialized by the
 * SecurityServicesFeature at build time, for all registered providers. The cache is used by
 * {@code javax.crypto.JceSecurity#canUseProvider} at run time to check whether a provider is
 * properly signed and can be used by JCE. It does that via jar verification which we cannot
 * support.
 */
@TargetClass(className = "javax.crypto.JceSecurity", onlyWith = SecurityProvidersInitializedAtBuildTime.class)
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+27/src/java.base/share/classes/javax/crypto/JceSecurity.java.template")
@SuppressWarnings({"unused"})
final class Target_javax_crypto_JceSecurity {

    // Checkstyle: stop
    @Alias //
    private static Object PROVIDER_VERIFIED;
    // Checkstyle: resume

    /*
     * Map<Provider, ?> of providers that have already been verified. A value of PROVIDER_VERIFIED
     * indicates successful verification. Otherwise, the value is the Exception that caused the
     * verification to fail.
     */
    @Alias //
    private static Map<Object, Object> verificationResults;

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private static Map<Provider, Object> verifyingProviders;

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias) //
    private static Map<Class<?>, URL> codeBaseCacheRef = new WeakHashMap<>();

    @Alias //
    @TargetElement //
    private static ReferenceQueue<Object> queue;

    @Substitute
    static Exception getVerificationResult(Provider p) {
        /* The verification results map key is an identity wrapper object. */
        Object key = new Target_javax_crypto_JceSecurity_WeakIdentityWrapper(p, queue);
        Object o = verificationResults.get(key);
        if (o == PROVIDER_VERIFIED) {
            return null;
        } else if (o != null) {
            return (Exception) o;
        }
        /*
         * If the verification result is not found in the verificationResults map, HotSpot will
         * attempt to verify the provider. This requires accessing the code base, which isn't
         * supported in Native Image, so we need to fail. We could either fail here or substitute
         * getCodeBase() and fail there, but handling it here is a cleaner approach.
         */
        throw new SecurityException(
                        "Attempted to verify a provider that was not registered at build time: " + p + ". " +
                                        "All security providers must be registered and verified during native image generation. " +
                                        "Try adding the option: -H:AdditionalSecurityProviders=" + p + " and rebuild the image.");
    }
}

@TargetClass(className = "javax.crypto.JceSecurity", innerClass = "WeakIdentityWrapper", onlyWith = SecurityProvidersInitializedAtBuildTime.class)
@SuppressWarnings({"unused"})
final class Target_javax_crypto_JceSecurity_WeakIdentityWrapper {

    @Alias //
    Target_javax_crypto_JceSecurity_WeakIdentityWrapper(Provider obj, ReferenceQueue<Object> queue) {
    }
}

/**
 * JDK 8 has the class `javax.crypto.JarVerifier`, but in JDK 11 and later that class is only
 * available in Oracle builds, and not in OpenJDK builds.
 */
@TargetClass(className = "javax.crypto.JarVerifier", onlyWith = PlatformHasClass.class)
@SuppressWarnings({"static-method", "unused"})
final class Target_javax_crypto_JarVerifier {

    @Substitute
    @TargetElement(onlyWith = ContainsVerifyJars.class)
    private String verifySingleJar(URL var1) {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Substitute
    @TargetElement(onlyWith = ContainsVerifyJars.class)
    private void verifyJars(URL var1, List<String> var2) {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
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

final class AllPermissionsPolicy extends Policy {

    static final Policy SINGLETON = new AllPermissionsPolicy();

    private AllPermissionsPolicy() {
    }

    private static PermissionCollection allPermissions() {
        Permissions result = new Permissions();
        result.add(SecurityConstants.ALL_PERMISSION);
        return result;
    }

    @Override
    @SuppressWarnings("deprecation") // deprecated starting JDK 17
    public PermissionCollection getPermissions(CodeSource codesource) {
        return allPermissions();
    }

    @Override
    @SuppressWarnings("deprecation") // deprecated starting JDK 17
    public PermissionCollection getPermissions(ProtectionDomain domain) {
        return allPermissions();
    }

    @Override
    @SuppressWarnings("deprecation") // deprecated starting JDK 17
    public boolean implies(ProtectionDomain domain, Permission permission) {
        return true;
    }
}

@TargetClass(className = "sun.security.jca.ProviderConfig", onlyWith = SecurityProvidersInitializedAtBuildTime.class)
@SuppressWarnings({"unused", "static-method"})
final class Target_sun_security_jca_ProviderConfig {

    @Alias //
    private String provName;

    /**
     * All security providers used in a native-image must be registered during image build time. At
     * runtime, we shouldn't have a call to doLoadProvider. However, this method is still reachable
     * at runtime, and transitively includes other types in the image, among which is
     * sun.security.jca.ProviderConfig.ProviderLoader. This class contains a static field with a
     * cache of providers loaded during the image build. The contents of this cache can vary even
     * when building the same image due to the way services are loaded on Java 11. This cache can
     * increase the final image size substantially (if it contains, for example,
     * {@code org.jcp.xml.dsig.internal.dom.XMLDSigRI}.
     */
    @Substitute
    private Provider doLoadProvider() {
        throw VMError.unsupportedFeature("Cannot load new security provider at runtime: " + provName + ".");
    }
}

@SuppressWarnings("unused")
@TargetClass(className = "sun.security.jca.ProviderConfig", innerClass = "ProviderLoader")
final class Target_sun_security_jca_ProviderConfig_ProviderLoader {
    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, isFinal = true)//
    static Target_sun_security_jca_ProviderConfig_ProviderLoader INSTANCE;
}

/** Dummy class to have a class with the file's name. */
public final class SecuritySubstitutions {
}
