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
package com.oracle.svm.core.graal.snippets;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.jdk.SubstrateObjectCloneWithExceptionNode;
import com.oracle.svm.core.identityhashcode.SubstrateIdentityHashCodeNode;
import java.util.Objects;
import java.util.function.Function;

import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.extended.ClassIsArrayNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.replacements.nodes.MacroNode.MacroParams;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Shared hosted/runtime plugin registrations used by hosted parsing and Ristretto runtime parsing
 * so both paths use the same plugin implementations.
 */
public final class SubstrateSharedGraphBuilderPlugins {

    public static void registerSystemPlugins(InvocationPlugins plugins) {
        registerSecurityManagerPlugin(plugins);
        registerSystemIdentityHashCodePlugin(plugins);
    }

    public static void registerSecurityManagerPlugin(InvocationPlugins plugins) {
        if (!SubstrateOptions.FoldSecurityManagerGetter.getValue()) {
            return;
        }
        Registration registration = new Registration(plugins, System.class);
        registration.register(new RequiredInvocationPlugin("getSecurityManager") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                /* System.getSecurityManager() always returns null. */
                b.addPush(JavaKind.Object, ConstantNode.forConstant(JavaConstant.NULL_POINTER, b.getMetaAccess(), b.getGraph()));
                return true;
            }
        });
    }

    public static void registerSystemIdentityHashCodePlugin(InvocationPlugins plugins) {
        Registration registration = new Registration(plugins, System.class);
        registration.register(new RequiredInvocationPlugin("identityHashCode", Object.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.addPush(JavaKind.Int, SubstrateIdentityHashCodeNode.create(object, b.bci(), b));
                return true;
            }
        });
    }

    public static void registerObjectPlugins(InvocationPlugins plugins) {
        Registration registration = new Registration(plugins, Object.class);
        registration.register(new RequiredInvocationPlugin("clone", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode object = receiver.get(true);
                b.addPush(JavaKind.Object, new SubstrateObjectCloneWithExceptionNode(MacroParams.of(b, targetMethod, object)));
                return true;
            }
        });

        registration.register(new RequiredInvocationPlugin("hashCode", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode object = receiver.get(true);
                b.addPush(JavaKind.Int, SubstrateIdentityHashCodeNode.create(object, b.bci(), b));
                return true;
            }
        });
    }

    public static void registerClassPlugins(InvocationPlugins plugins) {
        registerClassPlugins(plugins, null, null);
    }

    public static void registerClassPlugins(InvocationPlugins plugins, Function<String, String> classNameEncoder, Function<Object, Boolean> desiredAssertionStatusProvider) {
        registerClassGetNamePlugin(plugins, classNameEncoder);
        registerClassIsArrayPlugin(plugins);
        registerClassDesiredAssertionStatusPlugin(plugins, desiredAssertionStatusProvider);
    }

    private static void registerClassDesiredAssertionStatusPlugin(InvocationPlugins plugins, Function<Object, Boolean> desiredAssertionStatusProvider) {
        Registration registration = new Registration(plugins, Class.class);
        registration.register(new RequiredInvocationPlugin("desiredAssertionStatus", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                Object clazzOrHub = asConstantObject(b, Object.class, receiver.get(false));
                if (clazzOrHub == null) {
                    return false;
                }
                Boolean desiredAssertionStatus = null;
                if (desiredAssertionStatusProvider != null) {
                    desiredAssertionStatus = desiredAssertionStatusProvider.apply(clazzOrHub);
                }
                if (desiredAssertionStatus == null && clazzOrHub instanceof Class<?> clazz) {
                    desiredAssertionStatus = clazz.desiredAssertionStatus();
                }
                if (desiredAssertionStatus == null) {
                    return false;
                }
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(desiredAssertionStatus));
                return true;
            }
        });
    }

    private static void registerClassGetNamePlugin(InvocationPlugins plugins, Function<String, String> classNameEncoder) {
        Registration registration = new Registration(plugins, Class.class);
        registration.register(new InvocationPlugin.InlineOnlyInvocationPlugin("getName", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                JavaConstant constantReceiver = receiver.get(false).asJavaConstant();
                if (constantReceiver != null) {
                    ResolvedJavaType type = b.getConstantReflection().asJavaType(constantReceiver);
                    if (type != null) {
                        String className = type.toClassName();
                        if (classNameEncoder != null) {
                            className = Objects.requireNonNull(classNameEncoder.apply(className), "classNameEncoder returned null");
                        }
                        /*
                         * Class names must be interned according to the Java specification. This
                         * also ensures we get the same String instance that is stored in
                         * DynamicHub.name.
                         */
                        className = className.intern();
                        b.addPush(JavaKind.Object, ConstantNode.forConstant(b.getConstantReflection().forString(className), b.getMetaAccess()));
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private static void registerClassIsArrayPlugin(InvocationPlugins plugins) {
        Registration registration = new Registration(plugins, Class.class);
        registration.register(new InvocationPlugin("isArray", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                LogicNode isArray = b.add(ClassIsArrayNode.create(b.getConstantReflection(), receiver.get(true)));
                b.addPush(JavaKind.Boolean, ConditionalNode.create(isArray, NodeView.DEFAULT));
                return true;
            }
        });
    }

    private static <T> T asConstantObject(GraphBuilderContext b, Class<T> type, ValueNode node) {
        if (!node.isConstant()) {
            return null;
        }
        return b.getSnippetReflection().asObject(type, node.asJavaConstant());
    }
}
