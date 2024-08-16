/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.security.AccessControlContext;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.security.Provider;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.graal.snippets.CEntryPointSnippets;
import com.oracle.svm.core.thread.Target_java_lang_Thread;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import sun.security.jca.ProviderList;
import sun.security.util.SecurityConstants;

/*
 * All security checks are disabled.
 */

@TargetClass(java.security.AccessController.class)
@Platforms(InternalPlatform.NATIVE_ONLY.class)
@SuppressWarnings({"unused"})
final class Target_java_security_AccessController {

    @Substitute
    @SuppressWarnings("deprecation")
    static AccessControlContext getStackAccessControlContext() {
        if (!CEntryPointSnippets.isIsolateInitialized()) {
            /*
             * If isolate still isn't initialized, we can assume that we are so early in the JDK
             * initialization that any attempt at stalk walk will fail as not even the basic
             * PrintWriter/Logging is available yet. This manifested when
             * UseDedicatedVMOperationThread hosted option was set, triggering a runtime crash.
             */
            return null;
        }
        return StackAccessControlContextVisitor.getFromStack();
    }

    @Substitute
    static AccessControlContext getInheritedAccessControlContext() {
        return SubstrateUtil.cast(Thread.currentThread(), Target_java_lang_Thread.class).inheritedAccessControlContext;
    }

    @Substitute
    private static ProtectionDomain getProtectionDomain(final Class<?> caller) {
        return caller.getProtectionDomain();
    }

    @Substitute
    @SuppressWarnings("deprecation") // deprecated starting JDK 17
    static <T> T executePrivileged(PrivilegedExceptionAction<T> action, AccessControlContext context, Class<?> caller) throws Throwable {
        if (action == null) {
            throw new NullPointerException("Null action");
        }

        PrivilegedStack.push(context, caller);
        try {
            return action.run();
        } finally {
            PrivilegedStack.pop();
        }
    }

    @Substitute
    @SuppressWarnings("deprecation") // deprecated starting JDK 17
    static <T> T executePrivileged(PrivilegedAction<T> action, AccessControlContext context, Class<?> caller) {
        if (action == null) {
            throw new NullPointerException("Null action");
        }

        PrivilegedStack.push(context, caller);
        try {
            return action.run();
        } finally {
            PrivilegedStack.pop();
        }
    }

    @Substitute
    @SuppressWarnings("deprecation")
    static AccessControlContext checkContext(AccessControlContext context, Class<?> caller) {

        if (context != null && context.equals(AccessControllerUtil.DISALLOWED_CONTEXT_MARKER)) {
            VMError.shouldNotReachHere("Non-allowed AccessControlContext that was replaced with a blank one at build time was invoked without being reinitialized at run time.\n" +
                            "This might be an indicator of improper build time initialization, or of a non-compatible JDK version.\n" +
                            "In order to fix this you can either:\n" +
                            "    * Annotate the offending context's field with @RecomputeFieldValue\n" +
                            "    * Implement a custom runtime accessor and annotate said field with @InjectAccessors\n" +
                            "    * If this context originates from the JDK, and it doesn't leak sensitive info, you can allow it in 'AccessControlContextReplacerFeature.duringSetup'");
        }

        // check if caller is authorized to create context
        if (System.getSecurityManager() != null) {
            throw VMError.unsupportedFeature("SecurityManager isn't supported");
        }
        return context;
    }
}

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
    Object getCryptoPermission(String var1) {
        return Target_javax_crypto_CryptoAllPermission.INSTANCE;
    }
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
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ServiceKeyComputer.class) //
    private static Target_java_security_Provider_ServiceKey previousKey;
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

@Platforms(Platform.HOSTED_ONLY.class)
class ServiceKeyComputer implements FieldValueTransformer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        try {
            Class<?> serviceKey = Class.forName("java.security.Provider$ServiceKey");
            Constructor<?> constructor = ReflectionUtil.lookupConstructor(serviceKey, String.class, String.class, boolean.class);
            return constructor.newInstance("", "", false);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }
}

