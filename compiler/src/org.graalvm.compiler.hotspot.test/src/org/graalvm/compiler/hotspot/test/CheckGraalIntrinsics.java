/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.meta.UnimplementedGraalIntrinsics;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Binding;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.compiler.test.GraalTest;
import org.junit.Test;

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
 * {@link UnimplementedGraalIntrinsics#toBeInvestigated} and * either implementing the intrinsic or
 * moving it to {@link UnimplementedGraalIntrinsics#ignore}.
 */
public class CheckGraalIntrinsics extends GraalTest {

    public static boolean match(String type, Binding binding, VMIntrinsicMethod intrinsic) {
        if (intrinsic.name.equals(binding.name)) {
            if (intrinsic.descriptor.startsWith(binding.argumentsDescriptor)) {
                if (type.equals(intrinsic.declaringClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static InvocationPlugin findPlugin(EconomicMap<String, List<Binding>> bindings, VMIntrinsicMethod intrinsic) {
        MapCursor<String, List<Binding>> cursor = bindings.getEntries();
        while (cursor.advance()) {
            // Match format of VMIntrinsicMethod.declaringClass
            String type = MetaUtil.internalNameToJava(cursor.getKey(), true, false).replace('.', '/');
            for (Binding binding : cursor.getValue()) {
                if (match(type, binding, intrinsic)) {
                    return binding.plugin;
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
        return null;
    }

    public interface Refiner {
        void refine(CheckGraalIntrinsics checker);
    }

    public final HotSpotGraalRuntimeProvider rt = (HotSpotGraalRuntimeProvider) Graal.getRequiredCapability(RuntimeProvider.class);
    public final GraalHotSpotVMConfig config = rt.getVMConfig();
    public final UnimplementedGraalIntrinsics unimplementedGraalIntrinsics = new UnimplementedGraalIntrinsics(config, rt.getHostBackend().getTarget().arch);

    @Test
    @SuppressWarnings("try")
    public void test() throws ClassNotFoundException {
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
        EconomicMap<String, List<Binding>> bindings = invocationPlugins.getBindings(true);
        for (VMIntrinsicMethod intrinsic : intrinsics) {
            InvocationPlugin plugin = findPlugin(bindings, intrinsic);
            String m = String.format("%s.%s%s", intrinsic.declaringClass, intrinsic.name, intrinsic.descriptor);
            if (plugin == null) {
                ResolvedJavaMethod method = resolveIntrinsic(providers.getMetaAccess(), intrinsic);
                if (method != null) {
                    IntrinsicMethod intrinsicMethod = providers.getConstantReflection().getMethodHandleAccess().lookupMethodHandleIntrinsic(method);
                    if (intrinsicMethod != null) {
                        continue;
                    }
                }
                if (!unimplementedGraalIntrinsics.isDocumented(m)) {
                    missing.add(m);
                }
            } else {
                if (unimplementedGraalIntrinsics.isMissing(m)) {
                    mischaracterizedAsToBeInvestigated.add(m + " [plugin: " + plugin + "]");
                } else if (unimplementedGraalIntrinsics.isIgnored(m)) {
                    mischaracterizedAsIgnored.add(m + " [plugin: " + plugin + "]");
                }
            }
        }

        Formatter errorMsgBuf = new Formatter();
        if (!missing.isEmpty()) {
            Collections.sort(missing);
            String missingString = missing.stream().collect(Collectors.joining(String.format("%n    ")));
            errorMsgBuf.format("missing Graal intrinsics for:%n    %s%n", missingString);
        }
        if (!mischaracterizedAsToBeInvestigated.isEmpty()) {
            Collections.sort(mischaracterizedAsToBeInvestigated);
            String missingString = mischaracterizedAsToBeInvestigated.stream().collect(Collectors.joining(String.format("%n    ")));
            errorMsgBuf.format("found plugins for intrinsics characterized as toBeInvestigated:%n    %s%n", missingString);
        }
        if (!mischaracterizedAsIgnored.isEmpty()) {
            Collections.sort(mischaracterizedAsIgnored);
            String missingString = mischaracterizedAsIgnored.stream().collect(Collectors.joining(String.format("%n    ")));
            errorMsgBuf.format("found plugins for intrinsics characterized as IGNORED:%n    %s%n", missingString);
        }
        String errorMsg = errorMsgBuf.toString();
        if (!errorMsg.isEmpty()) {
            fail(errorMsg);
        }
    }
}
