/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.nodes;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;
import static jdk.graal.compiler.nodeinfo.InputType.Memory;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_128;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_256;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_64;

import java.util.EnumSet;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(allowedUsageTypes = Memory)
public abstract class MessageDigestNode extends MemoryKillStubIntrinsicNode {

    public static final NodeClass<MessageDigestNode> TYPE = NodeClass.create(MessageDigestNode.class);

    private static final LocationIdentity[] KILLED_LOCATIONS = {NamedLocationIdentity.getArrayLocation(JavaKind.Byte), NamedLocationIdentity.getArrayLocation(JavaKind.Int),
                    NamedLocationIdentity.getArrayLocation(JavaKind.Long)};

    private static ForeignCallDescriptor foreignCallDescriptor(String name) {
        return new ForeignCallDescriptor(name, void.class, new Class<?>[]{Pointer.class, Pointer.class}, HAS_SIDE_EFFECT, KILLED_LOCATIONS, false, false);
    }

    private static ForeignCallDescriptor multiBlockForeignCallDescriptor(String name) {
        return new ForeignCallDescriptor(name, int.class, new Class<?>[]{Pointer.class, Pointer.class, int.class, int.class}, HAS_SIDE_EFFECT, KILLED_LOCATIONS, false, false);
    }

    @Input protected ValueNode buf;
    @Input protected ValueNode state;

    public MessageDigestNode(NodeClass<? extends MessageDigestNode> c, ValueNode buf, ValueNode state, EnumSet<?> runtimeCheckedCPUFeatures) {
        this(c, StampFactory.forVoid(), buf, state, runtimeCheckedCPUFeatures);
    }

    protected MessageDigestNode(NodeClass<? extends MessageDigestNode> c, Stamp stamp, ValueNode buf, ValueNode state, EnumSet<?> runtimeCheckedCPUFeatures) {
        super(c, stamp, runtimeCheckedCPUFeatures, LocationIdentity.any());
        this.buf = buf;
        this.state = state;
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return KILLED_LOCATIONS;
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{buf, state};
    }

