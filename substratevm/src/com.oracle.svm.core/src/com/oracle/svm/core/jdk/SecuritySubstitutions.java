/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.thread.Target_java_lang_ThreadLocal;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

import sun.security.util.SecurityConstants;

// §FS-002-security-providers.3.1 and §FS-002-security-providers.4.1:
// Reject an unregistered SUN provider before the JDK SecureRandom fallback can expose it.
@TargetClass(className = "sun.security.jca.Providers", onlyWith = ExplicitSecurityProviderRegistration.class)
final class Target_sun_security_jca_Providers_ExplicitRegistration {
    @Alias
    public static native sun.security.jca.ProviderList getProviderList();

    @Substitute
    public static Provider getSunProvider() {
        if (!SecurityProviderRuntimeAccess.isJdkAcquirable("sun.security.provider.Sun")) {
            SecurityProviderRuntimeAccess.reportMissingRegistration(sun.security.provider.Sun.class);
        }
        return new sun.security.provider.Sun();
    }

    /**
     * Keep conditionally registered configurations in their original positions. Enumeration
     * filters inactive entries without letting {@code ProviderList.removeInvalid()} discard them.
     */
    @Substitute
    public static sun.security.jca.ProviderList getFullProviderList() {
        return getProviderList();
    }
}

@TargetClass(className = "sun.security.jca.ProviderConfig", onlyWith = ExplicitSecurityProviderRegistration.class)
final class Target_sun_security_jca_ProviderConfig_ExplicitRegistration {
    @Alias //
    String provName;

    @Alias //
    Provider provider;

    @Alias
    Target_sun_security_jca_ProviderConfig_ExplicitRegistration(@SuppressWarnings("unused") Provider provider) {
    }

    @Alias
    native Provider getProvider();
}

@TargetClass(className = "sun.security.jca.ProviderList", onlyWith = ExplicitSecurityProviderRegistration.class)
@SuppressWarnings({"unused", "static-method"})
final class Target_sun_security_jca_ProviderList_ExplicitRegistration {
    @Alias //
    Target_sun_security_jca_ProviderConfig_ExplicitRegistration[] configs;

    @Alias
    Target_sun_security_jca_ProviderList_ExplicitRegistration(
                    @SuppressWarnings("unused") Target_sun_security_jca_ProviderConfig_ExplicitRegistration[] configs,
                    @SuppressWarnings("unused") boolean allLoaded) {
    }

    // §FS-002-security-providers.2.5:
    /**
     * Preserve inactive conditional configurations when a provider is removed. The JDK
     * implementation assumes that {@code getFullProviderList()} discarded configurations whose
     * provider is {@code null}, but explicit registration intentionally retains them so they can
     * become observable after their run-time condition is satisfied.
     */
    @Substitute
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jvmci-25.2-b20/src/java.base/share/classes/sun/security/jca/ProviderList.java#L112-L129")
    public static sun.security.jca.ProviderList remove(sun.security.jca.ProviderList providerList, String name) {
        Target_sun_security_jca_ProviderList_ExplicitRegistration targetProviderList = SubstrateUtil.cast(providerList,
                        Target_sun_security_jca_ProviderList_ExplicitRegistration.class);
        /* Load the active configurations, as the original getFullProviderList() call would. */
        providerList.toArray();
        int removeIndex = -1;
        for (int index = 0; index < targetProviderList.configs.length; index++) {
            Target_sun_security_jca_ProviderConfig_ExplicitRegistration config = targetProviderList.configs[index];
            Provider provider = config.provider;
            if (provider != null && provider.getName().equals(name)) {
                removeIndex = index;
                break;
            }
        }
        if (removeIndex < 0) {
            return providerList;
        }

        Target_sun_security_jca_ProviderConfig_ExplicitRegistration[] updatedConfigs = new Target_sun_security_jca_ProviderConfig_ExplicitRegistration[targetProviderList.configs.length - 1];
        System.arraycopy(targetProviderList.configs, 0, updatedConfigs, 0, removeIndex);
        System.arraycopy(targetProviderList.configs, removeIndex + 1, updatedConfigs, removeIndex,
                        targetProviderList.configs.length - removeIndex - 1);
        return SubstrateUtil.cast(new Target_sun_security_jca_ProviderList_ExplicitRegistration(updatedConfigs, true),
                        sun.security.jca.ProviderList.class);
    }

}

