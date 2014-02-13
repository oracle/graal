/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.hotspot.bridge;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;

/**
 * Calls from Java into HotSpot.
 */
public interface CompilerToVM {

    /**
     * Copies the original bytecode of a given method into a given byte array.
     * 
     * @param metaspaceMethod the metaspace Method object
     * @param code the array into which to copy the original bytecode
     * @return the value of {@code code}
     */
    byte[] initializeBytecode(long metaspaceMethod, byte[] code);

    long exceptionTableStart(long metaspaceMethod);

    /**
     * Determines if a given metaspace Method object has balanced monitors.
     * 
     * @param metaspaceMethod the metaspace Method object to query
     * @return true if the method has balanced monitors
     */
    boolean hasBalancedMonitors(long metaspaceMethod);

    /**
     * Determines if a given metaspace Method can be inlined. A method may not be inlinable for a
     * number of reasons such as:
     * <ul>
     * <li>a CompileOracle directive may prevent inlining or compilation of this methods</li>
     * <li>the method may have a bytecode breakpoint set</li>
     * <li>the method may have other bytecode features that require special handling by the VM</li>
     * </ul>
     * 
     * @param metaspaceMethod the metaspace Method object to query
     * @return true if the method can be inlined
     */
    boolean canInlineMethod(long metaspaceMethod);

    /**
     * Determines if a given metaspace Method should be inlined at any cost. This could be because:
     * <ul>
     * <li>a CompileOracle directive may forces inlining of this methods</li>
     * <li>an annotation forces inlining of this method</li>
     * </ul>
     * 
     * @param metaspaceMethod the metaspace Method object to query
     * @return true if the method should be inlined
     */
    boolean shouldInlineMethod(long metaspaceMethod);

    /**
     * Used to implement {@link ResolvedJavaType#findUniqueConcreteMethod(ResolvedJavaMethod)}.
     * 
     * @param metaspaceMethod the metaspace Method on which to based the search
     * @return the metaspace Method result or 0 is there is no unique concrete method for
     *         {@code metaspaceMethod}
     */
    long findUniqueConcreteMethod(long metaspaceMethod);

    /**
     * Returns the implementor for the given interface class.
     * 
     * @param metaspaceKlass the metaspace klass to get the implementor for
     * @return the implementor as metaspace klass pointer or null if there is no implementor
     */
    long getKlassImplementor(long metaspaceKlass);

    /**
     * Initializes a {@link HotSpotResolvedJavaMethod} object from a metaspace Method object.
     * 
     * @param metaspaceMethod the metaspace Method object
     * @param method address of a metaspace Method object
     */
    void initializeMethod(long metaspaceMethod, HotSpotResolvedJavaMethod method);

    /**
     * Converts a name to a metaspace klass.
     * 
     * @param name a well formed Java type in {@linkplain JavaType#getName() internal} format
     * @param accessingClass the context of resolution (may be null)
     * @param eagerResolve force resolution to a {@link ResolvedJavaType}. If true, this method will
     *            either return a {@link ResolvedJavaType} or throw an exception
     * @return a metaspace klass for {@code name}
     * @throws LinkageError if {@code eagerResolve == true} and the resolution failed
     */
    long lookupType(String name, Class<?> accessingClass, boolean eagerResolve);

    Object lookupConstantInPool(long metaspaceConstantPool, int cpi);

    JavaMethod lookupMethodInPool(long metaspaceConstantPool, int cpi, byte opcode);

    JavaType lookupTypeInPool(long metaspaceConstantPool, int cpi);

    JavaField lookupFieldInPool(long metaspaceConstantPool, int cpi, byte opcode);

    void lookupReferencedTypeInPool(long metaspaceConstantPool, int cpi, byte opcode);

    Object lookupAppendixInPool(long metaspaceConstantPool, int cpi, byte opcode);

    public enum CodeInstallResult {
        OK("ok"), DEPENDENCIES_FAILED("dependencies failed"), CACHE_FULL("code cache is full"), CODE_TOO_LARGE("code is too large");

        private int value;
        private String message;

        private CodeInstallResult(String name) {
            HotSpotVMConfig config = HotSpotGraalRuntime.runtime().getConfig();
            switch (name) {
                case "ok":
                    this.value = config.codeInstallResultOk;
                    break;
                case "dependencies failed":
                    this.value = config.codeInstallResultDependenciesFailed;
                    break;
                case "code cache is full":
                    this.value = config.codeInstallResultCacheFull;
                    break;
                case "code is too large":
                    this.value = config.codeInstallResultCodeTooLarge;
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere("unknown enum name " + name);
            }
            this.message = name;
        }

