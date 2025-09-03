/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage;

import java.math.BigInteger;
import java.util.Arrays;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.hosted.webimage.wasm.WasmLMGraphBuilderPlugins;
import com.oracle.svm.webimage.functionintrinsics.JSCallNode;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.extended.JavaReadNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.nodes.memory.address.IndexAddressNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.BigIntegerSnippets;
import jdk.graal.compiler.replacements.SnippetSubstitutionInvocationPlugin;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.StringUTF16Snippets;
import jdk.graal.compiler.replacements.TargetGraphBuilderPlugins;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class JSGraphBuilderPlugins implements TargetGraphBuilderPlugins {
    /**
     * The sentinel value for {@link IsolateThread} in Web Image, where there can be only a single
     * isolate with a single thread.
     */
    private static final Word SINGLE_THREAD_SENTINEL = Word.unsigned(0x150_150_150_150_777L);

    @Override
    public void registerPlugins(GraphBuilderConfiguration.Plugins plugins, OptionValues options) {
        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        invocationPlugins.defer(() -> {
            registerCharacterPlugins(invocationPlugins);
            registerShortPlugins(invocationPlugins);
            registerIntegerLongPlugins(invocationPlugins, JavaKind.Int);
            registerIntegerLongPlugins(invocationPlugins, JavaKind.Long);
            registerStringPlugins(invocationPlugins);
            registerJSCopyOfPlugins(invocationPlugins);
            registerMathPlugins(invocationPlugins);
            registerBigIntegerPlugins(invocationPlugins);
            registerThreadPlugins(invocationPlugins);
            registerCurrentIsolatePlugins(invocationPlugins);
            // TODO GR-61725 Support ArrayFillNodes
            WasmLMGraphBuilderPlugins.unregisterArrayFillPlugins(invocationPlugins);
        });
    }

    private static void registerIntegerLongPlugins(InvocationPlugins plugins, JavaKind kind) {
        /*
         * In the standard GBP, these methods produce nodes which do not have an equivalent in
         * JavaScript, it is easier to just lower the method implementation.
         */

        Class<?> declaringClass = kind.toBoxedJavaClass();
        Class<?> type = kind.toJavaClass();
        Registration r = new Registration(plugins, declaringClass).setAllowOverwrite(true);
        r.register(new InvocationPlugin("reverseBytes", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                return false;
            }
        });
        r.register(new InvocationPlugin("divideUnsigned", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode dividend, ValueNode divisor) {
                return false;
            }
        });
        r.register(new InvocationPlugin("remainderUnsigned", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode dividend, ValueNode divisor) {
                return false;
            }
        });
        r.register(new InvocationPlugin("reverse", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                return false;
            }
        });
        r.register(new InvocationPlugin("bitCount", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                return false;
            }
        });
        r.register(new InvocationPlugin("numberOfLeadingZeros", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                return false;
            }
        });
        r.register(new InvocationPlugin("numberOfTrailingZeros", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                return false;
            }
        });
    }

    private static void registerCharacterPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Character.class).setAllowOverwrite(true);
        r.register(new InvocationPlugin("reverseBytes", char.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                /*
                 * In the standard GBP, this produces a ReverseBytesNode, for which there is no good
                 * lowering in JavaScript.
                 */
                return false;
            }
        });
    }

    private static void registerShortPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Short.class).setAllowOverwrite(true);
        r.register(new InvocationPlugin("reverseBytes", short.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                /*
                 * In the standard GBP, this produces a ReverseBytesNode, for which there is no good
                 * lowering in JavaScript.
                 */
                return false;
            }
        });
    }

    public static void registerStringPlugins(InvocationPlugins plugins) {
        /*
         * Disable getChar and putChar substitutions from StandardGraphBuilderPlugins. The
         * substitution there would generate raw memory accesses which is only partially supported.
         *
         * More concretely, the synthesized code there depends on accessing a two-byte char on a
         * byte array with one operation, which cannot be done with our current.
         *
         * See GR-34119
         */
        final Registration utf16r = new Registration(plugins, "java.lang.StringUTF16");
        utf16r.setAllowOverwrite(true);

        utf16r.register(new InvocationPlugin("getChar", byte[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                return false;
            }
        });
        utf16r.register(new InvocationPlugin("putChar", byte[].class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3) {
                return false;
            }
        });

        Registration r2 = new Registration(plugins, StringUTF16Snippets.class);
        r2.register(new InvocationPlugin("getChar", byte[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                b.addPush(JavaKind.Char, new JavaReadNode(JavaKind.Char,
                                new IndexAddressNode(arg1, new LeftShiftNode(arg2, ConstantNode.forInt(1)), JavaKind.Byte),
                                NamedLocationIdentity.getArrayLocation(JavaKind.Byte), BarrierType.NONE, MemoryOrderMode.PLAIN, false));
                return true;
            }
        });
    }

    private static void registerMathPlugins(InvocationPlugins plugins) {
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins, Math.class).setAllowOverwrite(true);
        /*
         * We disable the plugin for rint because the JavaScript Math.round() round x.5 towards
         * +infinity whereas Java round to the nearest even integer. For floor/ceil a RoundNode is
         * introduced which makes use of Javascript's Math.floor() and Math.ceil().
         */
        unregisterRound(r, "rint");

        /*
         * Disable multiplyHigh as it is not yet supported by Long64 emulation.
         *
         * TODO: GR-44083
         */
        unregisterMultiplyHigh(r);
    }

    private static void unregisterRound(InvocationPlugins.Registration r, String name) {
        r.register(new InvocationPlugin(name, Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                return false;
            }
        });

    }

    private static void unregisterMultiplyHigh(InvocationPlugins.Registration r) {
        r.register(new InvocationPlugin("multiplyHigh", long.class, long.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                return false;
            }
        });

        r.register(new InvocationPlugin("unsignedMultiplyHigh", long.class, long.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                return false;
            }
        });

    }

    private static void registerJSCopyOfPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Arrays.class).setAllowOverwrite(true);

        r.register(new InvocationPlugin("copyOf", Object[].class, int.class, Class.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3) {
                JavaConstant constant = JavaConstant.defaultForKind(arg3.stamp(NodeView.DEFAULT).getStackKind());
                ConstantNode c = b.add(ConstantNode.forConstant(constant, b.getMetaAccess()));
                b.addPush(JSCallNode.ARRAYS_COPY_OF_1.stamp().getStackKind(),
                                new JSCallNode(JSCallNode.ARRAYS_COPY_OF_1,
                                                StampFactory.forDeclaredType(null, b.getMetaAccess().lookupJavaType(Object[].class), false).getTrustedStamp(),
                                                arg1, arg2, arg3,
                                                c));
                return true;
            }
        });

        r.register(new InvocationPlugin("copyOf", Object[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                ConstantNode c = b.add(ConstantNode.forConstant(JavaConstant.NULL_POINTER, b.getMetaAccess()));
                b.addPush(JSCallNode.ARRAYS_COPY_OF_0.stamp().getStackKind(),
                                new JSCallNode(JSCallNode.ARRAYS_COPY_OF_0,
                                                StampFactory.forDeclaredType(null, b.getMetaAccess().lookupJavaType(Object[].class), false).getTrustedStamp(), arg1, arg2, c));
                return true;
            }
        });

        r.register(new InvocationPlugin("copyOf", byte[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                ConstantNode c = b.add(ConstantNode.forInt(0));
                b.addPush(JSCallNode.ARRAYS_COPY_OF_0.stamp().getStackKind(),
                                new JSCallNode(JSCallNode.ARRAYS_COPY_OF_0,
                                                StampFactory.forDeclaredType(null, b.getMetaAccess().lookupJavaType(byte[].class), false).getTrustedStamp(), arg1, arg2, c));
                return true;
            }
        });
        r.register(new InvocationPlugin("copyOf", short[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                ConstantNode c = b.add(ConstantNode.forInt(0));
                b.addPush(JSCallNode.ARRAYS_COPY_OF_0.stamp().getStackKind(),
                                new JSCallNode(JSCallNode.ARRAYS_COPY_OF_0,
                                                StampFactory.forDeclaredType(null, b.getMetaAccess().lookupJavaType(short[].class), false).getTrustedStamp(), arg1, arg2, c));
                return true;
            }
        });
        r.register(new InvocationPlugin("copyOf", int[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                ConstantNode c = b.add(ConstantNode.forInt(0));
                b.addPush(JSCallNode.ARRAYS_COPY_OF_0.stamp().getStackKind(),
                                new JSCallNode(JSCallNode.ARRAYS_COPY_OF_0,
                                                StampFactory.forDeclaredType(null, b.getMetaAccess().lookupJavaType(int[].class), false).getTrustedStamp(), arg1, arg2, c));
                return true;
            }
        });
        r.register(new InvocationPlugin("copyOf", long[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                ConstantNode c = b.add(ConstantNode.forLong(0));
                b.addPush(JSCallNode.ARRAYS_COPY_OF_0.stamp().getStackKind(),
                                new JSCallNode(JSCallNode.ARRAYS_COPY_OF_0,
                                                StampFactory.forDeclaredType(null, b.getMetaAccess().lookupJavaType(long[].class), false).getTrustedStamp(), arg1, arg2, c));
                return true;
            }
        });
        r.register(new InvocationPlugin("copyOf", char[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                ConstantNode c = b.add(ConstantNode.forInt(0));
                b.addPush(JSCallNode.ARRAYS_COPY_OF_0.stamp().getStackKind(),
                                new JSCallNode(JSCallNode.ARRAYS_COPY_OF_0,
                                                StampFactory.forDeclaredType(null, b.getMetaAccess().lookupJavaType(char[].class), false).getTrustedStamp(), arg1, arg2, c));
                return true;
            }
        });
        r.register(new InvocationPlugin("copyOf", float[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                ConstantNode c = b.add(ConstantNode.forInt(0));
                b.addPush(JSCallNode.ARRAYS_COPY_OF_0.stamp().getStackKind(),
                                new JSCallNode(JSCallNode.ARRAYS_COPY_OF_0,
                                                StampFactory.forDeclaredType(null, b.getMetaAccess().lookupJavaType(float[].class), false).getTrustedStamp(), arg1, arg2, c));
                return true;
            }
        });
        r.register(new InvocationPlugin("copyOf", double[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                ConstantNode c = b.add(ConstantNode.forInt(0));
                b.addPush(JSCallNode.ARRAYS_COPY_OF_0.stamp().getStackKind(),
                                new JSCallNode(JSCallNode.ARRAYS_COPY_OF_0,
                                                StampFactory.forDeclaredType(null, b.getMetaAccess().lookupJavaType(double[].class), false).getTrustedStamp(), arg1, arg2, c));
                return true;
            }
        });
        r.register(new InvocationPlugin("copyOf", boolean[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                ConstantNode c = b.add(ConstantNode.forBoolean(false));
                b.addPush(JSCallNode.ARRAYS_COPY_OF_0.stamp().getStackKind(),
                                new JSCallNode(JSCallNode.ARRAYS_COPY_OF_0,
                                                StampFactory.forDeclaredType(null, b.getMetaAccess().lookupJavaType(boolean[].class), false).getTrustedStamp(), arg1, arg2, c));
                return true;
            }
        });
    }

    private static void registerBigIntegerPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, BigInteger.class).setAllowOverwrite(true);
        // the upstream plugin introduces a ComputeObjectAddressNode that is not supported (yet)
        r.register(new SnippetSubstitutionInvocationPlugin<>(BigIntegerSnippets.Templates.class,
                        "implMultiplyToLen", int[].class, int.class, int[].class, int.class, int[].class) {
            @Override
            public SnippetTemplate.SnippetInfo getSnippet(BigIntegerSnippets.Templates templates) {
                return null;
            }

            @Override
            public boolean execute(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode[] args) {
                return false;
            }
        });
    }

    public static void registerThreadPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Thread.class).setAllowOverwrite(true);
        r.register(new InvocationPlugin("onSpinWait") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                return false;
            }
        });
    }

    /**
     * Overwrites the GBPs from {@link com.oracle.svm.hosted.c.function.CEntryPointSupport} with the
     * single-threaded version.
     * <p>
     * This is called for all Web Image backends.
     */
    public static void registerCurrentIsolatePlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, CurrentIsolate.class).setAllowOverwrite(true);
        r.register(new InvocationPlugin.RequiredInvocationPlugin("getCurrentThread") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Object, ConstantNode.forIntegerKind(ConfigurationValues.getWordKind(), SINGLE_THREAD_SENTINEL.rawValue()));
                return true;
            }
        });
    }
}
