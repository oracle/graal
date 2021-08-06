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

import java.net.URL;
import java.security.AccessControlContext;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.security.Provider;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import org.graalvm.compiler.phases.common.LazyValue;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.thread.Target_java_lang_Thread;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

// Checkstyle: stop
import sun.security.jca.ProviderList;
import sun.security.util.SecurityConstants;
// Checkstyle: resume

// Checkstyle: allow reflection

/*
 * All security checks are disabled.
 */

@TargetClass(java.security.AccessController.class)
@SuppressWarnings({"unused"})
final class Target_java_security_AccessController {

    @Substitute
    @TargetElement(onlyWith = JDK11OrEarlier.class)
    public static <T> T doPrivileged(PrivilegedAction<T> action) throws Throwable {
        return executePrivileged(action, null, Target_jdk_internal_reflect_Reflection.getCallerClass());
    }

    @Substitute
    @TargetElement(onlyWith = JDK11OrEarlier.class)
    public static <T> T doPrivileged(PrivilegedAction<T> action, AccessControlContext context) throws Throwable {
        Class<?> caller = Target_jdk_internal_reflect_Reflection.getCallerClass();
        AccessControlContext acc = checkContext(context, caller);
        return executePrivileged(action, acc, caller);
    }

    @Substitute
    @TargetElement(onlyWith = JDK11OrEarlier.class)
    public static <T> T doPrivileged(PrivilegedExceptionAction<T> action) throws Throwable {
        Class<?> caller = Target_jdk_internal_reflect_Reflection.getCallerClass();
        return executePrivileged(action, null, caller);
    }

    @Substitute
    @TargetElement(onlyWith = JDK11OrEarlier.class)
    static <T> T doPrivileged(PrivilegedExceptionAction<T> action, AccessControlContext context) throws Throwable {
        Class<?> caller = Target_jdk_internal_reflect_Reflection.getCallerClass();
        AccessControlContext acc = checkContext(context, caller);
        return executePrivileged(action, acc, caller);
    }

    @Substitute
    static AccessControlContext getStackAccessControlContext() {
        return StackAccessControlContextVisitor.getFromStack();
    }

    @Substitute
    static AccessControlContext getInheritedAccessControlContext() {
        return SubstrateUtil.cast(Thread.currentThread(), Target_java_lang_Thread.class).inheritedAccessControlContext;
    }

    @Substitute
    @TargetElement(onlyWith = JDK14OrLater.class)
    private static ProtectionDomain getProtectionDomain(final Class<?> caller) {
        return caller.getProtectionDomain();
    }

    @Substitute
    @TargetElement(onlyWith = JDK14OrLater.class)
    static <T> T executePrivileged(PrivilegedExceptionAction<T> action, AccessControlContext context, Class<?> caller) throws Throwable {
        if (action == null) {
            throw new NullPointerException("Null action");
        }

        if (context != null && context.equals(AccessControllerUtil.NO_CONTEXT_SINGLETON)) {
            VMError.shouldNotReachHere("Invoked AccessControlContext was replaced at build time but wasn't reinitialized at run time.");
        }

        AccessControllerUtil.PrivilegedStack.push(context, caller);
        try {
            return action.run();
        } catch (Exception e) {
            throw AccessControllerUtil.wrapCheckedException(e);
        } finally {
            AccessControllerUtil.PrivilegedStack.pop();
        }
    }

    @Substitute
    @TargetElement(onlyWith = JDK14OrLater.class)
    static <T> T executePrivileged(PrivilegedAction<T> action, AccessControlContext context, Class<?> caller) throws Throwable {
        if (action == null) {
            throw new NullPointerException("Null action");
        }

        if (context != null && context.equals(AccessControllerUtil.NO_CONTEXT_SINGLETON)) {
            VMError.shouldNotReachHere("Invoked AccessControlContext was replaced at build time but wasn't reinitialized at run time.");
        }

        AccessControllerUtil.PrivilegedStack.push(context, caller);
        try {
            return action.run();
        } catch (Exception e) {
            throw AccessControllerUtil.wrapCheckedException(e);
        } finally {
            AccessControllerUtil.PrivilegedStack.pop();
        }
    }

    @Substitute
    @TargetElement(onlyWith = JDK14OrLater.class)
    @SuppressWarnings("unused")
    static AccessControlContext checkContext(AccessControlContext context, Class<?> caller) {
        // check if caller is authorized to create context
        if (System.getSecurityManager() != null) {
            throw VMError.shouldNotReachHere("Needs to be implemented when SecurityManager is supported");
        }
        return context;
    }
}

@InternalVMMethod
@SuppressWarnings("unused")
class AccessControllerUtil {