@Platforms(Platform.WINDOWS.class)
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

    static void initialize(Target_java_security_Provider_Windows provider) {
        if (initialized) {
            return;
        }

        if (provider.name.equals("SunMSCAPI")) {
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

    @Substitute
    static boolean isTrustedCryptoProvider(Provider provider) {
        /*
         * This is just used for fast-path checks, so returning false is a safe default. The
         * original method accesses the Java home directory.
         */
        return false;
    }
}

@TargetClass(className = "javax.crypto.JceSecurity")
@SuppressWarnings({"unused"})
final class Target_javax_crypto_JceSecurity {

    /*
     * The JceSecurity.verificationResults cache is initialized by the SecurityServicesFeature at
     * build time, for all registered providers. The cache is used by JceSecurity.canUseProvider()
     * at runtime to check whether a provider is properly signed and can be used by JCE. It does
     * that via jar verification which we cannot support.
     */

    // Checkstyle: stop
    @Alias //
    private static Object PROVIDER_VERIFIED;
    // Checkstyle: resume

    // Map<Provider,?> of the providers we already have verified
    // value == PROVIDER_VERIFIED is successfully verified
    // value is failure cause Exception in error case
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = VerificationCacheTransformer.class, disableCaching = true) //
    private static Map<Object, Object> verificationResults;

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private static Map<Provider, Object> verifyingProviders;

    @Alias //
    @TargetElement(onlyWith = JDK21OrLater.class) //
    private static ReferenceQueue<Object> queue;

    @Substitute
    static void verifyProvider(URL codeBase, Provider p) {
        throw VMError.shouldNotReachHere("javax.crypto.JceSecurity.verifyProviderJar(URL, Provider) is reached at runtime. " +
                        "This should not happen. The contents of JceSecurity.verificationResults " +
                        "are computed and cached at image build time.");
    }

    @Substitute
    static URL getCodeBase(final Class<?> clazz) {
        throw VMError.unsupportedFeature("Trying to access the code base of " + clazz + ". ");
    }

    @Substitute
    static Exception getVerificationResult(Provider p) {
        /* Start code block copied from original method. */
        /* The verification results map key is an identity wrapper object. */
        Object key = JavaVersionUtil.JAVA_SPEC <= 20 ? // JDK-8168469
                        new Target_javax_crypto_JceSecurity_IdentityWrapper(p) : //
                        new Target_javax_crypto_JceSecurity_WeakIdentityWrapper(p, queue);
        Object o = verificationResults.get(key);
        if (o == PROVIDER_VERIFIED) {
            return null;
        } else if (o != null) {
            return (Exception) o;
        }
        /* End code block copied from original method. */
        /*
         * If the verification result is not found in the verificationResults map JDK proceeds to
         * verify it. That requires accessing the code base which we don't support. The substitution
         * for getCodeBase() would be enough to take care of this too, but substituting
         * getVerificationResult() allows for a better error message.
         */
        throw VMError.unsupportedFeature("Trying to verify a provider that was not registered at build time: " + p + ". " +
                        "All providers must be registered and verified in the Native Image builder. ");
    }

    private static class VerificationCacheTransformer implements FieldValueTransformer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            return SecurityProvidersFilter.instance().cleanVerificationCache(originalValue);
        }
    }
}

@TargetClass(className = "javax.crypto.JceSecurity", innerClass = "IdentityWrapper", onlyWith = JDK20OrEarlier.class)
@SuppressWarnings({"unused"})
final class Target_javax_crypto_JceSecurity_IdentityWrapper {
    @Alias //
    Target_javax_crypto_JceSecurity_IdentityWrapper(Provider obj) {
    }
}

@TargetClass(className = "javax.crypto.JceSecurity", innerClass = "WeakIdentityWrapper", onlyWith = JDK21OrLater.class)
@SuppressWarnings({"unused"})
final class Target_javax_crypto_JceSecurity_WeakIdentityWrapper {

