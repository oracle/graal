/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.util.VMError.unimplemented;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.FrameInfoQueryResult.ValueInfo;
import com.oracle.svm.core.code.FrameInfoQueryResult.ValueType;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.DeoptimizedFrame.VirtualFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.snippets.KnownIntrinsics;

import jdk.vm.ci.code.stack.InspectedFrame;
import jdk.vm.ci.code.stack.InspectedFrameVisitor;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
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
    public boolean visitFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptimizedFrame) {
        VirtualFrame virtualFrame = null;
        CodeInfoQueryResult info = null;
        FrameInfoQueryResult deoptInfo = null;

        if (deoptimizedFrame != null) {
            virtualFrame = deoptimizedFrame.getTopFrame();
        } else {
            info = CodeInfoTable.lookupCodeInfoQueryResult(codeInfo, ip);
            if (info == null || info.getFrameInfo() == null) {
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
            int method;
            if (virtualFrame != null) {
                assert deoptInfo == null : "must have either deoptimized or non-deoptimized frame information, but not both";
                method = virtualFrame.getFrameInfo().getDeoptMethodOffset();
            } else {
                method = deoptInfo.getDeoptMethodOffset();
            }

            if (matches(method, curMatchingMethods)) {
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

    private static boolean matches(int needle, ResolvedJavaMethod[] haystack) {
        if (haystack == null) {
            return true;
        }
        for (ResolvedJavaMethod method : haystack) {
            if (((SharedMethod) method).getDeoptOffsetInImage() == needle) {
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
    private CodeInfoQueryResult codeInfo;
    private FrameInfoQueryResult frameInfo;
    private final int virtualFrameIndex;

    private final JavaConstant[] locals;
    private Deoptimizer deoptimizer;

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
        this.locals = new JavaConstant[this.frameInfo.getNumLocals()];
    }

    private Deoptimizer getDeoptimizer() {
        assert virtualFrame == null;
        if (deoptimizer == null) {
            deoptimizer = new Deoptimizer(sp, codeInfo);
        }
        return deoptimizer;
    }

    private void checkLocalIndex(int index) {
        if (index < 0 || index >= frameInfo.getNumLocals()) {
            throw new IndexOutOfBoundsException();
        }
    }

    @Override
    public Object getLocal(int index) {
        JavaConstant result = getLocalConstant(index);
        if (result.getJavaKind() != JavaKind.Object) {
            throw new IllegalArgumentException("can only access Object local variables for now: " + result.getJavaKind());
        }
        return KnownIntrinsics.convertUnknownValue(SubstrateObjectConstant.asObject(Object.class, result), Object.class);
    }

    private JavaConstant getLocalConstant(int index) {
        checkDeoptimized();
        checkLocalIndex(index);
        JavaConstant result;
        if (virtualFrame != null) {
            result = virtualFrame.getConstant(index);
            assert locals[index] == null || locals[index].equals(result) : "value before and after deoptimization must be equal";
        } else {
            result = locals[index];
            if (result == null) {
                result = getDeoptimizer().readLocalVariable(index, frameInfo);
                locals[index] = result;
            }
        }
        return result;
    }

    @Override
    public boolean isVirtual(int index) {
        checkDeoptimized();
        checkLocalIndex(index);
        if (virtualFrame == null) {
            ValueInfo[] valueInfos = frameInfo.getValueInfos();
            if (index >= valueInfos.length) {
                return false;
            } else if (valueInfos[index].getType() == ValueType.VirtualObject) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasVirtualObjects() {
        checkDeoptimized();
        if (virtualFrame == null) {
            ValueInfo[] valueInfos = frameInfo.getValueInfos();
            for (int i = 0; i < valueInfos.length; i++) {
                if (valueInfos[i].getType() == ValueType.VirtualObject) {
                    return true;
                }
            }
        }
        return false;
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

    private VirtualFrame lookupVirtualFrame() {
        DeoptimizedFrame deoptimizedFrame = Deoptimizer.checkDeoptimized(sp);
        if (deoptimizedFrame != null) {
            /*
             * Find the matching inlined frame, by skipping over the virtual frames that were
             * already processed before deoptimization.
             */
            VirtualFrame cur = deoptimizedFrame.getTopFrame();
            for (int i = 0; i < virtualFrameIndex; i++) {
                cur = cur.getCaller();
            }
            return cur;
        } else {
            return null;
        }
    }

    @Override
    public void materializeVirtualObjects(boolean invalidateCode) {
        if (virtualFrame == null) {
            DeoptimizedFrame deoptimizedFrame = getDeoptimizer().deoptSourceFrame(ip, false);
            assert deoptimizedFrame == Deoptimizer.checkDeoptimized(sp);
        }

        if (invalidateCode) {
            /*
             * Note that we deoptimize the our frame before invalidating the method, with would also
             * deoptimize our frame. But we would deoptimize it with new materialized objects, i.e.,
             * a virtual object that was accessed via a local variable before would now have a
             * different value.
             */
            Deoptimizer.invalidateMethodOfFrame(sp, null);
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

    @Override
    public ResolvedJavaMethod getMethod() {
        /*
         * Substrate VM currently does not store a mapping from deoptimization information back to
         * ResolvedJavaMethod.
         */
        throw unimplemented();
    }

    @Override
    public boolean isMethod(ResolvedJavaMethod method) {
        checkDeoptimized();
        return ((SharedMethod) method).getDeoptOffsetInImage() == frameInfo.getDeoptMethodOffset();
    }

    @Override
    public String toString() {
        checkDeoptimized();

        StringBuilder result = new StringBuilder();
        final StackTraceElement sourceReference = frameInfo.getSourceReference();
        result.append(sourceReference != null ? sourceReference.toString() : "[method name not available]");

        result.append("  bci: ").append(frameInfo.getBci());
        if (virtualFrame != null) {
            result.append("  [deoptimized]");
        }
        result.append("  sp: 0x").append(Long.toHexString(sp.rawValue()));
        result.append("  ip: 0x").append(Long.toHexString(ip.rawValue()));
        if (frameInfo.getDeoptMethodOffset() != 0) {
            result.append("  deoptTarget: 0x").append(Long.toHexString(frameInfo.getDeoptMethodAddress().rawValue()));
        }

        for (int i = 0; i < frameInfo.getNumLocals(); i++) {
            JavaConstant con = getLocalConstant(i);
            if (con.getJavaKind() != JavaKind.Illegal) {
                result.append("\n    local ").append(i);
                String name = frameInfo.getLocalVariableName(i);
                if (name != null) {
                    result.append(" ").append(name);
                }
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
