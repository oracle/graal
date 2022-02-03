/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements;

import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import java.util.EnumMap;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.extended.AbstractBoxingNode;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.extended.UnboxNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetCounter.Group;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

public class BoxingSnippets implements Snippets {

    @Snippet
    public static Object booleanValueOf(boolean value, @ConstantParameter SnippetCounter valueOfCounter) {
        valueOfCounter.inc();
        return PiNode.piCastToSnippetReplaceeStamp(Boolean.valueOf(value));
    }

    @Snippet
    public static Object byteValueOf(byte value, @ConstantParameter SnippetCounter valueOfCounter) {
        valueOfCounter.inc();
        return PiNode.piCastToSnippetReplaceeStamp(Byte.valueOf(value));
    }

    @Snippet
    public static Object charValueOf(char value, @ConstantParameter SnippetCounter valueOfCounter) {
        valueOfCounter.inc();
        return PiNode.piCastToSnippetReplaceeStamp(Character.valueOf(value));
    }

    @Snippet
    public static Object doubleValueOf(double value, @ConstantParameter SnippetCounter valueOfCounter) {
        valueOfCounter.inc();
        return PiNode.piCastToSnippetReplaceeStamp(Double.valueOf(value));
    }

    @Snippet
    public static Object floatValueOf(float value, @ConstantParameter SnippetCounter valueOfCounter) {
        valueOfCounter.inc();
        return PiNode.piCastToSnippetReplaceeStamp(Float.valueOf(value));
    }

    @Snippet
    public static Object intValueOf(int value, @ConstantParameter SnippetCounter valueOfCounter) {
        valueOfCounter.inc();
        return PiNode.piCastToSnippetReplaceeStamp(Integer.valueOf(value));
    }

    @Snippet
    public static Object longValueOf(long value, @ConstantParameter SnippetCounter valueOfCounter) {
        valueOfCounter.inc();
        return PiNode.piCastToSnippetReplaceeStamp(Long.valueOf(value));
    }

    @Snippet
    public static Object shortValueOf(short value, @ConstantParameter SnippetCounter valueOfCounter) {
        valueOfCounter.inc();
        return PiNode.piCastToSnippetReplaceeStamp(Short.valueOf(value));
    }

    @Snippet
    public static boolean booleanValue(Boolean value, @ConstantParameter SnippetCounter valueCounter) {
        valueCounter.inc();
        return value.booleanValue();
    }

    @Snippet
    public static byte byteValue(Byte value, @ConstantParameter SnippetCounter valueCounter) {
        valueCounter.inc();
        return value.byteValue();
    }

    @Snippet
    public static char charValue(Character value, @ConstantParameter SnippetCounter valueCounter) {
        valueCounter.inc();
        return value.charValue();
    }

    @Snippet
    public static double doubleValue(Double value, @ConstantParameter SnippetCounter valueCounter) {
        valueCounter.inc();
        return value.doubleValue();
    }

    @Snippet
    public static float floatValue(Float value, @ConstantParameter SnippetCounter valueCounter) {
        valueCounter.inc();
        return value.floatValue();
    }

    @Snippet
    public static int intValue(Integer value, @ConstantParameter SnippetCounter valueCounter) {
        valueCounter.inc();
        return value.intValue();
    }

    @Snippet
    public static long longValue(Long value, @ConstantParameter SnippetCounter valueCounter) {
        valueCounter.inc();
        return value.longValue();
    }

    @Snippet
    public static short shortValue(Short value, @ConstantParameter SnippetCounter valueCounter) {
        valueCounter.inc();
        return value.shortValue();
    }

    public static class Templates extends AbstractTemplates {

        private final EnumMap<JavaKind, SnippetInfo> boxSnippets = new EnumMap<>(JavaKind.class);
        private final EnumMap<JavaKind, SnippetInfo> unboxSnippets = new EnumMap<>(JavaKind.class);

        private final SnippetCounter valueOfCounter;
        private final SnippetCounter valueCounter;