    @Alias //
    Target_javax_crypto_JceSecurity_WeakIdentityWrapper(Provider obj, ReferenceQueue<Object> queue) {
    }
}

class JceSecurityAccessor {
    private static volatile SecureRandom RANDOM;

    static SecureRandom get() {
        SecureRandom result = RANDOM;
        if (result == null) {
            /* Lazy initialization on first access. */
            result = initializeOnce();
        }
        return result;
    }

    private static synchronized SecureRandom initializeOnce() {
        SecureRandom result = RANDOM;
        if (result != null) {
            /* Double-checked locking is OK because INSTANCE is volatile. */
            return result;
        }

        result = new SecureRandom();
        RANDOM = result;
        return result;
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

@TargetClass(value = java.security.Policy.class, innerClass = "PolicyInfo")
final class Target_java_security_Policy_PolicyInfo {
}

@TargetClass(java.security.Policy.class)
final class Target_java_security_Policy {

    @Delete //
    private static Target_java_security_Policy_PolicyInfo policyInfo;

    @Substitute
    private static Policy getPolicyNoCheck() {
        return AllPermissionsPolicy.SINGLETON;
    }

    @Substitute
    private static boolean isSet() {
        return true;
    }

    @Substitute
    @SuppressWarnings("unused")
    private static void setPolicy(Policy p) {
        /*
         * We deliberately treat this as a non-recoverable fatal error. We want to prevent bugs
         * where an exception is silently ignored by an application and then necessary security
         * checks are not in place.
         */
        throw VMError.shouldNotReachHere("Installing a Policy is not yet supported");
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

/**
 * This class is instantiated indirectly from the {@code Policy#getInstance} methods via the
 * {@link java.security.Security#getProviders security provider} abstractions. We could just
 * substitute the Policy.getInstance methods to return {@link AllPermissionsPolicy#SINGLETON}, this
 * version is more fool-proof in case someone manually registers security providers for reflective
 * instantiation.
 */
@TargetClass(className = "sun.security.provider.PolicySpiFile")
@SuppressWarnings({"unused", "static-method", "deprecation"})
final class Target_sun_security_provider_PolicySpiFile {

    @Substitute
    private Target_sun_security_provider_PolicySpiFile(Policy.Parameters params) {
    }

    @Substitute
    @SuppressWarnings("deprecation") // deprecated starting JDK 17
    private PermissionCollection engineGetPermissions(CodeSource codesource) {
        return AllPermissionsPolicy.SINGLETON.getPermissions(codesource);
    }

    @Substitute
    @SuppressWarnings("deprecation") // deprecated starting JDK 17
    private PermissionCollection engineGetPermissions(ProtectionDomain d) {
        return AllPermissionsPolicy.SINGLETON.getPermissions(d);
    }

    @Substitute
    @SuppressWarnings("deprecation") // deprecated starting JDK 17
    private boolean engineImplies(ProtectionDomain d, Permission p) {
        return AllPermissionsPolicy.SINGLETON.implies(d, p);
    }

    @Substitute
    private void engineRefresh() {
        AllPermissionsPolicy.SINGLETON.refresh();
    }
}

@Delete("Substrate VM does not use SecurityManager, so loading a security policy file would be misleading")
@TargetClass(className = "sun.security.provider.PolicyFile")
final class Target_sun_security_provider_PolicyFile {
}

@TargetClass(className = "sun.security.jca.ProviderConfig")
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

@TargetClass(className = "sun.security.jca.Providers")
final class Target_sun_security_jca_Providers {
    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ProviderListTransformer.class, disableCaching = true)//
    private static ProviderList providerList;

    private static class ProviderListTransformer implements FieldValueTransformer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            ProviderList originalProviderList = (ProviderList) originalValue;
            return SecurityProvidersFilter.instance().cleanUnregisteredProviders(originalProviderList);
        }
    }
}

/** Dummy class to have a class with the file's name. */
public final class SecuritySubstitutions {
}
