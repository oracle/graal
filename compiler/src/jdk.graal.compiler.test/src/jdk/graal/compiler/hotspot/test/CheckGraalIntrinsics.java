/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import static jdk.graal.compiler.hotspot.HotSpotGraalServices.isIntrinsicAvailable;
import static jdk.graal.compiler.hotspot.HotSpotGraalServices.isIntrinsicSupportedByC2;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.junit.Test;

import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.meta.UnimplementedGraalIntrinsics;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.runtime.RuntimeProvider;
import jdk.graal.compiler.test.GraalTest;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.hotspot.VMIntrinsicMethod;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.MethodHandleAccessProvider.IntrinsicMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Checks the intrinsics implemented by Graal against the set of intrinsics declared by HotSpot. The
 * purpose of this test is to detect when new intrinsics are added to HotSpot and process them
 * appropriately in Graal. This will be achieved by working through
 * {@link UnimplementedGraalIntrinsics#toBeInvestigated} and either implementing the intrinsic or
 * moving it to {@link UnimplementedGraalIntrinsics#ignore}.
 */
public class CheckGraalIntrinsics extends GraalTest {

    public static boolean match(String type, InvocationPlugin plugin, VMIntrinsicMethod intrinsic) {
        if (intrinsic.name.equals(plugin.name)) {
            if (intrinsic.descriptor.startsWith(plugin.argumentsDescriptor)) {
                if (type.equals(intrinsic.declaringClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static InvocationPlugin findPlugin(EconomicMap<String, List<InvocationPlugin>> invocationPluginsMap, VMIntrinsicMethod intrinsic) {
        MapCursor<String, List<InvocationPlugin>> cursor = invocationPluginsMap.getEntries();
        while (cursor.advance()) {
            // Match format of VMIntrinsicMethod.declaringClass
            String type = MetaUtil.internalNameToJava(cursor.getKey(), true, false).replace('.', '/');
            for (InvocationPlugin plugin : cursor.getValue()) {
                if (match(type, plugin, intrinsic)) {
                    return plugin;
                }
            }
        }
        return null;
    }

    public static ResolvedJavaMethod resolveIntrinsic(MetaAccessProvider metaAccess, VMIntrinsicMethod intrinsic) throws ClassNotFoundException {
        Class<?> c;
        try {
            c = Class.forName(intrinsic.declaringClass.replace('/', '.'), false, CheckGraalIntrinsics.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            try {
                Class.forName("javax.naming.Reference");
            } catch (ClassNotFoundException coreNamingMissing) {
                // if core JDK classes aren't found, we are probably running in a
                // JDK9 java.base environment and then missing class is OK
                return null;
            }
            throw ex;
        }
        for (Method javaMethod : c.getDeclaredMethods()) {
            if (javaMethod.getName().equals(intrinsic.name)) {
                ResolvedJavaMethod method = metaAccess.lookupJavaMethod(javaMethod);
                if (intrinsic.descriptor.equals("*")) {
                    // Signature polymorphic method - name match is enough
                    return method;
                } else {
                    if (method.getSignature().toMethodDescriptor().equals(intrinsic.descriptor)) {
                        return method;
                    }
                }
            }
        }
        for (Constructor<?> constructor : c.getDeclaredConstructors()) {
            if (constructor.getName().equals(intrinsic.name)) {
                ResolvedJavaMethod method = metaAccess.lookupJavaMethod(constructor);
                if (intrinsic.descriptor.equals("*")) {
                    // Signature polymorphic method - name match is enough
                    return method;
                } else {
                    if (method.getSignature().toMethodDescriptor().equals(intrinsic.descriptor)) {
                        return method;
                    }
                }
            }
        }
        return null;
    }

    public interface Refiner {
        void refine(CheckGraalIntrinsics checker);
    }

    public final HotSpotGraalRuntimeProvider rt = (HotSpotGraalRuntimeProvider) Graal.getRequiredCapability(RuntimeProvider.class);
    public final GraalHotSpotVMConfig config = rt.getVMConfig();
    public final UnimplementedGraalIntrinsics unimplementedGraalIntrinsics = new UnimplementedGraalIntrinsics(rt.getTarget().arch);

    @Test
    @SuppressWarnings("try")
    public void test() throws ClassNotFoundException, NoSuchFieldException {
        HotSpotProviders providers = rt.getHostBackend().getProviders();
        Plugins graphBuilderPlugins = providers.getGraphBuilderPlugins();
        InvocationPlugins invocationPlugins = graphBuilderPlugins.getInvocationPlugins();

        HotSpotVMConfigStore store = config.getStore();
        List<VMIntrinsicMethod> intrinsics = store.getIntrinsics();

        for (Refiner refiner : ServiceLoader.load(Refiner.class)) {
            refiner.refine(this);
        }

        List<String> missing = new ArrayList<>();
        List<String> mischaracterizedAsToBeInvestigated = new ArrayList<>();
        List<String> mischaracterizedAsIgnored = new ArrayList<>();
        List<String> notAvailableYetIntrinsified = new ArrayList<>();

        for (VMIntrinsicMethod intrinsic : intrinsics) {
            ResolvedJavaMethod method = resolveIntrinsic(providers.getMetaAccess(), intrinsic);
            if (method == null) {
                continue;
            }

            InvocationPlugin plugin = invocationPlugins.lookupInvocation(method, Graal.getRequiredCapability(OptionValues.class));
            String m = String.format("%s.%s%s", intrinsic.declaringClass, intrinsic.name, intrinsic.descriptor);
            if (plugin == null) {
                if (method != null) {
                    IntrinsicMethod intrinsicMethod = providers.getConstantReflection().getMethodHandleAccess().lookupMethodHandleIntrinsic(method);
                    if (intrinsicMethod != null) {
                        continue;
                    }
                }
                if (!unimplementedGraalIntrinsics.isDocumented(m) && isIntrinsicAvailable(intrinsic) && isIntrinsicSupportedByC2(intrinsic)) {
                    missing.add(m);
                }
            } else {
                if (unimplementedGraalIntrinsics.isMissing(m)) {
                    mischaracterizedAsToBeInvestigated.add(m + " [plugin: " + plugin + "]");
                } else if (unimplementedGraalIntrinsics.isIgnored(m)) {
                    mischaracterizedAsIgnored.add(m + " [plugin: " + plugin + "]");
                } else if (!isIntrinsicAvailable(intrinsic) && !plugin.isGraalOnly()) {
                    notAvailableYetIntrinsified.add(m + " [plugin: " + plugin + "]");
                }
            }
        }

        Formatter errorMsgBuf = new Formatter();
        if (!missing.isEmpty()) {
            Field toBeInvestigated = UnimplementedGraalIntrinsics.class.getDeclaredField("toBeInvestigated");
            Collections.sort(missing);
            String missingString = missing.stream().map(s -> '"' + s + '"').collect(Collectors.joining(String.format(",%n    ")));
            errorMsgBuf.format("missing Graal intrinsics for:%n    %s%n", missingString);
            errorMsgBuf.format("To fix, modify the %s constructor to add them to %s.%n",
                            UnimplementedGraalIntrinsics.class.getSimpleName(), toBeInvestigated);
        }
        if (!mischaracterizedAsToBeInvestigated.isEmpty()) {
            Collections.sort(mischaracterizedAsToBeInvestigated);
            String missingString = mischaracterizedAsToBeInvestigated.stream().map(s -> '"' + s + '"').collect(Collectors.joining(String.format(",%n    ")));
            errorMsgBuf.format("found plugins for intrinsics characterized as toBeInvestigated:%n    %s%n", missingString);
        }
        if (!mischaracterizedAsIgnored.isEmpty()) {
            Collections.sort(mischaracterizedAsIgnored);
            String missingString = mischaracterizedAsIgnored.stream().map(s -> '"' + s + '"').collect(Collectors.joining(String.format(",%n    ")));
            errorMsgBuf.format("found plugins for intrinsics characterized as ignored:%n    %s%n", missingString);
        }
        if (!notAvailableYetIntrinsified.isEmpty()) {
            Collections.sort(notAvailableYetIntrinsified);
            String missingString = notAvailableYetIntrinsified.stream().map(s -> '"' + s + '"').collect(Collectors.joining(String.format(",%n    ")));
            errorMsgBuf.format("found plugins for intrinsics marked as unavailable:%n    %s%n", missingString);
        }
        String errorMsg = errorMsgBuf.toString();
        if (!errorMsg.isEmpty()) {
            fail(errorMsg);
        }
    }
}