    static final AccessControlContext NO_CONTEXT_SINGLETON;

    static {
        try {
            NO_CONTEXT_SINGLETON = ReflectionUtil.lookupConstructor(AccessControlContext.class, ProtectionDomain[].class, boolean.class).newInstance(new ProtectionDomain[0], true);
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    static class INNOCUOUS_ACC {
        static LazyValue<AccessControlContext> acc = new LazyValue<>(() -> new AccessControlContext(new ProtectionDomain[]{new ProtectionDomain(null, null)}));

        static AccessControlContext get() {
            return acc.get();
        }

        static void set(AccessControlContext ctx) {
        }
    }

    static class NO_PERMISSIONS_CONTEXT {
        static LazyValue<AccessControlContext> acc = new LazyValue<>(() -> AccessControllerUtil.contextWithPermissions(new Permission[0]));

        static AccessControlContext get() {
            return acc.get();
        }

        static void set(AccessControlContext ctx) {
        }
    }

    static class GET_CLASS_LOADER_CONTEXT {
        static LazyValue<AccessControlContext> acc = new LazyValue<>(() -> AccessControllerUtil.contextWithPermissions(new RuntimePermission("getClassLoader")));

        static AccessControlContext get() {
            return acc.get();
        }

        static void set(AccessControlContext ctx) {
        }
    }

    static class GET_LOOKUP_CONTEXT {
        static LazyValue<AccessControlContext> acc = new LazyValue<>(() -> AccessControllerUtil.contextWithPermissions(new RuntimePermission("dynalink.getLookup")));

        static AccessControlContext get() {
            return acc.get();
        }

        static void set(AccessControlContext ctx) {
        }
    }

    public static class PrivilegedStack {

        public static class StackElement {
            protected AccessControlContext context;
            protected Class<?> caller;

            StackElement(AccessControlContext context, Class<?> caller) {
                this.context = context;
                this.caller = caller;
            }

            public AccessControlContext getContext() {
                return context;
            }

            public Class<?> getCaller() {
                return caller;
            }
        }

        @SuppressWarnings("rawtypes") private static final FastThreadLocalObject<ArrayDeque> stack = FastThreadLocalFactory.createObject(ArrayDeque.class);

        @SuppressWarnings("unchecked")
        private static ArrayDeque<StackElement> getStack() {
            ArrayDeque<StackElement> tmp = stack.get();
            if (tmp == null) {
                tmp = new ArrayDeque<>();
                stack.set(tmp);
            }
            return tmp;
        }

        public static void push(AccessControlContext context, Class<?> caller) {
            getStack().push(new StackElement(context, caller));
        }

        public static void pop() {
            getStack().pop();
        }

        public static AccessControlContext peekContext() {
            return Objects.requireNonNull(getStack().peek()).getContext();
        }

        public static Class<?> peekCaller() {
            return Objects.requireNonNull(getStack().peek()).getCaller();
        }

        public static int length() {
            return getStack().size();
        }
    }

    public static AccessControlContext contextWithPermissions(Permission... perms) {
        Permissions permissions = new Permissions();
        for (Permission perm : perms) {
            permissions.add(perm);
        }
        return new AccessControlContext(new ProtectionDomain[]{new ProtectionDomain(null, permissions)});
    }

    static Throwable wrapCheckedException(Throwable ex) {
        if (ex instanceof Exception && !(ex instanceof RuntimeException)) {
            return new PrivilegedActionException((Exception) ex);
        } else {
            return ex;
        }
    }
}

@AutomaticFeature
@SuppressWarnings({"unused"})
class AccessControlContextFeature implements Feature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(AccessControlContextFeature::replaceAccessControlContext);
    }

    private static Object replaceAccessControlContext(Object obj) {
        if (obj instanceof AccessControlContext) {
            return AccessControllerUtil.NO_CONTEXT_SINGLETON;
        }
        return obj;
    }
}

@TargetClass(java.security.AccessControlContext.class)
@SuppressWarnings({"unused"})
final class Target_java_security_AccessControlContext {
    @Alias protected boolean isPrivileged;
    @Alias protected boolean isAuthorized;

    @Alias
    Target_java_security_AccessControlContext(ProtectionDomain[] context, AccessControlContext privilegedContext) {
    }
}

@TargetClass(SecurityManager.class)
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

@Platforms(Platform.WINDOWS.class)
@TargetClass(value = java.security.Provider.class)
final class Target_java_security_Provider {
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