final class SecurityProviderListSupport {
    /** §FS-002-security-providers.5.2: Translate an API position through visible providers. */
    static sun.security.jca.ProviderList insertAtVisiblePosition(sun.security.jca.ProviderList providerList, Provider provider, int position) {
        Target_sun_security_jca_ProviderList_ExplicitRegistration targetProviderList = SubstrateUtil.cast(providerList,
                        Target_sun_security_jca_ProviderList_ExplicitRegistration.class);
        Provider[] activeProviders = providerList.toArray();
        for (Provider activeProvider : activeProviders) {
            if (activeProvider.getName().equals(provider.getName())) {
                return providerList;
            }
        }

        int visibleIndex = position <= 0 || position > activeProviders.length ? activeProviders.length : position - 1;
        int rawInsertionIndex = targetProviderList.configs.length;
        int currentVisibleIndex = 0;
        int lastActiveRawIndex = -1;
        for (int rawIndex = 0; rawIndex < targetProviderList.configs.length && currentVisibleIndex < activeProviders.length; rawIndex++) {
            if (configurationMatchesProvider(targetProviderList.configs[rawIndex], activeProviders[currentVisibleIndex])) {
                if (currentVisibleIndex == visibleIndex) {
                    rawInsertionIndex = rawIndex;
                    break;
                }
                lastActiveRawIndex = rawIndex;
                currentVisibleIndex++;
            }
        }
        if (visibleIndex == activeProviders.length && lastActiveRawIndex >= 0) {
            rawInsertionIndex = lastActiveRawIndex + 1;
        }

        Target_sun_security_jca_ProviderConfig_ExplicitRegistration[] updatedConfigs = new Target_sun_security_jca_ProviderConfig_ExplicitRegistration[targetProviderList.configs.length + 1];
        System.arraycopy(targetProviderList.configs, 0, updatedConfigs, 0, rawInsertionIndex);
        updatedConfigs[rawInsertionIndex] = new Target_sun_security_jca_ProviderConfig_ExplicitRegistration(provider);
        System.arraycopy(targetProviderList.configs, rawInsertionIndex, updatedConfigs, rawInsertionIndex + 1,
                        targetProviderList.configs.length - rawInsertionIndex);
        return SubstrateUtil.cast(new Target_sun_security_jca_ProviderList_ExplicitRegistration(updatedConfigs, true),
                        sun.security.jca.ProviderList.class);
    }

    /** Return a provider's one-based position in the filtered list. */
    static int visibleIndex(sun.security.jca.ProviderList providerList, String name) {
        int visibleIndex = 0;
        for (Provider provider : providerList.toArray()) {
            visibleIndex++;
            if (provider.getName().equals(name)) {
                return visibleIndex;
            }
        }
        return -1;
    }

    /** §FS-002-security-providers.4.3: Map visible entries without probing configurations. */
    private static boolean configurationMatchesProvider(Target_sun_security_jca_ProviderConfig_ExplicitRegistration config, Provider provider) {
        if (config.provider == provider || config.provName.equals(provider.getName()) || config.provName.equals(provider.getClass().getName())) {
            return true;
        }
        SecurityProviderRuntimeState.ConfiguredProviderInfo configuredProvider = SecurityProviderRuntimeState.getConfiguredProviderForDiagnostics(config.provName);
        if (configuredProvider != null && configuredProvider.providerClassName().equals(provider.getClass().getName())) {
            return true;
        }
        String builtInProviderClassName = BuiltInSecurityProviderLoader.getProviderClassName(config.provName);
        return provider.getClass().getName().equals(builtInProviderClassName) ||
                        SecurityProviderRuntimeAccess.isConfiguredProviderAcquirable(config.provName);
    }

    private SecurityProviderListSupport() {
    }
}

/*
 * All security checks are disabled.
 */

@TargetClass(className = "javax.crypto.JceSecurityManager")
@SuppressWarnings({"static-method", "unused"})
final class Target_javax_crypto_JceSecurityManager {
    @Substitute
    Target_javax_crypto_CryptoPermission getCryptoPermission(String var1) {
        return SubstrateUtil.cast(Target_javax_crypto_CryptoAllPermission.INSTANCE, Target_javax_crypto_CryptoPermission.class);
    }

    /**
     * Native Image cannot perform the JAR verification used by the JDK to establish caller trust.
     * All callers embedded in an image are trusted; provider verification remains enforced
     * separately by {@link JceProviderVerificationSupport}.
     */
    @Substitute
    boolean isCallerTrusted(Class<?> callerClass, Provider provider) {
        return true;
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
 * JCE jar verification cannot run in the image. SecurityServicesFeature records build-time
 * verification outcomes by provider class in SecurityProviderRuntimeState and clears the JDK's
 * provider-instance-keyed weak cache.
 */
@TargetClass(className = "javax.crypto.JceSecurity", onlyWith = SecurityProvidersInitializedAtBuildTime.class)
@BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-24+27/src/java.base/share/classes/javax/crypto/JceSecurity.java.template")
@SuppressWarnings({"unused"})
final class Target_javax_crypto_JceSecurity {
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private static Map<Provider, Object> verifyingProviders;

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias) //
    private static Map<Class<?>, URL> codeBaseCacheRef = new WeakHashMap<>();

    @Substitute
    static Exception getVerificationResult(Provider p) {
        return JceProviderVerificationSupport.getVerificationResult(p);
    }
}

/**
 * JDK 8 has the class `javax.crypto.JarVerifier`, but in JDK 11 and later that class is only
 * available in Oracle builds, and not in OpenJDK builds.
 */
@TargetClass(className = "javax.crypto.JarVerifier", onlyWith = {PlatformHasClass.class, OracleJDK.class})
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
