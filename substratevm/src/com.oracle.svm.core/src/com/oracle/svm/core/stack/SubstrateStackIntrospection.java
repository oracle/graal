/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.stack;

import static com.oracle.svm.shared.util.VMError.intentionallyUnimplemented;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.FrameInfoQueryResult.ValueInfo;
import com.oracle.svm.core.code.FrameInfoQueryResult.ValueType;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.deopt.VirtualFrame;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.guest.staging.core.graal.KnownIntrinsics;
import com.oracle.svm.shared.util.SubstrateUtil;

import jdk.vm.ci.code.stack.InspectedFrame;
import jdk.vm.ci.code.stack.InspectedFrameVisitor;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCI;

public class SubstrateStackIntrospection implements StackIntrospection {

    public static final SubstrateStackIntrospection SINGLETON = new SubstrateStackIntrospection();

    @NeverInline("Stack walking starts at the physical caller frame of this method")
    @Override
    public <T> T iterateFrames(ResolvedJavaMethod[] initialMethods, ResolvedJavaMethod[] matchingMethods, int initialSkip, InspectedFrameVisitor<T> visitor) {
        if (SubstrateUtil.HOSTED) {
            /*
             * During native-image generation we use HotSpotStackIntrospection to iterate frames.
             * `initialMethods` and `matchingMethods` are hosted versions of `ResolvedJavaMethod`
             * that we provide them in `SubstrateTruffleRuntime`.
             */
            StackIntrospection hostedStackIntrospection = JVMCI.getRuntime().getHostJVMCIBackend().getStackIntrospection();
            return hostedStackIntrospection.iterateFrames(initialMethods, matchingMethods, initialSkip, visitor);
        }

        /* Stack walking starts at the physical caller frame of this method. */
        Pointer startSP = KnownIntrinsics.readCallerStackPointer();
        PhysicalStackFrameVisitor<T> physicalFrameVisitor = new PhysicalStackFrameVisitor<>(initialMethods, matchingMethods, initialSkip, visitor);
        JavaStackWalker.walkCurrentThread(startSP, physicalFrameVisitor);
        return physicalFrameVisitor.result;
    }
}

class PhysicalStackFrameVisitor<T> extends StackFrameVisitor {

    private ResolvedJavaMethod[] curMatchingMethods;
    private final ResolvedJavaMethod[] laterMatchingMethods;
    private int skip;
    private final InspectedFrameVisitor<T> visitor;

    protected T result;

    PhysicalStackFrameVisitor(ResolvedJavaMethod[] initialMethods, ResolvedJavaMethod[] matchingMethods, int initialSkip, InspectedFrameVisitor<T> visitor) {
        this.curMatchingMethods = initialMethods;
        this.laterMatchingMethods = matchingMethods;
        this.skip = initialSkip;
        this.visitor = visitor;
    }

