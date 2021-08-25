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
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.svm.core.annotate.AutomaticFeature;

@AutomaticFeature
public class JDKInitializationFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        RuntimeClassInitializationSupport rci = ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
        rci.initializeAtBuildTime("com.sun.java.util.jar.pack", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.management", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.naming.internal", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.net.ssl", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.nio.file", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.nio.sctp", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.nio.zipfs", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("java.io", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.lang", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.math", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.net", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.nio", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.text", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.time", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.util", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("javax.annotation.processing", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.lang.model", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.management", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.naming", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.net", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.tools", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("jdk.internal", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.jfr", "Needed for Native Image substitutions");
        rci.initializeAtBuildTime("jdk.net", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.nio", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.vm.ci", "Native Image classes are always initialized at build time");

        rci.initializeAtBuildTime("sun.invoke", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.launcher", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.management", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.misc", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("sun.nio", "Core JDK classes are initialized at build time");
        if (Platform.includedIn(Platform.WINDOWS.class)) {
            rci.rerunInitialization("sun.nio.ch.PipeImpl", "Contains SecureRandom reference, therefore can't be included in the image heap");
        }

        rci.rerunInitialization("sun.net.PortConfig", "Calls PortConfig.getLower0() and PortConfig.getUpper0()");

        rci.rerunInitialization("sun.nio.ch.DevPollArrayWrapper", "Calls IOUtil.fdLimit()");
        rci.rerunInitialization("sun.nio.ch.EPoll", "Calls EPoll.eventSize(), EPoll.eventsOffset() and EPoll.dataOffset()");
        rci.rerunInitialization("sun.nio.ch.EPollSelectorImpl", "Calls IOUtil.fdLimit()");
        rci.rerunInitialization("sun.nio.ch.EventPortSelectorImpl", "Calls IOUtil.fdLimit()");
        rci.rerunInitialization("sun.nio.fs.LinuxWatchService$Poller", "LinuxWatchService.eventSize() and LinuxWatchService.eventOffsets()");

        rci.initializeAtBuildTime("sun.reflect", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.mscapi", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.text", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.util", "Core JDK classes are initialized at build time");

        /* Minor fixes to make the list work */
        rci.initializeAtRunTime("com.sun.naming.internal.ResourceManager$AppletParameter", "Initializes AWT");
        rci.initializeAtBuildTime("java.awt.font.TextAttribute", "Required for sun.text.bidi.BidiBase.NumericShapings");
        rci.initializeAtBuildTime("java.awt.font.NumericShaper", "Required for sun.text.bidi.BidiBase.NumericShapings");

        /* Security services */
        rci.initializeAtBuildTime("com.sun.crypto.provider", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.security.auth", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.security.jgss", "Core JDK classes are initialized at build time for better performance");
        rci.initializeAtBuildTime("com.sun.security.cert.internal.x509", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.security.ntlm", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.security.sasl", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("java.security", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("javax.crypto", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.security.auth", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.security.cert", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.security.sasl", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("sun.security.action", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.ec", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.internal.interfaces", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.internal.spec", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.jca", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.jgss", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("org.ietf.jgss.GSSException", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("org.ietf.jgss.GSSName", "Core JDK classes are initialized at build time");
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

        rci.rerunInitialization("com.sun.jndi.dns.DnsClient", "Contains Random references, therefore can't be included in the image heap.");
        rci.rerunInitialization("sun.net.www.protocol.http.DigestAuthentication$Parameters", "Contains Random references, therefore can't be included in the image heap.");
        rci.rerunInitialization("sun.security.krb5.KrbServiceLocator", "Contains Random references, therefore can't be included in the image heap.");
        rci.rerunInitialization("com.sun.jndi.ldap.ServiceLocator", "Contains Random references, therefore can't be included in the image heap.");

        // The random number provider classes should be reinitialized at runtime to reset their
        // values properly. Otherwise the numbers generated will be fixed for each generated image.
        rci.rerunInitialization("java.lang.Math$RandomNumberGeneratorHolder", "Contains random seeds");
        rci.rerunInitialization("java.lang.StrictMath$RandomNumberGeneratorHolder", "Contains random seeds");

        rci.rerunInitialization("jdk.internal.misc.InnocuousThread", "Contains a thread group INNOCUOUSTHREADGROUP.");
    }
}
