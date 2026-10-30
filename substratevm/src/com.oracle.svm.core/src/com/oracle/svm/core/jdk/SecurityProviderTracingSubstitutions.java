/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.util.function.BooleanSupplier;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.ReflectionUtil;

/**
 * The successful-selection bridge. Current JDKs use the copied {@code checkSuperClass}; a JDK with
 * the reflection lookup boundary substitutes only that narrower hook.
 */
@TargetClass(className = "sun.security.jca.GetInstance")
final class Target_sun_security_jca_GetInstance_Tracing {
    // §FS-002-security-providers.5.1
    /** Enforce registration and trace cached SPI and provider types after successful construction. */
    @Substitute
    @TargetElement(onlyWith = JDKWithoutSecurityProviderReflection.class)
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jvmci-25.2-b20/src/java.base/share/classes/sun/security/jca/GetInstance.java#L243-L253")
    public static void checkSuperClass(Provider.Service service, Class<?> subClass, Class<?> superClass) throws NoSuchAlgorithmException {
        // §FS-002-security-providers.3.1 and §FS-002-security-providers.4.1:
        // A provider-object factory call must fail before it exposes an unregistered provider.
        SecurityProviderRuntimeAccess.requireRegisteredProvider(service.getProvider());
        if (superClass != null && !superClass.isAssignableFrom(subClass)) {
            // Checkstyle: allow inconsistent exceptions and errors (JDK-compatible message)
            throw new NoSuchAlgorithmException("class configured for " + service.getType() + ": " +
                            service.getClassName() + " not a " + service.getType());
            // Checkstyle: disallow inconsistent exceptions and errors
        }
        SecurityProviderRuntimeAccess.traceServiceSelection(service.getProvider(), superClass);
    }

}

final class JDKWithSecurityProviderReflection implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        Class<?> providerReflection = ReflectionUtil.lookupClass(true, "sun.security.jca.ProviderReflection");
        return providerReflection != null &&
                        ReflectionUtil.lookupMethod(true, providerReflection, "trace", Provider.class, Class.class, boolean.class) != null;
    }
}

@TargetClass(className = "sun.security.jca.ProviderReflection", onlyWith = JDKWithSecurityProviderReflection.class)
final class Target_sun_security_jca_ProviderReflection {
    /**
     * Preserve the JDK hook's reflection contract while enforcing the Native Image provider
     * manifest and recording replayable service metadata.
     */
    @Substitute
    public static void trace(Provider provider, Class<?> implementationClass, boolean jdkManagedProvider) {
        if (provider != null) {
            SecurityProviderRuntimeAccess.requireRegisteredProvider(provider);
        }
        SecurityProviderRuntimeAccess.traceServiceSelection(jdkManagedProvider ? provider : null, implementationClass);
    }
}

final class JDKWithoutSecurityProviderReflection implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return !new JDKWithSecurityProviderReflection().getAsBoolean();
    }
}

public final class SecurityProviderTracingSubstitutions {
}