    @Override
    public boolean visitRegularFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo) {
        return visitFrame(sp, ip, codeInfo, null);
    }

    @Override
    protected boolean visitDeoptimizedFrame(Pointer originalSP, CodePointer deoptStubIP, DeoptimizedFrame deoptimizedFrame) {
        CodeInfo imageCodeInfo = CodeInfoTable.lookupImageCodeInfo(deoptStubIP);
        return visitFrame(originalSP, deoptStubIP, imageCodeInfo, deoptimizedFrame);
    }

    private boolean visitFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptimizedFrame) {
        VirtualFrame virtualFrame = null;
        CodeInfoQueryResult info = null;
        FrameInfoQueryResult deoptInfo = null;

        if (deoptimizedFrame != null) {
            virtualFrame = deoptimizedFrame.getTopFrame();
        } else {
            info = CodeInfoTable.lookupCodeInfoQueryResult(codeInfo, ip);
            if (info.getFrameInfo() == null) {
                /*
                 * We do not have detailed information about this physical frame. It does not
                 * contain Java frames that we care about, so we can move on to the caller.
                 */
                return true;
            }
            deoptInfo = info.getFrameInfo();
        }

        int virtualFrameIndex = 0;
        do {
            CodePointer deoptAddress;
            if (virtualFrame != null) {
                assert deoptInfo == null : "must have either deoptimized or non-deoptimized frame information, but not both";
                deoptAddress = virtualFrame.getFrameInfo().getDeoptMethodAddress();
            } else {
                deoptAddress = deoptInfo.getDeoptMethodAddress();
            }

            if (matchesDeoptAddress(deoptAddress, curMatchingMethods)) {
                if (skip > 0) {
                    skip--;
                } else {
                    SubstrateInspectedFrame inspectedFrame = new SubstrateInspectedFrame(sp, ip, virtualFrame, info, deoptInfo, virtualFrameIndex);
                    result = visitor.visitFrame(inspectedFrame);
                    if (result != null) {
                        /* The user told us to stop the stackwalk. */
                        return false;
                    }

                    if (virtualFrame == null && inspectedFrame.virtualFrame != null) {
                        /*
                         * We deoptimized while visiting the InspectedFrame. Continue walking the
                         * deoptimized frame.
                         */
                        virtualFrame = inspectedFrame.virtualFrame;
                        deoptInfo = null;
                    }
                    curMatchingMethods = laterMatchingMethods;
                }
            }

            if (virtualFrame != null) {
                virtualFrame = virtualFrame.getCaller();
            } else {
                deoptInfo = deoptInfo.getCaller();
            }
            virtualFrameIndex++;
        } while (virtualFrame != null || deoptInfo != null);

        return true;
    }

    private static boolean matchesDeoptAddress(CodePointer ip, ResolvedJavaMethod[] methods) {
        if (methods == null) {
            return true;
        }
        for (ResolvedJavaMethod method : methods) {
            CodeInfo codeInfo = CodeInfoTable.getImageCodeInfo((SharedMethod) method);
            if (ip == CodeInfoAccess.absoluteIP(codeInfo, ((SharedMethod) method).getImageCodeDeoptOffset())) {
                return true;
            }
        }
        return false;
    }
}

class SubstrateInspectedFrame implements InspectedFrame {
    private final Pointer sp;
    private final CodePointer ip;
    protected VirtualFrame virtualFrame;
    private final CodeInfoQueryResult codeInfo;
    private FrameInfoQueryResult frameInfo;
    private final int virtualFrameIndex;

    private final int numLocals;
    private Deoptimizer deoptimizer;
    private final int deoptMethodOffset;
    private final long encodedBci;
    private final int sourceMethodId;

    SubstrateInspectedFrame(Pointer sp, CodePointer ip, VirtualFrame virtualFrame, CodeInfoQueryResult codeInfo, FrameInfoQueryResult frameInfo, int virtualFrameIndex) {
        this.sp = sp;
        this.ip = ip;
        this.virtualFrame = virtualFrame;
        this.codeInfo = codeInfo;
        if (virtualFrame != null) {
            this.frameInfo = virtualFrame.getFrameInfo();
        } else {
            this.frameInfo = frameInfo;
        }
        this.virtualFrameIndex = virtualFrameIndex;
        this.numLocals = this.frameInfo.getNumLocals();
        this.deoptMethodOffset = this.frameInfo.getDeoptMethodOffset();
        this.encodedBci = this.frameInfo.getEncodedBci();
        this.sourceMethodId = this.frameInfo.getSourceMethodId();
    }

    private Deoptimizer getDeoptimizer() {
        assert virtualFrame == null;
        if (deoptimizer == null) {
            deoptimizer = new Deoptimizer(sp, codeInfo, CurrentIsolate.getCurrentThread(), CurrentIsolate.getCurrentThread());
        }
        return deoptimizer;
    }

    private void checkLocalIndex(int index) {
        if (index < 0 || index >= numLocals) {
            throw new IndexOutOfBoundsException();
        }
    }

    @Override
    public Object getLocal(int index) {
        JavaConstant result = getLocalConstant(index);
        if (result.getJavaKind() != JavaKind.Object) {
            throw new UnsupportedOperationException("Local " + index + " is " + result.getJavaKind() + ", not object");
        }
        return SubstrateObjectConstant.asObject(Object.class, result);
    }

