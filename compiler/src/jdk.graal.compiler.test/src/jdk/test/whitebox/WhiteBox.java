/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.whitebox;

import java.lang.management.MemoryUsage;
import java.lang.reflect.Executable;

import jdk.test.whitebox.parser.DiagnosticCommand;

// Checkstyle: stop

/// Compile-time WhiteBox API. This allows for tests such as `jdk.graal.compiler.test.WhiteBoxTest`
/// to successfully build via mx. The actual WhiteBox class from the JDK test sources needs to be supplied
/// via `Xbootclasspath/a` and the `-XX:+WhiteBoxAPI` option added when running such tests.
public class WhiteBox {

    private WhiteBox() {
        throw new UnsupportedOperationException("WhiteBox constructor should not have been called");
    }

    public final String AFTER_MARKING_STARTED = "AFTER MARKING STARTED";
    public final String BEFORE_MARKING_COMPLETED = "BEFORE MARKING COMPLETED";
    public final String AFTER_CONCURRENT_REFERENCE_PROCESSING_STARTED = "AFTER CONCURRENT REFERENCE PROCESSING STARTED";
    public final String G1_AFTER_REBUILD_STARTED = "AFTER REBUILD STARTED";
    public final String G1_BEFORE_REBUILD_COMPLETED = "BEFORE REBUILD COMPLETED";
    public final String G1_AFTER_CLEANUP_STARTED = "AFTER CLEANUP STARTED";
    public final String G1_BEFORE_CLEANUP_COMPLETED = "BEFORE CLEANUP COMPLETED";

    public synchronized static WhiteBox getWhiteBox() {
        throw new UnsupportedOperationException("WhiteBox method getWhiteBox should not have been called");
    }

    public long getCompressedOopsMaxHeapSize() {
        throw new UnsupportedOperationException("WhiteBox method getCompressedOopsMaxHeapSize should not have been called");
    }

    public void printHeapSizes() {
        throw new UnsupportedOperationException("WhiteBox method printHeapSizes should not have been called");
    }

    public long getObjectAddress(Object o) {
        throw new UnsupportedOperationException("WhiteBox method getObjectAddress should not have been called");
    }

    public int getHeapOopSize() {
        throw new UnsupportedOperationException("WhiteBox method getHeapOopSize should not have been called");
    }

    public int getVMPageSize() {
        throw new UnsupportedOperationException("WhiteBox method getVMPageSize should not have been called");
    }

    public long getVMAllocationGranularity() {
        throw new UnsupportedOperationException("WhiteBox method getVMAllocationGranularity should not have been called");
    }

    public long getVMLargePageSize() {
        throw new UnsupportedOperationException("WhiteBox method getVMLargePageSize should not have been called");
    }

    public long getHeapSpaceAlignment() {
        throw new UnsupportedOperationException("WhiteBox method getHeapSpaceAlignment should not have been called");
    }

    public long getHeapAlignment() {
        throw new UnsupportedOperationException("WhiteBox method getHeapAlignment should not have been called");
    }

    public long getMinimumJavaStackSize() {
        throw new UnsupportedOperationException("WhiteBox method getMinimumJavaStackSize should not have been called");
    }

    public boolean hasExternalSymbolsStripped() {
        throw new UnsupportedOperationException("WhiteBox method hasExternalSymbolsStripped should not have been called");
    }

    public boolean isObjectInOldGen(Object o) {
        throw new UnsupportedOperationException("WhiteBox method isObjectInOldGen should not have been called");
    }

    public long getObjectSize(Object o) {
        throw new UnsupportedOperationException("WhiteBox method getObjectSize should not have been called");
    }

    public String printString(String str, int maxLength) {
        throw new UnsupportedOperationException("WhiteBox method printString should not have been called");
    }

    public void lockAndStuckInSafepoint() {
        throw new UnsupportedOperationException("WhiteBox method lockAndStuckInSafepoint should not have been called");
    }

    public int countAliveClasses(String name) {
        throw new UnsupportedOperationException("WhiteBox method countAliveClasses should not have been called");
    }

    public boolean isClassAlive(String name) {
        throw new UnsupportedOperationException("WhiteBox method isClassAlive should not have been called");
    }

    public int getSymbolRefcount(String name) {
        throw new UnsupportedOperationException("WhiteBox method getSymbolRefcount should not have been called");
    }

    public boolean deflateIdleMonitors() {
        throw new UnsupportedOperationException("WhiteBox method deflateIdleMonitors should not have been called");
    }

    public boolean isMonitorInflated(Object obj) {
        throw new UnsupportedOperationException("WhiteBox method isMonitorInflated should not have been called");
    }

    public long getInUseMonitorCount() {
        throw new UnsupportedOperationException("WhiteBox method getInUseMonitorCount should not have been called");
    }

    public int getLockStackCapacity() {
        throw new UnsupportedOperationException("WhiteBox method getLockStackCapacity should not have been called");
    }

    public boolean supportsRecursiveLightweightLocking() {
        throw new UnsupportedOperationException("WhiteBox method supportsRecursiveLightweightLocking should not have been called");
    }

