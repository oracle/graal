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

import java.lang.reflect.Field;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.TypeResult;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.hosted.FeatureImpl.AfterRegistrationAccessImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.util.ConstantFoldUtil;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.serviceprovider.JavaVersionUtil;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

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
        rci.initializeAtRunTime("java.time.chrono.HijrahChronology", "Reads java.home in class initializer.");
        rci.initializeAtBuildTime("java.util", JDK_CLASS_REASON);
        rci.initializeAtRunTime("java.util.concurrent.SubmissionPublisher", "Executor service must be recomputed");

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
        /*
         * The XML classes have cyclic class initializer dependencies, and class initialization can
         * deadlock/fail when initialization is started at the "wrong part" of the cycle.
         * Force-initializing the correct class of the cycle here, in addition to the
         * "whole package" initialization above, breaks the cycle because it triggers immediate
         * initilalization here before the static analysis is started.
         */
        rci.initializeAtBuildTime("jdk.xml.internal.JdkXmlUtils", JDK_CLASS_REASON);

        rci.initializeAtBuildTime("sun.invoke", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.launcher", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.management", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.misc", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.net", JDK_CLASS_REASON);

        rci.initializeAtBuildTime("sun.nio", JDK_CLASS_REASON);
        if (Platform.includedIn(Platform.WINDOWS.class)) {
            rci.initializeAtRunTime("sun.nio.ch.PipeImpl", "Contains SecureRandom reference, therefore can't be included in the image heap");
        }

        rci.initializeAtRunTime("sun.net.PortConfig", "Calls PortConfig.getLower0() and PortConfig.getUpper0()");

        /*
         * In the cases that java.io.ObjectInputFilter$Config#serialFilter field is needed, this
         * class needs to be initialized. Field is initialized in the static block of the Config
         * class in runtime, so we need to initialize class at run time. This change also makes us
         * create substitution for jdkSerialFilterFactory in the
         * com.oracle.svm.core.jdk.Target_jdk_internal_util_StaticProperty.
         */
        rci.initializeAtRunTime("java.io.ObjectInputFilter$Config", "Field filter have to be initialized at runtime");

        rci.initializeAtRunTime("sun.nio.ch.DevPollArrayWrapper", "Calls IOUtil.fdLimit()");
        rci.initializeAtRunTime("sun.nio.ch.EPoll", "Calls EPoll.eventSize(), EPoll.eventsOffset() and EPoll.dataOffset()");
        rci.initializeAtRunTime("sun.nio.ch.EPollSelectorImpl", "Calls IOUtil.fdLimit()");
        rci.initializeAtRunTime("sun.nio.ch.EventPortSelectorImpl", "Calls IOUtil.fdLimit()");
        rci.initializeAtRunTime("sun.nio.fs.LinuxWatchService$Poller", "LinuxWatchService.eventSize() and LinuxWatchService.eventOffsets()");

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
        rci.initializeAtBuildTime("sun.security.pkcs12", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.provider", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.rsa", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.ssl", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.timestamp", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.tools", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.util", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.validator", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("sun.security.x509", JDK_CLASS_REASON);
        rci.initializeAtBuildTime("com.sun.jndi", JDK_CLASS_REASON);
        if (Platform.includedIn(Platform.DARWIN.class)) {
            rci.initializeAtBuildTime("apple.security", JDK_CLASS_REASON);
        }

        rci.initializeAtBuildTime("sun.rmi.transport.GC", "Loaded an unneeded library (rmi) in static initializer.");
        rci.initializeAtBuildTime("sun.rmi.transport.GC$LatencyLock", "Loaded an unneeded library (rmi) in static initializer.");

        rci.initializeAtRunTime("com.sun.jndi.dns.DnsClient", "Contains Random references, therefore can't be included in the image heap.");
        rci.initializeAtRunTime("sun.net.www.protocol.http.DigestAuthentication$Parameters", "Contains Random references, therefore can't be included in the image heap.");
        rci.initializeAtRunTime("sun.security.krb5.KrbServiceLocator", "Contains Random references, therefore can't be included in the image heap.");
        rci.initializeAtRunTime("com.sun.jndi.ldap.ServiceLocator", "Contains Random references, therefore can't be included in the image heap.");

        /*
         * The random number provider classes should be initialized at run time to reset their
         * values properly. Otherwise the numbers generated will be fixed for each generated image.
         */
        rci.initializeAtRunTime("java.lang.Math$RandomNumberGeneratorHolder", "Contains random seeds");
        rci.initializeAtRunTime("java.lang.StrictMath$RandomNumberGeneratorHolder", "Contains random seeds");

        rci.initializeAtRunTime("jdk.internal.misc.InnocuousThread", "Contains a thread group INNOCUOUSTHREADGROUP.");
        rci.initializeAtRunTime("jdk.internal.util.StaticProperty", "Contains run time specific values.");

        rci.initializeAtRunTime("sun.nio.ch.Poller", "Contains an InnocuousThread.");
        rci.initializeAtRunTime("jdk.internal.jimage", "Pulls in direct byte buffers");

        rci.initializeAtRunTime("sun.net.www.protocol.jrt.JavaRuntimeURLConnection", "Pulls in jimage reader");

        rci.initializeAtRunTime("sun.launcher.LauncherHelper", "Pulls in jimage reader");

        rci.initializeAtRunTime("jdk.internal.foreign.abi.fallback.LibFallback$NativeConstants", "Fails build-time initialization");
        rci.initializeAtRunTime("jdk.internal.foreign.abi.fallback.FFIType", "Fails build-time initialization");
        rci.initializeAtRunTime("jdk.internal.foreign.abi.fallback.FFIABI", "Fails build-time initialization");
        rci.initializeAtRunTime("sun.reflect.misc.Trampoline", "Fails build-time initialization");

        rci.initializeAtRunTime("com.sun.org.apache.xml.internal.serialize.HTMLdtd", "Fails build-time initialization");

        rci.initializeAtRunTime("sun.security.ssl.SSLContextImpl$DefaultSSLContextHolder", "Stores secure random");
        rci.initializeAtRunTime("sun.security.ssl.SSLSocketFactoryImpl", "Stores secure random");
        rci.initializeAtRunTime("sun.security.provider.certpath.ssl.SSLServerCertStore", "Stores secure random");

        rci.initializeAtRunTime("jdk.internal.foreign.SystemLookup$WindowsFallbackSymbols", "Does not work on non-Windows modular images");

        rci.initializeAtRunTime("jdk.internal.logger.LoggerFinderLoader", "Contains a static field with a FilePermission value");

        if (JavaVersionUtil.JAVA_SPEC >= 23) {
            rci.initializeAtRunTime("jdk.internal.markdown.MarkdownTransformer", "Contains a static field with a DocTreeScanner which is initialized at run time");
        }

        /*
         * The local class Holder in FallbackLinker#getInstance fails the build time initialization
         * starting JDK 22. There is no way to obtain a list of local classes using reflection. They
         * are thus accessed by name. According to the code in Check.localClassName, the identifier
         * in the name should be continuous.
         */
        ImageClassLoader imageClassLoader = ((AfterRegistrationAccessImpl) access).getImageClassLoader();
        int i = 1;
        TypeResult<Class<?>> currentHolderClass = imageClassLoader.findClass("jdk.internal.foreign.abi.fallback.FallbackLinker$%dHolder".formatted(i));
        while (currentHolderClass.isPresent()) {
            rci.initializeAtRunTime(currentHolderClass.get(), "Fails build-time initialization");
            currentHolderClass = imageClassLoader.findClass("jdk.internal.foreign.abi.fallback.FallbackLinker$%dHolder".formatted(i++));
        }
    }

    @Override
    public void registerInvocationPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        var enableNativeAccessClass = ReflectionUtil.lookupClass("java.lang.Module$EnableNativeAccess");
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins.getInvocationPlugins(), enableNativeAccessClass);
        r.register(new ModuleEnableNativeAccessPlugin());
    }

    /**
     * Inlines calls to {@code Module$EnableNativeAccess#isNativeAccessEnabled()} if and only if
     * {@code Module#enableNativeAccess} is true. This is ok because the field is {@code @Stable},
     * meaning that a non-default value (i.e., {@code true}, will never change again. Thus, we can
     * constant-fold the call to enable optimizations, most importantly dead code elimination.
     */
    private static final class ModuleEnableNativeAccessPlugin extends InvocationPlugin.InlineOnlyInvocationPlugin {

        private static final Field ENABLE_NATIVE_ACCESS_FIELD = ReflectionUtil.lookupField(Module.class, "enableNativeAccess");

        ModuleEnableNativeAccessPlugin() {
            super("isNativeAccessEnabled", Module.class);
        }

        /**
         * See {@code java.lang.Module$EnableNativeAccess#isNativeAccessEnabled(Module target)}.
         */
        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode targetNode) {
            JavaConstant moduleConstant = targetNode.asJavaConstant();
            if (moduleConstant != null) {
                var enableNativeAccessField = b.getMetaAccess().lookupJavaField(ENABLE_NATIVE_ACCESS_FIELD);
                if (enableNativeAccessField != null) {
                    var constant = ConstantFoldUtil.tryConstantFold(b.getConstantFieldProvider(), b.getConstantReflection(), b.getMetaAccess(),
                                    enableNativeAccessField, moduleConstant, b.getOptions(), targetMethod);
                    /*
                     * ConstantFoldUtil.tryConstantFold adheres to the @Stable field semantics,
                     * i.e., it only constant folds if the field has a non-default value (in this
                     * case `true`). See
                     * jdk.graal.compiler.core.common.spi.JavaConstantFieldProvider#
                     * readConstantField. In other words, if the field is `false`, `constant` would
                     * be null.
                     */
                    if (constant != null) {
                        /*
                         * Booleans are represented as int on the VM level so checking for int 1
                         * instead of boolean true.
                         */
                        assert constant.isJavaConstant() && constant.asJavaConstant().asInt() == 1 : "Must not constant fold if enableNativeAccess is false (@Stable semantics)";
                        b.push(JavaKind.Boolean, b.add(constant));
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