    @Override
    public int getLocalInt(int index) {
        return requirePrimitive32Local(index);
    }

    @Override
    public long getLocalLong(int index) {
        return requirePrimitive64Local(index);
    }

    @Override
    public float getLocalFloat(int index) {
        return Float.intBitsToFloat(requirePrimitive32Local(index));
    }

    @Override
    public double getLocalDouble(int index) {
        return Double.longBitsToDouble(requirePrimitive64Local(index));
    }

    private JavaConstant getLocalConstant(int index) {
        checkDeoptimized();
        checkLocalIndex(index);
        if (virtualFrame != null) {
            return virtualFrame.getConstant(index);
        }
        return getDeoptimizer().getDeoptState().readLocalVariable(index, frameInfo);
    }

    /*
     * Primitive access is storage-based. SVM may currently retain more precise kinds in frame
     * metadata, but the shared contract only relies on whether 32-bit or 64-bit primitive storage
     * is available.
     */
    private int requirePrimitive32Local(int index) {
        JavaConstant result = getLocalConstant(index);
        JavaKind kind = result.getJavaKind();
        if (!(result instanceof PrimitiveConstant primitiveConstant) || kind == JavaKind.Illegal || kind == JavaKind.Long || kind == JavaKind.Double) {
            throw new UnsupportedOperationException("Local " + index + " is " + kind + ", not 32-bit primitive storage");
        }
        return (int) primitiveConstant.getRawValue();
    }

    private long requirePrimitive64Local(int index) {
        JavaConstant result = getLocalConstant(index);
        JavaKind kind = result.getJavaKind();
        if (!(result instanceof PrimitiveConstant primitiveConstant) || (kind != JavaKind.Long && kind != JavaKind.Double)) {
            throw new UnsupportedOperationException("Local " + index + " is " + kind + ", not 64-bit primitive storage");
        }
        return primitiveConstant.getRawValue();
    }

    @Override
    public boolean isVirtual(int index) {
        checkDeoptimized();
        checkLocalIndex(index);
        if (virtualFrame == null) {
            ValueInfo[] valueInfos = frameInfo.getValueInfos();
            return valueInfos != null && index < valueInfos.length && valueInfos[index].getType() == ValueType.VirtualObject;
        }
        return false;
    }

    @Override
    public boolean hasVirtualObjects() {
        checkDeoptimized();
        return virtualFrame == null && hasVirtualObjects(frameInfo.getValueInfos());
    }

    private static boolean hasVirtualObjects(ValueInfo[] valueInfos) {
        if (valueInfos != null) {
            /*
             * Frame value info is ordered as locals, expression stack, then locks. Virtual objects
             * outside the local prefix still make the frame report virtual objects.
             */
            for (ValueInfo valueInfo : valueInfos) {
                if (valueInfo.getType() == ValueType.VirtualObject) {
                    return true;
                }
            }
        }
        return false;
    }

    private static FrameInfoQueryResult lookupFrameInfo(FrameInfoQueryResult topFrameInfo, int frameIndex) {
        FrameInfoQueryResult cur = topFrameInfo;
        for (int i = 0; i < frameIndex && cur != null; i++) {
            cur = cur.getCaller();
        }
        return cur;
    }

    private VirtualFrame lookupVirtualFrame(DeoptimizedFrame deoptimizedFrame) {
        /*
         * Find the matching inlined frame, by skipping over the virtual frames that were already
         * processed before deoptimization.
         */
        VirtualFrame cur = deoptimizedFrame.getTopFrame();
        for (int i = 0; i < virtualFrameIndex && cur != null; i++) {
            cur = cur.getCaller();
        }
        return cur;
    }

    private VirtualFrame lookupVirtualFrame() {
        IsolateThread thread = CurrentIsolate.getCurrentThread();
        DeoptimizedFrame deoptimizedFrame = Deoptimizer.checkEagerDeoptimized(thread, sp);
        return deoptimizedFrame == null ? null : lookupVirtualFrame(deoptimizedFrame);
    }