    static void initialize(Target_java_security_Provider provider) {
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

@TargetClass(className = "javax.crypto.ProviderVerifier", onlyWith = JDK11OrLater.class)
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
     * Lazily recompute the RANDOM field at runtime. We cannot push the entire static initialization
     * of JceSecurity to run time because we want the JceSecurity.verificationResults initialized at
     * image build time.
     *
     * This is only used in {@link KeyAgreement}, it's safe to remove.
     */
    @Alias @TargetElement(onlyWith = JDK15OrEarlier.class) //
    @InjectAccessors(JceSecurityAccessor.class) //
    static SecureRandom RANDOM;

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

    @Substitute
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    static void verifyProviderJar(URL var0) {
        throw JceSecurityUtil.shouldNotReach("javax.crypto.JceSecurity.verifyProviderJar(URL)");
    }

    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    static void verifyProvider(URL codeBase, Provider p) {
        throw JceSecurityUtil.shouldNotReach("javax.crypto.JceSecurity.verifyProviderJar(URL, Provider)");
    }

    @Substitute
    static URL getCodeBase(final Class<?> clazz) {
        throw VMError.unsupportedFeature("Trying to access the code base of " + clazz + ". ");
    }

    @Substitute
    static Exception getVerificationResult(Provider p) {
        /* Start code block copied from original method. */
        Object o = verificationResults.get(JceSecurityUtil.providerKey(p));
        if (o == PROVIDER_VERIFIED) {
            return null;
        } else if (o != null) {
            return (Exception) o;
        }
        /* End code block copied from original method. */
        /*
         * If the verification result is not found in the verificationResults map JDK proceeds to
         * verify it. That requires accesing the code base which we don't support. The substitution
         * for getCodeBase() would be enough to take care of this too, but substituting
         * getVerificationResult() allows for a better error message.
         */
        throw VMError.unsupportedFeature("Trying to verify a provider that was not registered at build time: " + p + ". " +
                        "All providers must be registered and verified in the Native Image builder. ");
    }

    private static class VerificationCacheTransformer implements RecomputeFieldValue.CustomFieldValueTransformer {
        @Override
        public Object transform(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver, Object originalValue) {
            return SecurityProvidersFilter.instance().cleanVerificationCache(originalValue);
        }
    }
}

@TargetClass(className = "javax.crypto.JceSecurity", innerClass = "IdentityWrapper", onlyWith = JDK16OrLater.class)
@SuppressWarnings({"unused"})
final class Target_javax_crypto_JceSecurity_IdentityWrapper {
    @Alias //
    Provider obj;

    @Alias //
    Target_javax_crypto_JceSecurity_IdentityWrapper(Provider obj) {
        this.obj = obj;
    }
}

class JceSecurityAccessor {
    private static volatile SecureRandom RANDOM;

    @SuppressWarnings({"unused"})
    static SecureRandom get() {
        SecureRandom result = RANDOM;
        if (result == null) {
            /* Lazy initialization on first access. */
            result = initializeOnce();
        }
        return result;
    }

