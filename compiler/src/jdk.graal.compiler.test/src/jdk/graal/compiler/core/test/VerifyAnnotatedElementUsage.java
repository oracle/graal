/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.UncheckedInterfaceProvider;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Verifies that calls to methods declared by {@link AnnotatedElement} never have a receiver of type
 * {@link ResolvedJavaType}, {@link ResolvedJavaMethod} or {@link ResolvedJavaField}. Once GR-69713,
 * is resolved ("Remove AnnotatedElement from JVMCI types"), this verification can be deleted.
 */
public class VerifyAnnotatedElementUsage extends VerifyStringFormatterUsage {

    private volatile Map<String, String> annotatedElementMethods;

    private static final String JVMCI_META_PACKAGE_PREFIX = "L" + ResolvedJavaType.class.getPackage().getName().replace('.', '/');
    private static final Set<String> ANNOTATED_ELEMENT_METHOD_NAMES = Stream.of(AnnotatedElement.class.getDeclaredMethods()).map(Method::getName).collect(Collectors.toSet());

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        MetaAccessProvider metaAccess = context.getMetaAccess();
        ResolvedJavaType annotatedElementType = metaAccess.lookupJavaType(AnnotatedElement.class);
        ResolvedJavaType resolvedJavaTypeType = metaAccess.lookupJavaType(ResolvedJavaType.class);
        ResolvedJavaType resolvedJavaMethodType = metaAccess.lookupJavaType(ResolvedJavaMethod.class);
        ResolvedJavaType resolvedJavaFieldType = metaAccess.lookupJavaType(ResolvedJavaField.class);

        if (annotatedElementMethods == null) {
            Map<String, String> map = new EconomicHashMap<>();
            for (Method m : AnnotatedElement.class.getDeclaredMethods()) {
                map.put(m.getName(), metaAccess.lookupJavaMethod(m).getSignature().toMethodDescriptor());
            }
            annotatedElementMethods = map;
        }

        for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod callee = t.targetMethod();
            String descriptor = annotatedElementMethods.get(callee.getName());
            if (descriptor != null && descriptor.equals(callee.getSignature().toMethodDescriptor())) {
                if (callee.hasReceiver()) {
                    ValueNode receiver = t.arguments().getFirst();
                    Stamp receiverStamp = receiver.stamp(NodeView.DEFAULT);
                    if (receiver instanceof UncheckedInterfaceProvider unchecked) {
                        Stamp uncheckedStamp = unchecked.uncheckedStamp();
                        if (uncheckedStamp != null) {
                            receiverStamp = uncheckedStamp;
                        }
                    }
                    ResolvedJavaType receiverType = receiverStamp.javaType(metaAccess);
                    if (resolvedJavaTypeType.isAssignableFrom(receiverType) ||
                                    resolvedJavaMethodType.isAssignableFrom(receiverType) ||
                                    resolvedJavaFieldType.isAssignableFrom(receiverType)) {

                        throw new VerificationError(
                                        t, "call to %s with receiver type %s should be replaced by use of %s.%n",
                                        callee.format("%H.%n(%p)"),
                                        receiverType.toClassName(),
                                        AnnotationValueSupport.class.getName());
                    }
                }
            }
        }
    }
}
