/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.sun.cri.ri;

import java.lang.reflect.*;

import com.sun.cri.ci.*;

/**
 * Encapsulates the main functionality of the runtime for the compiler, including access
 * to constant pools, OSR frames, inlining requirements, and runtime calls such as checkcast.
s */
public interface RiRuntime {

    /**
     * Checks whether the specified method is required to be inlined (for semantic reasons).
     * If this method returns true, then the null-check of the receiver emitted during
     * inlining is omitted.
     *
     * @param method the method being called
     * @return {@code true} if the method must be inlined; {@code false} to let the compiler
     * use its own heuristics
     */
    boolean mustInline(RiResolvedMethod method);

    /**
     * Checks whether the specified method must not be inlined (for semantic reasons).
     * @param method the method being called
     * @return {@code true} if the method must not be inlined; {@code false} to let the compiler
     * use its own heuristics
     */
    boolean mustNotInline(RiResolvedMethod method);

    /**
     * Checks whether the specified method cannot be compiled.
     * @param method the method being called
     * @return {@code true} if the method cannot be compiled
     */
    boolean mustNotCompile(RiResolvedMethod method);

    /**
     * Offset of the lock within the lock object on the stack.

     * Note: superseded by sizeOfLockData() in Graal.
     *
     * @return the offset in bytes
     */
    int basicObjectLockOffsetInBytes();

    /**
     * Get the size in bytes of a lock object on the stack.
     *
     * Note: superseded by sizeOfLockData() in Graal.
     */
    int sizeOfBasicObjectLock();

    /**
     * Get the size in bytes for locking information on the stack.
     */
    int sizeOfLockData();

    /**
     * The offset of the normal entry to the code. The compiler inserts NOP instructions to satisfy this constraint.
     *
     * @return the code offset in bytes
     */
    int codeOffset();

    /**
     * Returns the disassembly of the given code bytes. Used for debugging purposes only.
     *
     * @param code the code bytes that should be disassembled
     * @param address an address at which the bytes are located. This can be used for an address prefix per line of disassembly.
     * @return the disassembly. This will be of length 0 if the runtime does not support disassembling.
     */
    String disassemble(byte[] code, long address);

    /**
     * Returns the disassembly of the given code bytes. Used for debugging purposes only.
     *
     * @param targetMethod the {@link CiTargetMethod} containing the code bytes that should be disassembled
     * @return the disassembly. This will be of length 0 if the runtime does not support disassembling.
     */
    String disassemble(CiTargetMethod targetMethod);

    /**
     * Returns the disassembly of the given method in a {@code javap}-like format.
     * Used for debugging purposes only.
     *
     * @param method the method that should be disassembled
     * @return the disassembly. This will be of length 0 if the runtime does not support disassembling.
     */
    String disassemble(RiResolvedMethod method);

    /**
     * Registers the given compiler stub and returns an object that can be used to identify it in the relocation
     * information.
     *
     * @param targetMethod the target method representing the code of the compiler stub
     * @param name the name of the stub, used for debugging purposes only
     * @return the identification object
     */
    Object registerCompilerStub(CiTargetMethod targetMethod, String name);

    /**
     * Returns the RiType object representing the base type for the given kind.
     */
    RiResolvedType asRiType(CiKind kind);

    /**
     * Returns the type of the given constant object.
     *
     * @return {@code null} if {@code constant.isNull() || !constant.kind.isObject()}
     */
    RiResolvedType getTypeOf(CiConstant constant);


    RiResolvedType getType(Class<?> clazz);

    /**
     * Returns true if the given type is a subtype of java/lang/Throwable.
     */
    boolean isExceptionType(RiResolvedType type);

    /**
     * Checks whether this method is foldable (i.e. if it is a pure function without side effects).
     * @param method the method that is checked
     * @return whether the method is foldable
     */
    boolean isFoldable(RiResolvedMethod method);

    /**
     * Attempts to compile-time evaluate or "fold" a call to a given method. A foldable method is a pure function
     * that has no side effects. Such methods can be executed via reflection when all their inputs are constants,
     * and the resulting value is substituted for the method call. May only be called on methods for which
     * isFoldable(method) returns {@code true}. The array of constant for arguments may contain {@code null} values, which
     * means that this particular argument does not evaluate to a compile time constant.
     *
     * @param method the compiler interface method for which folding is being requested
     * @param args the arguments to the call as an array of CiConstant objects
     * @return the result of the folding or {@code null} if no folding occurred
     */
    CiConstant fold(RiResolvedMethod method, CiConstant[] args);

    /**
     * Used by the canonicalizer to compare objects, since a given runtime might not want to expose the real objects to the compiler.
     *
     * @return true if the two parameters represent the same runtime object, false otherwise
     */
    boolean areConstantObjectsEqual(CiConstant x, CiConstant y);

    /**
     * Gets the register configuration to use when compiling a given method.
     *
     * @param method the top level method of a compilation
     */
    RiRegisterConfig getRegisterConfig(RiMethod method);

    /**
     * Custom area on the stack of each compiled method that the VM can use for its own purposes.
     * @return the size of the custom area in bytes
     */
    int getCustomStackAreaSize();

    /**
     * Gets the length of the array that is wrapped in a CiConstant object.
     */
    int getArrayLength(CiConstant array);

    /**
     * Converts the given CiConstant object to a object.
     *
     * @return {@code null} if the conversion is not possible <b>OR</b> {@code c.isNull() == true}
     */
    Object asJavaObject(CiConstant c);

    /**
     * Converts the given CiConstant object to a {@link Class} object.
     *
     * @return {@code null} if the conversion is not possible.
     */
    Class<?> asJavaClass(CiConstant c);

    /**
     * Performs any runtime-specific conversion on the object used to describe the target of a call.
     */
    Object asCallTarget(Object target);

    /**
     * Returns the maximum absolute offset of a runtime call target from any position in the code cache or -1
     * when not known or not applicable. Intended for determining the required size of address/offset fields.
     */
    long getMaxCallTargetOffset(CiRuntimeCall rtcall);

    /**
     * Provides the {@link RiMethod} for a {@link Method} obtained via reflection.
     */
    RiResolvedMethod getRiMethod(Method reflectionMethod);

    /**
     * Installs some given machine code as the implementation of a given method.
     *
     * @param method a method whose executable code is being modified
     * @param code the code to be executed when {@code method} is called
     */
    void installMethod(RiMethod method, CiTargetMethod code);

    /**
     * Adds the given machine code as an implementation of the given method without making it the default implementation.
     * @param method a method to which the executable code is begin added
     * @param code the code to be added
     * @return a reference to the compiled and ready-to-run code
     */
    RiCompiledMethod addMethod(RiResolvedMethod method, CiTargetMethod code);

    /**
     * Executes the given runnable on a compiler thread, which means that it can access constant pools, etc.
     */
    void executeOnCompilerThread(Runnable r);
}
