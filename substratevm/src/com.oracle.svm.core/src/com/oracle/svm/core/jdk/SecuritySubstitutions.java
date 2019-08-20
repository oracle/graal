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

import static com.oracle.svm.core.snippets.KnownIntrinsics.readCallerStackPointer;

import java.net.URL;
import java.security.AccessControlContext;
import java.security.AccessControlException;
import java.security.DomainCombiner;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.security.SecureRandom;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.graalvm.word.Pointer;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.SubstrateOptionsParser;
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
        SubstrateUtil.cast(result, Target_java_security_AccessControlContext.class).isPrivileged = true;
        return result;
    }

    @Substitute
    private static AccessControlContext createWrapper(DomainCombiner combiner, Class<?> caller,
                    AccessControlContext parent, AccessControlContext context, Permission[] perms) {
        /* Avoid allocating ProtectionDomain objects. Should go away when GR-11112 is fixed. */
        AccessControlContext result = new AccessControlContext(new ProtectionDomain[0]);
        SubstrateUtil.cast(result, Target_java_security_AccessControlContext.class).isPrivileged = true;
        return result;
    }
}

@TargetClass(java.security.AccessControlContext.class)
final class Target_java_security_AccessControlContext {

    @Alias protected boolean isPrivileged;
}

@TargetClass(SecurityManager.class)
@SuppressWarnings({"static-method", "unused"})
final class Target_java_lang_SecurityManager {
    @Substitute
    @NeverInline("Starting a stack walk in the caller frame")
    protected Class<?>[] getClassContext() {
        final Pointer startSP = readCallerStackPointer();
        return StackTraceUtils.getClassContext(1, startSP);
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

final class EnableAllSecurityServicesIsSet implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return SubstrateOptions.EnableAllSecurityServices.getValue();
    }
}

/**
 * This substitution is enabled only when EnableAllSecurityServices is set since the functionality
 * that it currently provides, i.e., loading security native libraries, is not needed by default.
 */
@TargetClass(value = java.security.Provider.class, onlyWith = EnableAllSecurityServicesIsSet.class)
final class Target_java_security_Provider {

    @Alias //
    private transient boolean initialized;

    @Alias//
    private String name;

    /*
     * Provider.checkInitialized() is called from the other Provider API methods, before any
     * computation, thus is a convenient location to do our own initialization, i.e., make sure that
     * the required libraries are loaded.
     */
    @Substitute
    private void checkInitialized() {
        if (this.name.equals("SunEC")) {
            ProviderUtil.initSunEC();
        }

        if (!initialized) {
            throw new IllegalStateException();
        }
    }

}

final class ProviderUtil {
    private static volatile boolean initialized = false;

    static void initSunEC() {
        if (initialized) {
            return;
        }
        /* Lazy initialization. */
        initOnce();
    }

    // Checkstyle: stop
    private static synchronized void initOnce() {
        // Checkstyle: resume
        if (!initialized) {
            try {
                System.loadLibrary("sunec");
            } catch (UnsatisfiedLinkError e) {
                /*
                 * SunEC has a mode where it can function without the full ECC implementation when
                 * native library is absent, however, then fewer EC algorithms are available). If
                 * those algorithms are actually used an java.lang.UnsatisfiedLinkError will be
                 * thrown. Just warn the user that the library could not be loaded.
                 */
                Log.log().string("WARNING: The sunec native library, required by the SunEC provider, could not be loaded. " +
                                "This library is usually shipped as part of the JDK and can be found under <JAVA_HOME>/jre/lib/<platform>/libsunec.so. " +
                                "It is loaded at run time via System.loadLibrary(\"sunec\"), the first time services from SunEC are accessed. " +
                                "To use this provider's services the java.library.path system property needs to be set accordingly " +
                                "to point to a location that contains libsunec.so. " +
                                "Note that if java.library.path is not set it defaults to the current working directory.").newline();
            }
            initialized = true;
        }
    }

}

@TargetClass(className = "javax.crypto.JceSecurity")
@SuppressWarnings({"unused"})
final class Target_javax_crypto_JceSecurity {

    /*
     * Lazily recompute the RANDOM field at runtime. We cannot push the entire static initialization
     * of JceSecurity to run time because we want the JceSecurity.verificationResults initialized at
     * image build time.
     */
    @Alias @InjectAccessors(JceSecurityAccessor.class) //
    static SecureRandom RANDOM;

    @Substitute
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    static void verifyProviderJar(URL var0) {
        throw JceSecurityUtil.shouldNotReach("javax.crypto.JceSecurity.verifyProviderJar(URL)");
    }

    @Substitute
    static URL getCodeBase(final Class<?> var0) {
        throw JceSecurityUtil.shouldNotReach("javax.crypto.JceSecurity.getCodeBase(Class)");
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
    private static final String enableAllSecurityServices = SubstrateOptionsParser.commandArgument(SubstrateOptions.EnableAllSecurityServices, "+");

    static RuntimeException shouldNotReach(String method) {
        throw VMError.shouldNotReachHere(method + " is reached at runtime. " +
                        "This should not happen. The contents of JceSecurity.verificationResults " +
                        "are computed and cached at image build time. Try enabling all security services with " + enableAllSecurityServices + ".");
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

/** Dummy class to have a class with the file's name. */
public final class SecuritySubstitutions {
}