    public void forceSafepoint() {
        throw new UnsupportedOperationException("WhiteBox method forceSafepoint should not have been called");
    }

    public void forceClassLoaderStatsSafepoint() {
        throw new UnsupportedOperationException("WhiteBox method forceClassLoaderStatsSafepoint should not have been called");
    }

    public long getConstantPool(Class<?> aClass) {
        throw new UnsupportedOperationException("WhiteBox method getConstantPool should not have been called");
    }

    public Object[] getResolvedReferences(Class<?> aClass) {
        throw new UnsupportedOperationException("WhiteBox method getResolvedReferences should not have been called");
    }

    public int remapInstructionOperandFromCPCache(Class<?> aClass, int index) {
        throw new UnsupportedOperationException("WhiteBox method remapInstructionOperandFromCPCache should not have been called");
    }

    public int encodeConstantPoolIndyIndex(int index) {
        throw new UnsupportedOperationException("WhiteBox method encodeConstantPoolIndyIndex should not have been called");
    }

    public int getFieldEntriesLength(Class<?> aClass) {
        throw new UnsupportedOperationException("WhiteBox method getFieldEntriesLength should not have been called");
    }

    public int getFieldCPIndex(Class<?> aClass, int index) {
        throw new UnsupportedOperationException("WhiteBox method getFieldCPIndex should not have been called");
    }

    public int getMethodEntriesLength(Class<?> aClass) {
        throw new UnsupportedOperationException("WhiteBox method getMethodEntriesLength should not have been called");
    }

    public int getMethodCPIndex(Class<?> aClass, int index) {
        throw new UnsupportedOperationException("WhiteBox method getMethodCPIndex should not have been called");
    }

    public int getIndyInfoLength(Class<?> aClass) {
        throw new UnsupportedOperationException("WhiteBox method getIndyInfoLength should not have been called");
    }

    public int getIndyCPIndex(Class<?> aClass, int index) {
        throw new UnsupportedOperationException("WhiteBox method getIndyCPIndex should not have been called");
    }

    public String printClasses(String classNamePattern, int flags) {
        throw new UnsupportedOperationException("WhiteBox method printClasses should not have been called");
    }

    public String printMethods(String classNamePattern, String methodPattern, int flags) {
        throw new UnsupportedOperationException("WhiteBox method printMethods should not have been called");
    }

    public void addToBootstrapClassLoaderSearch(String segment) {
        throw new UnsupportedOperationException("WhiteBox method addToBootstrapClassLoaderSearch should not have been called");
    }

    public void addToSystemClassLoaderSearch(String segment) {
        throw new UnsupportedOperationException("WhiteBox method addToSystemClassLoaderSearch should not have been called");
    }

    public boolean g1InConcurrentMark() {
        throw new UnsupportedOperationException("WhiteBox method g1InConcurrentMark should not have been called");
    }

    public int g1CompletedConcurrentMarkCycles() {
        throw new UnsupportedOperationException("WhiteBox method g1CompletedConcurrentMarkCycles should not have been called");
    }

    public boolean g1RunConcurrentGC(boolean errorIfFail) {
        throw new UnsupportedOperationException("WhiteBox method g1RunConcurrentGC should not have been called");
    }

    public void g1RunConcurrentGC() {
        throw new UnsupportedOperationException("WhiteBox method g1RunConcurrentGC should not have been called");
    }

    public void g1StartConcurrentGC() {
        throw new UnsupportedOperationException("WhiteBox method g1StartConcurrentGC should not have been called");
    }

    public boolean g1HasRegionsToUncommit() {
        throw new UnsupportedOperationException("WhiteBox method g1HasRegionsToUncommit should not have been called");
    }

    public boolean g1IsHumongous(Object o) {
        throw new UnsupportedOperationException("WhiteBox method g1IsHumongous should not have been called");
    }

    public boolean g1BelongsToHumongousRegion(long adr) {
        throw new UnsupportedOperationException("WhiteBox method g1BelongsToHumongousRegion should not have been called");
    }

    public boolean g1BelongsToFreeRegion(long adr) {
        throw new UnsupportedOperationException("WhiteBox method g1BelongsToFreeRegion should not have been called");
    }

    public long g1NumMaxRegions() {
        throw new UnsupportedOperationException("WhiteBox method g1NumMaxRegions should not have been called");
    }

    public long g1NumFreeRegions() {
        throw new UnsupportedOperationException("WhiteBox method g1NumFreeRegions should not have been called");
    }

    public int g1RegionSize() {
        throw new UnsupportedOperationException("WhiteBox method g1RegionSize should not have been called");
    }

    public MemoryUsage g1AuxiliaryMemoryUsage() {
        throw new UnsupportedOperationException("WhiteBox method g1AuxiliaryMemoryUsage should not have been called");
    }

    public Object[] parseCommandLine(String commandline, char delim, DiagnosticCommand[] args) {
        throw new UnsupportedOperationException("WhiteBox method parseCommandLine should not have been called");
    }

