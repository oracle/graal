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
package com.oracle.svm.shared.security;

/**
 * Provider names and classes that require shared handling in Native Image runtime support and the
 * Tracing Agent.
 *
 * @see <a href="https://github.com/graalvm/labs-openjdk/blob/jdk-25+21/src/java.base/share/conf/security/java.security">JDK default provider configuration</a>
 * @see <a href="https://docs.oracle.com/en/java/javase/25/security/howtoimplaprovider.html">JDK provider configuration guide</a>
 */
public final class SecurityProviderCatalog {
    private SecurityProviderCatalog() {
    }

    public static String getProviderName(String providerNameOrClassName) {
        String providerClassName = getProviderClassName(providerNameOrClassName);
        if (providerClassName == null) {
            return null;
        }
        return switch (providerClassName) {
            case "sun.security.provider.Sun" -> "SUN";
            case "sun.security.rsa.SunRsaSign" -> "SunRsaSign";
            case "com.sun.crypto.provider.SunJCE" -> "SunJCE";
            case "sun.security.ssl.SunJSSE" -> "SunJSSE";
            case "sun.security.ec.SunEC" -> "SunEC";
            case "sun.security.jgss.SunProvider" -> "SunJGSS";
            case "com.sun.security.sasl.Provider" -> "SunSASL";
            case "org.jcp.xml.dsig.internal.dom.XMLDSigRI" -> "XMLDSig";
            case "sun.security.smartcardio.SunPCSC" -> "SunPCSC";
            case "sun.security.provider.certpath.ldap.JdkLDAP" -> "JdkLDAP";
            case "com.sun.security.sasl.gsskerb.JdkSASL" -> "JdkSASL";
            case "sun.security.pkcs11.SunPKCS11" -> "SunPKCS11";
            case "sun.security.mscapi.SunMSCAPI" -> "SunMSCAPI";
            case "com.oracle.security.ucrypto.UcryptoProvider" -> "OracleUcrypto";
            case "apple.security.AppleProvider" -> "Apple";
            default -> null;
        };
    }

    public static String getProviderClassName(String providerNameOrClassName) {
        return switch (providerNameOrClassName) {
            case "SUN", "sun.security.provider.Sun" -> "sun.security.provider.Sun";
            case "SunRsaSign", "sun.security.rsa.SunRsaSign" -> "sun.security.rsa.SunRsaSign";
            case "SunJCE", "com.sun.crypto.provider.SunJCE" -> "com.sun.crypto.provider.SunJCE";
            case "SunJSSE", "sun.security.ssl.SunJSSE" -> "sun.security.ssl.SunJSSE";
            case "SunEC", "sun.security.ec.SunEC" -> "sun.security.ec.SunEC";
            case "SunJGSS", "sun.security.jgss.SunProvider" -> "sun.security.jgss.SunProvider";
            case "SunSASL", "com.sun.security.sasl.Provider" -> "com.sun.security.sasl.Provider";
            case "XMLDSig", "org.jcp.xml.dsig.internal.dom.XMLDSigRI" -> "org.jcp.xml.dsig.internal.dom.XMLDSigRI";
            case "SunPCSC", "sun.security.smartcardio.SunPCSC" -> "sun.security.smartcardio.SunPCSC";
            case "JdkLDAP", "sun.security.provider.certpath.ldap.JdkLDAP" -> "sun.security.provider.certpath.ldap.JdkLDAP";
            case "JdkSASL", "com.sun.security.sasl.gsskerb.JdkSASL" -> "com.sun.security.sasl.gsskerb.JdkSASL";
            case "SunPKCS11", "sun.security.pkcs11.SunPKCS11" -> "sun.security.pkcs11.SunPKCS11";
            case "SunMSCAPI", "sun.security.mscapi.SunMSCAPI" -> "sun.security.mscapi.SunMSCAPI";
            case "OracleUcrypto", "com.oracle.security.ucrypto.UcryptoProvider" -> "com.oracle.security.ucrypto.UcryptoProvider";
            case "Apple", "apple.security.AppleProvider" -> "apple.security.AppleProvider";
            default -> null;
        };
    }

    public static boolean isDirectlyConstructible(String providerNameOrClassName) {
        String providerClassName = getProviderClassName(providerNameOrClassName);
        if (providerClassName == null) {
            return false;
        }
        return switch (providerClassName) {
            case "sun.security.provider.Sun",
                            "sun.security.rsa.SunRsaSign",
                            "com.sun.crypto.provider.SunJCE",
                            "sun.security.ssl.SunJSSE",
                            "sun.security.ec.SunEC",
                            "apple.security.AppleProvider" ->
                true;
            default -> false;
        };
    }
}
