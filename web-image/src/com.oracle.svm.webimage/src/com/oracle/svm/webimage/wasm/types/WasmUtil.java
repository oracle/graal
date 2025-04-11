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

package com.oracle.svm.webimage.wasm.types;

import java.nio.ByteOrder;

import org.graalvm.word.WordBase;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.common.type.VoidStamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.graph.iterators.NodePredicates;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.VirtualState;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderTool;
import jdk.graal.compiler.nodes.graphbuilderconf.TypePlugin;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.CoreProvidersDelegate;
import jdk.graal.compiler.word.WordOperationPlugin;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Utility class to bridge the gap between the Graal IR and WASM, mainly relating to the two type
 * systems.
 * <p>
 * Encodes some decisions made when mapping from Graal to WASM (e.g. which nodes produce a value or
 * the size of a pointer).
 */
public abstract class WasmUtil {

    /**
     * Each WASM page is 64KiB.
     */
    public static final int PAGE_SIZE = 1 << 16;

    /**
     * WASM uses little endian byte ordering.
     */
    public static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    /**
     * The {@link WasmPrimitiveType} for {@link WordBase}.
     */
    public static final WasmPrimitiveType WORD_TYPE = WasmPrimitiveType.i64;

    /**
     * Extension types for loads of {@link WasmPackedType}s.
     */
    public enum Extension {
        None,
        Sign,
        Zero;

        /**
         * What kind of extension mode should be used when loading an element of the given kind.
         */
        public static Extension forKind(JavaKind kind) {
            return switch (kind) {
                // Unsigned -> Zero-extend
                case Boolean, Char -> Zero;
                // Signed -> Sign-extend
                case Byte, Short -> Sign;
                // No extension necessary, these already match the Wasm stack type
                case Int, Float, Long, Double, Object -> None;
                default -> throw VMError.shouldNotReachHere(kind.toString());
            };
        }
    }

    protected final CoreProviders providers;

    /**
     * Stub instance of {@link StubGraphBuilderTools} for
     * {@link #applyTypePlugins(ResolvedJavaType)}.
     */
    private final GraphBuilderTool stubGraphBuilderTools;

    /**
     * Graph builder plugins used for bytecode parsing, needed for
     * {@link #applyTypePlugins(ResolvedJavaType)}.
     */
    private final GraphBuilderConfiguration.Plugins graphBuilderPlugins;

    public WasmUtil(CoreProviders providers, GraphBuilderConfiguration.Plugins graphBuilderPlugins) {
        this.providers = providers;
        this.stubGraphBuilderTools = new StubGraphBuilderTools(providers);
        this.graphBuilderPlugins = graphBuilderPlugins;
    }

    /**
     * Maps {@link JavaKind} to the corresponding stack type in WASM.
     */
    public WasmValType mapType(JavaKind kind) {
        return storageTypeForKind(kind).toValType();
    }

    /**
     * Maps {@link JavaKind} to Wasm's {@link WasmStorageType} (i.e. how that kind would be
     * represented as struct field or array element).
     * <p>
     * Throws an exception if not called with a {@link JavaKind} that represents a value.
     *
     * @return A non-null {@link WasmStorageType}.
     */
    public WasmStorageType storageTypeForKind(JavaKind kind) {
        return switch (kind) {
            case Boolean, Byte -> WasmPackedType.i8;
            case Short, Char -> WasmPackedType.i16;
            case Int -> WasmPrimitiveType.i32;
            case Float -> WasmPrimitiveType.f32;
            case Long -> WasmPrimitiveType.i64;
            case Double -> WasmPrimitiveType.f64;
            case Object -> getJavaLangObjectType();
            default -> throw GraalError.shouldNotReachHere(kind.toString());
        };
    }

    /**
     * Returns the Wasm type that corresponds to the given Java class.
     */
    public WasmValType typeForJavaClass(Class<?> clazz) {
        return typeForJavaType(providers.getMetaAccess().lookupJavaType(clazz));
    }

    public WasmValType getJavaLangObjectType() {
        return typeForJavaClass(Object.class);
    }

    public WasmValType getHubObjectType() {
        return typeForJavaClass(DynamicHub.class);
    }

    public WasmValType getThrowableType() {
        return typeForJavaClass(Throwable.class);
    }