    public int g1ActiveMemoryNodeCount() {
        throw new UnsupportedOperationException("WhiteBox method g1ActiveMemoryNodeCount should not have been called");
    }

    public int[] g1MemoryNodeIds() {
        throw new UnsupportedOperationException("WhiteBox method g1MemoryNodeIds should not have been called");
    }

    public long psVirtualSpaceAlignment() {
        throw new UnsupportedOperationException("WhiteBox method psVirtualSpaceAlignment should not have been called");
    }

    public long psHeapGenerationAlignment() {
        throw new UnsupportedOperationException("WhiteBox method psHeapGenerationAlignment should not have been called");
    }

    public long[] g1GetMixedGCInfo(int liveness) {
        throw new UnsupportedOperationException("WhiteBox method g1GetMixedGCInfo should not have been called");
    }

    public long NMTMalloc(long size) {
        throw new UnsupportedOperationException("WhiteBox method NMTMalloc should not have been called");
    }

    public void NMTFree(long mem) {
        throw new UnsupportedOperationException("WhiteBox method NMTFree should not have been called");
    }

    public long NMTReserveMemory(long size) {
        throw new UnsupportedOperationException("WhiteBox method NMTReserveMemory should not have been called");
    }

    public long NMTAttemptReserveMemoryAt(long addr, long size) {
        throw new UnsupportedOperationException("WhiteBox method NMTAttemptReserveMemoryAt should not have been called");
    }

    public void NMTCommitMemory(long addr, long size) {
        throw new UnsupportedOperationException("WhiteBox method NMTCommitMemory should not have been called");
    }

    public void NMTUncommitMemory(long addr, long size) {
        throw new UnsupportedOperationException("WhiteBox method NMTUncommitMemory should not have been called");
    }

    public void NMTReleaseMemory(long addr, long size) {
        throw new UnsupportedOperationException("WhiteBox method NMTReleaseMemory should not have been called");
    }

    public long NMTMallocWithPseudoStack(long size, int index) {
        throw new UnsupportedOperationException("WhiteBox method NMTMallocWithPseudoStack should not have been called");
    }

    public long NMTMallocWithPseudoStackAndType(long size, int index, int type) {
        throw new UnsupportedOperationException("WhiteBox method NMTMallocWithPseudoStackAndType should not have been called");
    }

    public int NMTGetHashSize() {
        throw new UnsupportedOperationException("WhiteBox method NMTGetHashSize should not have been called");
    }

    public long NMTNewArena(long initSize) {
        throw new UnsupportedOperationException("WhiteBox method NMTNewArena should not have been called");
    }

    public void NMTFreeArena(long arena) {
        throw new UnsupportedOperationException("WhiteBox method NMTFreeArena should not have been called");
    }

    public void NMTArenaMalloc(long arena, long size) {
        throw new UnsupportedOperationException("WhiteBox method NMTArenaMalloc should not have been called");
    }

    public boolean isAsanEnabled() {
        throw new UnsupportedOperationException("WhiteBox method isAsanEnabled should not have been called");
    }

    public boolean isUbsanEnabled() {
        throw new UnsupportedOperationException("WhiteBox method isUbsanEnabled should not have been called");
    }

    public boolean hasLibgraal() {
        throw new UnsupportedOperationException("WhiteBox method hasLibgraal should not have been called");
    }

    public boolean isC2OrJVMCIIncluded() {
        throw new UnsupportedOperationException("WhiteBox method isC2OrJVMCIIncluded should not have been called");
    }

    public boolean isJVMCISupportedByGC() {
        throw new UnsupportedOperationException("WhiteBox method isJVMCISupportedByGC should not have been called");
    }

    public int matchesMethod(Executable method, String pattern) {
        throw new UnsupportedOperationException("WhiteBox method matchesMethod should not have been called");
    }

    public int matchesInline(Executable method, String pattern) {
        throw new UnsupportedOperationException("WhiteBox method matchesInline should not have been called");
    }

    public boolean shouldPrintAssembly(Executable method, int comp_level) {
        throw new UnsupportedOperationException("WhiteBox method shouldPrintAssembly should not have been called");
    }

    public int deoptimizeFrames(boolean makeNotEntrant) {
        throw new UnsupportedOperationException("WhiteBox method deoptimizeFrames should not have been called");
    }

    public boolean isFrameDeoptimized(int depth) {
        throw new UnsupportedOperationException("WhiteBox method isFrameDeoptimized should not have been called");
    }

    public void deoptimizeAll() {
        throw new UnsupportedOperationException("WhiteBox method deoptimizeAll should not have been called");
    }

    public boolean isMethodCompiled(Executable method) {
        throw new UnsupportedOperationException("WhiteBox method isMethodCompiled should not have been called");
    }

    public boolean isMethodCompiled(Executable method, boolean isOsr) {
        throw new UnsupportedOperationException("WhiteBox method isMethodCompiled should not have been called");
    }

    public boolean isMethodCompilable(Executable method) {
        throw new UnsupportedOperationException("WhiteBox method isMethodCompilable should not have been called");
    }

