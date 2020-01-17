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
        rci.initializeAtBuildTime("com.sun.jmx.remote.internal", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.jmx.remote.security", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.jmx.remote.util", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.jndi.ldap", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.jndi.ldap.ext", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.jndi.ldap.pool", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.jndi.ldap.sasl", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.jndi.toolkit.ctx", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.jndi.toolkit.dir", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.jndi.toolkit.url", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.jndi.url.ldap", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.jndi.url.ldaps", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.management", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.management.internal", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.naming.internal", "Core JDK classes are initialized at build time");
        rci.initializeAtRunTime("com.sun.naming.internal.ResourceManager$AppletParameter", "Initializes AWT");
        rci.initializeAtBuildTime("com.sun.net.ssl", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.net.ssl.internal.ssl", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.net.ssl.internal.www.protocol.https", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.nio.file", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.nio.sctp", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.nio.zipfs", "Native Image classes are always initialized at build time");
        rci.initializeAtBuildTime("com.sun.security.auth", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.security.auth.callback", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.security.auth.login", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.security.auth.module", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.security.cert.internal.x509", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.security.ntlm", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.security.sasl", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.security.sasl.digest", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.security.sasl.ntlm", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("com.sun.security.sasl.util", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.io", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.lang", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.lang.annotation", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.lang.invoke", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.lang.management", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.lang.module", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.lang.ref", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.lang.reflect", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.math", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.net", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.net.http", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.net.spi", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.nio", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.nio.channels", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.nio.channels.spi", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.nio.charset", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.nio.charset.spi", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.nio.file", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.nio.file.attribute", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.nio.file.spi", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.security", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.security.acl", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.security.cert", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.security.interfaces", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.security.spec", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.text", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.text.spi", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.time", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.time.chrono", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.time.format", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.time.temporal", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.time.zone", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.util", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.util.concurrent", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.util.concurrent.atomic", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.util.concurrent.locks", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.util.function", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.util.jar", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.util.logging", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.util.regex", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.util.spi", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.util.stream", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("java.util.zip", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.annotation.processing", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.crypto", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.crypto.interfaces", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.crypto.spec", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.lang.model", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.lang.model.element", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.lang.model.type", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.lang.model.util", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.management", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.management.loading", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.management.modelmbean", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.management.monitor", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.management.openmbean", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.management.relation", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.management.remote", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.management.timer", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.naming", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.naming.directory", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.naming.event", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.naming.ldap", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.naming.spi", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.net", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.net.ssl", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.security.auth", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.security.auth.callback", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.security.auth.kerberos", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.security.auth.login", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.security.auth.spi", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.security.auth.x500", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.security.cert", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.security.sasl", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("javax.tools", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.event", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.jimage", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.jimage.decompressor", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.jmod", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.jrtfs", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.loader", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.logger", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.math", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.misc", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.module", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.net.http", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.net.http.common", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.net.http.frame", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.net.http.hpack", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.net.http.websocket", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.org.objectweb.asm", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.org.objectweb.asm.commons", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.org.objectweb.asm.signature", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.org.objectweb.asm.tree", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.org.objectweb.asm.tree.analysis", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.org.objectweb.asm.util", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.org.xml.sax", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.org.xml.sax.helpers", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.perf", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.platform", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.ref", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.reflect", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.util", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.util.jar", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.util.xml", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.util.xml.impl", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.vm", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.internal.vm.annotation", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.net", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.nio", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("jdk.nio.zipfs", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("org.ietf.jgss", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.invoke", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.invoke.empty", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.invoke.util", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.launcher", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.launcher.resources", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.management", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.management.counter", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.management.counter.perf", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.management.spi", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.misc", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.dns", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.ext", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.ftp", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.ftp.impl", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.idn", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.sdp", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.smtp", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.spi", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.util", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.www", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.www.content.text", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.www.http", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.www.protocol.file", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.www.protocol.ftp", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.www.protocol.http", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.www.protocol.http.logging", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.www.protocol.http.ntlm", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.www.protocol.http.spnego", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.www.protocol.https", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.www.protocol.jar", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.www.protocol.jmod", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.www.protocol.jrt", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.net.www.protocol.mailto", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.nio", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.nio.ch", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.nio.ch.sctp", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.nio.cs", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.nio.fs", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.reflect", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.reflect.annotation", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.reflect.generics.factory", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.reflect.generics.parser", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.reflect.generics.reflectiveObjects", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.reflect.generics.repository", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.reflect.generics.scope", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.reflect.generics.tree", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.reflect.generics.visitor", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.reflect.misc", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.action", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.ec", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.ec.point", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.internal.interfaces", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.internal.spec", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.jca", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.jgss", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.jgss.krb5", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.jgss.spi", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.jgss.spnego", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.jgss.wrapper", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.krb5", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.krb5.internal", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.krb5.internal.ccache", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.krb5.internal.crypto", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.krb5.internal.crypto.dk", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.krb5.internal.ktab", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.krb5.internal.rcache", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.krb5.internal.util", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.pkcs", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.pkcs10", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.pkcs12", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.provider", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.provider.certpath", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.provider.certpath.ldap", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.provider.certpath.ssl", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.rsa", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.ssl", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.timestamp", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.tools", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.tools.keytool", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.util", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.util.math", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.util.math.intpoly", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.validator", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.security.x509", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.text", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.text.bidi", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.text.normalizer", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.text.resources", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.text.resources.cldr", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.text.spi", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.util", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.util.calendar", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.util.cldr", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.util.locale", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.util.locale.provider", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.util.logging", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.util.logging.internal", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.util.logging.resources", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.util.resources", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.util.resources.cldr", "Core JDK classes are initialized at build time");
        rci.initializeAtBuildTime("sun.util.spi", "Core JDK classes are initialized at build time");

        rci.initializeAtBuildTime("org.ietf.jgss", "Core JDK classes are initialized at build time for better performance");
        rci.initializeAtBuildTime("com.sun.security.jgss", "Core JDK classes are initialized at build time for better performance");
        rci.initializeAtBuildTime("sun.security.pkcs11", "Core JDK classes are initialized at build time for better performance");

        rci.initializeAtBuildTime("org.jcp.xml", "Core JDK classes are initialized at build time for better performance");
        rci.initializeAtBuildTime("javax.xml.crypto", "Core JDK classes are initialized at build time for better performance");
        rci.initializeAtBuildTime("com.sun.org.apache.xml.internal.security", "Core JDK classes are initialized at build time for better performance");

        rci.initializeAtBuildTime("java.sql", "Core JDK classes are initialized at build time for better performance");

        rci.initializeAtBuildTime("java.beans.PropertyChangeListener", "Core JDK classes are initialized at build time for better performance");
        rci.initializeAtBuildTime("java.beans.PropertyChangeEvent", "Core JDK classes are initialized at build time for better performance");
        rci.initializeAtBuildTime("sun.security.smartcardio.SunPCSC", "Core JDK classes are initialized at build time for better performance");
        rci.initializeAtBuildTime("sun.security.smartcardio.SunPCSC$1", "Core JDK classes are initialized at build time for better performance");
        rci.initializeAtBuildTime("sun.security.smartcardio.SunPCSC$ProviderService", "Core JDK classes are initialized at build time for better performance");
    }
}
