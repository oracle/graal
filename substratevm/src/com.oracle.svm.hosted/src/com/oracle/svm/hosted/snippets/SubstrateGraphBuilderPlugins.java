/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.snippets;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import java.util.stream.Stream;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.Edges;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeList;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DynamicPiNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FullInfopointNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode;
import org.graalvm.compiler.nodes.extended.GetClassNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.DynamicNewInstanceNode;
import org.graalvm.compiler.nodes.java.InstanceOfDynamicNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.nodes.java.ValidateNewInstanceClassNode;
import org.graalvm.compiler.nodes.spi.ArrayLengthProvider;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.type.NarrowOopStamp;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.replacements.nodes.MacroNode.MacroParams;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.word.WordCastNode;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.util.DirectAnnotationAccess;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.nodes.UnsafePartitionLoadNode;
import com.oracle.graal.pointsto.nodes.UnsafePartitionStoreNode;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.RuntimeAssertionsSupport;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.GraalEdgeUnsafePartition;
import com.oracle.svm.core.graal.jdk.ObjectCloneWithExceptionNode;
import com.oracle.svm.core.graal.jdk.SubstrateArraysCopyOfWithExceptionNode;
import com.oracle.svm.core.graal.nodes.DeoptEntryNode;
import com.oracle.svm.core.graal.nodes.FarReturnNode;
import com.oracle.svm.core.graal.nodes.LazyConstantNode;
import com.oracle.svm.core.graal.nodes.ReadCallerStackPointerNode;
import com.oracle.svm.core.graal.nodes.ReadReservedRegister;
import com.oracle.svm.core.graal.nodes.ReadReturnAddressNode;
import com.oracle.svm.core.graal.nodes.SubstrateCompressionNode;
import com.oracle.svm.core.graal.nodes.SubstrateNarrowOopStamp;
import com.oracle.svm.core.graal.nodes.SubstrateReflectionGetCallerClassNode;
import com.oracle.svm.core.graal.nodes.TestDeoptimizeNode;
import com.oracle.svm.core.graal.stackvalue.StackValueNode;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.ReferenceAccessImpl;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.identityhashcode.SubstrateIdentityHashCodeNode;
import com.oracle.svm.core.jdk.RecordSupport;
import com.oracle.svm.core.jdk.proxy.DynamicProxyRegistry;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FallbackFeature;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.nodes.DeoptProxyNode;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SubstrateGraphBuilderPlugins {

    /** Collection of debug options for SubstrateGraphBuilderPlugins. */
    public static class Options {
        @Option(help = "Enable trace logging for dynamic proxy.")//
        public static final HostedOptionKey<Boolean> DynamicProxyTracing = new HostedOptionKey<>(false);
    }

    private static final String reflectionClass;

    static {
        if (JavaVersionUtil.JAVA_SPEC <= 8) {
            reflectionClass = "sun.reflect.Reflection";
        } else {
            reflectionClass = "jdk.internal.reflect.Reflection";
        }
    }

    public static void registerInvocationPlugins(AnnotationSubstitutionProcessor annotationSubstitutions, MetaAccessProvider metaAccess,
                    SnippetReflectionProvider snippetReflection, InvocationPlugins plugins, Replacements replacements, ParsingReason parsingReason) {

        // register the substratevm plugins
        registerSystemPlugins(metaAccess, plugins);
        registerReflectionPlugins(plugins, replacements);
        registerImageInfoPlugins(metaAccess, plugins);
        registerProxyPlugins(snippetReflection, annotationSubstitutions, plugins, parsingReason);
        registerAtomicUpdaterPlugins(metaAccess, snippetReflection, plugins, parsingReason);
        registerObjectPlugins(plugins);
        registerUnsafePlugins(metaAccess, plugins, snippetReflection, parsingReason);
        registerKnownIntrinsicsPlugins(plugins);
        registerStackValuePlugins(snippetReflection, plugins);
        registerArraysPlugins(plugins);
        registerArrayPlugins(plugins, snippetReflection, parsingReason);
        registerClassPlugins(plugins, snippetReflection);
        registerEdgesPlugins(metaAccess, plugins);
        registerJFRThrowablePlugins(plugins, replacements);
        registerJFREventTokenPlugins(plugins, replacements);
        registerVMConfigurationPlugins(snippetReflection, plugins);
        registerPlatformPlugins(snippetReflection, plugins);
        registerAWTPlugins(plugins);
        registerSizeOfPlugins(snippetReflection, plugins);
        registerReferenceAccessPlugins(plugins);
    }

    private static void registerSystemPlugins(MetaAccessProvider metaAccess, InvocationPlugins plugins) {
        Registration r = new Registration(plugins, System.class);
        if (SubstrateOptions.FoldSecurityManagerGetter.getValue()) {
            r.register0("getSecurityManager", new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    /* System.getSecurityManager() always returns null. */
                    b.addPush(JavaKind.Object, ConstantNode.forConstant(SubstrateObjectConstant.forObject(null), metaAccess, b.getGraph()));
                    return true;
                }
            });
        }

        r.register1("identityHashCode", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.addPush(JavaKind.Int, new SubstrateIdentityHashCodeNode(object, b.bci()));
                return true;
            }
        });
    }

    private static void registerReflectionPlugins(InvocationPlugins plugins, Replacements replacements) {
        Registration r = new Registration(plugins, reflectionClass, replacements);
        r.register0("getCallerClass", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Object, new SubstrateReflectionGetCallerClassNode(MacroParams.of(b, targetMethod)));
                return true;
            }

            @Override
            public boolean inlineOnly() {
                return true;
            }
        });
    }

    private static void registerImageInfoPlugins(MetaAccessProvider metaAccess, InvocationPlugins plugins) {
        Registration proxyRegistration = new Registration(plugins, ImageInfo.class);
        proxyRegistration.register0("inImageCode", new InvocationPlugin() {
            /** See {@link ImageInfo#inImageCode()}. */
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.push(JavaKind.Boolean, ConstantNode.forConstant(JavaConstant.TRUE, metaAccess, b.getGraph()));
                return true;
            }
        });
        proxyRegistration.register0("inImageBuildtimeCode", new InvocationPlugin() {
            /** See {@link ImageInfo#inImageBuildtimeCode()}. */
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.push(JavaKind.Boolean, ConstantNode.forConstant(JavaConstant.FALSE, metaAccess, b.getGraph()));
                return true;
            }
        });
        proxyRegistration.register0("inImageRuntimeCode", new InvocationPlugin() {
            /** See {@link ImageInfo#inImageRuntimeCode()}. */
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.push(JavaKind.Boolean, ConstantNode.forConstant(JavaConstant.TRUE, metaAccess, b.getGraph()));
                return true;
            }
        });
    }

    private static void registerProxyPlugins(SnippetReflectionProvider snippetReflection, AnnotationSubstitutionProcessor annotationSubstitutions, InvocationPlugins plugins, ParsingReason reason) {
        if (SubstrateOptions.parseOnce() || reason == ParsingReason.PointsToAnalysis) {
            Registration proxyRegistration = new Registration(plugins, Proxy.class);
            proxyRegistration.register2("getProxyClass", ClassLoader.class, Class[].class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode classLoaderNode, ValueNode interfacesNode) {
                    interceptProxyInterfaces(b, targetMethod, snippetReflection, annotationSubstitutions, interfacesNode);
                    return false;
                }
            });

            proxyRegistration.register3("newProxyInstance", ClassLoader.class, Class[].class, InvocationHandler.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode classLoaderNode, ValueNode interfacesNode, ValueNode invocationHandlerNode) {
                    interceptProxyInterfaces(b, targetMethod, snippetReflection, annotationSubstitutions, interfacesNode);
                    return false;
                }
            });
        }
    }

    /**
     * Try to intercept proxy interfaces passed in as literal constants, and register the interfaces
     * in the {@link DynamicProxyRegistry}.
     */
    private static void interceptProxyInterfaces(GraphBuilderContext b, ResolvedJavaMethod targetMethod, SnippetReflectionProvider snippetReflection,
                    AnnotationSubstitutionProcessor annotationSubstitutions, ValueNode interfacesNode) {
        Class<?>[] interfaces = extractClassArray(snippetReflection, annotationSubstitutions, interfacesNode);
        if (interfaces != null) {
            /* The interfaces array can be empty. The java.lang.reflect.Proxy API allows it. */
            ImageSingletons.lookup(DynamicProxyRegistry.class).addProxyClass(interfaces);
            if (ImageSingletons.contains(FallbackFeature.class)) {
                ImageSingletons.lookup(FallbackFeature.class).addAutoProxyInvoke(b.getMethod(), b.bci());
            }
            if (Options.DynamicProxyTracing.getValue()) {
                System.out.println("Successfully determined constant value for interfaces argument of call to " + targetMethod.format("%H.%n(%p)") +
                                " reached from " + b.getGraph().method().format("%H.%n(%p)") + ". " + "Registered proxy class for " + Arrays.toString(interfaces) + ".");
            }
        } else {
            if (Options.DynamicProxyTracing.getValue() && !b.parsingIntrinsic()) {
                System.out.println("Could not determine constant value for interfaces argument of call to " + targetMethod.format("%H.%n(%p)") +
                                " reached from " + b.getGraph().method().format("%H.%n(%p)") + ".");
            }
        }
    }

    /**
     * Try to extract a Class array from a ValueNode. It does not guarantee that the array content
     * will not change.
     */
    static Class<?>[] extractClassArray(SnippetReflectionProvider snippetReflection, AnnotationSubstitutionProcessor annotationSubstitutions, ValueNode arrayNode) {
        return extractClassArray(annotationSubstitutions, snippetReflection, arrayNode, false);
    }

    /**
     * Try to extract a Class array from a ValueNode. There are two situations:
     *
     * 1. The node is a ConstantNode. Then we get its initial value. However, since Java doesn't
     * have immutable arrays this method cannot guarantee that the array content will not change.
     * Therefore, if <code>exact</code> is set to true we return null.
     *
     * 2. The node is a NewArrayNode. Then we track the stores in the array as long as all are
     * constants and there is no control flow split. If the content of the array cannot be determine
     * a null value is returned.
     */
    static Class<?>[] extractClassArray(AnnotationSubstitutionProcessor annotationSubstitutions, SnippetReflectionProvider snippetReflection, ValueNode arrayNode, boolean exact) {
        /* Use the original value in case we are in a deopt target method. */
        ValueNode originalArrayNode = getDeoptProxyOriginalValue(arrayNode);
        if (originalArrayNode.isJavaConstant() && !exact) {
            /*
             * The array is a constant, however that doesn't make the array immutable, i.e., its
             * elements can still be changed. We assume that will not happen.
             */
            Class<?>[] classes = snippetReflection.asObject(Class[].class, originalArrayNode.asJavaConstant());

            /*
             * If any of the element is null just bailout, this is probably a situation where the
             * array will be filled in later and we don't track that.
             */
            return classes == null ? null : Stream.of(classes).allMatch(Objects::nonNull) ? classes : null;

        } else if (originalArrayNode instanceof NewArrayNode) {
            /*
             * Find the elements written to the array. If the array length is a constant, all
             * written elements are constants and all array elements are filled then return the
             * array elements.
             */
            NewArrayNode newArray = (NewArrayNode) originalArrayNode;
            ValueNode newArrayLengthNode = newArray.length();
            if (!newArrayLengthNode.isJavaConstant()) {
                /*
                 * If the array size is not a constant we bail out early since we cannot check that
                 * all array elements are filled.
                 */
                return null;
            }
            assert newArrayLengthNode.asJavaConstant().getJavaKind() == JavaKind.Int;

            /*
             * Walk down the control flow successor as long as we find StoreIndexedNode. Those are
             * values written in the array.
             */
            List<Class<?>> classList = new ArrayList<>();
            FixedNode successor = unwrapNode(newArray.next());
            while (successor instanceof StoreIndexedNode) {
                StoreIndexedNode store = (StoreIndexedNode) successor;
                assert getDeoptProxyOriginalValue(store.array()).equals(newArray);
                ValueNode valueNode = store.value();
                if (valueNode.isConstant() && !valueNode.isNullConstant()) {
                    Class<?> clazz = snippetReflection.asObject(Class.class, valueNode.asJavaConstant());
                    /*
                     * It is possible that the returned class is a substitution class, e.g.,
                     * DynamicHub returned for a Class.class constant. Get the target class of the
                     * substitution class.
                     */
                    classList.add(annotationSubstitutions == null ? clazz : annotationSubstitutions.getTargetClass(clazz));
                } else {
                    /* If not all classes are non-null constants we bail out. */
                    classList = null;
                    break;
                }
                successor = unwrapNode(store.next());
            }

            /*
             * Check that all array elements are filled, i.e., the number of writes matches the size
             * of the array.
             */
            int newArrayLength = newArrayLengthNode.asJavaConstant().asInt();

            return classList != null && classList.size() == newArrayLength ? classList.toArray(new Class<?>[0]) : null;
        }
        return null;
    }

    private static ValueNode getDeoptProxyOriginalValue(ValueNode node) {
        ValueNode original = node;
        while (original instanceof DeoptProxyNode) {
            original = ((DeoptProxyNode) original).getOriginalNode();
        }
        return original;
    }

    /**
     * Unwrap FullInfopointNode and DeoptEntryNode since they are not important for the Class[]
     * elements analysis.
     */
    private static FixedNode unwrapNode(FixedNode node) {
        FixedNode successor = node;
        while (successor instanceof FullInfopointNode || successor instanceof DeoptEntryNode) {
            assert !(successor instanceof DeoptEntryNode) || ((HostedMethod) successor.graph().method()).isDeoptTarget();
            if (successor instanceof FullInfopointNode) {
                successor = ((FullInfopointNode) successor).next();
            } else {
                successor = ((DeoptEntryNode) successor).next();
            }
        }
        return successor;
    }

    private static void registerAtomicUpdaterPlugins(MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflection, InvocationPlugins plugins, ParsingReason reason) {
        Registration referenceUpdaterRegistration = new Registration(plugins, AtomicReferenceFieldUpdater.class);
        referenceUpdaterRegistration.register3("newUpdater", Class.class, Class.class, String.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode tclassNode, ValueNode vclassNode, ValueNode fieldNameNode) {
                interceptUpdaterInvoke(metaAccess, snippetReflection, reason, tclassNode, fieldNameNode);
                /* Always return false; the call is not replaced. */
                return false;
            }
        });

        Registration integerUpdaterRegistration = new Registration(plugins, AtomicIntegerFieldUpdater.class);
        integerUpdaterRegistration.register2("newUpdater", Class.class, String.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode tclassNode, ValueNode fieldNameNode) {
                interceptUpdaterInvoke(metaAccess, snippetReflection, reason, tclassNode, fieldNameNode);
                /* Always return false; the call is not replaced. */
                return false;
            }
        });

        Registration longUpdaterRegistration = new Registration(plugins, AtomicLongFieldUpdater.class);
        longUpdaterRegistration.register2("newUpdater", Class.class, String.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode tclassNode, ValueNode fieldNameNode) {
                interceptUpdaterInvoke(metaAccess, snippetReflection, reason, tclassNode, fieldNameNode);
                /* Always return false; the call is not replaced. */
                return false;
            }
        });
    }

    /**
     * Intercept the invoke to newUpdater. If the holder class and field name are constant register
     * them for reflection/unsafe access.
     */
    private static void interceptUpdaterInvoke(MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflection, ParsingReason reason, ValueNode tclassNode, ValueNode fieldNameNode) {
        if (SubstrateOptions.parseOnce() || reason == ParsingReason.PointsToAnalysis) {
            if (tclassNode.isConstant() && fieldNameNode.isConstant()) {
                Class<?> tclass = snippetReflection.asObject(Class.class, tclassNode.asJavaConstant());
                String fieldName = snippetReflection.asObject(String.class, fieldNameNode.asJavaConstant());
                try {
                    Field field = tclass.getDeclaredField(fieldName);
                    // register the holder class and the field for reflection
                    RuntimeReflection.register(tclass);
                    RuntimeReflection.register(field);

                    // register the field for unsafe access
                    registerAsUnsafeAccessed(metaAccess, field);
                } catch (NoSuchFieldException e) {
                    /*
                     * Ignore the exception. : If the field does not exist, there will be an error
                     * at run time. That is then the same behavior as on HotSpot. The allocation of
                     * the AtomicReferenceFieldUpdater could be in a never-executed path, in which
                     * case, if we threw the exception during image building, we would wrongly
                     * prohibit image generation.
                     */
                }
            }
        }
    }

    private static void registerAsUnsafeAccessed(MetaAccessProvider metaAccess, Field field) {
        AnalysisField targetField = (AnalysisField) metaAccess.lookupJavaField(field);
        targetField.registerAsAccessed();
        AnalysisUniverse universe = (AnalysisUniverse) ((UniverseMetaAccess) metaAccess).getUniverse();
        targetField.registerAsUnsafeAccessed(universe);
    }

    private static void registerObjectPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Object.class);
        r.register1("clone", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode object = receiver.get();
                b.addPush(JavaKind.Object, new ObjectCloneWithExceptionNode(MacroParams.of(b, targetMethod, object)));
                return true;
            }
        });

        r.register1("hashCode", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode object = receiver.get();
                b.addPush(JavaKind.Int, new SubstrateIdentityHashCodeNode(object, b.bci()));
                return true;
            }
        });
    }

    private static void registerUnsafePlugins(MetaAccessProvider metaAccess, InvocationPlugins plugins, SnippetReflectionProvider snippetReflection, ParsingReason reason) {
        registerUnsafePlugins(metaAccess, new Registration(plugins, sun.misc.Unsafe.class), snippetReflection, reason, true);
        if (JavaVersionUtil.JAVA_SPEC > 8) {
            Registration r = new Registration(plugins, "jdk.internal.misc.Unsafe");
            registerUnsafePlugins(metaAccess, r, snippetReflection, reason, false);

            r.register3("objectFieldOffset", Receiver.class, Class.class, String.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode classNode, ValueNode nameNode) {
                    if (classNode.isConstant() && nameNode.isConstant()) {
                        /* If the class and field name arguments are constant. */
                        Class<?> clazz = snippetReflection.asObject(Class.class, classNode.asJavaConstant());
                        String fieldName = snippetReflection.asObject(String.class, nameNode.asJavaConstant());
                        try {
                            Field targetField = clazz.getDeclaredField(fieldName);
                            return processFieldOffset(b, targetField, reason, metaAccess, false);
                        } catch (NoSuchFieldException | LinkageError e) {
                            return false;
                        }
                    }
                    return false;
                }
            });
            r.register3("allocateUninitializedArray", Receiver.class, Class.class, int.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode componentTypeNode, ValueNode lengthNode) {
                    /*
                     * For simplicity, we only intrinsify if the componentType is a compile-time
                     * constant. That also allows us to constant-fold the required check that the
                     * component type is a primitive type.
                     */
                    if (componentTypeNode.isJavaConstant() && componentTypeNode.asJavaConstant().isNonNull()) {
                        ResolvedJavaType componentType = b.getConstantReflection().asJavaType(componentTypeNode.asJavaConstant());
                        if (componentType.isPrimitive()) {
                            /* Emits a null-check for the otherwise unused receiver. */
                            unsafe.get();

                            LogicNode lengthNegative = b.append(IntegerLessThanNode.create(lengthNode, ConstantNode.forInt(0), NodeView.DEFAULT));
                            b.emitBytecodeExceptionCheck(lengthNegative, false, BytecodeExceptionNode.BytecodeExceptionKind.ILLEGAL_ARGUMENT_EXCEPTION_NEGATIVE_LENGTH);
                            b.addPush(JavaKind.Object, new NewArrayNode(componentType, lengthNode, false));
                            return true;
                        }
                    }
                    return false;
                }
            });
        }
    }

    private static void registerUnsafePlugins(MetaAccessProvider metaAccess, Registration r, SnippetReflectionProvider snippetReflection, ParsingReason reason, boolean isSunMiscUnsafe) {
        r.register2("staticFieldOffset", Receiver.class, Field.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode fieldNode) {
                if (fieldNode.isConstant()) {
                    Field targetField = snippetReflection.asObject(Field.class, fieldNode.asJavaConstant());
                    return processFieldOffset(b, targetField, reason, metaAccess, isSunMiscUnsafe);
                }
                return false;
            }
        });
        r.register2("staticFieldBase", Receiver.class, Field.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode fieldNode) {
                if (fieldNode.isConstant()) {
                    Field targetField = snippetReflection.asObject(Field.class, fieldNode.asJavaConstant());
                    return processStaticFieldBase(b, targetField, isSunMiscUnsafe);
                }
                return false;

            }
        });
        r.register2("objectFieldOffset", Receiver.class, Field.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode fieldNode) {
                if (fieldNode.isConstant()) {
                    Field targetField = snippetReflection.asObject(Field.class, fieldNode.asJavaConstant());
                    return processFieldOffset(b, targetField, reason, metaAccess, isSunMiscUnsafe);
                }
                return false;
            }
        });
        r.register2("allocateInstance", Receiver.class, Class.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode clazz) {
                /* Emits a null-check for the otherwise unused receiver. */
                unsafe.get();
                /*
                 * The allocated class must be null-checked already before the class initialization
                 * check.
                 */
                ValueNode clazzNonNull = b.nullCheckedValue(clazz, DeoptimizationAction.None);
                b.add(new EnsureClassInitializedNode(clazzNonNull));
                ValueNode clazzLegal = b.add(new ValidateNewInstanceClassNode(clazzNonNull));
                b.addPush(JavaKind.Object, new DynamicNewInstanceNode(clazzLegal, true));
                return true;
            }
        });
    }

    private static boolean processFieldOffset(GraphBuilderContext b, Field targetField, ParsingReason reason, MetaAccessProvider metaAccess, boolean isSunMiscUnsafe) {
        if (!isValidField(targetField, isSunMiscUnsafe)) {
            return false;
        }

        if (SubstrateOptions.parseOnce() || reason == ParsingReason.PointsToAnalysis) {
            /* Register the field for unsafe access. */
            registerAsUnsafeAccessed(metaAccess, targetField);

        } else {
            HostedMetaAccess hostedMetaAccess = (HostedMetaAccess) metaAccess;
            HostedField hostedField = hostedMetaAccess.lookupJavaField(targetField);
            if (!hostedField.wrapped.isUnsafeAccessed()) {
                /*
                 * The target field was not constant folded during static analysis, so the above
                 * unsafe access registration did not run. A UnsupportedFeatureError will be thrown
                 * at run time for this call.
                 */
                return false;
            }
        }

        /* Usage of lambdas is not allowed in Graal nodes, so need explicit inner class. */
        Function<CoreProviders, JavaConstant> fieldOffsetConstantProvider = new Function<CoreProviders, JavaConstant>() {
            @Override
            public JavaConstant apply(CoreProviders providers) {
                ResolvedJavaField rField = providers.getMetaAccess().lookupJavaField(targetField);
                if (rField instanceof SharedField) {
                    long fieldOffset = ((SharedField) rField).getLocation();
                    assert fieldOffset > 0;
                    return JavaConstant.forLong(fieldOffset);
                }
                return null;
            }
        };

        b.addPush(JavaKind.Long, LazyConstantNode.create(StampFactory.forKind(JavaKind.Long), fieldOffsetConstantProvider, b));
        return true;
    }

    private static boolean isValidField(Field targetField, boolean isSunMiscUnsafe) {
        if (targetField == null) {
            /* A NullPointerException will be thrown at run time for this call. */
            return false;
        }
        if (isSunMiscUnsafe && JavaVersionUtil.JAVA_SPEC >= 16 &&
                        (RecordSupport.singleton().isRecord(targetField.getDeclaringClass()) || SubstrateUtil.isHiddenClass(targetField.getDeclaringClass()))) {
            /*
             * After JDK 11, sun.misc.Unsafe performs a few more checks than
             * jdk.internal.misc.Unsafe to explicitly disallow hidden classes and records.
             */
            return false;
        }
        return true;
    }

    private static final Function<CoreProviders, JavaConstant> primitiveStaticFieldBaseConstant = new Function<CoreProviders, JavaConstant>() {
        @Override
        public JavaConstant apply(CoreProviders providers) {
            return StaticFieldsSupport.dataAvailable() ? SubstrateObjectConstant.forObject(StaticFieldsSupport.getStaticPrimitiveFields()) : null;
        }
    };
    private static final Function<CoreProviders, JavaConstant> objectStaticFieldBaseConstant = new Function<CoreProviders, JavaConstant>() {
        @Override
        public JavaConstant apply(CoreProviders providers) {
            return StaticFieldsSupport.dataAvailable() ? SubstrateObjectConstant.forObject(StaticFieldsSupport.getStaticObjectFields()) : null;
        }
    };

    private static boolean processStaticFieldBase(GraphBuilderContext b, Field targetField, boolean isSunMiscUnsafe) {
        if (!isValidField(targetField, isSunMiscUnsafe)) {
            return false;
        }

        b.addPush(JavaKind.Object, LazyConstantNode.create(StampFactory.objectNonNull(),
                        targetField.getType().isPrimitive() ? primitiveStaticFieldBaseConstant : objectStaticFieldBaseConstant, b));
        return true;
    }

    private static void registerArrayPlugins(InvocationPlugins plugins, SnippetReflectionProvider snippetReflection, ParsingReason reason) {
        Registration r = new Registration(plugins, Array.class).setAllowOverwrite(true);
        r.register2("newInstance", Class.class, int[].class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode clazzNode, ValueNode dimensionsNode) {
                if (SubstrateOptions.parseOnce() || reason == ParsingReason.PointsToAnalysis) {
                    /*
                     * There is no Graal node for dynamic multi array allocation, and it is also not
                     * necessary for performance reasons. But when the arguments are constant, we
                     * can register the array types as instantiated so that the allocation succeeds
                     * at run time without manual registration.
                     */
                    ValueNode dimensionCountNode = GraphUtil.arrayLength(dimensionsNode, ArrayLengthProvider.FindLengthMode.SEARCH_ONLY, b.getConstantReflection());
                    if (clazzNode.isConstant() && !clazzNode.isNullConstant() && dimensionCountNode != null && dimensionCountNode.isConstant()) {
                        Class<?> clazz = snippetReflection.asObject(Class.class, clazzNode.asJavaConstant());
                        int dimensionCount = dimensionCountNode.asJavaConstant().asInt();

                        AnalysisType type = (AnalysisType) b.getMetaAccess().lookupJavaType(clazz);
                        for (int i = 0; i < dimensionCount; i++) {
                            type = type.getArrayClass();
                            type.registerAsAllocated(clazzNode);
                        }
                    }
                }
                return false;
            }
        });
    }

    private static void registerArraysPlugins(InvocationPlugins plugins) {

        Registration r = new Registration(plugins, Arrays.class).setAllowOverwrite(true);

        r.register2("copyOf", Object[].class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode original, ValueNode newLength) {
                ValueNode nonNullOriginal = b.nullCheckedValue(original);

                /* Get the class from the original node. */
                GetClassNode originalArrayType = b.add(new GetClassNode(original.stamp(NodeView.DEFAULT), nonNullOriginal));

                ValueNode originalLength = b.add(ArrayLengthNode.create(nonNullOriginal, b.getConstantReflection()));
                Stamp stamp = b.getInvokeReturnStamp(b.getAssumptions()).getTrustedStamp().join(nonNullOriginal.stamp(NodeView.DEFAULT));

                b.addPush(JavaKind.Object, new SubstrateArraysCopyOfWithExceptionNode(stamp, nonNullOriginal, originalLength, newLength, originalArrayType, b.bci()));
                return true;
            }
        });

        r.register3("copyOf", Object[].class, int.class, Class.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode original, ValueNode newLength, ValueNode newArrayType) {
                Stamp stamp;
                if (newArrayType.isConstant()) {
                    ResolvedJavaType newType = b.getConstantReflection().asJavaType(newArrayType.asConstant());
                    stamp = StampFactory.objectNonNull(TypeReference.createExactTrusted(newType));
                } else {
                    stamp = b.getInvokeReturnStamp(b.getAssumptions()).getTrustedStamp();
                }

                ValueNode nonNullOriginal = b.nullCheckedValue(original);
                ValueNode originalLength = b.add(ArrayLengthNode.create(nonNullOriginal, b.getConstantReflection()));
                b.addPush(JavaKind.Object, new SubstrateArraysCopyOfWithExceptionNode(stamp, nonNullOriginal, originalLength, newLength, newArrayType, b.bci()));
                return true;
            }
        });

    }

    private static void registerKnownIntrinsicsPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, KnownIntrinsics.class);
        r.register0("heapBase", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Object, ReadReservedRegister.createReadHeapBaseNode(b.getGraph()));
                return true;
            }
        });
        r.register1("readHub", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                ValueNode nonNullObject = b.nullCheckedValue(object);
                b.addPush(JavaKind.Object, new LoadHubNode(b.getStampProvider(), nonNullObject));
                return true;
            }
        });
        r.register1("nonNullPointer", Pointer.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.addPush(JavaKind.Object, new PiNode(object, nonZeroWord()));
                return true;
            }
        });

        r.register0("readStackPointer", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Object, ReadReservedRegister.createReadStackPointerNode(b.getGraph()));
                return true;
            }
        });
        r.register0("readCallerStackPointer", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                checkNeverInline(b);
                b.addPush(JavaKind.Object, new ReadCallerStackPointerNode());
                return true;
            }
        });
        r.register0("readReturnAddress", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                checkNeverInline(b);
                b.addPush(JavaKind.Object, new ReadReturnAddressNode());
                return true;
            }
        });
        r.register4("farReturn", Object.class, Pointer.class, CodePointer.class, boolean.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode result, ValueNode sp, ValueNode ip,
                            ValueNode fromMethodWithCalleeSavedRegisters) {

                if (!fromMethodWithCalleeSavedRegisters.isConstant()) {
                    throw b.bailout("parameter fromMethodWithCalleeSavedRegisters is not a compile time constant for call to " +
                                    targetMethod.format("%H.%n(%p)") + " in " + b.getMethod().asStackTraceElement(b.bci()));
                }
                b.add(new FarReturnNode(result, sp, ip, fromMethodWithCalleeSavedRegisters.asJavaConstant().asInt() != 0));
                return true;
            }
        });
        r.register0("testDeoptimize", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new TestDeoptimizeNode());
                return true;
            }
        });

        r.register0("isDeoptimizationTarget", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (SubstrateOptions.parseOnce()) {
                    throw VMError.unimplemented("Intrinsification of isDeoptimizationTarget not done yet");
                }

                if (b.getGraph().method() instanceof SharedMethod) {
                    SharedMethod method = (SharedMethod) b.getGraph().method();
                    if (method.isDeoptTarget()) {
                        b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(true));
                    } else {
                        b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(false));
                    }
                } else {
                    // In analysis the value is always true.
                    b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(true));
                }
                return true;
            }
        });

        registerCastExact(r);
    }

    public static void registerCastExact(Registration r) {
        r.register2("castExact", Object.class, Class.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object, ValueNode javaClass) {
                ValueNode nullCheckedClass = b.nullCheckedValue(javaClass);
                LogicNode condition = b.append(InstanceOfDynamicNode.create(b.getAssumptions(), b.getConstantReflection(), nullCheckedClass, object, true, true));
                AbstractBeginNode guard = b.emitBytecodeExceptionCheck(condition, true, BytecodeExceptionNode.BytecodeExceptionKind.CLASS_CAST, object, nullCheckedClass);
                if (guard != null) {
                    b.addPush(JavaKind.Object, DynamicPiNode.create(b.getAssumptions(), b.getConstantReflection(), object, guard, nullCheckedClass, true, true));
                } else {
                    b.addPush(JavaKind.Object, object);
                }
                return true;
            }
        });
    }

    private static void checkNeverInline(GraphBuilderContext b) {
        if (!DirectAnnotationAccess.isAnnotationPresent(b.getMethod(), NeverInline.class)) {
            throw VMError.shouldNotReachHere("Accessing the stack pointer or instruction pointer of the caller frame is only safe and deterministic if the method is not inlined. " +
                            "Therefore, the method " + b.getMethod().format("%H.%n(%p)") + " must be annoated with @" + NeverInline.class.getSimpleName());
        }
    }

    private static IntegerStamp nonZeroWord() {
        return StampFactory.forUnsignedInteger(64, 1, 0xffffffffffffffffL);
    }

    private static void registerStackValuePlugins(SnippetReflectionProvider snippetReflection, InvocationPlugins plugins) {
        Registration r = new Registration(plugins, StackValue.class);

        r.register1("get", int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode sizeNode) {
                long size = longValue(b, targetMethod, sizeNode, "size");
                b.addPush(JavaKind.Object, StackValueNode.create(1, size, b));
                return true;
            }
        });
        r.register1("get", Class.class, new InvocationPlugin() {
            @SuppressWarnings("unchecked")
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode classNode) {
                Class<? extends PointerBase> clazz = constantObjectParameter(b, snippetReflection, targetMethod, 0, Class.class, classNode);
                int size = SizeOf.get(clazz);
                b.addPush(JavaKind.Object, StackValueNode.create(1, size, b));
                return true;
            }
        });
        r.register2("get", int.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode numElementsNode, ValueNode elementSizeNode) {
                long numElements = longValue(b, targetMethod, numElementsNode, "numElements");
                long elementSize = longValue(b, targetMethod, elementSizeNode, "elementSize");
                b.addPush(JavaKind.Object, StackValueNode.create(numElements, elementSize, b));
                return true;
            }
        });
        r.register2("get", int.class, Class.class, new InvocationPlugin() {
            @SuppressWarnings("unchecked")
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode numElementsNode, ValueNode classNode) {
                long numElements = longValue(b, targetMethod, numElementsNode, "numElements");
                Class<? extends PointerBase> clazz = constantObjectParameter(b, snippetReflection, targetMethod, 0, Class.class, classNode);
                int size = SizeOf.get(clazz);
                b.addPush(JavaKind.Object, StackValueNode.create(numElements, size, b));
                return true;
            }
        });
    }

    private static void registerClassPlugins(InvocationPlugins plugins, SnippetReflectionProvider snippetReflection) {
        registerClassDesiredAssertionStatusPlugin(plugins, snippetReflection);
    }

    public static void registerClassDesiredAssertionStatusPlugin(InvocationPlugins plugins, SnippetReflectionProvider snippetReflection) {
        Registration r = new Registration(plugins, Class.class);
        r.register1("desiredAssertionStatus", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                JavaConstant constantReceiver = receiver.get().asJavaConstant();
                if (constantReceiver != null && constantReceiver.isNonNull()) {
                    Object clazzOrHub = snippetReflection.asObject(Object.class, constantReceiver);
                    boolean desiredAssertionStatus;
                    if (clazzOrHub instanceof Class) {
                        desiredAssertionStatus = RuntimeAssertionsSupport.singleton().desiredAssertionStatus((Class<?>) clazzOrHub);
                    } else if (clazzOrHub instanceof DynamicHub) {
                        desiredAssertionStatus = ((DynamicHub) clazzOrHub).desiredAssertionStatus();
                    } else {
                        throw VMError.shouldNotReachHere("Unexpected class object: " + clazzOrHub);
                    }
                    b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(desiredAssertionStatus));
                    return true;
                }
                return false;
            }
        });
    }

    private static void registerEdgesPlugins(MetaAccessProvider metaAccess, InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Edges.class).setAllowOverwrite(true);
        for (Class<?> c : new Class<?>[]{Node.class, NodeList.class}) {
            r.register2("get" + c.getSimpleName() + "Unsafe", Node.class, long.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode node, ValueNode offset) {
                    b.addPush(JavaKind.Object, new UnsafePartitionLoadNode(node, offset, JavaKind.Object, LocationIdentity.any(), GraalEdgeUnsafePartition.get(), metaAccess.lookupJavaType(c)));
                    return true;
                }
            });

            r.register3("put" + c.getSimpleName() + "Unsafe", Node.class, long.class, c, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode node, ValueNode offset, ValueNode value) {
                    b.add(new UnsafePartitionStoreNode(node, offset, value, JavaKind.Object, LocationIdentity.any(), GraalEdgeUnsafePartition.get(), metaAccess.lookupJavaType(c)));
                    return true;
                }
            });
        }
    }

    protected static long longValue(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode node, String name) {
        if (!node.isConstant()) {
            throw b.bailout("parameter " + name + " is not a compile time constant for call to " + targetMethod.format("%H.%n(%p)") + " in " + b.getMethod().asStackTraceElement(b.bci()));
        }
        return node.asJavaConstant().asLong();
    }

    /*
     * When Java Flight Recorder is enabled during image generation, the bytecodes of some methods
     * get instrumented. Undo the instrumentation so that it does not end up in the generated image.
     */

    private static void registerJFRThrowablePlugins(InvocationPlugins plugins, Replacements replacements) {
        Registration r = new Registration(plugins, "oracle.jrockit.jfr.jdkevents.ThrowableTracer", replacements).setAllowOverwrite(true);
        r.register2("traceError", Error.class, String.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode throwable, ValueNode message) {
                return true;
            }
        });
        r.register2("traceThrowable", Throwable.class, String.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode throwable, ValueNode message) {
                return true;
            }
        });
    }

    private static void registerJFREventTokenPlugins(InvocationPlugins plugins, Replacements replacements) {
        Registration r = new Registration(plugins, "com.oracle.jrockit.jfr.EventToken", replacements);
        r.register1("isEnabled", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                receiver.get();
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(false));
                return true;
            }
        });
    }

    private static void registerVMConfigurationPlugins(SnippetReflectionProvider snippetReflection, InvocationPlugins plugins) {
        Registration r = new Registration(plugins, ImageSingletons.class);
        r.register1("contains", Class.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode classNode) {
                Class<?> key = constantObjectParameter(b, snippetReflection, targetMethod, 0, Class.class, classNode);
                boolean result = ImageSingletons.contains(key);
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(result));
                return true;
            }
        });
        r.register1("lookup", Class.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode classNode) {
                Class<?> key = constantObjectParameter(b, snippetReflection, targetMethod, 0, Class.class, classNode);
                Object result = ImageSingletons.lookup(key);
                b.addPush(JavaKind.Object, ConstantNode.forConstant(snippetReflection.forObject(result), b.getMetaAccess()));
                return true;
            }
        });
    }

    private static void registerPlatformPlugins(SnippetReflectionProvider snippetReflection, InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Platform.class);
        r.register1("includedIn", Class.class, new InvocationPlugin() {
            @SuppressWarnings("unchecked")
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode classNode) {
                Class<? extends Platform> platform = constantObjectParameter(b, snippetReflection, targetMethod, 0, Class.class, classNode);
                boolean result = Platform.includedIn(platform);
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(result));
                return true;
            }
        });
    }

    /**
     * To prevent AWT linkage error on {@link OS#LINUX} that happens with 'awt_headless' in headless
     * mode, we eliminate native methods that depend on 'awt_xawt' library in the call-tree.
     */
    private static void registerAWTPlugins(InvocationPlugins plugins) {
        if (OS.getCurrent() == OS.LINUX) {
            Registration r = new Registration(plugins, GraphicsEnvironment.class);
            r.register0("isHeadless", new InvocationPlugin() {
                @SuppressWarnings("unchecked")
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    boolean isHeadless = GraphicsEnvironment.isHeadless();
                    b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(isHeadless));
                    return true;
                }
            });
        }
    }

    private static void registerSizeOfPlugins(SnippetReflectionProvider snippetReflection, InvocationPlugins plugins) {
        Registration r = new Registration(plugins, SizeOf.class);
        r.register1("get", Class.class, new InvocationPlugin() {
            @SuppressWarnings("unchecked")
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode classNode) {
                Class<? extends PointerBase> clazz = constantObjectParameter(b, snippetReflection, targetMethod, 0, Class.class, classNode);
                int result = SizeOf.get(clazz);
                b.addPush(JavaKind.Int, ConstantNode.forInt(result));
                return true;
            }
        });
        r.register1("unsigned", Class.class, new InvocationPlugin() {
            @SuppressWarnings("unchecked")
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode classNode) {
                Class<? extends PointerBase> clazz = constantObjectParameter(b, snippetReflection, targetMethod, 0, Class.class, classNode);
                UnsignedWord result = SizeOf.unsigned(clazz);
                b.addPush(JavaKind.Object, ConstantNode.forConstant(snippetReflection.forObject(result), b.getMetaAccess()));
                return true;
            }
        });
    }

    private static void registerReferenceAccessPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, ReferenceAccessImpl.class);
        r.register2("getCompressedRepresentation", Receiver.class, Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode objectNode) {
                if (ReferenceAccess.singleton().haveCompressedReferences()) {
                    ValueNode compressedObj = SubstrateCompressionNode.compress(objectNode, ImageSingletons.lookup(CompressEncoding.class));
                    JavaKind compressedIntKind = JavaKind.fromWordSize(ConfigurationValues.getObjectLayout().getReferenceSize());
                    ValueNode compressedValue = b.add(WordCastNode.narrowOopToUntrackedWord(compressedObj, compressedIntKind));
                    b.addPush(JavaKind.Object, ZeroExtendNode.convertUnsigned(compressedValue, FrameAccess.getWordStamp(), NodeView.DEFAULT));
                } else {
                    b.addPush(JavaKind.Object, WordCastNode.objectToUntrackedPointer(objectNode, FrameAccess.getWordKind()));
                }
                return true;
            }
        });
        r.register2("uncompressReference", Receiver.class, UnsignedWord.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode wordNode) {
                if (ReferenceAccess.singleton().haveCompressedReferences()) {
                    CompressEncoding encoding = ImageSingletons.lookup(CompressEncoding.class);
                    JavaKind compressedIntKind = JavaKind.fromWordSize(ConfigurationValues.getObjectLayout().getReferenceSize());
                    NarrowOopStamp compressedStamp = (NarrowOopStamp) SubstrateNarrowOopStamp.compressed((AbstractObjectStamp) StampFactory.object(), encoding);
                    ValueNode narrowNode = b.add(NarrowNode.convertUnsigned(wordNode, StampFactory.forKind(compressedIntKind), NodeView.DEFAULT));
                    WordCastNode compressedObj = b.add(WordCastNode.wordToNarrowObject(narrowNode, compressedStamp));
                    b.addPush(JavaKind.Object, SubstrateCompressionNode.uncompress(compressedObj, encoding));
                } else {
                    b.addPush(JavaKind.Object, WordCastNode.wordToObject(wordNode, FrameAccess.getWordKind()));
                }
                return true;
            }
        });
    }

    private static <T> T constantObjectParameter(GraphBuilderContext b, SnippetReflectionProvider snippetReflection, ResolvedJavaMethod targetMethod, int parameterIndex, Class<T> declaredType,
                    ValueNode classNode) {
        checkParameterUsage(classNode.isConstant(), b, targetMethod, parameterIndex, "parameter is not a compile time constant");
        T result = snippetReflection.asObject(declaredType, classNode.asJavaConstant());
        checkParameterUsage(result != null, b, targetMethod, parameterIndex, "parameter is null");
        return result;
    }

    private static void checkParameterUsage(boolean condition, GraphBuilderContext b, ResolvedJavaMethod targetMethod, int parameterIndex, String message) {
        if (condition) {
            return;
        }

        String parameterName = null;
        LocalVariableTable variableTable = targetMethod.getLocalVariableTable();
        if (variableTable != null) {
            Local variable = variableTable.getLocal(parameterIndex, 0);
            if (variable != null) {
                parameterName = variable.getName();
            }
        }
        if (parameterName == null) {
            /* Fall back to parameter number if no name is available. */
            parameterName = String.valueOf(parameterIndex);
        }

        throw UserError.abort("%s: parameter %s of call to %s in %s", message, parameterName, targetMethod, b.getMethod().asStackTraceElement(b.bci()));
    }
}