    private LiveFrameLookupVisitor findLiveFrame(Pointer startSP) {
        LiveFrameLookupVisitor visitor = new LiveFrameLookupVisitor();
        JavaStackWalker.walkCurrentThread(startSP, visitor);
        return visitor.found ? visitor : null;
    }

    private boolean matchesStackFrame(Pointer frameSP, CodePointer frameIP) {
        /*
         * The return address is part of the captured frame identity. If it changes, this handle may
         * refer to a returned frame whose stack slot has been reused.
         */
        return frameSP.equal(sp) && frameIP.equal(ip);
    }

    private boolean matchesCapturedFrameInfo(FrameInfoQueryResult currentFrameInfo) {
        return currentFrameInfo.getDeoptMethodOffset() == deoptMethodOffset &&
                        currentFrameInfo.getEncodedBci() == encodedBci &&
                        currentFrameInfo.getSourceMethodId() == sourceMethodId;
    }

    private final class LiveFrameLookupVisitor extends StackFrameVisitor {
        boolean found;
        boolean changed;

        @Override
        protected boolean visitRegularFrame(Pointer frameSP, CodePointer frameIP, CodeInfo currentCodeInfo) {
            if (!frameSP.equal(sp)) {
                return true;
            }
            found = true;
            if (!matchesStackFrame(frameSP, frameIP)) {
                changed = true;
            } else {
                codeInfo = CodeInfoTable.lookupCodeInfoQueryResult(currentCodeInfo, frameIP);
                liveIP = frameIP;
            }
            return false;
        }

        @Override
        protected boolean visitDeoptimizedFrame(Pointer originalSP, CodePointer deoptStubIP, DeoptimizedFrame currentDeoptimizedFrame) {
            if (!originalSP.equal(sp)) {
                return true;
            }
            found = true;
            this.deoptimizedFrame = currentDeoptimizedFrame;
            return false;
        }

        CodeInfoQueryResult codeInfo;
        CodePointer liveIP;
        DeoptimizedFrame deoptimizedFrame;
    }

    private void verifyLiveFrameInfo(FrameInfoQueryResult liveFrameInfo) {
        if (liveFrameInfo == null || !matchesCapturedFrameInfo(liveFrameInfo)) {
            throw new IllegalStateException("Stack frame changed");
        }
    }

    private void checkDeoptimized() {
        if (virtualFrame == null) {
            virtualFrame = lookupVirtualFrame();
            if (virtualFrame != null) {
                frameInfo = virtualFrame.getFrameInfo();
                deoptimizer = null;
            }
        } else {
            assert virtualFrame == lookupVirtualFrame();
        }
    }