    public boolean isMethodCompilable(Executable method, int compLevel) {
        throw new UnsupportedOperationException("WhiteBox method isMethodCompilable should not have been called");
    }

    public boolean isMethodCompilable(Executable method, int compLevel, boolean isOsr) {
        throw new UnsupportedOperationException("WhiteBox method isMethodCompilable should not have been called");
    }

    public boolean isMethodQueuedForCompilation(Executable method) {
        throw new UnsupportedOperationException("WhiteBox method isMethodQueuedForCompilation should not have been called");
    }

    public boolean isIntrinsicAvailable(Executable method, Executable compilationContext, int compLevel) {
        throw new UnsupportedOperationException("WhiteBox method isIntrinsicAvailable should not have been called");
    }

    public boolean isIntrinsicAvailable(Executable method, int compLevel) {
        throw new UnsupportedOperationException("WhiteBox method isIntrinsicAvailable should not have been called");
    }

    public int deoptimizeMethod(Executable method) {
        throw new UnsupportedOperationException("WhiteBox method deoptimizeMethod should not have been called");
    }

    public int deoptimizeMethod(Executable method, boolean isOsr) {
        throw new UnsupportedOperationException("WhiteBox method deoptimizeMethod should not have been called");
    }

    public void makeMethodNotCompilable(Executable method) {
        throw new UnsupportedOperationException("WhiteBox method makeMethodNotCompilable should not have been called");
    }

    public void makeMethodNotCompilable(Executable method, int compLevel) {
        throw new UnsupportedOperationException("WhiteBox method makeMethodNotCompilable should not have been called");
    }

    public void makeMethodNotCompilable(Executable method, int compLevel, boolean isOsr) {
        throw new UnsupportedOperationException("WhiteBox method makeMethodNotCompilable should not have been called");
    }

    public int getMethodCompilationLevel(Executable method) {
        throw new UnsupportedOperationException("WhiteBox method getMethodCompilationLevel should not have been called");
    }

    public int getMethodCompilationLevel(Executable method, boolean isOsr) {
        throw new UnsupportedOperationException("WhiteBox method getMethodCompilationLevel should not have been called");
    }

    public int getMethodDecompileCount(Executable method) {
        throw new UnsupportedOperationException("WhiteBox method getMethodDecompileCount should not have been called");
    }

    public int getMethodTrapCount(Executable method) {
        throw new UnsupportedOperationException("WhiteBox method getMethodTrapCount should not have been called");
    }

    public int getMethodTrapCount(Executable method, String reason) {
        throw new UnsupportedOperationException("WhiteBox method getMethodTrapCount should not have been called");
    }

    public int getDeoptCount() {
        throw new UnsupportedOperationException("WhiteBox method getDeoptCount should not have been called");
    }

    public int getDeoptCount(String reason, String action) {
        throw new UnsupportedOperationException("WhiteBox method getDeoptCount should not have been called");
    }

    public boolean testSetDontInlineMethod(Executable method, boolean value) {
        throw new UnsupportedOperationException("WhiteBox method testSetDontInlineMethod should not have been called");
    }

    public int getCompileQueuesSize() {
        throw new UnsupportedOperationException("WhiteBox method getCompileQueuesSize should not have been called");
    }

    public int getCompileQueueSize(int compLevel) {
        throw new UnsupportedOperationException("WhiteBox method getCompileQueueSize should not have been called");
    }

    public boolean testSetForceInlineMethod(Executable method, boolean value) {
        throw new UnsupportedOperationException("WhiteBox method testSetForceInlineMethod should not have been called");
    }

    public boolean enqueueMethodForCompilation(Executable method, int compLevel) {
        throw new UnsupportedOperationException("WhiteBox method enqueueMethodForCompilation should not have been called");
    }

    public boolean enqueueMethodForCompilation(Executable method, int compLevel, int entry_bci) {
        throw new UnsupportedOperationException("WhiteBox method enqueueMethodForCompilation should not have been called");
    }

    public boolean enqueueInitializerForCompilation(Class<?> aClass, int compLevel) {
        throw new UnsupportedOperationException("WhiteBox method enqueueInitializerForCompilation should not have been called");
    }

    public void markMethodProfiled(Executable method) {
        throw new UnsupportedOperationException("WhiteBox method markMethodProfiled should not have been called");
    }

    public void clearMethodState(Executable method) {
        throw new UnsupportedOperationException("WhiteBox method clearMethodState should not have been called");
    }

    public void lockCompilation() {
        throw new UnsupportedOperationException("WhiteBox method lockCompilation should not have been called");
    }

    public void unlockCompilation() {
        throw new UnsupportedOperationException("WhiteBox method unlockCompilation should not have been called");
    }

    public int getMethodEntryBci(Executable method) {
        throw new UnsupportedOperationException("WhiteBox method getMethodEntryBci should not have been called");
    }

    public Object[] getNMethod(Executable method, boolean isOsr) {
        throw new UnsupportedOperationException("WhiteBox method getNMethod should not have been called");
    }

    public long allocateCodeBlob(int size, int type) {
        throw new UnsupportedOperationException("WhiteBox method allocateCodeBlob should not have been called");
    }