    // Checkstyle: stop
    private static synchronized SecureRandom initializeOnce() {
        // Checkstyle: resume

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

final class JceSecurityUtil {

    static Object providerKey(Provider p) {
        if (JavaVersionUtil.JAVA_SPEC < 16) {
            return p;
        }
        /* Starting with JDK 16 the verification results map key is an identity wrapper object. */
        return new Target_javax_crypto_JceSecurity_IdentityWrapper(p);
    }

    static RuntimeException shouldNotReach(String method) {
        throw VMError.shouldNotReachHere(method + " is reached at runtime. " +
                        "This should not happen. The contents of JceSecurity.verificationResults " +
                        "are computed and cached at image build time.");
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
        throw VMError.unimplemented();
    }

    @Substitute
    @TargetElement(onlyWith = ContainsVerifyJars.class)
    private void verifyJars(URL var1, List<String> var2) {
        throw VMError.unimplemented();
    }
}

/** A predicate to tell whether this platform includes the argument class. */
final class PlatformHasClass implements Predicate<String> {
    @Override
    public boolean test(String className) {
        try {
            @SuppressWarnings({"unused"})
            /* { Allow use of `Class.forName`. Checkstyle: stop. */
            final Class<?> classForName = Class.forName(className);
            /* } Allow use of `Class.forName`. Checkstyle: resume. */
            return true;
        } catch (ClassNotFoundException cnfe) {
            return false;
        }
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
@SuppressWarnings({"unused"})
final class Target_java_security_Policy {

    @Delete @TargetElement(onlyWith = JDK8OrEarlier.class) //
    private static AtomicReference<?> policy;

    @Delete @TargetElement(onlyWith = JDK11OrLater.class) //
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
    public PermissionCollection getPermissions(CodeSource codesource) {
        return allPermissions();
    }

    @Override
    public PermissionCollection getPermissions(ProtectionDomain domain) {
        return allPermissions();
    }

    @Override
    public boolean implies(ProtectionDomain domain, Permission permission) {
        return true;
    }
}

/**
 * This class is instantiated indirectly from the {@link Policy#getInstance} methods via the
 * {@link java.security.Security#getProviders security provider} abstractions. We could just
 * substitute the {@link Policy#getInstance} methods to return
 * {@link AllPermissionsPolicy#SINGLETON}, this version is more fool-proof in case someone manually
 * registers security providers for reflective instantiation.
 */
@TargetClass(className = "sun.security.provider.PolicySpiFile")
@SuppressWarnings({"unused", "static-method"})
final class Target_sun_security_provider_PolicySpiFile {

    @Substitute
    private Target_sun_security_provider_PolicySpiFile(Policy.Parameters params) {
    }

    @Substitute
    private PermissionCollection engineGetPermissions(CodeSource codesource) {
        return AllPermissionsPolicy.SINGLETON.getPermissions(codesource);
    }

    @Substitute
    private PermissionCollection engineGetPermissions(ProtectionDomain d) {
        return AllPermissionsPolicy.SINGLETON.getPermissions(d);
    }

    @Substitute
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
@SuppressWarnings({"unused"})
final class Target_sun_security_provider_PolicyFile {
}

@TargetClass(className = "sun.security.jca.ProviderConfig")
@SuppressWarnings({"unused", "static-method"})
final class Target_sun_security_jca_ProviderConfig {

    @Alias @TargetElement(onlyWith = JDK11OrLater.class) //
    private String provName;

    @Alias @TargetElement(onlyWith = JDK8OrEarlier.class) //
    private String className;

    /**
     * All security providers used in a native-image must be registered during image build time. At
     * runtime, we shouldn't have a call to doLoadProvider. However, this method is still reachable
     * at runtime, and transitively includes other types in the image, among which is
     * sun.security.jca.ProviderConfig.ProviderLoader. This class contains a static field with a
     * cache of providers loaded during the image build. The contents of this cache can vary even
     * when building the same image due to the way services are loaded on Java 11. This cache can
     * increase the final image size substantially (if it contains, for example,
     * {@link org.jcp.xml.dsig.internal.dom.XMLDSigRI}.
     */
    @Substitute
    @TargetElement(name = "doLoadProvider", onlyWith = JDK11OrLater.class)
    private Provider doLoadProviderJDK11OrLater() {
        throw VMError.unsupportedFeature("Cannot load new security provider at runtime: " + provName + ".");
    }

    @Substitute
    @TargetElement(name = "doLoadProvider", onlyWith = JDK8OrEarlier.class)
    private Provider doLoadProviderJDK8OrEarlier() {
        throw VMError.unsupportedFeature("Cannot load new security provider at runtime: " + className + ".");
    }
}

@SuppressWarnings("unused")
@TargetClass(className = "sun.security.jca.ProviderConfig", innerClass = "ProviderLoader", onlyWith = JDK11OrLater.class)
final class Target_sun_security_jca_ProviderConfig_ProviderLoader {
    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, isFinal = true)//
    static Target_sun_security_jca_ProviderConfig_ProviderLoader INSTANCE;
}

/**
 * This only applies to JDK8 and JDK11. Experimental FIPS mode in the SunJSSE Provider was removed
 * in JDK-8217835. Going forward it is recommended to configure FIPS 140 compliant cryptography
 * providers by using the usual JCA providers configuration mechanism.
 */
@SuppressWarnings("unused")
@TargetClass(value = sun.security.ssl.SunJSSE.class, onlyWith = JDK11OrEarlier.class)
final class Target_sun_security_ssl_SunJSSE {

    @Substitute
    private Target_sun_security_ssl_SunJSSE(java.security.Provider cryptoProvider, String providerName) {
        throw VMError.unsupportedFeature("Experimental FIPS mode in the SunJSSE Provider is deprecated (JDK-8217835)." +
                        " To register a FIPS provider use the supported java.security.Security.addProvider() API.");
    }
}

@TargetClass(className = "sun.security.jca.Providers")
final class Target_sun_security_jca_Providers {
    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ProviderListTransformer.class, disableCaching = true)//
    private static ProviderList providerList;

    private static class ProviderListTransformer implements RecomputeFieldValue.CustomFieldValueTransformer {
        @Override
        public Object transform(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver, Object originalValue) {
            ProviderList originalProviderList = (ProviderList) originalValue;
            return SecurityProvidersFilter.instance().cleanUnregisteredProviders(originalProviderList);
        }
    }
}

/** Dummy class to have a class with the file's name. */
@SuppressWarnings({"unused"})
public final class SecuritySubstitutions {
}
