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

// Checkstyle: allow reflection

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Provider;
import java.security.Provider.Service;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import sun.security.jca.Providers;
import sun.security.provider.NativePRNG;
import sun.security.x509.OIDMap;

@AutomaticFeature
public class SecurityServicesFeature implements Feature {

    static class Options {
        @Option(help = "Enable the feature that provides support for security services.")//
        public static final HostedOptionKey<Boolean> EnableSecurityServicesFeature = new HostedOptionKey<>(true);

        @Option(help = "Enable trace logging for the security services feature.")//
        static final HostedOptionKey<Boolean> TraceSecurityServices = new HostedOptionKey<>(false);
    }

    /*
     * The providers names are defined in Java Cryptography Architecture Oracle Providers
     * Documentation:
     * https://docs.oracle.com/javase/8/docs/technotes/guides/security/SunProviders.html
     */
    private static final String SUN_PROVIDER = "SUN";

    /*
     * The security services names are defined in Java Cryptography Architecture Standard Algorithm
     * Name Documentation:
     * https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html.
     */
    private static final String SECURE_RANDOM_SERVICE = "SecureRandom";
    private static final String MESSAGE_DIGEST_SERVICE = "MessageDigest";
    private static final String SIGNATURE_SERVICE = "Signature";
    private static final String CIPHER_SERVICE = "Cipher";
    private static final String KEY_AGREEMENT_SERVICE = "KeyAgreement";

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return Options.EnableSecurityServicesFeature.getValue();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        /*
         * The SecureRandom implementations open the /dev/random and /dev/urandom files which are
         * used as sources for entropy. These files are opened in the static initializers. That's
         * why we rerun the static initializers at runtime. We cannot completely delay the static
         * initializers execution to runtime because the SecureRandom classes are needed by the
         * native image generator too, e.g., by Files.createTempDirectory().
         */
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(NativePRNG.class, "for substitutions");
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(NativePRNG.Blocking.class, "for substitutions");
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(NativePRNG.NonBlocking.class, "for substitutions");

        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(access.findClassByName("sun.security.provider.SeedGenerator"), "for substitutions");
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(access.findClassByName("sun.security.provider.SecureRandom$SeederHolder"), "for substitutions");

        if (JavaVersionUtil.JAVA_SPEC > 8) {
            ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(access.findClassByName("sun.security.provider.FileInputStreamPool"), "for substitutions");
        }

        /* java.util.UUID$Holder has a static final SecureRandom field. */
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(access.findClassByName("java.util.UUID$Holder"), "for substitutions");

        /*
         * The classes bellow have a static final SecureRandom field. Note that if the classes are
         * not found as reachable by the analysis registering them form class initialization rerun
         * doesn't have any effect.
         */
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(access.findClassByName("sun.security.jca.JCAUtil$CachedSecureRandomHolder"), "for substitutions");
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(access.findClassByName("com.sun.crypto.provider.SunJCE$SecureRandomHolder"), "for substitutions");
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(access.findClassByName("sun.security.krb5.Confounder"), "for substitutions");
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(javax.net.ssl.SSLContext.class, "for substitutions");

