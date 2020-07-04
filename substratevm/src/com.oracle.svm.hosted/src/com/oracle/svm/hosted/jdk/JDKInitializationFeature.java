/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jdk;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.svm.core.annotate.AutomaticFeature;

@AutomaticFeature
public class JDKInitializationFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        RuntimeClassInitializationSupport rci = ImageSingletons.lookup(RuntimeClassInitializationSupport.class);

        rci.initializeAtBuildTime("apple.security", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("com.sun.crypto.provider", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("com.sun.java.util.jar.pack", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("com.sun.jmx.defaults", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.jmx.interceptor", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.jmx.mbeanserver", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.beans.Introspector", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.beans.PropertyChangeListener", "Core JDK classes are initialized at build time for better performance");
        rci.initializeAtBuildTime("java.beans.PropertyChangeEvent", "Core JDK classes are initialized at build time for better performance");
        rci.initializeAtBuildTime("com.sun.jmx.remote.internal", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.jmx.remote.security", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.jmx.remote.util", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("com.sun.jndi.ldap", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("com.sun.jndi.toolkit.ctx", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.jndi.toolkit.dir", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.jndi.toolkit.url", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.jndi.url.ldap", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.jndi.url.ldaps", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("com.sun.management", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("com.sun.naming.internal", "Core JDK classes are initialized at build time");
        rci.initializeAtRunTime("com.sun.naming.internal.ResourceManager$AppletParameter", "Initializes AWT");

        rci.initializeAtBuildTime("com.sun.net.ssl", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("com.sun.nio.file", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.nio.sctp", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.nio.zipfs", "Native Image classes are always initialized at build time");

        rci.initializeAtBuildTime("com.sun.security.auth", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("com.sun.security.cert.internal.x509", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.security.ntlm", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.security.sasl", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("java.io", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("java.lang", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("java.math", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("java.net", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("java.nio", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("java.security", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("java.text", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("java.time", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("java.util", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("javax.annotation.processing", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.crypto", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("javax.lang.model", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("javax.management", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("javax.naming", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.naming.directory", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.naming.event", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.naming.ldap", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.naming.spi", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("javax.net", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.net.ssl", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.security.auth", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.security.cert", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.security.sasl", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.tools", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("jdk.internal", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("jdk.net", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("jdk.nio", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("org.ietf.jgss", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("sun.invoke", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("sun.launcher", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("sun.management", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("sun.misc", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("sun.nio", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("sun.reflect", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("sun.security.action", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.ec", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.internal.interfaces", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.internal.spec", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.jca", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.jgss", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.krb5", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.pkcs", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.pkcs10", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.pkcs11", "Core JDK classes are initialized at build time for better performance");
        rci.initializeAtBuildTime("sun.security.pkcs12", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.provider", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.rsa", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.ssl", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.timestamp", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.tools", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.util", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.validator", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.x509", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.smartcardio", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("sun.text", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("sun.util", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("org.ietf.jgss", "Core JDK classes are initialized at build time for better performance");
        rci.initializeAtBuildTime("com.sun.security.jgss", "Core JDK classes are initialized at build time for better performance");

        rci.initializeAtBuildTime("org.jcp.xml", "Core JDK classes are initialized at build time for better performance");
        rci.initializeAtBuildTime("javax.xml.crypto", "Core JDK classes are initialized at build time for better performance");
        rci.initializeAtBuildTime("com.sun.org.apache.xml.internal.security", "Core JDK classes are initialized at build time for better performance");

        /* Needed by our substitutions */
        rci.initializeAtBuildTime("jdk.jfr", "Core JDK classes are initialized at build time for better performance");
    }
}