    /**
     * Returns the canonical Java type for the given type.
     * <p>
     * The canonical Java type is the type that most closely matches the corresponding
     * {@link WasmStorageType} in the subset of the Wasm type system used by the backend. The
     * canonicalization is an intermediate step when mapping from arbitrary Java types to the
     * corresponding Wasm types. This makes it easier to work with Java types that are ultimately
     * mapped to Wasm.
     * <p>
     * The exact mapping depends on the backend and is described in the subclasses. But in any case,
     * {@link #applyTypePlugins} is applied and the primitive numeric types use the same mapping as
     * {@link #mapPrimitiveType}.
     * <p>
     * Additionally, primitive types are not remapped, types smaller than ints can later be
     * represented using {@link WasmPackedType}.
     */
    public ResolvedJavaType canonicalizeJavaType(ResolvedJavaType type) {
        assert type != null;

        return applyTypePlugins(type);
    }

    /**
     * Calls into graph builder plugins to get the replaced stamp for a type to mimic what the
     * bytecode parser does.
     * <p>
     * Due to the use of {@link TypePlugin}s during parsing, types found/expected in the IR may not
     * match types declared elsewhere.
     * <p>
     * The prime example here is {@link WordOperationPlugin}. In the IR, any {@link WordBase} type
     * is treated as an integer. However, outside the IR (method signature, field types, etc.), the
     * original {@link WordBase} type is used.
     * <p>
     * This is still a bit hacky, but at least it will not get out of sync with SVM.
     */
    private ResolvedJavaType applyTypePlugins(ResolvedJavaType type) {
        assert type != null;
        StampPair stampPair = graphBuilderPlugins.getOverridingStamp(stubGraphBuilderTools, type, false);

        if (stampPair == null) {
            return type;
        }

        return stampPair.getTrustedStamp().javaType(providers.getMetaAccess());
    }