        /**
         * Returns the enum object for the given value.
         */
        public static CodeInstallResult getEnum(int value) {
            for (CodeInstallResult e : values()) {
                if (e.value == value) {
                    return e;
                }
            }
            throw GraalInternalError.shouldNotReachHere("unknown enum value " + value);
        }

        @Override
        public String toString() {
            return message;
        }
    }

    /**
     * Installs the result of a compilation into the code cache.
     * 
     * @param compiledCode the result of a compilation
     * @param code the details of the installed CodeBlob are written to this object
     * @return the outcome of the installation as a {@link CodeInstallResult}.
     */
    CodeInstallResult installCode(HotSpotCompiledCode compiledCode, HotSpotInstalledCode code, SpeculationLog speculationLog);

    /**
     * Notifies the VM of statistics for a completed compilation.
     * 
     * @param id the identifier of the compilation
     * @param method the method compiled
     * @param osr specifies if the compilation was for on-stack-replacement
     * @param processedBytecodes the number of bytecodes processed during the compilation, including
     *            the bytecodes of all inlined methods
     * @param time the amount time spent compiling {@code method}
     * @param timeUnitsPerSecond the granularity of the units for the {@code time} value
     * @param installedCode the nmethod installed as a result of the compilation
     */
    void notifyCompilationStatistics(int id, HotSpotResolvedJavaMethod method, boolean osr, int processedBytecodes, long time, long timeUnitsPerSecond, HotSpotInstalledCode installedCode);

    void printCompilationStatistics(boolean perCompiler, boolean aggregate);

    void resetCompilationStatistics();

    void initializeConfiguration(HotSpotVMConfig config);

    long resolveMethod(HotSpotResolvedObjectType klass, String name, String signature);

    HotSpotResolvedJavaField[] getInstanceFields(HotSpotResolvedObjectType klass);

    long getClassInitializer(HotSpotResolvedObjectType klass);

    boolean hasFinalizableSubclass(HotSpotResolvedObjectType klass);

    /**
     * Gets the compiled code size for a method.
     * 
     * @param metaspaceMethod the metaspace Method object to query
     * @return the compiled code size the method
     */
    int getCompiledCodeSize(long metaspaceMethod);

    /**
     * Gets the metaspace Method object corresponding to a given {@link Class} object and slot
     * number.
     * 
     * @param holder method holder
     * @param slot slot number of the method
     * @return the metaspace Method
     */
    long getMetaspaceMethod(Class<?> holder, int slot);

    long getMaxCallTargetOffset(long address);

    String disassembleCodeBlob(long codeBlob);

    StackTraceElement getStackTraceElement(long metaspaceMethod, int bci);

    Object executeCompiledMethod(Object arg1, Object arg2, Object arg3, HotSpotInstalledCode hotspotInstalledCode) throws InvalidInstalledCodeException;

    Object executeCompiledMethodVarargs(Object[] args, HotSpotInstalledCode hotspotInstalledCode) throws InvalidInstalledCodeException;

    long[] getDeoptedLeafGraphIds();

    long[] getLineNumberTable(HotSpotResolvedJavaMethod method);

    long getLocalVariableTableStart(HotSpotResolvedJavaMethod method);

    int getLocalVariableTableLength(HotSpotResolvedJavaMethod method);

    String getFileName(HotSpotResolvedJavaType method);

    Object readUnsafeUncompressedPointer(Object o, long displacement);

    long readUnsafeKlassPointer(Object o);

    void doNotInlineOrCompile(long metaspaceMethod);

    /**
     * Invalidates the profiling information and restarts profiling upon the next invocation.
     * 
     * @param metaspaceMethod the metaspace Method object
     */
    void reprofile(long metaspaceMethod);

    void invalidateInstalledCode(HotSpotInstalledCode hotspotInstalledCode);

    /**
     * Collects the current values of all Graal benchmark counters, summed up over all threads.
     */
    long[] collectCounters();

    boolean isMature(long metaspaceMethodData);

    /**
     * Generate a unique id to identify the result of the compile.
     */
    int allocateCompileId(HotSpotResolvedJavaMethod method, int entryBCI);

    /**
     * Gets the names of the supported GPU architectures.
     * 
     * @return a comma separated list of names
     */
    String getGPUs();
}
