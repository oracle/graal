/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @library /test/lib
 * @summary Exercises safe public WhiteBox API methods supplied by the runtime test image.
 * @build jdk.test.whitebox.WhiteBox
 * @modules java.base/java.lang.ref:open
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. jdk.graal.compiler.test.WhiteBoxTest
 */

package jdk.graal.compiler.test;

import java.lang.reflect.Executable;
import java.util.function.Supplier;

import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.parser.DiagnosticCommand;

/// Exercises the public `WhiteBox` API methods that can be called safely by the runtime test image.
/// This is only a basic smoke test that linking a WhiteBox native method either succeeds or
/// raises an [UnsatisfiedLinkError] in an expected format.
///
/// This is a JDK test that can be run as follows in the compiler suite:
/// ```
/// mx -p ../vm --env crema-ce run-jdk-test /path/to/labsjdk-ce-repo \
///     src/jdk.graal.compiler.test/src/jdk/graal/compiler/test/WhiteBoxTest.java \
///     --java-option=-DWhiteBoxTest.verbose=true
/// ```
public class WhiteBoxTest {

    /// Enables logging of each `WhiteBox` call when the corresponding system property is set.
    private static final boolean VERBOSE = Boolean.getBoolean("WhiteBoxTest.verbose");

    /// Invokes the public `WhiteBox` methods with representative arguments.
    public static void main(String[] args) {
        WhiteBox whiteBox = WhiteBox.getWhiteBox();
        Object sampleObject = new Object();
        // Use a real executable to exercise compilation-related APIs.
        Executable sampleMethod;
        try {
            sampleMethod = WhiteBoxTest.class.getDeclaredMethod("main", String[].class);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }

        call("getCompressedOopsMaxHeapSize", whiteBox::getCompressedOopsMaxHeapSize);
        callVoid("printHeapSizes", whiteBox::printHeapSizes);
        call("getObjectAddress", () -> whiteBox.getObjectAddress(sampleObject));
        call("getHeapOopSize", whiteBox::getHeapOopSize);
        call("getVMPageSize", whiteBox::getVMPageSize);
        call("getVMAllocationGranularity", whiteBox::getVMAllocationGranularity);
        call("getVMLargePageSize", whiteBox::getVMLargePageSize);
        call("getHeapSpaceAlignment", whiteBox::getHeapSpaceAlignment);
        call("getHeapAlignment", whiteBox::getHeapAlignment);
        call("getMinimumJavaStackSize", whiteBox::getMinimumJavaStackSize);
        call("hasExternalSymbolsStripped", whiteBox::hasExternalSymbolsStripped);
        call("isObjectInOldGen", () -> whiteBox.isObjectInOldGen(sampleObject));
        call("getObjectSize", () -> whiteBox.getObjectSize(sampleObject));
        call("printString", () -> whiteBox.printString("WhiteBoxTest", 64));
        call("countAliveClasses", () -> whiteBox.countAliveClasses("jdk.graal.compiler.test.WhiteBoxTest"));
        call("isClassAlive", () -> whiteBox.isClassAlive("jdk.graal.compiler.test.WhiteBoxTest"));
        call("getSymbolRefcount", () -> whiteBox.getSymbolRefcount("WhiteBoxTest"));
        call("deflateIdleMonitors", whiteBox::deflateIdleMonitors);
        call("isMonitorInflated", () -> whiteBox.isMonitorInflated(sampleObject));
        call("getInUseMonitorCount", whiteBox::getInUseMonitorCount);
        call("getLockStackCapacity", whiteBox::getLockStackCapacity);
        call("supportsRecursiveLightweightLocking", whiteBox::supportsRecursiveLightweightLocking);
        callVoid("forceSafepoint", whiteBox::forceSafepoint);
        callVoid("forceClassLoaderStatsSafepoint", whiteBox::forceClassLoaderStatsSafepoint);
        call("getConstantPool", () -> whiteBox.getConstantPool(WhiteBoxTest.class));
        call("getResolvedReferences", () -> whiteBox.getResolvedReferences(WhiteBoxTest.class));
        call("encodeConstantPoolIndyIndex", () -> whiteBox.encodeConstantPoolIndyIndex(0));
        call("printClasses", () -> whiteBox.printClasses(".*WhiteBoxTest.*", 0));
        call("printMethods", () -> whiteBox.printMethods(".*WhiteBoxTest.*", ".*", 0));
        call("remapInstructionOperandFromCPCache", () -> whiteBox.remapInstructionOperandFromCPCache(WhiteBoxTest.class, 0));
        call("getFieldEntriesLength", () -> whiteBox.getFieldEntriesLength(WhiteBoxTest.class));
        call("getFieldCPIndex", () -> whiteBox.getFieldCPIndex(WhiteBoxTest.class, 0));
        call("getMethodEntriesLength", () -> whiteBox.getMethodEntriesLength(WhiteBoxTest.class));
        call("getMethodCPIndex", () -> whiteBox.getMethodCPIndex(WhiteBoxTest.class, 0));
        call("getIndyInfoLength", () -> whiteBox.getIndyInfoLength(WhiteBoxTest.class));
        call("getIndyCPIndex", () -> whiteBox.getIndyCPIndex(WhiteBoxTest.class, 0));
        callVoid("addToBootstrapClassLoaderSearch", () -> whiteBox.addToBootstrapClassLoaderSearch(""));
        callVoid("addToSystemClassLoaderSearch", () -> whiteBox.addToSystemClassLoaderSearch(""));

        call("g1InConcurrentMark", whiteBox::g1InConcurrentMark);
        call("g1CompletedConcurrentMarkCycles", whiteBox::g1CompletedConcurrentMarkCycles);
        call("g1HasRegionsToUncommit", whiteBox::g1HasRegionsToUncommit);
        call("g1IsHumongous", () -> whiteBox.g1IsHumongous(sampleObject));
        call("g1BelongsToHumongousRegion", () -> whiteBox.g1BelongsToHumongousRegion(1000));
        call("g1BelongsToFreeRegion", () -> whiteBox.g1BelongsToFreeRegion(1000));
        call("g1NumMaxRegions", whiteBox::g1NumMaxRegions);
        call("g1NumFreeRegions", whiteBox::g1NumFreeRegions);
        call("g1RegionSize", whiteBox::g1RegionSize);
        call("g1AuxiliaryMemoryUsage", whiteBox::g1AuxiliaryMemoryUsage);
        call("parseCommandLine", () -> whiteBox.parseCommandLine("", ',', new DiagnosticCommand[0]));
        call("g1ActiveMemoryNodeCount", whiteBox::g1ActiveMemoryNodeCount);
        call("g1MemoryNodeIds", whiteBox::g1MemoryNodeIds);
        call("psVirtualSpaceAlignment", whiteBox::psVirtualSpaceAlignment);
        call("psHeapGenerationAlignment", whiteBox::psHeapGenerationAlignment);
        call("g1GetMixedGCInfo", () -> whiteBox.g1GetMixedGCInfo(0));
        call("g1RunConcurrentGC(boolean)", () -> whiteBox.g1RunConcurrentGC(false));
        callVoid("g1RunConcurrentGC", whiteBox::g1RunConcurrentGC);
        callVoid("g1StartConcurrentGC", whiteBox::g1StartConcurrentGC);
        call("NMTMalloc", () -> {
            long address = whiteBox.NMTMalloc(1);
            if (address != 0) {
                whiteBox.NMTFree(address);
            }
            return address;
        });
        callVoid("NMTFree", () -> whiteBox.NMTFree(0));
        call("NMTReserveMemory", () -> whiteBox.NMTReserveMemory(0));
        call("NMTAttemptReserveMemoryAt", () -> whiteBox.NMTAttemptReserveMemoryAt(0, 0));
        callVoid("NMTCommitMemory", () -> whiteBox.NMTCommitMemory(0, 0));
        callVoid("NMTUncommitMemory", () -> whiteBox.NMTUncommitMemory(0, 0));
        callVoid("NMTReleaseMemory", () -> whiteBox.NMTReleaseMemory(0, 0));
        call("NMTMallocWithPseudoStack", () -> whiteBox.NMTMallocWithPseudoStack(0, 0));
        call("NMTMallocWithPseudoStackAndType", () -> whiteBox.NMTMallocWithPseudoStackAndType(0, 0, 0));
        call("NMTGetHashSize", whiteBox::NMTGetHashSize);
        call("NMTNewArena", () -> whiteBox.NMTNewArena(0));
        callVoid("NMTFreeArena", () -> whiteBox.NMTFreeArena(0));
        callVoid("NMTArenaMalloc", () -> whiteBox.NMTArenaMalloc(0, 0));

        call("matchesMethod", () -> whiteBox.matchesMethod(sampleMethod, ".*"));
        call("matchesInline", () -> whiteBox.matchesInline(sampleMethod, ".*"));
        call("shouldPrintAssembly", () -> whiteBox.shouldPrintAssembly(sampleMethod, 0));
        call("deoptimizeFrames", () -> whiteBox.deoptimizeFrames(false));
        call("isFrameDeoptimized", () -> whiteBox.isFrameDeoptimized(0));
        callVoid("deoptimizeAll", whiteBox::deoptimizeAll);
        call("isMethodCompiled", () -> whiteBox.isMethodCompiled(sampleMethod));
        call("isMethodCompiled(boolean)", () -> whiteBox.isMethodCompiled(sampleMethod, false));
        call("isMethodCompilable", () -> whiteBox.isMethodCompilable(sampleMethod));
        call("isMethodCompilable(int)", () -> whiteBox.isMethodCompilable(sampleMethod, 1));
        call("isMethodCompilable(int, boolean)", () -> whiteBox.isMethodCompilable(sampleMethod, 1, false));
        call("isMethodQueuedForCompilation", () -> whiteBox.isMethodQueuedForCompilation(sampleMethod));
        call("isIntrinsicAvailable", () -> whiteBox.isIntrinsicAvailable(sampleMethod, sampleMethod, 1));
        call("isIntrinsicAvailable(int)", () -> whiteBox.isIntrinsicAvailable(sampleMethod, 1));
        call("deoptimizeMethod", () -> whiteBox.deoptimizeMethod(sampleMethod));
        call("deoptimizeMethod(boolean)", () -> whiteBox.deoptimizeMethod(sampleMethod, false));
        callVoid("makeMethodNotCompilable", () -> whiteBox.makeMethodNotCompilable(sampleMethod));
        callVoid("makeMethodNotCompilable(int)", () -> whiteBox.makeMethodNotCompilable(sampleMethod, 1));
        callVoid("makeMethodNotCompilable(int, boolean)", () -> whiteBox.makeMethodNotCompilable(sampleMethod, 1, false));
        call("getMethodCompilationLevel", () -> whiteBox.getMethodCompilationLevel(sampleMethod));
        call("getMethodCompilationLevel(boolean)", () -> whiteBox.getMethodCompilationLevel(sampleMethod, false));
        call("getMethodDecompileCount", () -> whiteBox.getMethodDecompileCount(sampleMethod));
        call("getMethodTrapCount", () -> whiteBox.getMethodTrapCount(sampleMethod));
        call("getMethodTrapCount(String)", () -> whiteBox.getMethodTrapCount(sampleMethod, ""));
        call("getDeoptCount", whiteBox::getDeoptCount);
        call("getDeoptCount(String, String)", () -> whiteBox.getDeoptCount("", ""));
        call("testSetDontInlineMethod", () -> whiteBox.testSetDontInlineMethod(sampleMethod, false));
        call("testSetForceInlineMethod", () -> whiteBox.testSetForceInlineMethod(sampleMethod, false));
        call("enqueueMethodForCompilation", () -> whiteBox.enqueueMethodForCompilation(sampleMethod, 1));
        call("enqueueMethodForCompilation(int, int)", () -> whiteBox.enqueueMethodForCompilation(sampleMethod, 1, 0));
        call("enqueueInitializerForCompilation", () -> whiteBox.enqueueInitializerForCompilation(WhiteBoxTest.class, 1));
        callVoid("markMethodProfiled", () -> whiteBox.markMethodProfiled(sampleMethod));
        callVoid("clearMethodState", () -> whiteBox.clearMethodState(sampleMethod));
        callVoid("lockCompilation", whiteBox::lockCompilation);
        callVoid("unlockCompilation", whiteBox::unlockCompilation);
        call("getMethodEntryBci", () -> whiteBox.getMethodEntryBci(sampleMethod));
        call("getNMethod", () -> whiteBox.getNMethod(sampleMethod, false));
        call("allocateCodeBlob(int)", () -> whiteBox.allocateCodeBlob(0, 0));
        call("allocateCodeBlob(long)", () -> whiteBox.allocateCodeBlob(0L, 0));
        callVoid("freeCodeBlob", () -> whiteBox.freeCodeBlob(0));
        callVoid("clearInlineCaches", whiteBox::clearInlineCaches);
        callVoid("clearInlineCaches(boolean)", () -> whiteBox.clearInlineCaches(false));
        callVoid("readReservedMemory", whiteBox::readReservedMemory);
        call("allocateMetaspace", () -> whiteBox.allocateMetaspace(null, 0));
        callVoid("cleanMetaspaces", whiteBox::cleanMetaspaces);
        call("createMetaspaceTestContext", () -> whiteBox.createMetaspaceTestContext(0, 0));
        callVoid("destroyMetaspaceTestContext", () -> whiteBox.destroyMetaspaceTestContext(0));
        callVoid("purgeMetaspaceTestContext", () -> whiteBox.purgeMetaspaceTestContext(0));
        callVoid("printMetaspaceTestContext", () -> whiteBox.printMetaspaceTestContext(0));
        call("getTotalCommittedBytesInMetaspaceTestContext", () -> whiteBox.getTotalCommittedBytesInMetaspaceTestContext(0));
        call("getTotalUsedBytesInMetaspaceTestContext", () -> whiteBox.getTotalUsedBytesInMetaspaceTestContext(0));
        call("createArenaInTestContext", () -> whiteBox.createArenaInTestContext(0, false));
        callVoid("destroyMetaspaceTestArena", () -> whiteBox.destroyMetaspaceTestArena(0));
        call("allocateFromMetaspaceTestArena", () -> whiteBox.allocateFromMetaspaceTestArena(0, 0));
        callVoid("deallocateToMetaspaceTestArena", () -> whiteBox.deallocateToMetaspaceTestArena(0, 0, 0));

        call("isAsanEnabled", whiteBox::isAsanEnabled);
        call("isUbsanEnabled", whiteBox::isUbsanEnabled);
        call("hasLibgraal", whiteBox::hasLibgraal);
        call("isC2OrJVMCIIncluded", whiteBox::isC2OrJVMCIIncluded);
        call("isJVMCISupportedByGC", whiteBox::isJVMCISupportedByGC);
        call("getCompileQueuesSize", whiteBox::getCompileQueuesSize);
        call("getCompileQueueSize", () -> whiteBox.getCompileQueueSize(0));
        call("getCompilationActivityMode", whiteBox::getCompilationActivityMode);
        call("getCodeHeapEntries", () -> whiteBox.getCodeHeapEntries(0));
        call("getMethodData", () -> whiteBox.getMethodData(sampleMethod));
        call("getCodeBlob", () -> whiteBox.getCodeBlob(0));
        call("isInStringTable", () -> whiteBox.isInStringTable("WhiteBoxTest"));
        call("incMetaspaceCapacityUntilGC", () -> whiteBox.incMetaspaceCapacityUntilGC(0));
        call("metaspaceCapacityUntilGC", whiteBox::metaspaceCapacityUntilGC);
        call("metaspaceSharedRegionAlignment", whiteBox::metaspaceSharedRegionAlignment);
        call("maxMetaspaceAllocationSize", whiteBox::maxMetaspaceAllocationSize);
        call("wordSize", whiteBox::wordSize);
        call("rootChunkWordSize", whiteBox::rootChunkWordSize);
        call("isGCSupported", () -> whiteBox.isGCSupported(0));
        call("isGCSupportedByJVMCICompiler", () -> whiteBox.isGCSupportedByJVMCICompiler(0));
        call("isGCSelected", () -> whiteBox.isGCSelected(0));
        call("isGCSelectedErgonomically", whiteBox::isGCSelectedErgonomically);
        call("supportsConcurrentGCBreakpoints", whiteBox::supportsConcurrentGCBreakpoints);
        callVoid("youngGC", whiteBox::youngGC);
        callVoid("fullGC", whiteBox::fullGC);
        call("waitForReferenceProcessing", () -> {
            try {
                return whiteBox.waitForReferenceProcessing();
            } catch (InterruptedException exception) {
                throw new RuntimeException(exception);
            }
        });
        callVoid("concurrentGCAcquireControl", whiteBox::concurrentGCAcquireControl);
        callVoid("concurrentGCReleaseControl", whiteBox::concurrentGCReleaseControl);
        callVoid("concurrentGCRunToIdle", whiteBox::concurrentGCRunToIdle);
        callVoid("concurrentGCRunTo", () -> whiteBox.concurrentGCRunTo(""));
        call("concurrentGCRunTo(boolean)", () -> whiteBox.concurrentGCRunTo("", false));
        call("stressVirtualSpaceResize", () -> whiteBox.stressVirtualSpaceResize(0, 0, 0));
        callVoid("decodeNKlassAndAccessKlass", () -> whiteBox.decodeNKlassAndAccessKlass(0));

        call("getCPUFeatures", whiteBox::getCPUFeatures);
        call("isConstantVMFlag", () -> whiteBox.isConstantVMFlag("UseCompressedOops"));
        call("isDefaultVMFlag", () -> whiteBox.isDefaultVMFlag("UseCompressedOops"));
        call("isLockedVMFlag", () -> whiteBox.isLockedVMFlag("UseCompressedOops"));
        call("getBooleanVMFlag", () -> whiteBox.getBooleanVMFlag("UseCompressedOops"));
        call("getIntVMFlag", () -> whiteBox.getIntVMFlag("ActiveProcessorCount"));
        call("getUintVMFlag", () -> whiteBox.getUintVMFlag("ActiveProcessorCount"));
        call("getIntxVMFlag", () -> whiteBox.getIntxVMFlag("ActiveProcessorCount"));
        call("getUintxVMFlag", () -> whiteBox.getUintxVMFlag("ActiveProcessorCount"));
        call("getUint64VMFlag", () -> whiteBox.getUint64VMFlag("ActiveProcessorCount"));
        call("getSizeTVMFlag", () -> whiteBox.getSizeTVMFlag("ThreadStackSize"));
        call("getStringVMFlag", () -> whiteBox.getStringVMFlag("UseCompressedOops"));
        call("getDoubleVMFlag", () -> whiteBox.getDoubleVMFlag("ObjectAlignmentInBytes"));
        call("getVMFlag", () -> whiteBox.getVMFlag("UseCompressedOops"));
        callVoid("setBooleanVMFlag", () -> whiteBox.setBooleanVMFlag("UseCompressedOops", false));
        callVoid("setIntVMFlag", () -> whiteBox.setIntVMFlag("ActiveProcessorCount", 1));
        callVoid("setUintVMFlag", () -> whiteBox.setUintVMFlag("ActiveProcessorCount", 1));
        callVoid("setIntxVMFlag", () -> whiteBox.setIntxVMFlag("ActiveProcessorCount", 1));
        callVoid("setUintxVMFlag", () -> whiteBox.setUintxVMFlag("ActiveProcessorCount", 1));
        callVoid("setUint64VMFlag", () -> whiteBox.setUint64VMFlag("ActiveProcessorCount", 1));
        callVoid("setSizeTVMFlag", () -> whiteBox.setSizeTVMFlag("ThreadStackSize", 0));
        callVoid("setStringVMFlag", () -> whiteBox.setStringVMFlag("UseCompressedOops", ""));
        callVoid("setDoubleVMFlag", () -> whiteBox.setDoubleVMFlag("ObjectAlignmentInBytes", 8));
        callVoid("DefineModule", () -> whiteBox.DefineModule(null, false, null, null, new Object[0]));
        callVoid("AddModuleExports", () -> whiteBox.AddModuleExports(null, "", null));
        callVoid("AddReadsModule", () -> whiteBox.AddReadsModule(null, null));
        callVoid("AddModuleExportsToAllUnnamed", () -> whiteBox.AddModuleExportsToAllUnnamed(null, ""));
        callVoid("AddModuleExportsToAll", () -> whiteBox.AddModuleExportsToAll(null, ""));

        call("getCDSGenericHeaderMinVersion", whiteBox::getCDSGenericHeaderMinVersion);
        call("getCurrentCDSVersion", whiteBox::getCurrentCDSVersion);
        call("getDefaultArchivePath", whiteBox::getDefaultArchivePath);
        call("cdsMemoryMappingFailed", whiteBox::cdsMemoryMappingFailed);
        call("isSharingEnabled", whiteBox::isSharingEnabled);
        call("isSharedClass", () -> whiteBox.isSharedClass(WhiteBoxTest.class));
        call("areSharedStringsMapped", whiteBox::areSharedStringsMapped);
        call("isSharedInternedString", () -> whiteBox.isSharedInternedString("WhiteBoxTest"));
        call("isCDSIncluded", whiteBox::isCDSIncluded);
        call("isJFRIncluded", whiteBox::isJFRIncluded);
        call("isDTraceIncluded", whiteBox::isDTraceIncluded);
        call("canWriteJavaHeapArchive", whiteBox::canWriteJavaHeapArchive);
        callVoid("linkClass", () -> whiteBox.linkClass(WhiteBoxTest.class));
        call("getCDSOffsetForName0", () -> whiteBox.getCDSOffsetForName0(""));
        call("getCDSOffsetForName", () -> {
            try {
                return whiteBox.getCDSOffsetForName("");
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
        call("getCDSConstantForName0", () -> whiteBox.getCDSConstantForName0(""));
        call("getCDSConstantForName", () -> {
            try {
                return whiteBox.getCDSConstantForName("");
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
        call("getMethodBooleanOption", () -> whiteBox.getMethodBooleanOption(sampleMethod, ""));
        call("getMethodIntxOption", () -> whiteBox.getMethodIntxOption(sampleMethod, ""));
        call("getMethodUintxOption", () -> whiteBox.getMethodUintxOption(sampleMethod, ""));
        call("getMethodDoubleOption", () -> whiteBox.getMethodDoubleOption(sampleMethod, ""));
        call("getMethodStringOption", () -> whiteBox.getMethodStringOption(sampleMethod, ""));
        call("getMethodOption", () -> whiteBox.getMethodOption(sampleMethod, ""));
        call("areOpenArchiveHeapObjectsMapped", whiteBox::areOpenArchiveHeapObjectsMapped);
        call("isContainerized", whiteBox::isContainerized);
        call("hostPhysicalMemory", whiteBox::hostPhysicalMemory);
        call("hostAvailableMemory", whiteBox::hostAvailableMemory);
        call("hostPhysicalSwap", whiteBox::hostPhysicalSwap);
        call("hostCPUs", whiteBox::hostCPUs);
        call("resolvedMethodItemsCount", whiteBox::resolvedMethodItemsCount);
        call("getKlassMetadataSize", () -> whiteBox.getKlassMetadataSize(WhiteBoxTest.class));
        call("getLibcName", whiteBox::getLibcName);
        call("isJVMTIIncluded", whiteBox::isJVMTIIncluded);
        call("rss", whiteBox::rss);
        call("isStatic", whiteBox::isStatic);
        call("getThreadStackSize", whiteBox::getThreadStackSize);
        call("getThreadRemainingStackSize", whiteBox::getThreadRemainingStackSize);
        call("addCompilerDirective", () -> whiteBox.addCompilerDirective(""));
        callVoid("removeCompilerDirective", () -> whiteBox.removeCompilerDirective(0));
        call("handshakeWalkStack", () -> whiteBox.handshakeWalkStack(Thread.currentThread(), false));
        call("handshakeReadMonitors", () -> whiteBox.handshakeReadMonitors(Thread.currentThread()));
        callVoid("asyncHandshakeWalkStack", () -> whiteBox.asyncHandshakeWalkStack(Thread.currentThread()));
        call("checkLibSpecifiesNoexecstack", () -> whiteBox.checkLibSpecifiesNoexecstack(""));
        call("validateCgroup", () -> whiteBox.validateCgroup(false, "", "", ""));
        callVoid("printOsInfo", whiteBox::printOsInfo);
        callVoid("disableElfSectionCache", whiteBox::disableElfSectionCache);
        callVoid("checkThreadObjOfTerminatingThread", () -> whiteBox.checkThreadObjOfTerminatingThread(Thread.currentThread()));
        callVoid("verifyFrames", () -> whiteBox.verifyFrames(false, false));
        callVoid("waitUnsafe", () -> whiteBox.waitUnsafe(0));
        callVoid("busyWaitCPUTime", () -> whiteBox.busyWaitCPUTime(0));
        call("cpuSamplerSetOutOfStackWalking", () -> whiteBox.cpuSamplerSetOutOfStackWalking(false));
        callVoid("pinObject", () -> whiteBox.pinObject(sampleObject));
        callVoid("unpinObject", () -> whiteBox.unpinObject(sampleObject));
        call("setVirtualThreadsNotifyJvmtiMode", () -> whiteBox.setVirtualThreadsNotifyJvmtiMode(false));
        callVoid("preTouchMemory", () -> whiteBox.preTouchMemory(0, 0));
    }

    /// Invokes an API method and records unsupported native entry points without aborting the test.
    ///
    /// @param name identifies the API method in diagnostic output
    /// @param action supplies the API invocation
    private static void call(String name, Supplier<?> action) {
        try {
            log(name, action.get());
        } catch (UnsatisfiedLinkError e) {
            if (!e.getMessage().contains(" [symbol:")) {
                // The message comes from JNINativeLinkage.toString()
                throw new AssertionError("Does not look like a crema UnsatisfiedLinkError", e);
            }
            log(name, e);
        }
    }

    /// Adapts a void API method to the common invocation path.
    ///
    /// @param name identifies the API method in diagnostic output
    /// @param action supplies the API invocation
    private static void callVoid(String name, Runnable action) {
        call(name, () -> {
            action.run();
            return null;
        });
    }

    /// Logs an API result when verbose output is enabled.
    ///
    /// @param name identifies the API method in diagnostic output
    /// @param result contains the returned value or failure
    private static void log(String name, Object result) {
        if (VERBOSE) {
            System.out.println("WhiteBox." + name + " -> " + result);
        }
    }
}