    public long allocateCodeBlob(long size, int type) {
        throw new UnsupportedOperationException("WhiteBox method allocateCodeBlob should not have been called");
    }

    public void freeCodeBlob(long addr) {
        throw new UnsupportedOperationException("WhiteBox method freeCodeBlob should not have been called");
    }

    public Object[] getCodeHeapEntries(int type) {
        throw new UnsupportedOperationException("WhiteBox method getCodeHeapEntries should not have been called");
    }

    public int getCompilationActivityMode() {
        throw new UnsupportedOperationException("WhiteBox method getCompilationActivityMode should not have been called");
    }

    public long getMethodData(Executable method) {
        throw new UnsupportedOperationException("WhiteBox method getMethodData should not have been called");
    }

    public Object[] getCodeBlob(long addr) {
        throw new UnsupportedOperationException("WhiteBox method getCodeBlob should not have been called");
    }

    public void clearInlineCaches() {
        throw new UnsupportedOperationException("WhiteBox method clearInlineCaches should not have been called");
    }

    public void clearInlineCaches(boolean preserve_static_stubs) {
        throw new UnsupportedOperationException("WhiteBox method clearInlineCaches should not have been called");
    }

    public boolean isInStringTable(String str) {
        throw new UnsupportedOperationException("WhiteBox method isInStringTable should not have been called");
    }

    public void readReservedMemory() {
        throw new UnsupportedOperationException("WhiteBox method readReservedMemory should not have been called");
    }

    public long allocateMetaspace(ClassLoader classLoader, long size) {
        throw new UnsupportedOperationException("WhiteBox method allocateMetaspace should not have been called");
    }

    public long incMetaspaceCapacityUntilGC(long increment) {
        throw new UnsupportedOperationException("WhiteBox method incMetaspaceCapacityUntilGC should not have been called");
    }

    public long metaspaceCapacityUntilGC() {
        throw new UnsupportedOperationException("WhiteBox method metaspaceCapacityUntilGC should not have been called");
    }

    public long metaspaceSharedRegionAlignment() {
        throw new UnsupportedOperationException("WhiteBox method metaspaceSharedRegionAlignment should not have been called");
    }

    public void cleanMetaspaces() {
        throw new UnsupportedOperationException("WhiteBox method cleanMetaspaces should not have been called");
    }

    public long createMetaspaceTestContext(long commit_limit, long reserve_limit) {
        throw new UnsupportedOperationException("WhiteBox method createMetaspaceTestContext should not have been called");
    }

    public void destroyMetaspaceTestContext(long context) {
        throw new UnsupportedOperationException("WhiteBox method destroyMetaspaceTestContext should not have been called");
    }

    public void purgeMetaspaceTestContext(long context) {
        throw new UnsupportedOperationException("WhiteBox method purgeMetaspaceTestContext should not have been called");
    }

    public void printMetaspaceTestContext(long context) {
        throw new UnsupportedOperationException("WhiteBox method printMetaspaceTestContext should not have been called");
    }

    public long getTotalCommittedBytesInMetaspaceTestContext(long context) {
        throw new UnsupportedOperationException("WhiteBox method getTotalCommittedBytesInMetaspaceTestContext should not have been called");
    }

    public long getTotalUsedBytesInMetaspaceTestContext(long context) {
        throw new UnsupportedOperationException("WhiteBox method getTotalUsedBytesInMetaspaceTestContext should not have been called");
    }

    public long createArenaInTestContext(long context, boolean is_micro) {
        throw new UnsupportedOperationException("WhiteBox method createArenaInTestContext should not have been called");
    }

    public void destroyMetaspaceTestArena(long arena) {
        throw new UnsupportedOperationException("WhiteBox method destroyMetaspaceTestArena should not have been called");
    }

    public long allocateFromMetaspaceTestArena(long arena, long size) {
        throw new UnsupportedOperationException("WhiteBox method allocateFromMetaspaceTestArena should not have been called");
    }

    public void deallocateToMetaspaceTestArena(long arena, long p, long size) {
        throw new UnsupportedOperationException("WhiteBox method deallocateToMetaspaceTestArena should not have been called");
    }

    public long maxMetaspaceAllocationSize() {
        throw new UnsupportedOperationException("WhiteBox method maxMetaspaceAllocationSize should not have been called");
    }

    public long wordSize() {
        throw new UnsupportedOperationException("WhiteBox method wordSize should not have been called");
    }

    public long rootChunkWordSize() {
        throw new UnsupportedOperationException("WhiteBox method rootChunkWordSize should not have been called");
    }

    public boolean isGCSupported(int name) {
        throw new UnsupportedOperationException("WhiteBox method isGCSupported should not have been called");
    }

    public boolean isGCSupportedByJVMCICompiler(int name) {
        throw new UnsupportedOperationException("WhiteBox method isGCSupportedByJVMCICompiler should not have been called");
    }

    public boolean isGCSelected(int name) {
        throw new UnsupportedOperationException("WhiteBox method isGCSelected should not have been called");
    }

