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
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;

/**
 * Calls from Java into HotSpot.
 */
public interface CompilerToVM {

    /**
     * Copies the original bytecode of a given method into a new byte array and returns it.
     *
     * @param metaspaceMethod the metaspace Method object
     * @return a new byte array containing the original bytecode
     */
    byte[] getBytecode(long metaspaceMethod);

    int exceptionTableLength(long metaspaceMethod);

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
     * <li>a CompileOracle directive may prevent inlining or compilation of methods</li>
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
     * Determines if a given metaspace method is ignored by security stack walks.
     *
     * @param metaspaceMethod the metaspace Method object
     * @return true if the method is ignored
     */
    boolean methodIsIgnoredBySecurityStackWalk(long metaspaceMethod);

    /**
     * Converts a name to a metaspace klass.
     *
     * @param name a well formed Java type in {@linkplain JavaType#getName() internal} format
     * @param accessingClass the context of resolution (may be null)
     * @param resolve force resolution to a {@link ResolvedJavaType}. If true, this method will
     *            either return a {@link ResolvedJavaType} or throw an exception
     * @return a metaspace klass for {@code name}
     * @throws LinkageError if {@code resolve == true} and the resolution failed
     */
    long lookupType(String name, Class<?> accessingClass, boolean resolve);

    Object resolveConstantInPool(long metaspaceConstantPool, int cpi);

    Object resolvePossiblyCachedConstantInPool(long metaspaceConstantPool, int cpi);

    int lookupNameAndTypeRefIndexInPool(long metaspaceConstantPool, int cpi);

    String lookupNameRefInPool(long metaspaceConstantPool, int cpi);

    String lookupSignatureRefInPool(long metaspaceConstantPool, int cpi);

    int lookupKlassRefIndexInPool(long metaspaceConstantPool, int cpi);

    long constantPoolKlassAt(long metaspaceConstantPool, int cpi);

    /**
     * Looks up a class entry in a constant pool.
     *
     * @param metaspaceConstantPool metaspace constant pool pointer
     * @param cpi constant pool index
     * @return a metaspace Klass for a resolved method entry, a metaspace Symbol otherwise (with
     *         tagging)
     */
    long lookupKlassInPool(long metaspaceConstantPool, int cpi);

    /**
     * Looks up a method entry in a constant pool.
     *
     * @param metaspaceConstantPool metaspace constant pool pointer
     * @param cpi constant pool index
     * @return a metaspace Method for a resolved method entry, 0 otherwise
     */
    long lookupMethodInPool(long metaspaceConstantPool, int cpi, byte opcode);

    /**
     * Looks up a field entry in a constant pool and attempts to resolve it. The values returned in
     * {@code info} are:
     *
     * <pre>
     *     [(int) flags,   // only valid if field is resolved
     *      (int) offset]  // only valid if field is resolved
     * </pre>
     *
     * @param metaspaceConstantPool metaspace constant pool pointer
     * @param cpi constant pool index
     * @param info an array in which the details of the field are returned
     * @return true if the field is resolved
     */
    long resolveField(long metaspaceConstantPool, int cpi, byte opcode, long[] info);

    int constantPoolRemapInstructionOperandFromCache(long metaspaceConstantPool, int cpi);

    Object lookupAppendixInPool(long metaspaceConstantPool, int cpi);

    public enum CodeInstallResult {
        OK("ok"),
        DEPENDENCIES_FAILED("dependencies failed"),
        CACHE_FULL("code cache is full"),
        CODE_TOO_LARGE("code is too large");

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
    CodeInstallResult installCode(HotSpotCompiledCode compiledCode, InstalledCode code, SpeculationLog speculationLog);

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
    void notifyCompilationStatistics(int id, HotSpotResolvedJavaMethod method, boolean osr, int processedBytecodes, long time, long timeUnitsPerSecond, InstalledCode installedCode);

    void resetCompilationStatistics();

    void initializeConfiguration(HotSpotVMConfig config);

    long resolveMethod(long metaspaceKlassExactReceiver, long metaspaceMethod, long metaspaceKlassCaller);

    long getClassInitializer(long metaspaceKlass);

    boolean hasFinalizableSubclass(long metaspaceKlass);

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

    Object executeCompiledMethod(Object arg1, Object arg2, Object arg3, InstalledCode hotspotInstalledCode) throws InvalidInstalledCodeException;

    Object executeCompiledMethodVarargs(Object[] args, InstalledCode hotspotInstalledCode) throws InvalidInstalledCodeException;

    long[] getLineNumberTable(long metaspaceMethod);

    long getLocalVariableTableStart(long metaspaceMethod);

    int getLocalVariableTableLength(long metaspaceMethod);

    String getFileName(HotSpotResolvedJavaType method);

    Class<?> getJavaMirror(long metaspaceKlass);

    long readUnsafeKlassPointer(Object o);

    void doNotInlineOrCompile(long metaspaceMethod);

    /**
     * Invalidates the profiling information and restarts profiling upon the next invocation.
     *
     * @param metaspaceMethod the metaspace Method object
     */
    void reprofile(long metaspaceMethod);

    void invalidateInstalledCode(InstalledCode hotspotInstalledCode);

    /**
     * Collects the current values of all Graal benchmark counters, summed up over all threads.
     */
    long[] collectCounters();

    boolean isMature(long metaspaceMethodData);

    /**
     * Generate a unique id to identify the result of the compile.
     */
    int allocateCompileId(long metaspaceMethod, int entryBCI);

    /**
     * Gets the names of the supported GPU architectures.
     *
     * @return a comma separated list of names
     */
    String getGPUs();

    /**
     *
     * @param metaspaceMethod the method to check
     * @param entryBCI
     * @param level the compilation level
     * @return true if the {@code metaspaceMethod} has code for {@code level}
     */
    boolean hasCompiledCodeForOSR(long metaspaceMethod, int entryBCI, int level);

    /**
     * Fetch the time stamp used for printing inside hotspot. It's relative to VM start to that all
     * events can be ordered.
     *
     * @return milliseconds since VM start
     */
    long getTimeStamp();

    /**
     * Gets the value of a metaspace {@code Symbol} as a String.
     *
     * @param metaspaceSymbol
     */
    String getSymbol(long metaspaceSymbol);

    /**
     * Looks for the next Java stack frame with the given method.
     *
     * @param frame the starting point of the search, where {@code null} refers to the topmost frame
     * @param methods the metaspace methods to look for, where {@code null} means that any frame is
     *            returned
     * @return the frame, or {@code null} if the end of the stack was reached during the search
     */
    HotSpotStackFrameReference getNextStackFrame(HotSpotStackFrameReference frame, long[] methods, int initialSkip);

    /**
     * Materialized all virtual objects within the given stack frame and update the locals within
     * the given stackFrame object.
     *
     * @param invalidate if {@code true}, the compiled method for the stack frame will be
     *            invalidated.
     */
    void materializeVirtualObjects(HotSpotStackFrameReference stackFrame, boolean invalidate);

    void resolveInvokeDynamic(long metaspaceConstantPool, int index);

    int getVtableIndexForInterface(long metaspaceKlass, long metaspaceMethod);
}
