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
package com.oracle.svm.core.graal.snippets;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.util.Map;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.UnreachableNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerDivRemNode;
import org.graalvm.compiler.nodes.calc.SignedDivNode;
import org.graalvm.compiler.nodes.calc.SignedRemNode;
import org.graalvm.compiler.nodes.calc.UnsignedDivNode;
import org.graalvm.compiler.nodes.calc.UnsignedRemNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;

public abstract class ArithmeticSnippets extends SubstrateTemplates implements Snippets {

    @Snippet
    protected static int idivSnippet(int x, int y, @ConstantParameter boolean needsZeroCheck, @ConstantParameter boolean needsBoundsCheck) {
        if (needsZeroCheck) {
            zeroCheck(y);
        }
        if (needsBoundsCheck && x == Integer.MIN_VALUE && y == -1) {
            return Integer.MIN_VALUE;
        }
        return safeDiv(x, y);
    }

    @Snippet
    protected static long ldivSnippet(long x, long y, @ConstantParameter boolean needsZeroCheck, @ConstantParameter boolean needsBoundsCheck) {
        if (needsZeroCheck) {
            zeroCheck(y);
        }
        if (needsBoundsCheck && x == Long.MIN_VALUE && y == -1) {
            return Long.MIN_VALUE;
        }
        return safeDiv(x, y);
    }

    @Snippet
    protected static int iremSnippet(int x, int y, @ConstantParameter boolean needsZeroCheck, @ConstantParameter boolean needsBoundsCheck) {
        if (needsZeroCheck) {
            zeroCheck(y);
        }
        if (needsBoundsCheck && x == Integer.MIN_VALUE && y == -1) {
            return 0;
        }
        return safeRem(x, y);
    }

    @Snippet
    protected static long lremSnippet(long x, long y, @ConstantParameter boolean needsZeroCheck, @ConstantParameter boolean needsBoundsCheck) {
        if (needsZeroCheck) {
            zeroCheck(y);
        }
        if (needsBoundsCheck && x == Long.MIN_VALUE && y == -1) {
            return 0;
        }
        return safeRem(x, y);
    }