    public boolean isGCSelectedErgonomically() {
        throw new UnsupportedOperationException("WhiteBox method isGCSelectedErgonomically should not have been called");
    }

    public void youngGC() {
        throw new UnsupportedOperationException("WhiteBox method youngGC should not have been called");
    }

    public void fullGC() {
        throw new UnsupportedOperationException("WhiteBox method fullGC should not have been called");
    }

    public boolean waitForReferenceProcessing() throws InterruptedException {
        throw new UnsupportedOperationException("WhiteBox method waitForReferenceProcessing should not have been called");
    }

    public boolean supportsConcurrentGCBreakpoints() {
        throw new UnsupportedOperationException("WhiteBox method supportsConcurrentGCBreakpoints should not have been called");
    }

    public void concurrentGCAcquireControl() {
        throw new UnsupportedOperationException("WhiteBox method concurrentGCAcquireControl should not have been called");
    }

    public void concurrentGCReleaseControl() {
        throw new UnsupportedOperationException("WhiteBox method concurrentGCReleaseControl should not have been called");
    }

    public void concurrentGCRunToIdle() {
        throw new UnsupportedOperationException("WhiteBox method concurrentGCRunToIdle should not have been called");
    }

    public void concurrentGCRunTo(String breakpoint) {
        throw new UnsupportedOperationException("WhiteBox method concurrentGCRunTo should not have been called");
    }

    public boolean concurrentGCRunTo(String breakpoint, boolean errorIfFail) {
        throw new UnsupportedOperationException("WhiteBox method concurrentGCRunTo should not have been called");
    }

    public int stressVirtualSpaceResize(long reservedSpaceSize, long magnitude, long iterations) {
        throw new UnsupportedOperationException("WhiteBox method stressVirtualSpaceResize should not have been called");
    }

    public void readFromNoaccessArea() {
        throw new UnsupportedOperationException("WhiteBox method readFromNoaccessArea should not have been called");
    }

    public void decodeNKlassAndAccessKlass(int nKlass) {
        throw new UnsupportedOperationException("WhiteBox method decodeNKlassAndAccessKlass should not have been called");
    }

    public long getThreadStackSize() {
        throw new UnsupportedOperationException("WhiteBox method getThreadStackSize should not have been called");
    }

    public long getThreadRemainingStackSize() {
        throw new UnsupportedOperationException("WhiteBox method getThreadRemainingStackSize should not have been called");
    }

    public String getCPUFeatures() {
        throw new UnsupportedOperationException("WhiteBox method getCPUFeatures should not have been called");
    }

    public boolean isConstantVMFlag(String name) {
        throw new UnsupportedOperationException("WhiteBox method isConstantVMFlag should not have been called");
    }

    public boolean isDefaultVMFlag(String name) {
        throw new UnsupportedOperationException("WhiteBox method isDefaultVMFlag should not have been called");
    }

    public boolean isLockedVMFlag(String name) {
        throw new UnsupportedOperationException("WhiteBox method isLockedVMFlag should not have been called");
    }

    public void setBooleanVMFlag(String name, boolean value) {
        throw new UnsupportedOperationException("WhiteBox method setBooleanVMFlag should not have been called");
    }

    public void setIntVMFlag(String name, long value) {
        throw new UnsupportedOperationException("WhiteBox method setIntVMFlag should not have been called");
    }

    public void setUintVMFlag(String name, long value) {
        throw new UnsupportedOperationException("WhiteBox method setUintVMFlag should not have been called");
    }

    public void setIntxVMFlag(String name, long value) {
        throw new UnsupportedOperationException("WhiteBox method setIntxVMFlag should not have been called");
    }

    public void setUintxVMFlag(String name, long value) {
        throw new UnsupportedOperationException("WhiteBox method setUintxVMFlag should not have been called");
    }

    public void setUint64VMFlag(String name, long value) {
        throw new UnsupportedOperationException("WhiteBox method setUint64VMFlag should not have been called");
    }

    public void setSizeTVMFlag(String name, long value) {
        throw new UnsupportedOperationException("WhiteBox method setSizeTVMFlag should not have been called");
    }

    public void setStringVMFlag(String name, String value) {
        throw new UnsupportedOperationException("WhiteBox method setStringVMFlag should not have been called");
    }

    public void setDoubleVMFlag(String name, double value) {
        throw new UnsupportedOperationException("WhiteBox method setDoubleVMFlag should not have been called");
    }

    public Boolean getBooleanVMFlag(String name) {
        throw new UnsupportedOperationException("WhiteBox method getBooleanVMFlag should not have been called");
    }

    public Long getIntVMFlag(String name) {
        throw new UnsupportedOperationException("WhiteBox method getIntVMFlag should not have been called");
    }

    public Long getUintVMFlag(String name) {
        throw new UnsupportedOperationException("WhiteBox method getUintVMFlag should not have been called");
    }

    public Long getIntxVMFlag(String name) {
        throw new UnsupportedOperationException("WhiteBox method getIntxVMFlag should not have been called");
    }

    public Long getUintxVMFlag(String name) {
        throw new UnsupportedOperationException("WhiteBox method getUintxVMFlag should not have been called");
    }

