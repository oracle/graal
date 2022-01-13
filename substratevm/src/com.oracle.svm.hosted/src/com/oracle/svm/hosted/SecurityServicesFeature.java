/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import static com.oracle.svm.hosted.SecurityServicesFeature.SecurityServicesPrinter.dedent;
import static com.oracle.svm.hosted.SecurityServicesFeature.SecurityServicesPrinter.indent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AlgorithmParameterGenerator;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Policy;
import java.security.Provider;
import java.security.Provider.Service;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathValidator;
import java.security.cert.CertStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.login.Configuration;
import javax.security.sasl.SaslClientFactory;
import javax.security.sasl.SaslServerFactory;
import javax.smartcardio.TerminalFactory;
import javax.xml.crypto.dsig.TransformService;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.TypeResult;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.jdk.SecurityProvidersFilter;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import sun.security.jca.ProviderList;
import sun.security.provider.NativePRNG;
import sun.security.x509.OIDMap;

@AutomaticFeature
public class SecurityServicesFeature extends JNIRegistrationUtil implements Feature, SecurityProvidersFilter {

    public static class Options {
        @Option(help = "Enable automatic registration of security services.")//
        public static final HostedOptionKey<Boolean> EnableSecurityServicesFeature = new HostedOptionKey<>(true);

        @Option(help = "Enable tracing of security services automatic registration.")//
        public static final HostedOptionKey<Boolean> TraceSecurityServices = new HostedOptionKey<>(false);

        @Option(help = "Comma-separated list of additional security service types (fully qualified class names) for automatic registration. Note that these must be JCA compliant.")//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> AdditionalSecurityServiceTypes = new HostedOptionKey<>(new LocatableMultiOptionValue.Strings());

        @Option(help = "Comma-separated list of additional security provider fully qualified class names to mark as used." +
                        "Note that this option is only necessary if you use custom engine classes not available in JCA that are not JCA compliant.")//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> AdditionalSecurityProviders = new HostedOptionKey<>(new LocatableMultiOptionValue.Strings());
    }

    /*
     * The providers names are defined in Java Cryptography Architecture Oracle Providers
     * Documentation:
     * https://docs.oracle.com/javase/8/docs/technotes/guides/security/SunProviders.html
     * https://docs.oracle.com/en/java/javase/11/security/oracle-providers.html
     *
     * The security services names are defined in Java Cryptography Architecture Standard Algorithm
     * Name Documentation:
     * https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html.
     * https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html
     */
    private static final String SECURE_RANDOM_SERVICE = "SecureRandom";
    private static final String SIGNATURE_SERVICE = "Signature";
    private static final String CIPHER_SERVICE = "Cipher";
    private static final String KEY_AGREEMENT_SERVICE = "KeyAgreement";
    private static final String KEY_STORE = "KeyStore";
    private static final String CERTIFICATE_FACTORY = "CertificateFactory";
    private static final String JKS = "JKS";
    private static final String X509 = "X.509";
    private static final String[] emptyStringArray = new String[0];

    /** The list of known service classes defined by the JCA. */
    private static final Class<?>[] knownServices = {AlgorithmParameterGenerator.class, AlgorithmParameters.class,
                    CertPathBuilder.class, CertPathValidator.class, CertStore.class, CertificateFactory.class,
                    Cipher.class, Configuration.class, KeyAgreement.class, KeyFactory.class,
                    KeyGenerator.class, KeyInfoFactory.class, KeyManagerFactory.class, KeyPairGenerator.class,
                    KeyStore.class, Mac.class, MessageDigest.class, Policy.class, SSLContext.class,
                    SaslClientFactory.class, SaslServerFactory.class, SecretKeyFactory.class, SecureRandom.class, Signature.class,
                    TerminalFactory.class, TransformService.class, TrustManagerFactory.class, XMLSignatureFactory.class};

    private ImageClassLoader loader;
    /** Given a service type will return its constructor parameters, if any. */
    private Function<String, Class<?>> ctrParamClassAccessor;
    /** Access Security.getSpiClass. */
    private Method getSpiClassMethod;
    /** All available services, organized by service type. */
    private Map<String, Set<Service>> availableServices;

    /** All providers deemed to be used by this feature. */
    private final Set<Provider> usedProviders = new HashSet<>();

    /** Providers marked as used by the user. */
    private final Set<String> manuallyMarkedUsedProviderClassNames = new HashSet<>();

