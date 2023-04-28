/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.preview.panama.core;

import static com.oracle.svm.core.util.VMError.unsupportedFeature;
import static com.oracle.svm.preview.panama.core.NativeEntryPointInfo.checkType;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.stream.Stream;

import com.oracle.svm.core.graal.code.MemoryAssignment;

import jdk.internal.foreign.CABI;
import jdk.internal.foreign.abi.Binding;
import jdk.internal.foreign.abi.CallingSequence;
import jdk.internal.foreign.abi.CapturableState;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.abi.VMStorage;
import jdk.internal.foreign.abi.x64.X86_64Architecture;
import jdk.internal.foreign.abi.x64.sysv.CallArranger;

@SuppressWarnings("unused")
public abstract class AbiUtils {
    /**
     * From <a href="https://en.cppreference.com/w/c/language/arithmetic_types">cppreference</a>.
     * Project Panama doesn't support (yet?) 32-bit architectures.
     */
    public enum DataModel {
        LLP64(64),
        LP64(64);
        public final int wordSize;

        DataModel(int wordSize) {
            this.wordSize = wordSize;
        }
    }

    private static abstract class X86_64 extends AbiUtils {

        public MemoryAssignment[] toMemoryAssignment(VMStorage[] argMoves, boolean forReturn) {
            // See jdk.internal.foreign.abi.x64.X86_64Architecture
            int size = 0;
            for (VMStorage move: argMoves) {
                // Placeholders are ignored. They will be handled further down the line
                if (move.type() != X86_64Architecture.StorageType.PLACEHOLDER) {
                    ++size;
                }
                if (move.type() == X86_64Architecture.StorageType.X87) {
                    throw unsupportedFeature("Unsupported register kind: X87");
                }
                if (move.type() == X86_64Architecture.StorageType.STACK && forReturn) {
                    throw unsupportedFeature("Unsupported register kind for return: STACK");
                }
            }

            MemoryAssignment[] storages = new MemoryAssignment[size];
            int i = 0;
            for (VMStorage move: argMoves) {
                if (move.type() != X86_64Architecture.StorageType.PLACEHOLDER) {
                    MemoryAssignment.Kind kind = switch (move.type()) {
                        case X86_64Architecture.StorageType.INTEGER -> MemoryAssignment.Kind.INTEGER;
                        case X86_64Architecture.StorageType.VECTOR -> MemoryAssignment.Kind.FLOAT;
                        case X86_64Architecture.StorageType.STACK -> MemoryAssignment.Kind.STACK;
                        default -> throw unsupportedFeature("Unhandled VMStorage: " + move);
                    };
                    storages[i++] = new MemoryAssignment(kind, move.indexOrOffset());
                }
            }

            return storages;
        }
    }

    public static final AbiUtils SysV = new X86_64() {
        private static Stream<Binding.VMStore> argMoveBindingsStream(CallingSequence callingSequence) {
            return callingSequence.argumentBindings()
                    .filter(Binding.VMStore.class::isInstance)
                    .map(Binding.VMStore.class::cast);
        }

        private static Stream<Binding.VMLoad> retMoveBindingsStream(CallingSequence callingSequence) {
            return callingSequence.returnBindings().stream()
                    .filter(Binding.VMLoad.class::isInstance)
                    .map(Binding.VMLoad.class::cast);
        }

        private static Binding.VMLoad[] retMoveBindings(CallingSequence callingSequence) {
            return retMoveBindingsStream(callingSequence).toArray(Binding.VMLoad[]::new);
        }

        private VMStorage[] toStorageArray(Binding.Move[] moves) {
            return Arrays.stream(moves).map(Binding.Move::storage).toArray(VMStorage[]::new);
        }

        @Override
        public NativeEntryPointInfo makeEntrypoint(FunctionDescriptor desc, Linker.Option... options) {
            // From CallArranger.arrangeDowncall
            MethodType type = desc.toMethodType();
            CallArranger.Bindings bindings = CallArranger.getBindings(type, desc, false, LinkerOptions.forDowncall(desc, options));

            // From DowncallLinker.getBoundMethodHandle
            var callingSequence = bindings.callingSequence();
            var argMoves = toStorageArray(argMoveBindingsStream(callingSequence).toArray(Binding.VMStore[]::new));
            var returnMoves = toStorageArray(retMoveBindings(callingSequence));
            var methodType = callingSequence.calleeMethodType();
            var needsReturnBuffer = callingSequence.needsReturnBuffer();

            // From NativeEntrypoint.make
            checkType(methodType, needsReturnBuffer, callingSequence.capturedStateMask());
            var parametersAssignment = toMemoryAssignment(argMoves, false);
            var returnBuffering = needsReturnBuffer ? toMemoryAssignment(returnMoves, true) : null;
            return new NativeEntryPointInfo(methodType, parametersAssignment, returnBuffering, callingSequence.capturedStateMask());
        }

        @Override
        public DataModel dataModel() {
            return DataModel.LP64;
        }

        @Override
        public int supportedCaptureMask() {
            return CapturableState.ERRNO.mask();
        }
    };

    public static AbiUtils getInstance() {
        return switch (CABI.current()) {
            case SYS_V -> SysV;
            case WIN_64 -> throw unsupportedFeature("Foreign functions are not yet supported on Windows-x64.");
            case LINUX_AARCH_64 -> throw unsupportedFeature("Foreign functions are not yet supported on Linux-ARM.");
            case MAC_OS_AARCH_64 -> throw unsupportedFeature("Foreign functions are not yet supported on MacOS-ARM.");
        };
    }

    /**
     * This method re-implements a part of the logic from the JDK so that we can get the
     * callee-type (i.e. C type) of a function from its descriptor.
     * Note that this process is ABI (i.e. architecture and OS)  dependant.
     */
    public abstract NativeEntryPointInfo makeEntrypoint(FunctionDescriptor desc, Linker.Option... options);


    /**
     * Generate a register allocation for SubstrateVM from the one generated by and for Panama Foreign/HotSpot.
     */
    public abstract MemoryAssignment[] toMemoryAssignment(VMStorage[] moves, boolean forReturn);

    public abstract DataModel dataModel();

    public abstract int supportedCaptureMask();
}