    public Long getUint64VMFlag(String name) {
        throw new UnsupportedOperationException("WhiteBox method getUint64VMFlag should not have been called");
    }

    public Long getSizeTVMFlag(String name) {
        throw new UnsupportedOperationException("WhiteBox method getSizeTVMFlag should not have been called");
    }

    public String getStringVMFlag(String name) {
        throw new UnsupportedOperationException("WhiteBox method getStringVMFlag should not have been called");
    }

    public Double getDoubleVMFlag(String name) {
        throw new UnsupportedOperationException("WhiteBox method getDoubleVMFlag should not have been called");
    }

    public Object getVMFlag(String name) {
        throw new UnsupportedOperationException("WhiteBox method getVMFlag should not have been called");
    }

    public void DefineModule(Object module, boolean is_open, String version, String location, Object[] packages) {
        throw new UnsupportedOperationException("WhiteBox method DefineModule should not have been called");
    }

    public void AddModuleExports(Object from_module, String pkg, Object to_module) {
        throw new UnsupportedOperationException("WhiteBox method AddModuleExports should not have been called");
    }

    public void AddReadsModule(Object from_module, Object source_module) {
        throw new UnsupportedOperationException("WhiteBox method AddReadsModule should not have been called");
    }

    public void AddModuleExportsToAllUnnamed(Object module, String pkg) {
        throw new UnsupportedOperationException("WhiteBox method AddModuleExportsToAllUnnamed should not have been called");
    }

    public void AddModuleExportsToAll(Object module, String pkg) {
        throw new UnsupportedOperationException("WhiteBox method AddModuleExportsToAll should not have been called");
    }

    public int getCDSOffsetForName0(String name) {
        throw new UnsupportedOperationException("WhiteBox method getCDSOffsetForName0 should not have been called");
    }

    public int getCDSOffsetForName(String name) throws Exception {
        throw new UnsupportedOperationException("WhiteBox method getCDSOffsetForName should not have been called");
    }

    public int getCDSConstantForName0(String name) {
        throw new UnsupportedOperationException("WhiteBox method getCDSConstantForName0 should not have been called");
    }

    public int getCDSConstantForName(String name) throws Exception {
        throw new UnsupportedOperationException("WhiteBox method getCDSConstantForName should not have been called");
    }

    public Boolean getMethodBooleanOption(Executable method, String name) {
        throw new UnsupportedOperationException("WhiteBox method getMethodBooleanOption should not have been called");
    }

    public Long getMethodIntxOption(Executable method, String name) {
        throw new UnsupportedOperationException("WhiteBox method getMethodIntxOption should not have been called");
    }

    public Long getMethodUintxOption(Executable method, String name) {
        throw new UnsupportedOperationException("WhiteBox method getMethodUintxOption should not have been called");
    }

    public Double getMethodDoubleOption(Executable method, String name) {
        throw new UnsupportedOperationException("WhiteBox method getMethodDoubleOption should not have been called");
    }

    public String getMethodStringOption(Executable method, String name) {
        throw new UnsupportedOperationException("WhiteBox method getMethodStringOption should not have been called");
    }

    public Object getMethodOption(Executable method, String name) {
        throw new UnsupportedOperationException("WhiteBox method getMethodOption should not have been called");
    }

    public int getCDSGenericHeaderMinVersion() {
        throw new UnsupportedOperationException("WhiteBox method getCDSGenericHeaderMinVersion should not have been called");
    }

    public int getCurrentCDSVersion() {
        throw new UnsupportedOperationException("WhiteBox method getCurrentCDSVersion should not have been called");
    }

    public String getDefaultArchivePath() {
        throw new UnsupportedOperationException("WhiteBox method getDefaultArchivePath should not have been called");
    }

    public boolean cdsMemoryMappingFailed() {
        throw new UnsupportedOperationException("WhiteBox method cdsMemoryMappingFailed should not have been called");
    }

    public boolean isSharingEnabled() {
        throw new UnsupportedOperationException("WhiteBox method isSharingEnabled should not have been called");
    }

    public boolean isSharedClass(Class<?> c) {
        throw new UnsupportedOperationException("WhiteBox method isSharedClass should not have been called");
    }

    public boolean areSharedStringsMapped() {
        throw new UnsupportedOperationException("WhiteBox method areSharedStringsMapped should not have been called");
    }

    public boolean isSharedInternedString(String s) {
        throw new UnsupportedOperationException("WhiteBox method isSharedInternedString should not have been called");
    }

    public boolean isCDSIncluded() {
        throw new UnsupportedOperationException("WhiteBox method isCDSIncluded should not have been called");
    }

    public boolean isJFRIncluded() {
        throw new UnsupportedOperationException("WhiteBox method isJFRIncluded should not have been called");
    }

    public boolean isDTraceIncluded() {
        throw new UnsupportedOperationException("WhiteBox method isDTraceIncluded should not have been called");
    }

    public boolean canWriteJavaHeapArchive() {
        throw new UnsupportedOperationException("WhiteBox method canWriteJavaHeapArchive should not have been called");
    }