    /**
     * Maps a primitive {@link JavaKind} to the corresponding stack type in WASM.
     */
    public static WasmPrimitiveType mapPrimitiveType(JavaKind kind) {
        assert kind.isPrimitive() : kind;
        return switch (kind) {
            case Boolean, Byte, Short, Char, Int -> WasmPrimitiveType.i32;
            case Float -> WasmPrimitiveType.f32;
            case Long -> WasmPrimitiveType.i64;
            case Double -> WasmPrimitiveType.f64;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.toString());
        };
    }

    /**
     * Like {@link #storageTypeForJavaType(JavaType)} but how the type is represented on the operand
     * stack.
     */
    public final WasmValType typeForJavaType(JavaType type) {
        return storageTypeForJavaType(type).toValType();
    }

    /**
     * Converts the given {@link JavaType} to its representation in the Wasm type system.
     *
     * @return A non-null {@link WasmStorageType}. How the given type would be represented as a
     *         struct field or array element in Wasm.
     */
    public abstract WasmStorageType storageTypeForJavaType(JavaType type);

    /**
     * Determines the {@link WasmValType} produced by the given node.
     */
    public abstract WasmValType typeForNode(ValueNode n);

    /**
     * Like {@link #storageTypeForStamp(Stamp)} but how the stamp is represented on the operand
     * stack.
     */
    public final WasmValType typeForStamp(Stamp stamp) {
        WasmStorageType type = storageTypeForStamp(stamp);
        return type == null ? null : type.toValType();
    }

    /**
     * @return null for void stamps. Otherwise, the appropriate {@link WasmStorageType} for the
     *         given stamp.
     */
    public WasmStorageType storageTypeForStamp(Stamp stamp) {
        return switch (stamp) {
            case AbstractObjectStamp objectStamp -> storageTypeForObjectStamp(objectStamp);
            case PrimitiveStamp primitiveStamp -> storageTypeForPrimitiveStamp(primitiveStamp);
            case VoidStamp ignored -> null;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(stamp.getClass() + " " + stamp);
        };
    }

    protected WasmStorageType storageTypeForObjectStamp(AbstractObjectStamp stamp) {
        return typeForJavaType(stamp.javaType(providers.getMetaAccess()));
    }

    protected WasmStorageType storageTypeForPrimitiveStamp(PrimitiveStamp stamp) {
        return storageTypeForKind(stamp.javaType(providers.getMetaAccess()).getJavaKind());
    }

    /**
     * Maps the given stamp to the appropriate {@link JavaKind}, which ultimately should map to the
     * appropriate {@link WasmValType} using {@link #mapType(JavaKind)}.
     */
    protected JavaKind kindForStamp(Stamp stamp) {
        JavaKind kind = stamp.getStackKind();
        return switch (kind) {
            case Boolean, Byte, Short, Char, Int -> JavaKind.Int;
            case Float, Long, Double, Object, Void, Illegal -> kind;
        };
    }

    /**
     * Determines the {@link JavaKind} produced by the given node.
     * <p>
     * This does not always equal {@link ValueNode#getStackKind()} since {@link LogicNode}s are
     * {@link JavaKind#Void}, but in WASM they require a concrete value type ({@code i32} in this
     * case).
     * <p>
     * Used when no concrete {@link WasmValType} for a node is needed, or to check for {@code void}
     * "values".
     */
    public JavaKind kindForNode(ValueNode n) {
        if (n instanceof LogicNode) {
            // LogicNodes have a void stamp, but actually produce a boolean value
            return JavaKind.Boolean;
        }

        return kindForStamp(n.stamp(NodeView.DEFAULT));
    }

    public boolean hasValue(Node n) {
        if (n instanceof ValueNode valueNode) {
            JavaKind wasmKind = kindForNode(valueNode);

            return wasmKind != JavaKind.Illegal && wasmKind != JavaKind.Void;
        }

        return false;
    }

    /**
     * What {@link JavaKind} represents the in-memory value when reading or writing a value with the
     * given access stamp from or to memory.
     * <p>
     * Used to determine which Wasm memory operations should be used for a read or write.
     * <p>
     * Wasm memory operations require both the size of the stack value is read or written from or to
     * memory and the size of that value in memory, since they may be different (e.g. a 16-bit
     * memory value can only be represented as 32-bit on the stack).
     * <p>
     * Implemented for primitive stamps. It is up to subclasses to define how other stamps are
     * represented (e.g. objects).
     */
    public JavaKind memoryKind(Stamp accessStamp) {
        if (accessStamp instanceof IntegerStamp integerStamp) {
            int bits = integerStamp.getBits();

            if (bits <= 8) {
                return JavaKind.Byte;
            } else if (bits == 16) {
                return JavaKind.Short;
            } else if (bits == 32) {
                return JavaKind.Int;
            } else if (bits == 64) {
                return JavaKind.Long;
            } else {
                throw GraalError.shouldNotReachHere(accessStamp.toString());
            }

        } else if (accessStamp instanceof FloatStamp floatStamp) {
            return switch (floatStamp.getBits()) {
                case 32 -> JavaKind.Float;
                case 64 -> JavaKind.Double;
                default -> throw GraalError.shouldNotReachHere(accessStamp.toString());
            };
        } else {
            throw GraalError.shouldNotReachHere(accessStamp.toString());
        }
    }

    public JavaKind memoryKind(JavaKind kind) {
        return switch (kind) {
            case Boolean -> JavaKind.Byte;
            case Char -> JavaKind.Short;
            case Void, Illegal -> throw GraalError.shouldNotReachHere(kind.toString());
            default -> kind;
        };
    }

    /**
     * Finds relevant usages of a node.
     * <p>
     * For the purposes of codegen, not all uses of a node are relevant. For example
     * {@link VirtualState} using a node as input should not count when determining whether a node
     * is used.
     */
    public NodeIterable<Node> actualUsages(ValueNode node) {
        return node.usages().filter(NodePredicates.isNotA(VirtualState.class));
    }

    /**
     * Stub implementation of {@link GraphBuilderTool} that is used by
     * {@link #applyTypePlugins(ResolvedJavaType)}.
     * <p>
     * Only needs to provide as much functionality to apply all type plugins.
     */
    private static class StubGraphBuilderTools extends CoreProvidersDelegate implements GraphBuilderTool {

        protected StubGraphBuilderTools(CoreProviders providers) {
            super(providers);
        }

        @Override
        public <T extends Node> T append(T value) {
            throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
        }

        @Override
        public StructuredGraph getGraph() {
            return null;
        }

        @Override
        public boolean parsingIntrinsic() {
            return false;
        }
    }
}
