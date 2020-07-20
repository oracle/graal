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
package com.oracle.svm.core.graal.snippets.aarch64;

import java.util.Map;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.RemNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.Platform.AARCH64;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.ArithmeticSnippets;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;

import jdk.vm.ci.meta.JavaKind;

/**
 * AArch64 does not have a remainder operation. We lower it to a stub call.
 */
final class AArch64ArithmeticSnippets extends ArithmeticSnippets {
    private static final SubstrateForeignCallDescriptor FMOD = SnippetRuntime.findForeignCall(AArch64ArithmeticSnippets.class, "fmod", true, new LocationIdentity[]{});
    private static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{FMOD};

    public static void registerForeignCalls(Providers providers, SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(providers, FOREIGN_CALLS);
    }

    private static final double ONE = 1.0;
    private static final double[] ZERO = new double[]{0.0, -0.0};

    private static int highWord(double d) {
        return (int) (Double.doubleToRawLongBits(d) >> 32);
    }

    private static int lowWord(double d) {
        return (int) (Double.doubleToRawLongBits(d) & 0xffffffff);
    }

    private static double doubleFromHighLowWords(int high, int low) {
        long h = high;
        long l = low & 0xffffffffL; // convert without sign extension
        return Double.longBitsToDouble(h << 32 | l);
    }