    @NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_UNKNOWN, cyclesRationale = "Cannot estimate the time of a loop", size = SIZE_64)
    public abstract static class MessageDigestMultiBlockNode extends MessageDigestNode {

        public static final NodeClass<MessageDigestMultiBlockNode> TYPE = NodeClass.create(MessageDigestMultiBlockNode.class);

        @Input protected ValueNode ofs;
        @Input protected ValueNode limit;

        protected MessageDigestMultiBlockNode(NodeClass<? extends MessageDigestMultiBlockNode> c, ValueNode buf, ValueNode state, ValueNode ofs, ValueNode limit,
                        EnumSet<?> runtimeCheckedCPUFeatures) {
            super(c, StampFactory.forKind(JavaKind.Int), buf, state, runtimeCheckedCPUFeatures);
            this.ofs = ofs;
            this.limit = limit;
        }

        @Override
        public ValueNode[] getForeignCallArguments() {
            return new ValueNode[]{buf, state, ofs, limit};
        }
    }

    /**
     * Intrinsification for {@code sun.security.provider.SHA.implCompress0}.
     */
    @NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_UNKNOWN, cyclesRationale = "Cannot estimate the time of a loop", size = SIZE_64)
    public static final class SHA1Node extends MessageDigestNode {

        public static final NodeClass<SHA1Node> TYPE = NodeClass.create(SHA1Node.class);
        public static final ForeignCallDescriptor STUB = foreignCallDescriptor("sha1ImplCompress");

        public SHA1Node(ValueNode buf, ValueNode state) {
            super(TYPE, buf, state, null);
        }

        public SHA1Node(ValueNode buf, ValueNode state, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, buf, state, runtimeCheckedCPUFeatures);
        }

        public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
            return EnumSet.of(AMD64.CPUFeature.SSSE3, AMD64.CPUFeature.SSE4_1, AMD64.CPUFeature.SHA);
        }

        public static EnumSet<AArch64.CPUFeature> minFeaturesAARCH64() {
            return EnumSet.of(AArch64.CPUFeature.SHA1);
        }

        @SuppressWarnings("unlikely-arg-type")
        public static boolean isSupported(Architecture arch) {
            return switch (arch) {
                case AMD64 amd64 -> amd64.getFeatures().containsAll(minFeaturesAMD64());
                case AArch64 aarch64 -> aarch64.getFeatures().containsAll(minFeaturesAARCH64());
                default -> false;
            };
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.getLIRGeneratorTool().emitSha1ImplCompress(runtimeCheckedCPUFeatures, gen.operand(buf), gen.operand(state));
        }

        @NodeIntrinsic
        @GenerateStub(name = "sha1ImplCompress", minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
        public static native void sha1ImplCompress(Pointer buf, Pointer state);

        @NodeIntrinsic
        public static native void sha1ImplCompress(Pointer buf, Pointer state, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }

    /**
     * Intrinsification for {@code sun.security.provider.DigestBase.implCompressMultiBlock0}
     * with a {@code sun.security.provider.SHA} receiver.
     */
    @NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_UNKNOWN, cyclesRationale = "Cannot estimate the time of a loop", size = SIZE_64)
    public static final class SHA1MultiBlockNode extends MessageDigestMultiBlockNode {

        public static final NodeClass<SHA1MultiBlockNode> TYPE = NodeClass.create(SHA1MultiBlockNode.class);
        public static final ForeignCallDescriptor STUB = multiBlockForeignCallDescriptor("sha1ImplCompressMB");

        public SHA1MultiBlockNode(ValueNode buf, ValueNode state, ValueNode ofs, ValueNode limit) {
            super(TYPE, buf, state, ofs, limit, null);
        }

        public SHA1MultiBlockNode(ValueNode buf, ValueNode state, ValueNode ofs, ValueNode limit, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, buf, state, ofs, limit, runtimeCheckedCPUFeatures);
        }

        public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
            return SHA1Node.minFeaturesAMD64();
        }

        public static EnumSet<AArch64.CPUFeature> minFeaturesAARCH64() {
            return SHA1Node.minFeaturesAARCH64();
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.setResult(this, gen.getLIRGeneratorTool().emitSha1ImplCompressMB(runtimeCheckedCPUFeatures, gen.operand(buf), gen.operand(state), gen.operand(ofs), gen.operand(limit)));
        }

        @NodeIntrinsic
        @GenerateStub(name = "sha1ImplCompressMB", minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
        public static native int sha1ImplCompressMB(Pointer buf, Pointer state, int ofs, int limit);

        @NodeIntrinsic
        public static native int sha1ImplCompressMB(Pointer buf, Pointer state, int ofs, int limit, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }

    /**
     * Intrinsification for {@code sun.security.provider.SHA2.implCompress0}.
     */
    @NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_UNKNOWN, cyclesRationale = "Cannot estimate the time of a loop", size = SIZE_64)
    public static final class SHA256Node extends MessageDigestNode {

        public static final NodeClass<SHA256Node> TYPE = NodeClass.create(SHA256Node.class);
        public static final ForeignCallDescriptor STUB = foreignCallDescriptor("sha256ImplCompress");

        public SHA256Node(ValueNode buf, ValueNode state) {
            super(TYPE, buf, state, null);
        }

        public SHA256Node(ValueNode buf, ValueNode state, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, buf, state, runtimeCheckedCPUFeatures);
        }

        public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
            return EnumSet.of(AMD64.CPUFeature.SSSE3, AMD64.CPUFeature.SSE4_1, AMD64.CPUFeature.AVX, AMD64.CPUFeature.AVX2, AMD64.CPUFeature.BMI2);
        }

        public static EnumSet<AArch64.CPUFeature> minFeaturesAARCH64() {
            return EnumSet.of(AArch64.CPUFeature.SHA2);
        }

        @SuppressWarnings("unlikely-arg-type")
        public static boolean isSupported(Architecture arch) {
            return switch (arch) {
                case AMD64 amd64 -> amd64.getFeatures().containsAll(minFeaturesAMD64());
                case AArch64 aarch64 -> aarch64.getFeatures().containsAll(minFeaturesAARCH64());
                default -> false;
            };
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.getLIRGeneratorTool().emitSha256ImplCompress(runtimeCheckedCPUFeatures, gen.operand(buf), gen.operand(state));
        }

        @NodeIntrinsic
        @GenerateStub(name = "sha256ImplCompress", minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
        public static native void sha256ImplCompress(Pointer buf, Pointer state);

        @NodeIntrinsic
        public static native void sha256ImplCompress(Pointer buf, Pointer state, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }

    /**
     * Intrinsification for {@code sun.security.provider.DigestBase.implCompressMultiBlock0}
     * with a {@code sun.security.provider.SHA2} receiver.
     */
    @NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_UNKNOWN, cyclesRationale = "Cannot estimate the time of a loop", size = SIZE_64)
    public static final class SHA256MultiBlockNode extends MessageDigestMultiBlockNode {

        public static final NodeClass<SHA256MultiBlockNode> TYPE = NodeClass.create(SHA256MultiBlockNode.class);
        public static final ForeignCallDescriptor STUB = multiBlockForeignCallDescriptor("sha256ImplCompressMB");

        public SHA256MultiBlockNode(ValueNode buf, ValueNode state, ValueNode ofs, ValueNode limit) {
            super(TYPE, buf, state, ofs, limit, null);
        }

        public SHA256MultiBlockNode(ValueNode buf, ValueNode state, ValueNode ofs, ValueNode limit, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, buf, state, ofs, limit, runtimeCheckedCPUFeatures);
        }

        public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
            return SHA256Node.minFeaturesAMD64();
        }

        public static EnumSet<AArch64.CPUFeature> minFeaturesAARCH64() {
            return SHA256Node.minFeaturesAARCH64();
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.setResult(this, gen.getLIRGeneratorTool().emitSha256ImplCompressMB(runtimeCheckedCPUFeatures, gen.operand(buf), gen.operand(state), gen.operand(ofs), gen.operand(limit)));
        }

        @NodeIntrinsic
        @GenerateStub(name = "sha256ImplCompressMB", minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
        public static native int sha256ImplCompressMB(Pointer buf, Pointer state, int ofs, int limit);

        @NodeIntrinsic
        public static native int sha256ImplCompressMB(Pointer buf, Pointer state, int ofs, int limit, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }

    /**
     * Intrinsification for {@code sun.security.provider.SHA3.implCompress0}.
     */
    @NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_UNKNOWN, cyclesRationale = "Cannot estimate the time of a loop", size = SIZE_128)
    public static final class SHA3Node extends MessageDigestNode {

        public static final NodeClass<SHA3Node> TYPE = NodeClass.create(SHA3Node.class);
        public static final ForeignCallDescriptor STUB = new ForeignCallDescriptor("sha3ImplCompress", void.class, new Class<?>[]{Pointer.class, Pointer.class, int.class},
                        HAS_SIDE_EFFECT, KILLED_LOCATIONS, false, false);

        @Input protected ValueNode blockSize;

        public SHA3Node(ValueNode buf, ValueNode state, ValueNode blockSize) {
            super(TYPE, buf, state, null);

            this.blockSize = blockSize;
        }

        public SHA3Node(ValueNode buf, ValueNode state, ValueNode blockSize, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, buf, state, runtimeCheckedCPUFeatures);

            this.blockSize = blockSize;
        }

        public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
            return EnumSet.of(AMD64.CPUFeature.AVX, AMD64.CPUFeature.AVX2, AMD64.CPUFeature.AVX512F, AMD64.CPUFeature.AVX512BW);
        }

        public static EnumSet<AArch64.CPUFeature> minFeaturesAARCH64() {
            return EnumSet.of(AArch64.CPUFeature.SHA3);
        }

        @SuppressWarnings("unlikely-arg-type")
        public static boolean isSupported(Architecture arch) {
            return switch (arch) {
                case AMD64 amd64 -> amd64.getFeatures().containsAll(minFeaturesAMD64());
                case AArch64 aarch64 -> aarch64.getFeatures().containsAll(minFeaturesAARCH64());
                default -> false;
            };
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public ValueNode[] getForeignCallArguments() {
            return new ValueNode[]{buf, state, blockSize};
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.getLIRGeneratorTool().emitSha3ImplCompress(gen.operand(buf), gen.operand(state), gen.operand(blockSize));
        }

        @NodeIntrinsic
        @GenerateStub(name = "sha3ImplCompress", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
        public static native void sha3ImplCompress(Pointer buf, Pointer state, int blockSize);

        @NodeIntrinsic
        public static native void sha3ImplCompress(Pointer buf, Pointer state, int blockSize, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }

    /**
     * Intrinsification for {@code sun.security.provider.DigestBase.implCompressMultiBlock0}
     * with a {@code sun.security.provider.SHA3} receiver.
     */
    @NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_UNKNOWN, cyclesRationale = "Cannot estimate the time of a loop", size = SIZE_128)
    public static final class SHA3MultiBlockNode extends MessageDigestMultiBlockNode {

        public static final NodeClass<SHA3MultiBlockNode> TYPE = NodeClass.create(SHA3MultiBlockNode.class);
        public static final ForeignCallDescriptor STUB = new ForeignCallDescriptor("sha3ImplCompressMB", int.class,
                        new Class<?>[]{Pointer.class, Pointer.class, int.class, int.class, int.class}, HAS_SIDE_EFFECT, KILLED_LOCATIONS, false, false);

        @Input protected ValueNode blockSize;

        public SHA3MultiBlockNode(ValueNode buf, ValueNode state, ValueNode blockSize, ValueNode ofs, ValueNode limit) {
            super(TYPE, buf, state, ofs, limit, null);
            this.blockSize = blockSize;
        }

        public SHA3MultiBlockNode(ValueNode buf, ValueNode state, ValueNode blockSize, ValueNode ofs, ValueNode limit, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, buf, state, ofs, limit, runtimeCheckedCPUFeatures);
            this.blockSize = blockSize;
        }

        public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
            return SHA3Node.minFeaturesAMD64();
        }

        public static EnumSet<AArch64.CPUFeature> minFeaturesAARCH64() {
            return SHA3Node.minFeaturesAARCH64();
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public ValueNode[] getForeignCallArguments() {
            return new ValueNode[]{buf, state, blockSize, ofs, limit};
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.setResult(this, gen.getLIRGeneratorTool().emitSha3ImplCompressMB(gen.operand(buf), gen.operand(state), gen.operand(blockSize), gen.operand(ofs), gen.operand(limit)));
        }

        @NodeIntrinsic
        @GenerateStub(name = "sha3ImplCompressMB", minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
        public static native int sha3ImplCompressMB(Pointer buf, Pointer state, int blockSize, int ofs, int limit);

        @NodeIntrinsic
        public static native int sha3ImplCompressMB(Pointer buf, Pointer state, int blockSize, int ofs, int limit, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }

    /**
     * Intrinsification for {@code sun.security.provider.SHA5.implCompress0}.
     */
    @NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_UNKNOWN, cyclesRationale = "Cannot estimate the time of a loop", size = SIZE_256)
    public static final class SHA512Node extends MessageDigestNode {

        public static final NodeClass<SHA512Node> TYPE = NodeClass.create(SHA512Node.class);
        public static final ForeignCallDescriptor STUB = foreignCallDescriptor("sha512ImplCompress");

        public SHA512Node(ValueNode buf, ValueNode state) {
            super(TYPE, buf, state, null);
        }

        public SHA512Node(ValueNode buf, ValueNode state, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, buf, state, runtimeCheckedCPUFeatures);
        }

        public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
            return EnumSet.of(AMD64.CPUFeature.AVX, AMD64.CPUFeature.AVX2, AMD64.CPUFeature.BMI2);
        }

        /*
         * Substrate VM currently chooses minFeaturesAMD64() as the runtime-compilation variant
         * because SHA512 is not widely adopted yet. Native targets with BMI2 and SHA512 can still
         * use this feature set.
         */
        public static EnumSet<AMD64.CPUFeature> maxFeaturesAMD64() {
            return EnumSet.of(AMD64.CPUFeature.AVX, AMD64.CPUFeature.AVX2, AMD64.CPUFeature.BMI2, AMD64.CPUFeature.SHA512);
        }

        public static EnumSet<AArch64.CPUFeature> minFeaturesAARCH64() {
            return EnumSet.of(AArch64.CPUFeature.SHA512);
        }

        @SuppressWarnings("unlikely-arg-type")
        public static boolean isSupported(Architecture arch) {
            return switch (arch) {
                /*
                 * HotSpot supports AVX2 with either BMI2 or SHA512, but SVM currently models the
                 * runtime-compilation variant with minFeaturesAMD64(). Requiring BMI2 here keeps
                 * the public support predicate aligned with that generated-stub feature model.
                 */
                case AMD64 amd64 -> amd64.getFeatures().containsAll(minFeaturesAMD64());
                case AArch64 aarch64 -> aarch64.getFeatures().containsAll(minFeaturesAARCH64());
                default -> false;
            };
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.getLIRGeneratorTool().emitSha512ImplCompress(gen.operand(buf), gen.operand(state));
        }

        @NodeIntrinsic
        @GenerateStub(name = "sha512ImplCompress", minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
        public static native void sha512ImplCompress(Pointer buf, Pointer state);

        @NodeIntrinsic
        public static native void sha512ImplCompress(Pointer buf, Pointer state, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }

    /**
     * Intrinsification for {@code sun.security.provider.DigestBase.implCompressMultiBlock0}
     * with a {@code sun.security.provider.SHA5} receiver.
     */
    @NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_UNKNOWN, cyclesRationale = "Cannot estimate the time of a loop", size = SIZE_256)
    public static final class SHA512MultiBlockNode extends MessageDigestMultiBlockNode {

        public static final NodeClass<SHA512MultiBlockNode> TYPE = NodeClass.create(SHA512MultiBlockNode.class);
        public static final ForeignCallDescriptor STUB = multiBlockForeignCallDescriptor("sha512ImplCompressMB");

        public SHA512MultiBlockNode(ValueNode buf, ValueNode state, ValueNode ofs, ValueNode limit) {
            super(TYPE, buf, state, ofs, limit, null);
        }

        public SHA512MultiBlockNode(ValueNode buf, ValueNode state, ValueNode ofs, ValueNode limit, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, buf, state, ofs, limit, runtimeCheckedCPUFeatures);
        }

        public static EnumSet<AMD64.CPUFeature> minFeaturesAMD64() {
            return SHA512Node.minFeaturesAMD64();
        }

        public static EnumSet<AMD64.CPUFeature> maxFeaturesAMD64() {
            return SHA512Node.maxFeaturesAMD64();
        }

        public static EnumSet<AArch64.CPUFeature> minFeaturesAARCH64() {
            return SHA512Node.minFeaturesAARCH64();
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.setResult(this, gen.getLIRGeneratorTool().emitSha512ImplCompressMB(gen.operand(buf), gen.operand(state), gen.operand(ofs), gen.operand(limit)));
        }

        @NodeIntrinsic
        @GenerateStub(name = "sha512ImplCompressMB", minimumCPUFeaturesAMD64 = "minFeaturesAMD64", minimumCPUFeaturesAARCH64 = "minFeaturesAARCH64")
        public static native int sha512ImplCompressMB(Pointer buf, Pointer state, int ofs, int limit);

        @NodeIntrinsic
        public static native int sha512ImplCompressMB(Pointer buf, Pointer state, int ofs, int limit, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }

    /**
     * Intrinsification for {@code sun.security.provider.MD5.implCompress0}.
     */
    @NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_UNKNOWN, cyclesRationale = "Cannot estimate the time of a loop", size = SIZE_64)
    public static final class MD5Node extends MessageDigestNode {

        public static final NodeClass<MD5Node> TYPE = NodeClass.create(MD5Node.class);
        public static final ForeignCallDescriptor STUB = foreignCallDescriptor("md5ImplCompress");

        public MD5Node(ValueNode buf, ValueNode state) {
            super(TYPE, buf, state, null);
        }

        public MD5Node(ValueNode buf, ValueNode state, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, buf, state, runtimeCheckedCPUFeatures);
        }

        public static boolean isSupported(Architecture arch) {
            return arch instanceof AMD64 || arch instanceof AArch64;
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.getLIRGeneratorTool().emitMD5ImplCompress(gen.operand(buf), gen.operand(state));
        }

        @NodeIntrinsic
        @GenerateStub(name = "md5ImplCompress")
        public static native void md5ImplCompress(Pointer buf, Pointer state);

        @NodeIntrinsic
        public static native void md5ImplCompress(Pointer buf, Pointer state, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    }

    /**
     * Intrinsification for {@code sun.security.provider.DigestBase.implCompressMultiBlock0}
     * with a {@code sun.security.provider.MD5} receiver.
     */
    @NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_UNKNOWN, cyclesRationale = "Cannot estimate the time of a loop", size = SIZE_64)
    public static final class MD5MultiBlockNode extends MessageDigestMultiBlockNode {

        public static final NodeClass<MD5MultiBlockNode> TYPE = NodeClass.create(MD5MultiBlockNode.class);
        public static final ForeignCallDescriptor STUB = multiBlockForeignCallDescriptor("md5ImplCompressMB");

        public MD5MultiBlockNode(ValueNode buf, ValueNode state, ValueNode ofs, ValueNode limit) {
            super(TYPE, buf, state, ofs, limit, null);
        }

        public MD5MultiBlockNode(ValueNode buf, ValueNode state, ValueNode ofs, ValueNode limit, EnumSet<?> runtimeCheckedCPUFeatures) {
            super(TYPE, buf, state, ofs, limit, runtimeCheckedCPUFeatures);
        }

        @Override
        public ForeignCallDescriptor getForeignCallDescriptor() {
            return STUB;
        }

        @Override
        public void emitIntrinsic(NodeLIRBuilderTool gen) {
            gen.setResult(this, gen.getLIRGeneratorTool().emitMD5ImplCompressMB(gen.operand(buf), gen.operand(state), gen.operand(ofs), gen.operand(limit)));
        }

        @NodeIntrinsic
        @GenerateStub(name = "md5ImplCompressMB")
        public static native int md5ImplCompressMB(Pointer buf, Pointer state, int ofs, int limit);

        @NodeIntrinsic
        public static native int md5ImplCompressMB(Pointer buf, Pointer state, int ofs, int limit, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);
    }
}