    public void linkClass(Class<?> c) {
        throw new UnsupportedOperationException("WhiteBox method linkClass should not have been called");
    }

    public boolean areOpenArchiveHeapObjectsMapped() {
        throw new UnsupportedOperationException("WhiteBox method areOpenArchiveHeapObjectsMapped should not have been called");
    }

    public int addCompilerDirective(String compDirect) {
        throw new UnsupportedOperationException("WhiteBox method addCompilerDirective should not have been called");
    }

    public void removeCompilerDirective(int count) {
        throw new UnsupportedOperationException("WhiteBox method removeCompilerDirective should not have been called");
    }

    public int handshakeWalkStack(Thread t, boolean all_threads) {
        throw new UnsupportedOperationException("WhiteBox method handshakeWalkStack should not have been called");
    }

    public boolean handshakeReadMonitors(Thread t) {
        throw new UnsupportedOperationException("WhiteBox method handshakeReadMonitors should not have been called");
    }

    public void asyncHandshakeWalkStack(Thread t) {
        throw new UnsupportedOperationException("WhiteBox method asyncHandshakeWalkStack should not have been called");
    }

    public void lockAndBlock(boolean suspender) {
        throw new UnsupportedOperationException("WhiteBox method lockAndBlock should not have been called");
    }

    public boolean checkLibSpecifiesNoexecstack(String libfilename) {
        throw new UnsupportedOperationException("WhiteBox method checkLibSpecifiesNoexecstack should not have been called");
    }

    public boolean isContainerized() {
        throw new UnsupportedOperationException("WhiteBox method isContainerized should not have been called");
    }

    public int validateCgroup(boolean cgroupsV2Enabled, String controllersFile, String procSelfCgroup, String procSelfMountinfo) {
        throw new UnsupportedOperationException("WhiteBox method validateCgroup should not have been called");
    }

    public void printOsInfo() {
        throw new UnsupportedOperationException("WhiteBox method printOsInfo should not have been called");
    }

    public long hostPhysicalMemory() {
        throw new UnsupportedOperationException("WhiteBox method hostPhysicalMemory should not have been called");
    }

    public long hostAvailableMemory() {
        throw new UnsupportedOperationException("WhiteBox method hostAvailableMemory should not have been called");
    }

    public long hostPhysicalSwap() {
        throw new UnsupportedOperationException("WhiteBox method hostPhysicalSwap should not have been called");
    }

    public int hostCPUs() {
        throw new UnsupportedOperationException("WhiteBox method hostCPUs should not have been called");
    }

    public void disableElfSectionCache() {
        throw new UnsupportedOperationException("WhiteBox method disableElfSectionCache should not have been called");
    }

    public long resolvedMethodItemsCount() {
        throw new UnsupportedOperationException("WhiteBox method resolvedMethodItemsCount should not have been called");
    }

    public int getKlassMetadataSize(Class<?> c) {
        throw new UnsupportedOperationException("WhiteBox method getKlassMetadataSize should not have been called");
    }

    public void checkThreadObjOfTerminatingThread(Thread target) {
        throw new UnsupportedOperationException("WhiteBox method checkThreadObjOfTerminatingThread should not have been called");
    }

    public String getLibcName() {
        throw new UnsupportedOperationException("WhiteBox method getLibcName should not have been called");
    }

    public void verifyFrames(boolean log, boolean updateRegisterMap) {
        throw new UnsupportedOperationException("WhiteBox method verifyFrames should not have been called");
    }

    public boolean isJVMTIIncluded() {
        throw new UnsupportedOperationException("WhiteBox method isJVMTIIncluded should not have been called");
    }

    public void waitUnsafe(int time_ms) {
        throw new UnsupportedOperationException("WhiteBox method waitUnsafe should not have been called");
    }

    public void busyWaitCPUTime(int cpuTimeMs) {
        throw new UnsupportedOperationException("WhiteBox method busyWaitCPUTime should not have been called");
    }

    public boolean cpuSamplerSetOutOfStackWalking(boolean enable) {
        throw new UnsupportedOperationException("WhiteBox method cpuSamplerSetOutOfStackWalking should not have been called");
    }

    public void pinObject(Object o) {
        throw new UnsupportedOperationException("WhiteBox method pinObject should not have been called");
    }

    public void unpinObject(Object o) {
        throw new UnsupportedOperationException("WhiteBox method unpinObject should not have been called");
    }

    public boolean setVirtualThreadsNotifyJvmtiMode(boolean enabled) {
        throw new UnsupportedOperationException("WhiteBox method setVirtualThreadsNotifyJvmtiMode should not have been called");
    }

    public void preTouchMemory(long addr, long size) {
        throw new UnsupportedOperationException("WhiteBox method preTouchMemory should not have been called");
    }

    public long rss() {
        throw new UnsupportedOperationException("WhiteBox method rss should not have been called");
    }

    public boolean isStatic() {
        throw new UnsupportedOperationException("WhiteBox method isStatic should not have been called");
    }

}
// Checkstyle: resume