    /**
     * Computes the floating point remainder of its arguments. Ported from HotSpot's e_fmod.c,
     * preserving original comments. Called via the foreign call {@link #FMOD}.
     *
     * <pre>
     * __ieee754_fmod(x,y)
     * Return x mod y in exact arithmetic
     * Method: shift and subtract
     * </pre>
     */
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static double fmod(double xx, double yy) {
        double x = xx;
        double y = yy;
        int n;
        int hx;
        int hy;
        int hz;
        int ix;
        int iy;
        int sx;
        int i;
        /*
         * The following three variables lx, ly, lz are unsigned in the original C code. Be careful
         * when using them in shifts and comparisons.
         */
        /* unsigned */ int lx;
        /* unsigned */ int ly;
        /* unsigned */ int lz;

        hx = highWord(x); /* high word of x */
        lx = lowWord(x); /* low word of x */
        hy = highWord(y); /* high word of y */
        ly = lowWord(y); /* low word of y */
        sx = hx & 0x80000000; /* sign of x */
        hx ^= sx; /* |x| */
        hy &= 0x7fffffff; /* |y| */

        /* purge off exception values */
        if ((hy | ly) == 0 || (hx >= 0x7ff00000) || /* y=0,or x not finite */
                        ((hy | ((ly | -ly) >> 31)) > 0x7ff00000)) /* or y is NaN */ {
            return (x * y) / (x * y);
        }
        if (hx <= hy) {
            if ((hx < hy) || (Integer.compareUnsigned(lx, ly) < 0)) {
                return x; /* |x|<|y| return x */
            }
            if (lx == ly) {
                return ZERO[sx >>> 31]; /* |x|=|y| return x*0 */
            }
        }

        /* determine ix = ilogb(x) */
        if (hx < 0x00100000) { /* subnormal x */
            if (hx == 0) {
                for (ix = -1043, i = lx; i > 0; i <<= 1) {
                    ix -= 1;
                }
            } else {
                for (ix = -1022, i = (hx << 11); i > 0; i <<= 1) {
                    ix -= 1;
                }
            }
        } else {
            ix = (hx >> 20) - 1023;
        }

        /* determine iy = ilogb(y) */
        if (hy < 0x00100000) { /* subnormal y */
            if (hy == 0) {
                for (iy = -1043, i = ly; i > 0; i <<= 1) {
                    iy -= 1;
                }
            } else {
                for (iy = -1022, i = (hy << 11); i > 0; i <<= 1) {
                    iy -= 1;
                }
            }
        } else {
            iy = (hy >> 20) - 1023;
        }

        /* set up {hx,lx}, {hy,ly} and align y to x */
        if (ix >= -1022) {
            hx = 0x00100000 | (0x000fffff & hx);
        } else { /* subnormal x, shift x to normal */
            n = -1022 - ix;
            if (n <= 31) {
                hx = (hx << n) | (lx >>> (32 - n));
                lx <<= n;
            } else {
                hx = lx << (n - 32);
                lx = 0;
            }
        }
        if (iy >= -1022) {
            hy = 0x00100000 | (0x000fffff & hy);
        } else { /* subnormal y, shift y to normal */
            n = -1022 - iy;
            if (n <= 31) {
                hy = (hy << n) | (ly >>> (32 - n));
                ly <<= n;
            } else {
                hy = ly << (n - 32);
                ly = 0;
            }
        }

        /* fix point fmod */
        n = ix - iy;
        while (n-- != 0) {
            hz = hx - hy;
            lz = lx - ly;
            if (Integer.compareUnsigned(lx, ly) < 0) {
                hz -= 1;
            }
            if (hz < 0) {
                hx = hx + hx + (lx >>> 31);
                lx = lx + lx;
            } else {
                if ((hz | lz) == 0) /* return sign(x)*0 */ {
                    return ZERO[sx >>> 31];
                }
                hx = hz + hz + (lz >>> 31);
                lx = lz + lz;
            }
        }
        hz = hx - hy;
        lz = lx - ly;
        if (Integer.compareUnsigned(lx, ly) < 0) {
            hz -= 1;
        }
        if (hz >= 0) {
            hx = hz;
            lx = lz;
        }

        /* convert back to floating value and restore the sign */
        if ((hx | lx) == 0) /* return sign(x)*0 */ {
            return ZERO[sx >>> 31];
        }
        while (hx < 0x00100000) { /* normalize x */
            hx = hx + hx + (lx >>> 31);
            lx = lx + lx;
            iy -= 1;
        }
        if (iy >= -1022) { /* normalize output */
            hx = ((hx - 0x00100000) | ((iy + 1023) << 20));
            x = doubleFromHighLowWords(hx | sx, lx);
        } else { /* subnormal output */
            n = -1022 - iy;
            if (n <= 20) {
                lx = (lx >>> n) | (hx << (32 - n));
                hx >>= n;
            } else if (n <= 31) {
                lx = (hx << (32 - n)) | (lx >>> n);
                hx = sx;
            } else {
                lx = hx >> (n - 32);
                hx = sx;
            }
            x = doubleFromHighLowWords(hx | sx, lx);
            x *= ONE; /* create necessary signal */
        }
        return x; /* exact output */
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native double callFmod(@ConstantNodeParameter ForeignCallDescriptor descriptor, double x, double y);

    @Snippet
    protected static float fremSnippet(float x, float y) {
        return (float) callFmod(FMOD, x, y);
    }

    @Snippet
    protected static double dremSnippet(double x, double y) {
        return callFmod(FMOD, x, y);
    }

    private final SnippetInfo drem;
    private final SnippetInfo frem;

    @SuppressWarnings("unused")
    public static void registerLowerings(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers,
                    SnippetReflectionProvider snippetReflection, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {

        new AArch64ArithmeticSnippets(options, factories, providers, snippetReflection, lowerings);
    }

    private AArch64ArithmeticSnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers,
                    SnippetReflectionProvider snippetReflection, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {

        super(options, factories, providers, snippetReflection, lowerings);
        frem = snippet(AArch64ArithmeticSnippets.class, "fremSnippet");
        drem = snippet(AArch64ArithmeticSnippets.class, "dremSnippet");

        lowerings.put(RemNode.class, new AArch64RemLowering());
    }

    protected class AArch64RemLowering implements NodeLoweringProvider<RemNode> {
        @Override
        public void lower(RemNode node, LoweringTool tool) {
            JavaKind kind = node.stamp(NodeView.DEFAULT).getStackKind();
            assert kind == JavaKind.Float || kind == JavaKind.Double;
            SnippetTemplate.SnippetInfo snippet = kind == JavaKind.Float ? frem : drem;
            StructuredGraph graph = node.graph();
            Arguments args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("x", node.getX());
            args.add("y", node.getY());
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, tool, args);
        }
    }
}

@AutomaticFeature
@Platforms(AARCH64.class)
final class AArch64ArithmeticForeignCallsFeature implements GraalFeature {
    @Override
    public void registerForeignCalls(RuntimeConfiguration runtimeConfig, Providers providers, SnippetReflectionProvider snippetReflection,
                    SubstrateForeignCallsProvider foreignCalls, boolean hosted) {
        AArch64ArithmeticSnippets.registerForeignCalls(providers, foreignCalls);
    }
}