    @Snippet
    protected static int uidivSnippet(int x, int y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            zeroCheck(y);
        }
        return safeUDiv(x, y);
    }

    @Snippet
    protected static long uldivSnippet(long x, long y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            zeroCheck(y);
        }
        return safeUDiv(x, y);
    }

    @Snippet
    protected static int uiremSnippet(int x, int y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            zeroCheck(y);
        }
        return safeURem(x, y);
    }

    @Snippet
    protected static long ulremSnippet(long x, long y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            zeroCheck(y);
        }
        return safeURem(x, y);
    }

    private static void zeroCheck(int val) {
        if (val == 0) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.ArithmeticException);
            throw UnreachableNode.unreachable();
        }
    }

    private static void zeroCheck(long val) {
        if (val == 0) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.ArithmeticException);
            throw UnreachableNode.unreachable();
        }
    }

    @NodeIntrinsic(SafeSignedDivNode.class)
    private static native int safeDiv(int x, int y);

    @NodeIntrinsic(SafeSignedDivNode.class)
    private static native long safeDiv(long x, long y);

    @NodeIntrinsic(SafeSignedRemNode.class)
    private static native int safeRem(int x, int y);

    @NodeIntrinsic(SafeSignedRemNode.class)
    private static native long safeRem(long x, long y);

    @NodeIntrinsic(SafeUnsignedDivNode.class)
    private static native int safeUDiv(int x, int y);

    @NodeIntrinsic(SafeUnsignedDivNode.class)
    private static native long safeUDiv(long x, long y);

    @NodeIntrinsic(SafeUnsignedRemNode.class)
    private static native int safeURem(int x, int y);

    @NodeIntrinsic(SafeUnsignedRemNode.class)
    private static native long safeURem(long x, long y);

    private final ObjectLayout layout;

    private final SnippetInfo idiv;
    private final SnippetInfo ldiv;
    private final SnippetInfo irem;
    private final SnippetInfo lrem;
    private final SnippetInfo uidiv;
    private final SnippetInfo uldiv;
    private final SnippetInfo uirem;
    private final SnippetInfo ulrem;

    protected ArithmeticSnippets(OptionValues options, Providers providers,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean divRemNeedsSignedBoundsCheck) {
        super(options, providers);
        this.layout = ConfigurationValues.getObjectLayout();

        idiv = snippet(ArithmeticSnippets.class, "idivSnippet");
        ldiv = snippet(ArithmeticSnippets.class, "ldivSnippet");
        irem = snippet(ArithmeticSnippets.class, "iremSnippet");
        lrem = snippet(ArithmeticSnippets.class, "lremSnippet");
        uidiv = snippet(ArithmeticSnippets.class, "uidivSnippet");
        uldiv = snippet(ArithmeticSnippets.class, "uldivSnippet");
        uirem = snippet(ArithmeticSnippets.class, "uiremSnippet");
        ulrem = snippet(ArithmeticSnippets.class, "ulremSnippet");

        lowerings.put(SignedDivNode.class, new DivRemLowering(divRemNeedsSignedBoundsCheck));
        lowerings.put(SignedRemNode.class, new DivRemLowering(divRemNeedsSignedBoundsCheck));
        lowerings.put(UnsignedDivNode.class, new DivRemLowering(divRemNeedsSignedBoundsCheck));
        lowerings.put(UnsignedRemNode.class, new DivRemLowering(divRemNeedsSignedBoundsCheck));
        lowerings.put(SafeSignedDivNode.class, new IdentityLowering());
        lowerings.put(SafeSignedRemNode.class, new IdentityLowering());
        lowerings.put(SafeUnsignedDivNode.class, new IdentityLowering());
        lowerings.put(SafeUnsignedRemNode.class, new IdentityLowering());
    }

    protected class DivRemLowering implements NodeLoweringProvider<IntegerDivRemNode> {
        private final boolean needsSignedBoundsCheck;

        protected DivRemLowering(boolean needsSignedBoundsCheck) {
            this.needsSignedBoundsCheck = needsSignedBoundsCheck;
        }

        @Override
        public void lower(IntegerDivRemNode node, LoweringTool tool) {
            if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
                // wait for more precise stamp information
                return;
            }
            assert node.getStackKind() == JavaKind.Int || node.getStackKind() == JavaKind.Long;
            SnippetInfo snippet;
            if (node instanceof SignedDivNode) {
                snippet = node.getStackKind() == JavaKind.Int ? idiv : ldiv;
            } else if (node instanceof SignedRemNode) {
                snippet = node.getStackKind() == JavaKind.Int ? irem : lrem;
            } else if (node instanceof UnsignedDivNode) {
                snippet = node.getStackKind() == JavaKind.Int ? uidiv : uldiv;
            } else if (node instanceof UnsignedRemNode) {
                snippet = node.getStackKind() == JavaKind.Int ? uirem : ulrem;
            } else {
                throw shouldNotReachHere();
            }
            Arguments args = new Arguments(snippet, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("x", node.getX());
            args.add("y", node.getY());

            IntegerStamp yStamp = (IntegerStamp) node.getY().stamp(NodeView.DEFAULT);
            boolean needsZeroCheck = node.canDeoptimize() && (node.getZeroCheck() == null && yStamp.contains(0));
            args.addConst("needsZeroCheck", needsZeroCheck);
            if (node instanceof SignedDivNode || node instanceof SignedRemNode) {
                args.addConst("needsBoundsCheck", needsSignedBoundsCheck);
            }

            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    public static class IdentityLowering implements NodeLoweringProvider<Node> {
        @Override
        public void lower(Node node, LoweringTool tool) {
            // do nothing and leave node unchanged.
        }
    }
}

@NodeInfo
class SafeSignedDivNode extends SignedDivNode {
    public static final NodeClass<SafeSignedDivNode> TYPE = NodeClass.create(SafeSignedDivNode.class);

    protected SafeSignedDivNode(ValueNode x, ValueNode y) {
        /*
         * All "safe" division nodes use null as the zeroCheck guard. Since the safe nodes are still
         * fixed and no code in the backend depends on the guard information, so having an explicit
         * guard is not necessary. Passing in the guard via a snippet would be tricky (it is not a
         * GuardingNode but an arbitrary ValueNode until after snippet instantiation).
         */
        super(TYPE, x, y, null);
    }

    @Override
    protected SignedDivNode createWithInputs(ValueNode forX, ValueNode forY, GuardingNode forZeroCheck, FrameState forStateBefore) {
        assert forZeroCheck == null;
        // note that stateBefore is irrelevant, as this "safe" variant will not deoptimize
        return new SafeSignedDivNode(forX, forY);
    }

    @Override
    public boolean canDeoptimize() {
        /*
         * All checks have been done. Returning false is the indicator that no FrameState is
         * necessary anymore for the node.
         */
        return false;
    }
}

@NodeInfo
class SafeSignedRemNode extends SignedRemNode {
    public static final NodeClass<SafeSignedRemNode> TYPE = NodeClass.create(SafeSignedRemNode.class);

    protected SafeSignedRemNode(ValueNode x, ValueNode y) {
        super(TYPE, x, y, null);
    }

    @Override
    protected SignedRemNode createWithInputs(ValueNode forX, ValueNode forY, GuardingNode forZeroCheck) {
        assert forZeroCheck == null;
        return new SafeSignedRemNode(forX, forY);
    }

    @Override
    public boolean canDeoptimize() {
        /*
         * All checks have been done. Returning false is the indicator that no FrameState is
         * necessary anymore for the node.
         */
        return false;
    }
}

@NodeInfo
class SafeUnsignedDivNode extends UnsignedDivNode {
    public static final NodeClass<SafeUnsignedDivNode> TYPE = NodeClass.create(SafeUnsignedDivNode.class);

    protected SafeUnsignedDivNode(ValueNode x, ValueNode y) {
        super(TYPE, x, y, null);
    }

    @Override
    public boolean canDeoptimize() {
        /*
         * All checks have been done. Returning false is the indicator that no FrameState is
         * necessary anymore for the node.
         */
        return false;
    }
}

@NodeInfo
class SafeUnsignedRemNode extends UnsignedRemNode {
    public static final NodeClass<SafeUnsignedRemNode> TYPE = NodeClass.create(SafeUnsignedRemNode.class);

    protected SafeUnsignedRemNode(ValueNode x, ValueNode y) {
        super(TYPE, x, y, null);
    }

    @Override
    public boolean canDeoptimize() {
        /*
         * All checks have been done. Returning false is the indicator that no FrameState is
         * necessary anymore for the node.
         */
        return false;
    }
}