    /** Provider verification cache cleaner that removes unused providers from the cache. */
    private Function<Object, Object> verificationCacheCleaner;

    /** Provider verification cache that contains only used providers. */
    private Object filteredVerificationCache;

    /** Provider list that contains only used providers. */
    private ProviderList filteredProviderList;
    /** List of providers deemed not to be used by this feature. */
    private List<Provider> removedProviders;

    private boolean shouldFilterProviders = true;

    @Override
    public void afterRegistration(AfterRegistrationAccess a) {
        ModuleSupport.exportAndOpenPackageToClass("java.base", "sun.security.x509", false, getClass());
        ModuleSupport.openModuleByClass(Security.class, getClass());
        disableExperimentalFipsMode(a);
        ImageSingletons.add(SecurityProvidersFilter.class, this);
    }

    /**
     * The SunJSSE provider had a experimental feature that bound to a FIPS crypto provider. This
     * has been removed in JDK-8217835. We disabled explicitly here by calling SunJSSE.isFIPS(). If
     * it was already enabled that's an error.
     */
    private static void disableExperimentalFipsMode(AfterRegistrationAccess a) {
        if (JavaVersionUtil.JAVA_SPEC <= 11) {
            try {
                Boolean isFIPS = (Boolean) method(a, "sun.security.ssl.SunJSSE", "isFIPS").invoke(null);
                VMError.guarantee(!isFIPS, "SunJSSE is already initialized in experimental FIPS mode.");
            } catch (IllegalAccessException | InvocationTargetException e) {
                VMError.shouldNotReachHere(e);
            }
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;
        addManuallyConfiguredUsedProviders(a);

        RuntimeClassInitializationSupport rci = ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
        /*
         * The SecureRandom implementations open the /dev/random and /dev/urandom files which are
         * used as sources for entropy. These files are opened in the static initializers. That's
         * why we rerun the static initializers at runtime. We cannot completely delay the static
         * initializers execution to runtime because the SecureRandom classes are needed by the
         * native image generator too, e.g., by Files.createTempDirectory().
         */
        rci.rerunInitialization(NativePRNG.class, "for substitutions");
        rci.rerunInitialization(NativePRNG.Blocking.class, "for substitutions");
        rci.rerunInitialization(NativePRNG.NonBlocking.class, "for substitutions");

        rci.rerunInitialization(clazz(access, "sun.security.provider.SeedGenerator"), "for substitutions");
        rci.rerunInitialization(clazz(access, "sun.security.provider.SecureRandom$SeederHolder"), "for substitutions");

        /*
         * sun.security.provider.AbstractDrbg$SeederHolder has a static final EntropySource seeder
         * field that needs to be re-initialized at run time because it captures the result of
         * SeedGenerator.getSystemEntropy().
         */
        rci.rerunInitialization(clazz(access, "sun.security.provider.AbstractDrbg$SeederHolder"), "for substitutions");
        if (isWindows()) {
            /* PRNG.<clinit> creates a Cleaner (see JDK-8210476), which starts its thread. */
            rci.rerunInitialization(clazz(access, "sun.security.mscapi.PRNG"), "for substitutions");
        }
        rci.rerunInitialization(clazz(access, "sun.security.provider.FileInputStreamPool"), "for substitutions");
        /* java.util.UUID$Holder has a static final SecureRandom field. */
        rci.rerunInitialization(clazz(access, "java.util.UUID$Holder"), "for substitutions");

        /*
         * The classes below have a static final SecureRandom field. Note that if the classes are
         * not found as reachable by the analysis registering them for class initialization rerun
         * doesn't have any effect.
         */
        rci.rerunInitialization(clazz(access, "sun.security.jca.JCAUtil$CachedSecureRandomHolder"), "for substitutions");
        rci.rerunInitialization(clazz(access, "com.sun.crypto.provider.SunJCE$SecureRandomHolder"), "for substitutions");
        rci.rerunInitialization(clazz(access, "sun.security.krb5.Confounder"), "for substitutions");

        if (JavaVersionUtil.JAVA_SPEC >= 17) {
            rci.rerunInitialization(clazz(access, "sun.security.jca.JCAUtil"), "JCAUtil.def holds a SecureRandom.");
        }

        /*
         * When SSLContextImpl$DefaultManagersHolder sets-up the TrustManager in its initializer it
         * gets the value of the -Djavax.net.ssl.trustStore and -Djavax.net.ssl.trustStorePassword
         * properties from the build machine. Re-runing its initialization at run time is required
         * to use the run time provided values.
         */
        rci.rerunInitialization(clazz(access, "sun.security.ssl.SSLContextImpl$DefaultManagersHolder"), "for reading properties at run time");

        /*
         * SSL debug logging enabled by javax.net.debug system property is setup during the class
         * initialization of either sun.security.ssl.Debug or sun.security.ssl.SSLLogger. (In JDK 8
         * this was implemented in sun.security.ssl.Debug, the logic was moved to
         * sun.security.ssl.SSLLogger in JDK11 but not yet backported to all JDKs. See JDK-8196584
         * for details.) We cannot prevent these classes from being initialized at image build time,
         * so we have to reinitialize them at run time to honour the run time passed value for the
         * javax.net.debug system property.
         */
        optionalClazz(access, "sun.security.ssl.Debug").ifPresent(c -> rci.rerunInitialization(c, "for reading properties at run time"));
        optionalClazz(access, "sun.security.ssl.SSLLogger").ifPresent(c -> rci.rerunInitialization(c, "for reading properties at run time"));
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        loader = ((BeforeAnalysisAccessImpl) access).getImageClassLoader();
        verificationCacheCleaner = constructVerificationCacheCleaner();

        if (Options.EnableSecurityServicesFeature.getValue()) {
            registerServiceReachabilityHandlers(access);
        }

        if (JavaVersionUtil.JAVA_SPEC <= 11) {
            // https://bugs.openjdk.java.net/browse/JDK-8235710
            access.registerReachabilityHandler(SecurityServicesFeature::linkSunEC,
                            method(access, "sun.security.ec.ECDSASignature", "signDigest", byte[].class, byte[].class, byte[].class, byte[].class, int.class),
                            method(access, "sun.security.ec.ECDSASignature", "verifySignedDigest", byte[].class, byte[].class, byte[].class, byte[].class));
            /* Ensure native calls to sun_security_ec* will be resolved as builtIn. */
            PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("sun_security_ec");
        }

        if (isPosix()) {
            access.registerReachabilityHandler(SecurityServicesFeature::linkJaas, method(access, "com.sun.security.auth.module.UnixSystem", "getUnixInfo"));
            /* Resolve calls to com_sun_security_auth_module_UnixSystem* as builtIn. */
            PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("com_sun_security_auth_module_UnixSystem");
        }

        if (isWindows()) {
            access.registerReachabilityHandler(SecurityServicesFeature::registerSunMSCAPIConfig, clazz(access, "sun.security.mscapi.SunMSCAPI"));
            // statically linking sunmscapi conflicts with sunEC
            // after the removal of sunEC in jdk 17, we can statically link sunmscapi
            if (JavaVersionUtil.JAVA_SPEC >= 17) {
                access.registerReachabilityHandler(SecurityServicesFeature::linkSunMSCAPI, clazz(access, "sun.security.mscapi.SunMSCAPI"));
                /* Resolve calls to sun_security_mscapi* as builtIn. */
                PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("sun_security_mscapi");
            }
        }
    }

    private void addManuallyConfiguredUsedProviders(DuringSetupAccess access) {
        for (String value : Options.AdditionalSecurityProviders.getValue().values()) {
            for (String className : value.split(",")) {
                Class<?> classByName = access.findClassByName(className);
                UserError.guarantee(classByName != null,
                                "Manually marked security provider class doesn't exist: %s. Make sure that the class name is correct and that the class is on the image builder classpath.", className);
                trace("Marked provider %s as used", className);
                manuallyMarkedUsedProviderClassNames.add(className);
            }
        }
    }

    public boolean shouldRemoveProvider(Provider p) {
        if (usedProviders.contains(p)) {
            return false;
        }
        return !manuallyMarkedUsedProviderClassNames.contains(p.getClass().getName());
    }

    @Override
    public Object cleanVerificationCache(Object cache) {
        if (shouldFilterProviders) {
            Object cleanedCache = verificationCacheCleaner.apply(cache);
            if (filteredVerificationCache == null || !filteredVerificationCache.equals(cleanedCache)) {
                filteredVerificationCache = cleanedCache;
            }
        }
        return filteredVerificationCache;
    }

    @Override
    public ProviderList cleanUnregisteredProviders(ProviderList providerList) {
        if (shouldFilterProviders) {
            List<Provider> filteredProviders = new ArrayList<>(providerList.providers());
            filteredProviders.removeIf(this::shouldRemoveProvider);
            if (filteredProviderList == null || !filteredProviderList.providers().equals(filteredProviders)) {
                filteredProviderList = ProviderList.newList(filteredProviders.toArray(new Provider[0]));
                if (Options.TraceSecurityServices.getValue()) {
                    removedProviders = new ArrayList<>(providerList.providers());
                    removedProviders.removeIf(provider -> !shouldRemoveProvider(provider));
                }
            }
        }
        return filteredProviderList;
    }

    private void traceRemovedProviders() {
        if (removedProviders == null || removedProviders.isEmpty()) {
            trace("No security providers have been removed.");
        } else {
            trace("The following security providers were deemed to be unused and removed:");
            SecurityServicesPrinter.indent();
            trace("ProviderName - ProviderClass");
            for (Provider p : removedProviders) {
                trace("%s - %s", p.getName(), p.getClass().getName());
            }
            SecurityServicesPrinter.dedent();
        }
    }

    private static void linkSunEC(DuringAnalysisAccess a) {
        NativeLibraries nativeLibraries = ((DuringAnalysisAccessImpl) a).getNativeLibraries();
        /* We statically link sunec thus we classify it as builtIn library */
        PlatformNativeLibrarySupport.singleton();
        NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("sunec");

        nativeLibraries.addStaticJniLibrary("sunec");
        if (isPosix()) {
            /* Library sunec depends on stdc++ */
            nativeLibraries.addDynamicNonJniLibrary("stdc++");
        }
    }

    private static void linkSunMSCAPI(DuringAnalysisAccess a) {
        NativeLibraries nativeLibraries = ((FeatureImpl.DuringAnalysisAccessImpl) a).getNativeLibraries();
        /* We statically link sunmscapi thus we classify it as builtIn library */
        NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("sunmscapi");

        nativeLibraries.addStaticJniLibrary("sunmscapi");
        /* Library sunmscapi depends on ncrypt and crypt32 */
        nativeLibraries.addDynamicNonJniLibrary("ncrypt");
        nativeLibraries.addDynamicNonJniLibrary("crypt32");
    }

    private static void registerSunMSCAPIConfig(BeforeAnalysisAccess a) {
        registerForThrowNew(a, "java.security.cert.CertificateParsingException", "java.security.InvalidKeyException",
                        "java.security.KeyException", "java.security.KeyStoreException", "java.security.ProviderException",
                        "java.security.SignatureException", "java.lang.OutOfMemoryError");

        a.registerReachabilityHandler(SecurityServicesFeature::registerLoadKeysOrCertificateChains,
                        method(a, "sun.security.mscapi.CKeyStore", "loadKeysOrCertificateChains", String.class));
        a.registerReachabilityHandler(SecurityServicesFeature::registerGenerateCKeyPair,
                        method(a, "sun.security.mscapi.CKeyPairGenerator$RSA", "generateCKeyPair", String.class, int.class, String.class));
        a.registerReachabilityHandler(SecurityServicesFeature::registerCPrivateKeyOf,
                        method(a, "sun.security.mscapi.CKeyStore", "storePrivateKey", String.class, byte[].class, String.class, int.class));
        a.registerReachabilityHandler(SecurityServicesFeature::registerCPublicKeyOf,
                        method(a, "sun.security.mscapi.CSignature", "importECPublicKey", String.class, byte[].class, int.class),
                        method(a, "sun.security.mscapi.CSignature", "importPublicKey", String.class, byte[].class, int.class));
    }

    private static void registerLoadKeysOrCertificateChains(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(constructor(a, "java.util.ArrayList"));
        JNIRuntimeAccess.register(method(a, "sun.security.mscapi.CKeyStore", "generateCertificate", byte[].class, Collection.class));
        JNIRuntimeAccess.register(method(a, "sun.security.mscapi.CKeyStore", "generateCertificateChain", String.class, Collection.class));
        JNIRuntimeAccess.register(method(a, "sun.security.mscapi.CKeyStore", "generateKeyAndCertificateChain", boolean.class, String.class, long.class, long.class, int.class, Collection.class));
    }

    private static void registerGenerateCKeyPair(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(constructor(a, "sun.security.mscapi.CKeyPair", String.class, long.class, long.class, int.class));
    }

    private static void registerCPrivateKeyOf(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, "sun.security.mscapi.CPrivateKey", "of", String.class, long.class, long.class, int.class));
    }

    private static void registerCPublicKeyOf(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, "sun.security.mscapi.CPublicKey", "of", String.class, long.class, long.class, int.class));
    }

    private static void linkJaas(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, "com.sun.security.auth.module.UnixSystem", "username", "uid", "gid", "groups"));

        NativeLibraries nativeLibraries = ((DuringAnalysisAccessImpl) a).getNativeLibraries();
        /* We can statically link jaas, thus we classify it as builtIn library */
        NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary("jaas");
        nativeLibraries.addStaticJniLibrary("jaas");
    }

    private static Set<Class<?>> computeKnownServices(BeforeAnalysisAccess access) {
        Set<Class<?>> allKnownServices = new HashSet<>(Arrays.asList(knownServices));
        for (String value : Options.AdditionalSecurityServiceTypes.getValue().values()) {
            for (String serviceClazzName : value.split(",")) {
                Class<?> serviceClazz = access.findClassByName(serviceClazzName);
                UserError.guarantee(serviceClazz != null, "Unable to find additional security service class %s", serviceClazzName);
                allKnownServices.add(serviceClazz);
            }
        }
        return allKnownServices;
    }

    private void registerServiceReachabilityHandlers(BeforeAnalysisAccess access) {
        ctrParamClassAccessor = getConstructorParameterClassAccessor(loader);
        getSpiClassMethod = getSpiClassMethod();
        availableServices = computeAvailableServices();

        /*
         * The JCA defines the list of standard service classes available in the JDK. Each service
         * class implements a series of getInstance() factory methods which return concrete service
         * implementations, retrieved from the installed Provider objects. When a specific algorithm
         * type is requested at run time, by calling one of the getInstance() methods, the JCA finds
         * the corresponding implementation class by searching each provider's database and uses
         * reflection to instantiate the concrete service class. This API is the recommended
         * mechanism for requesting cryptographic services.
         *
         * The logic below registers reachability handlers for getInstance() methods of all service
         * classes. When one of the getInstance() methods becomes reachable all concrete
         * implementation classes corresponding to that service type are registered for reflection.
         *
         * See: https://docs.oracle.com/en/java/javase/11/security/index.html for more details.
         */

        for (Class<?> serviceClass : computeKnownServices(access)) {
            BiConsumer<DuringAnalysisAccess, Executable> handler = (a, t) -> registerServices(a, t, serviceClass);
            for (Method method : serviceClass.getMethods()) {
                if (method.getName().equals("getInstance")) {
                    checkGetInstanceMethod(method);
                    /* The handler will be executed only once if any of the methods is triggered. */
                    access.registerMethodOverrideReachabilityHandler(handler, method);
                }
            }
        }

        /*
         * On Oracle JDK the SecureRandom service implementations are not automatically discovered
         * by the mechanism above because SecureRandom.getInstance() is not invoked. For example
         * java.security.SecureRandom.getDefaultPRNG() calls
         * java.security.Provider.Service.newInstance() directly. On Open JDK
         * SecureRandom.getInstance() is used instead.
         */
        Optional<Method> defaultSecureRandomService = optionalMethod(access, "java.security.Provider", "getDefaultSecureRandomService");
        defaultSecureRandomService.ifPresent(m -> access.registerMethodOverrideReachabilityHandler((a, t) -> registerServices(a, t, SECURE_RANDOM_SERVICE), m));
    }

    private void registerServices(DuringAnalysisAccess access, Object trigger, Class<?> serviceClass) {
        /*
         * SPI classes, i.e., base classes for concrete service implementations, such as
         * java.security.MessageDigestSpi, can be dynamically loaded to double-check the base type
         * of a newly allocated SPI object. This only applies to SPIs in the java.security package,
         * but not any of its sub-packages. See java.security.Security.getSpiClass().
         */
        // Checkstyle: allow Class.getSimpleName
        String serviceType = serviceClass.getSimpleName();
        // Checkstyle: disallow Class.getSimpleName
        if (serviceClass.getPackage().getName().equals("java.security")) {
            registerSpiClass(getSpiClassMethod, serviceType);
        }
        registerServices(access, trigger, serviceType);
    }

    ConcurrentHashMap<String, Boolean> processedServiceClasses = new ConcurrentHashMap<>();

    private void registerServices(DuringAnalysisAccess access, Object trigger, String serviceType) {
        /*
         * registerMethodOverrideReachabilityHandler() invokes the callback "once during analysis
         * for each time a method that overrides the specified param baseMethod is determined to be
         * reachable at run time", therefore we need to make sure that each serviceClass is
         * processed only once.
         */
        processedServiceClasses.computeIfAbsent(serviceType, k -> {
            doRegisterServices(access, trigger, serviceType);
            return true;
        });
    }

    @SuppressWarnings("try")
    private void doRegisterServices(DuringAnalysisAccess access, Object trigger, String serviceType) {
        try (TracingAutoCloseable ignored = trace(access, trigger, serviceType)) {
            Set<Service> services = availableServices.get(serviceType);
            for (Service service : services) {
                registerService(service);
            }
        }
    }

    /** Service.getInstance() methods must be public static and should return the service type. */
    private static void checkGetInstanceMethod(Method method) {
        VMError.guarantee(Modifier.isPublic(method.getModifiers()));
        VMError.guarantee(Modifier.isStatic(method.getModifiers()));
        VMError.guarantee(method.getReturnType().equals(method.getDeclaringClass()));
    }

    /**
     * Collect available services, organized by service type. JDK doesn't have a way to iterate
     * services by type so we need to build our own structure.
     */
    private static Map<String, Set<Service>> computeAvailableServices() {
        Map<String, Set<Service>> availableServices = new HashMap<>();
        for (Provider provider : Security.getProviders()) {
            for (Service s : provider.getServices()) {
                availableServices.computeIfAbsent(s.getType(), t -> new HashSet<>()).add(s);
            }
        }
        return availableServices;
    }

    /**
     * Return a Function which given the serviceType as a String will return the corresponding
     * constructor parameter Class, or null.
     */
    private static Function<String, Class<?>> getConstructorParameterClassAccessor(ImageClassLoader loader) {
        Map<String, /* EngineDescription */ Object> knownEngines = ReflectionUtil.readStaticField(Provider.class, "knownEngines");
        Class<?> clazz = loader.findClassOrFail("java.security.Provider$EngineDescription");
        Field consParamClassNameField = ReflectionUtil.lookupField(clazz, "constructorParameterClassName");

        /*
         * The returned lambda captures the value of the Provider.knownEngines map retrieved above
         * and it uses it to find the parameterClass corresponding to the serviceType parameter.
         */
        return (serviceType) -> {
            try {
                /*
                 * Access the Provider.knownEngines map and extract the EngineDescription
                 * corresponding to the serviceType. Note that the map holds EngineDescription(s) of
                 * only those service types that are shipped in the JDK. From the EngineDescription
                 * object extract the value of the constructorParameterClassName field then, if the
                 * class name is not null, get the corresponding Class<?> object and return it.
                 */
                /* EngineDescription */
                Object engineDescription = knownEngines.get(serviceType);
                /*
                 * This isn't an engine known to the Provider (which actually means that it isn't
                 * one that's shipped in the JDK), so we don't have the predetermined knowledge of
                 * the constructor param class.
                 */
                if (engineDescription == null) {
                    return null;
                }
                String constrParamClassName = (String) consParamClassNameField.get(engineDescription);
                if (constrParamClassName != null) {
                    return loader.findClass(constrParamClassName).get();
                }
            } catch (IllegalAccessException e) {
                VMError.shouldNotReachHere(e);
            }
            return null;
        };
    }

    private static Method getSpiClassMethod() {
        try {
            Method method = Security.class.getDeclaredMethod("getSpiClass", String.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    /**
     * Given a service class, e.g., MessageDigest, returns the corresponding SPI class, e.g.,
     * MessageDigestSpi. Only available for SPIs in the java.security package, but not any of its
     * sub-packages.
     */
    private static void registerSpiClass(Method getSpiClassMethod, String serviceType) {
        try {
            Class<?> spiClass = (Class<?>) getSpiClassMethod.invoke(null, serviceType);
            /* The constructor doesn't need to be registered, objects are not allocated. */
            RuntimeReflection.register(spiClass);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private void registerProvider(Provider provider) {
        if (!usedProviders.contains(provider)) {
            usedProviders.add(provider);
            registerForReflection(provider.getClass());

            try {
                Method getVerificationResult = ReflectionUtil.lookupMethod(loader.findClassOrFail("javax.crypto.JceSecurity"), "getVerificationResult", Provider.class);
                /*
                 * Trigger initialization of JceSecurity.verificationResults used by
                 * JceSecurity.canUseProvider() at runtime to check whether a provider is properly
                 * signed and can be used by JCE. It does that via jar verification which we cannot
                 * support. See also Target_javax_crypto_JceSecurity.
                 */
                getVerificationResult.invoke(null, provider);
            } catch (ReflectiveOperationException ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }
    }

    @SuppressWarnings("try")
    private void registerService(Service service) {
        TypeResult<Class<?>> serviceClassResult = loader.findClass(service.getClassName());
        if (serviceClassResult.isPresent()) {
            try (TracingAutoCloseable ignored = trace(service)) {
                registerForReflection(serviceClassResult.get());

                Class<?> ctrParamClass = ctrParamClassAccessor.apply(service.getType());
                if (ctrParamClass != null) {
                    registerForReflection(ctrParamClass);
                    trace("Registered service constructor parameter class: %s", ctrParamClass.getName());
                }

                if (isSignature(service) || isCipher(service) || isKeyAgreement(service)) {
                    for (String keyClassName : getSupportedKeyClasses(service)) {
                        loader.findClass(keyClassName).ifPresent(SecurityServicesFeature::registerForReflection);
                    }
                }
                if (isKeyStore(service) && service.getAlgorithm().equals(JKS)) {
                    registerJks(loader);
                }
                if (isCertificateFactory(service) && service.getAlgorithm().equals(X509)) {
                    registerX509Extensions();
                }
                registerProvider(service.getProvider());
            }
        } else {
            trace("Cannot register service %s. Reason: %s.", asString(service), serviceClassResult.getException());
        }
    }

    /**
     * Register the default JavaKeyStore, JKS, for reflection. It is not registered as a key store
     * implementation in any provider but it is registered as a primary key store for
     * JavaKeyStore$DualFormatJKS, i.e., the KeyStore.JKS implementation class in the SUN provider,
     * and dynamically allocated by sun.security.provider.KeyStoreDelegator.engineLoad().
     */
    private static void registerJks(ImageClassLoader loader) {
        Class<?> javaKeyStoreJks = loader.findClassOrFail("sun.security.provider.JavaKeyStore$JKS");
        registerForReflection(javaKeyStoreJks);
        trace("Registered KeyStore.JKS implementation class: %s", javaKeyStoreJks.getName());
    }

    /**
     * Register the x509 certificate extension classes for reflection.
     */
    private static void registerX509Extensions() {
        /*
         * The OIDInfo class which represents the values in the map is not visible. Get the list of
         * extension names through reflection, i.e., the keys in the map, and use the
         * OIDMap.getClass(name) API to get the extension classes.
         */
        Map<String, Object> map = ReflectionUtil.readStaticField(OIDMap.class, "nameMap");
        for (String name : map.keySet()) {
            try {
                Class<?> extensionClass = OIDMap.getClass(name);
                assert sun.security.x509.Extension.class.isAssignableFrom(extensionClass);
                registerForReflection(extensionClass);
                trace("Registered X.509 extension class: %s", extensionClass.getName());
            } catch (CertificateException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        traceRemovedProviders();
        SecurityServicesPrinter.endTracing();
        shouldFilterProviders = false;
    }

    private static void registerForReflection(Class<?> clazz) {
        RuntimeReflection.register(clazz);
        RuntimeReflection.register(clazz.getConstructors());
    }

    @SuppressWarnings("unchecked")
    private Function<Object, Object> constructVerificationCacheCleaner() {
        /*
         * For JDK 11, the verification cache is a Provider -> Verification result IdentityHashMap.
         */
        if (JavaVersionUtil.JAVA_SPEC <= 11) {
            return obj -> {
                Map<Provider, Object> original = (Map<Provider, Object>) obj;
                Map<Provider, Object> verificationResults = new IdentityHashMap<>(original);

                verificationResults.keySet().removeIf(this::shouldRemoveProvider);

                return verificationResults;
            };
        }
        /*
         * For JDK 17 and later, the verification cache is an IdentityWrapper -> Verification result
         * ConcurrentHashMap. The IdentityWrapper contains the actual provider in the 'obj' field.
         */
        Class<?> identityWrapper = loader.findClassOrFail("javax.crypto.JceSecurity$IdentityWrapper");
        Field providerField = ReflectionUtil.lookupField(identityWrapper, "obj");

        Predicate<Object> listRemovalPredicate = wrapper -> {
            try {
                return shouldRemoveProvider((Provider) providerField.get(wrapper));
            } catch (IllegalAccessException e) {
                throw VMError.shouldNotReachHere(e);
            }
        };

        return obj -> {
            Map<Object, Object> original = (Map<Object, Object>) obj;
            Map<Object, Object> verificationResults = new ConcurrentHashMap<>(original);

            verificationResults.keySet().removeIf(listRemovalPredicate);

            return verificationResults;
        };

    }

    private static boolean isSignature(Service s) {
        return s.getType().equals(SIGNATURE_SERVICE);
    }

    private static boolean isCipher(Service s) {
        return s.getType().equals(CIPHER_SERVICE);
    }

    private static boolean isKeyAgreement(Service s) {
        return s.getType().equals(KEY_AGREEMENT_SERVICE);
    }

    private static boolean isKeyStore(Service s) {
        return s.getType().equals(KEY_STORE);
    }

    private static boolean isCertificateFactory(Service s) {
        return s.getType().equals(CERTIFICATE_FACTORY);
    }

    private static String[] getSupportedKeyClasses(Service s) {
        assert isSignature(s) || isCipher(s) || isKeyAgreement(s);
        String supportedKeyClasses = s.getAttribute("SupportedKeyClasses");
        if (supportedKeyClasses != null) {
            return supportedKeyClasses.split("\\|");
        }
        return emptyStringArray;
    }

    private static void trace(String format, Object... args) {
        SecurityServicesPrinter.trace((indent) -> indent + String.format(format + "%n", args));
    }

    abstract static class TracingAutoCloseable implements AutoCloseable {
        /** Without declaring close() javac complains that the try-with-resource needs a catch. */
        @Override
        public abstract void close();
    }

    private static TracingAutoCloseable trace(Service service) {
        return new TracingAutoCloseable() {
            {
                SecurityServicesPrinter.trace((indent) -> String.format("%s%s%n", indent, asString(service)));
                indent();
            }

            @Override
            public void close() {
                dedent();
            }
        };
    }

    private static TracingAutoCloseable trace(DuringAnalysisAccess a, Object trigger, String serviceType) {
        return new TracingAutoCloseable() {
            {
                indent();
                SecurityServicesPrinter.trace((indent) -> {
                    Method method = (Method) trigger;
                    DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;
                    AnalysisMethod analysisMethod = access.getMetaAccess().lookupJavaMethod(method);
                    String msg = String.format("Service factory method %s is reachable.%n", analysisMethod.format("%H.%n(%P)"));
                    msg += String.format("%sAnalysis parsing context: %s", indent, ReportUtils.parsingContext(analysisMethod, indent + "    "));
                    msg += String.format("%sReachability of %s service type API triggers registration of following services:%n", indent, serviceType);
                    return msg;
                });
                indent();
            }

            @Override
            public void close() {
                dedent();
                dedent();
                trace("");
            }
        };
    }

    private static String asString(Service s) {
        String str = "";
        str += "Type: " + s.getType() + ", ";
        str += "Provider: " + s.getProvider().getName() + ", ";
        str += "Algorithm: " + s.getAlgorithm() + ", ";
        str += "Class: " + s.getClassName();
        if (isSignature(s) || isCipher(s) || isKeyAgreement(s)) {
            str += ", " + "SupportedKeyClasses: " + Arrays.toString(getSupportedKeyClasses(s));
        }
        return str;
    }

    static class SecurityServicesPrinter {
        private static final int INDENT = 4;
        private static final boolean enabled = Options.TraceSecurityServices.getValue();
        private static final SecurityServicesPrinter instance = enabled ? new SecurityServicesPrinter() : null;

        private final PrintWriter writer;
        private int indent = 0;

        SecurityServicesPrinter() {
            File reportFile = reportFile(SubstrateOptions.reportsPath());
            System.out.println("# Printing security services automatic registration to: " + reportFile);
            try {
                writer = new PrintWriter(new FileWriter(reportFile));
            } catch (IOException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

        static void trace(Function<String, String> strFunction) {
            if (enabled) {
                String indent = "";
                if (instance.indent > 0) {
                    indent = String.format("%" + instance.indent + "s", " ");
                }
                instance.writer.print(strFunction.apply(indent));
            }
        }

        static void indent() {
            if (enabled) {
                instance.indent += INDENT;
            }
        }

        static void dedent() {
            if (enabled) {
                instance.indent -= INDENT;
            }
        }

        static void endTracing() {
            if (enabled) {
                instance.writer.close();
            }
        }

        private static File reportFile(String reportsPath) {
            return ReportUtils.reportFile(reportsPath, "security_services", "txt");
        }
    }
}