        if (SubstrateOptions.EnableAllSecurityServices.getValue()) {
            /* Prepare SunEC native library access. */
            prepareSunEC();
        }
    }

    private static void prepareSunEC() {

        // @formatter:off
        /* Registering byte[] is needed because Java_sun_security_ec_ECKeyPairGenerator_generateECKeyPair looks for it:
         *    baCls = env->FindClass("[B");
         *     if (baCls == NULL) {
         *         goto cleanup;
         *    }
         * If the byte[] is not registered it just silently fails.
         */
        // @formatter:on
        JNIRuntimeAccess.register(byte[].class);
    }

    /** Get the list of configured providers. The SUN provider is returned by default. */
    private static List<Provider> getProviders(boolean enableAllSecurityServices) {
        if (enableAllSecurityServices) {
            /* Parse and instantiate all providers. */
            return Providers.getProviderList().providers();
        } else {
            /*
             * Get only the SUN provider. Avoids parsing the entire providers list and instantiating
             * unused providers.
             */
            Provider sunProvider = Providers.getSunProvider();
            assert isSunProvider(sunProvider);
            return Collections.singletonList(sunProvider);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void beforeAnalysis(BeforeAnalysisAccess access) {

        boolean enableAllSecurityServices = SubstrateOptions.EnableAllSecurityServices.getValue();

        Function<String, Class<?>> consParamClassAccessor = getConsParamClassAccessor(access);

        trace("Registering security services...");
        for (Provider provider : getProviders(enableAllSecurityServices)) {
            register(provider);
            for (Service service : provider.getServices()) {
                if (enableAllSecurityServices || isMessageDigest(service) || isSecureRandom(service)) {
                    /* SecureRandom and MessageDigest SUN services are registered by default. */
                    register(access, service, consParamClassAccessor);
                }
            }
        }

        if (enableAllSecurityServices) {
            /*
             * Register the default JavaKeyStore, JKS. It is not returned by the
             * provider.getServices() enumeration.
             */
            Class<?> javaKeyStoreJks = access.findClassByName("sun.security.provider.JavaKeyStore$JKS");
            registerForReflection(javaKeyStoreJks);
            trace("Class registered for reflection: " + javaKeyStoreJks);

            /* Register the x509 certificate extension classes for reflection. */

            /*
             * The OIDInfo class which represents the values in the map is not visible. Get the list
             * of extension names through reflection, i.e., the keys in the map, and use the
             * OIDMap.getClass(name) API to get the extension classes.
             */
            Map<String, Object> map = ReflectionUtil.readStaticField(OIDMap.class, "nameMap");
            for (String name : map.keySet()) {
                try {
                    Class<?> extensionClass = OIDMap.getClass(name);
                    assert sun.security.x509.Extension.class.isAssignableFrom(extensionClass);
                    registerForReflection(extensionClass);
                    trace("Class registered for reflection: " + extensionClass);
                } catch (CertificateException e) {
                    throw VMError.shouldNotReachHere(e);
                }
            }
        }
    }

    /**
     * Return a Function which given the serviceType as a String will return the corresponding
     * constructor parameter Class, or null.
     */
    @SuppressWarnings("unchecked")
    private static Function<String, Class<?>> getConsParamClassAccessor(BeforeAnalysisAccess access) {
        Map<String, /* EngineDescription */ Object> knownEngines = ReflectionUtil.readStaticField(Provider.class, "knownEngines");
        Field consParamClassNameField = ReflectionUtil.lookupField(access.findClassByName("java.security.Provider$EngineDescription"), "constructorParameterClassName");

        /*
         * The returned lambda captures the value of the Provider.knownEngines map retrieved above
         * and it uses it to find the parameterClass corresponding to the serviceType parameter.
         */
        return (serviceType) -> {
            try {
                /*
                 * Access the Provider.knownEngines map and extract the EngineDescription
                 * corresponding to the serviceType. From the EngineDescription object extract the
                 * value of the constructorParameterClassName field then, if the class name is not
                 * null, get the corresponding Class<?> object and return it.
                 */
                /* EngineDescription */Object engineDescription = knownEngines.get(serviceType);
                String constrParamClassName = (String) consParamClassNameField.get(engineDescription);
                if (constrParamClassName != null) {
                    return access.findClassByName(constrParamClassName);
                }
            } catch (IllegalAccessException e) {
                VMError.shouldNotReachHere(e);
            }
            return null;
        };
    }

    private static void register(Provider provider) {
        registerForReflection(provider.getClass());

        try {
            Method getVerificationResult = ReflectionUtil.lookupMethod(Class.forName("javax.crypto.JceSecurity"), "getVerificationResult", Provider.class);
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

    private static void register(BeforeAnalysisAccess access, Service service, Function<String, Class<?>> consParamClassAccessor) {
        Class<?> serviceClass = access.findClassByName(service.getClassName());
        if (serviceClass != null) {
            registerForReflection(serviceClass);

            Class<?> consParamClass = consParamClassAccessor.apply(service.getType());
            if (consParamClass != null) {
                registerForReflection(consParamClass);
                trace("Parameter class registered: " + consParamClass);
            }

            if (isSignature(service) || isCipher(service) || isKeyAgreement(service)) {
                for (String keyClassName : getSupportedKeyClasses(service)) {
                    Class<?> keyClass = access.findClassByName(keyClassName);
                    if (keyClass != null) {
                        registerForReflection(keyClass);
                    }
                }
            }
            trace("Service registered: " + asString(service));
        } else {
            trace("Service registration failed: " + asString(service) + ". Cause: class not found " + service.getClassName());
        }
    }

    private static void registerForReflection(Class<?> clazz) {
        RuntimeReflection.register(clazz);
        RuntimeReflection.register(clazz.getConstructors());
    }

    private static boolean isSunProvider(Provider provider) {
        return provider.getName().equals(SUN_PROVIDER);
    }

    private static boolean isSecureRandom(Service s) {
        return s.getType().equals(SECURE_RANDOM_SERVICE);
    }

    private static boolean isMessageDigest(Service s) {
        return s.getType().equals(MESSAGE_DIGEST_SERVICE);
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

    private static final String[] emptyStringArray = new String[0];

    private static String[] getSupportedKeyClasses(Service s) {
        assert isSignature(s) || isCipher(s) || isKeyAgreement(s);
        String supportedKeyClasses = s.getAttribute("SupportedKeyClasses");
        if (supportedKeyClasses != null) {
            return supportedKeyClasses.split("\\|");
        }
        return emptyStringArray;
    }

    // Checkstyle issue: illegal space before a comma
    // Checkstyle: stop
    private static final String SEP = " , ";
    // Checkstyle: resume

    private static String asString(Service s) {
        String str = "Provider = " + s.getProvider().getName() + SEP;
        str += "Type = " + s.getType() + SEP;
        str += "Algorithm = " + s.getAlgorithm() + SEP;
        str += "Class = " + s.getClassName();
        if (isSignature(s) || isCipher(s) || isKeyAgreement(s)) {
            str += SEP + "SupportedKeyClasses = " + Arrays.toString(getSupportedKeyClasses(s));
        }
        return str;
    }

    private static void trace(String trace) {
        if (Options.TraceSecurityServices.getValue()) {
            // Checkstyle: stop
            System.out.println(trace);
            // Checkstyle: resume
        }
    }

}
