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

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;

@AutomaticallyRegisteredFeature
public class JDKInitializationFeature implements InternalFeature {
    private static final String JDK_CLASS_REASON = "Core JDK classes are initialized at build time";

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        RuntimeClassInitializationSupport rci = ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
        rci.initializeAtBuildTime("com.sun.java.util.jar.pack", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("com.sun.management", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("com.sun.naming.internal", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("com.sun.net.ssl", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("com.sun.nio.file", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("com.sun.nio.sctp", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("com.sun.nio.zipfs", JDK_CLASS_REASON);

        rci.initializeAtBuildTime("java.io", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("java.lang", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("java.math", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("java.net", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("java.nio", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("java.text", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("java.time", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("java.util", JDK_CLASS_REASON);
        rci.rerunInitialization("java.util.concurrent.SubmissionPublisher", "Executor service must be recomputed");

        rci.initializeAtBuildTime("javax.annotation.processing", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("javax.lang.model", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("javax.management", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("javax.naming", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("javax.net", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("javax.tools", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("javax.xml", JDK_CLASS_REASON);

        rci.initializeAtBuildTime("jdk.internal", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("jdk.jfr", "Needed for Native Image substitutions");
        rci.initializeAtBuildTime("jdk.net", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("jdk.nio", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("jdk.vm.ci", "Native Image classes are always initialized at build time");
        rci.initializeAtBuildTime("jdk.xml", JDK_CLASS_REASON);

        rci.initializeAtBuildTime("sun.invoke", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.launcher", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.management", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.misc", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.net", JDK_CLASS_REASON);

        rci.initializeAtBuildTime("sun.nio", JDK_CLASS_REASON);
        if (Platform.includedIn(Platform.WINDOWS.class)) {
            rci.rerunInitialization("sun.nio.ch.PipeImpl", "Contains SecureRandom reference, therefore can't be included in the image heap");
        }

        rci.rerunInitialization("sun.net.PortConfig", "Calls PortConfig.getLower0() and PortConfig.getUpper0()");

        /*
         * In the cases that java.io.ObjectInputFilter$Config#serialFilter field is needed, this
         * class needs to be reinitialized. Field is initialized in the static block of the Config
         * class in runtime, so we need to reinitialize class in runtime. This change also makes us
         * create substitution for jdkSerialFilterFactory in the
         * com.oracle.svm.core.jdk.Target_jdk_internal_util_StaticProperty.
         */
        rci.rerunInitialization("java.io.ObjectInputFilter$Config", "Field filter have to be initialized at runtime");

        rci.rerunInitialization("sun.nio.ch.DevPollArrayWrapper", "Calls IOUtil.fdLimit()");
        rci.rerunInitialization("sun.nio.ch.EPoll", "Calls EPoll.eventSize(), EPoll.eventsOffset() and EPoll.dataOffset()");
        rci.rerunInitialization("sun.nio.ch.EPollSelectorImpl", "Calls IOUtil.fdLimit()");
        rci.rerunInitialization("sun.nio.ch.EventPortSelectorImpl", "Calls IOUtil.fdLimit()");
        rci.rerunInitialization("sun.nio.fs.LinuxWatchService$Poller", "LinuxWatchService.eventSize() and LinuxWatchService.eventOffsets()");

        rci.initializeAtBuildTime("sun.reflect", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.mscapi", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.text", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.util", JDK_CLASS_REASON);

        /* Minor fixes to make the list work */
        rci.initializeAtRunTime("com.sun.naming.internal.ResourceManager$AppletParameter", "Initializes AWT");
        rci.initializeAtBuildTime("java.awt.font.TextAttribute", "Required for sun.text.bidi.BidiBase.NumericShapings");
        rci.initializeAtBuildTime("java.awt.font.NumericShaper", "Required for sun.text.bidi.BidiBase.NumericShapings");
        rci.initializeAtBuildTime("java.awt.font.JavaAWTFontAccessImpl", "Required for sun.text.bidi.BidiBase.NumericShapings");

        /* XML-related */
        rci.initializeAtBuildTime("com.sun.xml", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("com.sun.org.apache", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("com.sun.org.slf4j.internal", JDK_CLASS_REASON);

        /* Security services */
        rci.initializeAtBuildTime("com.sun.crypto.provider", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("com.sun.security.auth", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("com.sun.security.jgss", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("com.sun.security.cert.internal.x509", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("com.sun.security.ntlm", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("com.sun.security.sasl", JDK_CLASS_REASON);

        rci.initializeAtBuildTime("java.security", JDK_CLASS_REASON);

        rci.initializeAtBuildTime("javax.crypto", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("javax.security.auth", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("javax.security.cert", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("javax.security.sasl", JDK_CLASS_REASON);

        rci.initializeAtBuildTime("sun.security.action", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.ec", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.internal.interfaces", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.internal.spec", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.jca", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.jgss", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("org.ietf.jgss.Oid", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("org.ietf.jgss.GSSException", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("org.ietf.jgss.GSSName", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.krb5", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.pkcs", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.pkcs10", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.pkcs11", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.pkcs12", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.provider", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.rsa", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.ssl", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.timestamp", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.tools", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.util", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.validator", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.x509", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.smartcardio", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("com.sun.jndi", JDK_CLASS_REASON);
        if (Platform.includedIn(Platform.DARWIN.class)) {
            rci.initializeAtBuildTime("apple.security", JDK_CLASS_REASON);
        }

        rci.rerunInitialization("com.sun.jndi.dns.DnsClient", "Contains Random references, therefore can't be included in the image heap.");
        rci.rerunInitialization("sun.net.www.protocol.http.DigestAuthentication$Parameters", "Contains Random references, therefore can't be included in the image heap.");
        rci.rerunInitialization("sun.security.krb5.KrbServiceLocator", "Contains Random references, therefore can't be included in the image heap.");
        rci.rerunInitialization("com.sun.jndi.ldap.ServiceLocator", "Contains Random references, therefore can't be included in the image heap.");

        // The random number provider classes should be reinitialized at runtime to reset their
        // values properly. Otherwise the numbers generated will be fixed for each generated image.
        rci.rerunInitialization("java.lang.Math$RandomNumberGeneratorHolder", "Contains random seeds");
        rci.rerunInitialization("java.lang.StrictMath$RandomNumberGeneratorHolder", "Contains random seeds");

        rci.rerunInitialization("jdk.internal.misc.InnocuousThread", "Contains a thread group INNOCUOUSTHREADGROUP.");

        if (JavaVersionUtil.JAVA_SPEC >= 19) {
            rci.rerunInitialization("sun.nio.ch.Poller", "Contains an InnocuousThread.");
        }
    }
}