    @Override
    @NeverInline("Stack walking starts at the physical caller frame of this method.")
    public void materializeVirtualObjects(boolean invalidateCode) {
        /*
         * Start at the Java caller of this mutator. A frame that has already returned must not be
         * rediscovered by looking through stack storage reused by the mutator implementation.
         */
        Pointer startSP = KnownIntrinsics.readCallerStackPointer();
        LiveFrameLookupVisitor liveFrame = findLiveFrame(startSP);
        if (liveFrame == null) {
            throw new IllegalStateException("Stack frame not found");
        }
        if (liveFrame.changed) {
            throw new IllegalStateException("Stack frame changed");
        }
        IsolateThread thread = CurrentIsolate.getCurrentThread();
        if (liveFrame.deoptimizedFrame != null) {
            VirtualFrame liveVirtualFrame = lookupVirtualFrame(liveFrame.deoptimizedFrame);
            /*
             * A regular frame handle records the source IP, while a handle captured after
             * deoptimization records the deoptimization stub IP and is matched by VirtualFrame
             * identity instead.
             */
            if (liveVirtualFrame == null ||
                            (virtualFrame == null && !liveFrame.deoptimizedFrame.getSourcePC().equal(ip)) ||
                            (virtualFrame != null && liveVirtualFrame != virtualFrame)) {
                throw new IllegalStateException("Stack frame changed");
            }
            verifyLiveFrameInfo(liveVirtualFrame.getFrameInfo());
            virtualFrame = liveVirtualFrame;
        } else {
            if (liveFrame.codeInfo == null || liveFrame.codeInfo.getFrameInfo() == null) {
                throw new IllegalStateException("Stack frame not found");
            }
            FrameInfoQueryResult liveTopFrameInfo = liveFrame.codeInfo.getFrameInfo();
            verifyLiveFrameInfo(lookupFrameInfo(liveTopFrameInfo, virtualFrameIndex));
            if (!Deoptimizer.canEagerlyDeoptimize(liveTopFrameInfo, liveFrame.liveIP)) {
                throw new IllegalStateException("Stack frame cannot be materialized");
            }
            Deoptimizer liveDeoptimizer = deoptimizer != null ? deoptimizer : new Deoptimizer(sp, liveFrame.codeInfo, thread, thread);
            DeoptimizedFrame deoptimizedFrame = liveDeoptimizer.deoptimizeEagerly();
            assert deoptimizedFrame == Deoptimizer.checkEagerDeoptimized(thread, sp);
            virtualFrame = lookupVirtualFrame(deoptimizedFrame);
        }
        if (virtualFrame == null) {
            throw new IllegalStateException("Stack frame not found");
        }
        frameInfo = virtualFrame.getFrameInfo();
        deoptimizer = null;

        if (invalidateCode) {
            /*
             * Note that we deoptimize our frame before invalidating the method, which would also
             * deoptimize our frame. But we would deoptimize it with new materialized objects, i.e.,
             * a virtual object that was accessed via a local variable before would now have a
             * different value.
             */
            Deoptimizer.invalidateMethodOfFrame(thread, sp, null, DeoptimizationReason.None, false);
        }

        /* We must be deoptimized now. */
        assert lookupVirtualFrame() != null : "must be deoptimized now";
        checkDeoptimized();
    }

    @Override
    public int getBytecodeIndex() {
        checkDeoptimized();
        return frameInfo.getBci();
    }

    public ResolvedJavaMethod getMethod() {
        /*
         * Substrate VM currently does not store a mapping from deoptimization information back to
         * ResolvedJavaMethod.
         */
        throw intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public boolean isMethod(ResolvedJavaMethod method) {
        checkDeoptimized();
        return ((SharedMethod) method).getImageCodeDeoptOffset() == frameInfo.getDeoptMethodOffset();
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        checkDeoptimized();

        StackTraceElement sourceReference = frameInfo != null ? frameInfo.getSourceReference() : null;
        result.append(sourceReference != null ? sourceReference.toString() : "[method name not available]");

        result.append("  bci: ").append(frameInfo.getBci());
        if (virtualFrame != null) {
            result.append("  [deoptimized]");
        }
        result.append("  sp: 0x").append(Long.toHexString(sp.rawValue()));
        result.append("  ip: 0x").append(Long.toHexString(ip.rawValue()));
        if (frameInfo.getDeoptMethodOffset() != 0) {
            CodePointer deoptMethodAddress = frameInfo != null ? frameInfo.getDeoptMethodAddress() : Word.nullPointer();
            result.append("  deoptTarget: 0x").append(Long.toHexString(deoptMethodAddress.rawValue()));
        }

        for (int i = 0; i < numLocals; i++) {
            JavaConstant con = getLocalConstant(i);
            if (con.getJavaKind() != JavaKind.Illegal) {
                result.append(System.lineSeparator()).append("    local ").append(i);
                if (con.getJavaKind() == JavaKind.Object) {
                    if (isVirtual(i)) {
                        result.append("  [virtual object]");
                    }
                    Object val = SubstrateObjectConstant.asObject(con);
                    if (val == null) {
                        result.append("  null");
                    } else {
                        result.append("  class: ").append(val.getClass().getName());
                        result.append("  address: 0x").append(Long.toHexString(Word.objectToUntrackedPointer(val).rawValue()));
                    }
                } else {
                    result.append("  kind: ").append(con.getJavaKind().toString());
                    if (con.getJavaKind().isNumericInteger()) {
                        result.append("  value: ").append(con.asLong());
                    }
                }
            }
        }
        return result.toString();
    }
}