        @SuppressWarnings("hiding")
        public Templates(OptionValues options, Group.Factory factory, Providers providers) {
            super(options, providers);

            for (JavaKind kind : new JavaKind[]{JavaKind.Boolean, JavaKind.Byte, JavaKind.Char, JavaKind.Double, JavaKind.Float, JavaKind.Int, JavaKind.Long, JavaKind.Short}) {
                LocationIdentity accessedLocation = null;
                LocationIdentity cacheLocation = null;
                boolean mustHaveCacheField = false;
                switch (kind) {
                    case Byte:
                    case Short:
                    case Char:
                    case Int:
                    case Long:
                        accessedLocation = new FieldLocationIdentity(AbstractBoxingNode.getValueField(providers.getMetaAccess().lookupJavaType(kind.toBoxedJavaClass())));
                        cacheLocation = getCacheLocation(providers, kind);
                        mustHaveCacheField = true;
                        break;
                    case Boolean:
                    case Float:
                    case Double:
                        accessedLocation = new FieldLocationIdentity(AbstractBoxingNode.getValueField(providers.getMetaAccess().lookupJavaType(kind.toBoxedJavaClass())));
                        break;
                    default:
                        throw GraalError.unimplemented();
                }
                assert accessedLocation != null;
                // Boxing may write to cache or init location
                if (kind == JavaKind.Boolean) {
                    assert cacheLocation == null;
                    FieldLocationIdentity trueField = null;
                    FieldLocationIdentity falseField = null;
                    for (ResolvedJavaField field : providers.getMetaAccess().lookupJavaType(kind.toBoxedJavaClass()).getStaticFields()) {
                        if (field.getName().equals("TRUE")) {
                            trueField = new FieldLocationIdentity(field);
                        } else if (field.getName().equals("FALSE")) {
                            falseField = new FieldLocationIdentity(field);
                        }
                    }
                    // does no allocation
                    boxSnippets.put(kind, snippet(BoxingSnippets.class, kind.getJavaName() + "ValueOf", trueField, falseField));
                } else {
                    GraalError.guarantee(!mustHaveCacheField || cacheLocation != null, "Must have a cache location for kind %s", kind);
                    if (cacheLocation != null) {
                        boxSnippets.put(kind, snippet(BoxingSnippets.class, kind.getJavaName() + "ValueOf", LocationIdentity.INIT_LOCATION, accessedLocation, cacheLocation,
                                        NamedLocationIdentity.getArrayLocation(JavaKind.Object)));
                    } else {
                        boxSnippets.put(kind, snippet(BoxingSnippets.class, kind.getJavaName() + "ValueOf", LocationIdentity.INIT_LOCATION, accessedLocation,
                                        NamedLocationIdentity.getArrayLocation(JavaKind.Object)));
                    }
                }
                unboxSnippets.put(kind, snippet(BoxingSnippets.class, kind.getJavaName() + "Value", accessedLocation));
            }
            Group group = factory.createSnippetCounterGroup("Boxing");
            valueOfCounter = new SnippetCounter(group, "valueOf", "box intrinsification");
            valueCounter = new SnippetCounter(group, "<kind>Value", "unbox intrinsification");
        }

        private static LocationIdentity getCacheLocation(CoreProviders providers, JavaKind kind) {
            Class<?>[] innerClasses = null;
            try {
                innerClasses = kind.toBoxedJavaClass().getDeclaredClasses();
                if (innerClasses == null || innerClasses.length == 0) {
                    throw GraalError.shouldNotReachHere("Inner classes must exist");
                }
                return new FieldLocationIdentity(providers.getMetaAccess().lookupJavaField(innerClasses[0].getDeclaredField("cache")));
            } catch (Throwable e) {
                throw GraalError.shouldNotReachHere(e);
            }
        }

        public void lower(BoxNode box, LoweringTool tool) {
            Arguments args = null;
            args = new Arguments(boxSnippets.get(box.getBoxingKind()), box.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("value", box.getValue());
            args.addConst("valueOfCounter", valueOfCounter);
            SnippetTemplate template = template(box, args);
            box.getDebug().log("Lowering integerValueOf in %s: node=%s, template=%s, arguments=%s", box.graph(), box, template, args);
            template.instantiate(providers.getMetaAccess(), box, DEFAULT_REPLACER, args);
        }

        public void lower(UnboxNode unbox, LoweringTool tool) {
            Arguments args = new Arguments(unboxSnippets.get(unbox.getBoxingKind()), unbox.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("value", unbox.getValue());
            args.addConst("valueCounter", valueCounter);

            SnippetTemplate template = template(unbox, args);
            unbox.getDebug().log("Lowering integerValueOf in %s: node=%s, template=%s, arguments=%s", unbox.graph(), unbox, template, args);
            template.instantiate(providers.getMetaAccess(), unbox, DEFAULT_REPLACER, args);
        }
    }
}
