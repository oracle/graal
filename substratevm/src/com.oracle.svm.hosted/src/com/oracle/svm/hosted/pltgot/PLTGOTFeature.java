/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.pltgot;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;

import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.pltgot.GOTAccess;
import com.oracle.svm.core.pltgot.GOTHeapSupport;
import com.oracle.svm.core.pltgot.PLTGOTConfiguration;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.image.MethodPointerRelocationProvider;
import com.oracle.svm.hosted.image.RelocatableBuffer;
import com.oracle.svm.hosted.pltgot.aarch64.AArch64HostedPLTGOTConfiguration;
import com.oracle.svm.hosted.pltgot.amd64.AMD64HostedPLTGOTConfiguration;

import jdk.graal.compiler.util.json.JsonWriter;

/**
 * Introduces the PLT and GOT mechanism to native calls that allows resolving the target method's
 * address at runtime.
 *
 * To create a custom method address resolver, a user needs to:
 * <ol>
 * <li>Implement the {@link MethodAddressResolutionSupport} resolver support class.</li>
 * <li>Create a feature that enables the {@link PLTGOTOptions#EnablePLTGOT} option and registers
 * their resolver in the {@link ImageSingletons}.</li>
 * </ol>
 * For an example resolver, see {@link com.oracle.svm.core.pltgot.IdentityMethodAddressResolver}.
 *
 * From an implementation point of view, this feature and the supporting classes implement
 * <a href="https://maskray.me/blog/2021-09-19-all-about-procedure-linkage-table">the PLT (Procedure
 * Linkage Table) and GOT (Global Offset Table)</a> mechanism used by the Linux dynamic linker to
 * enable lazy binding.
 *
 * Native Image constructs the GOT during the image build by assigning each eligible method an entry
 * into the GOT. At runtime, the GOT is represented as an array of pointers mapped right before the
 * image heap. Multiple isolates all see the same GOT. Direct calls of eligible methods are replaced
 * with indirect calls that load the method's address from the GOT.
 *
 * For each eligible method, Native Image constructs a PLT stub. Initially, each GOT entry points to
 * the PLT stub of the method assigned to that GOT entry. Vtable entries of eligible methods also
 * point to the PLT stub.
 *
 * There are two kinds of calls on the native level:
 * <ol>
 * <li>Direct calls</li>
 * <li>Indirect calls</li>
 * </ol>
 * Native image emits direct calls as IP relative calls. If a method is directly called and requires
 * the PLT/GOT, these calls are instead emitted as indirect calls where the address of the method to
 * call is located at HEAP_BASE_REGISTER - (METHOD_GOT_ENTRY_NO * WORD_SIZE). Indirect calls, used
 * for virtual calls, are unchanged. For virtual calls, each vtable entry is rewritten to point to
 * the PLT stub associated with the method we are calling, and it will never change during program
 * execution. As a consequence, virtual calls for methods that are resolved with the PLT/GOT
 * mechanism are doubly indirected. Also, we only rewrite the vtable entries for the methods that
 * are called through the PLT/GOT mechanism.
 *
 * A couple of implementation notes:
 * <ul>
 * <li>The GOT is always mapped relative to the image heap of an isolate and is backed by the same
 * memory across isolates. This means that modifications made to the GOT by one isolate are visible
 * to all other isolates. This mapping is necessary to avoid relocations in the code (we don't want
 * to patch the code at runtime).</li>
 * <li>Virtual calls now have two levels of indirection instead of one. The vtables contain
 * addresses of PLT stubs corresponding to the virtual methods. A PLT stub either resolves the
 * address of the actual method and writes it to the GOT, or it reads the previously resolved method
 * address from the GOT, before finally jumping into the target method. The vtables themselves are
 * never modified so that the relocatable section of the image heap can remain read-only.</li>
 *
 * If the PLT/GOT mechanism is used for all eligible methods, the additional indirections that are
 * now present in calls can result in a slowdown that ranges from just a few percent to up to 20%
 * depending on the workload for the default configuration.
 * </ul>
 */
public class PLTGOTFeature implements InternalFeature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return PLTGOTOptions.EnablePLTGOT.getValue();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        VMError.guarantee(Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class) || Platform.includedIn(Platform.WINDOWS.class),
                        "PLT and GOT is currently only supported on Linux, Darwin and Windows.");
        VMError.guarantee(Platform.includedIn(Platform.AARCH64.class) || Platform.includedIn(Platform.AMD64.class), "PLT and GOT is currently only supported on AArch64 and AMD64.");
        VMError.guarantee(!RuntimeCompilation.isEnabled(), "PLT and GOT is currently not supported with runtime compilation.");
        VMError.guarantee(SubstrateOptions.SpawnIsolates.getValue(), "PLT and GOT cannot work without isolates.");
        VMError.guarantee("lir".equals(SubstrateOptions.CompilerBackend.getValue()), "PLT and GOT cannot work with a custom compiler backend.");

        ImageSingletons.add(PLTGOTConfiguration.class, createConfiguration());
    }

    private static PLTGOTConfiguration createConfiguration() {
        if (Platform.includedIn(Platform.AMD64.class)) {
            return new AMD64HostedPLTGOTConfiguration();
        } else if (Platform.includedIn(Platform.AARCH64.class)) {
            return new AArch64HostedPLTGOTConfiguration();
        } else {
            throw VMError.shouldNotReachHere("PLT and GOT is currently only supported on AArch64 and AMD64.");
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        Method resolver = HostedPLTGOTConfiguration.singleton().getArchSpecificResolverAsMethod();
        ((FeatureImpl.BeforeAnalysisAccessImpl) access).registerAsRoot(resolver, false, "PLT GOT support, registered in " + PLTGOTFeature.class);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        HostedPLTGOTConfiguration.singleton().setHostedMetaAccess(((FeatureImpl.BeforeCompilationAccessImpl) access).getMetaAccess());
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        MethodAddressResolutionSupport methodAddressResolutionSupport = HostedPLTGOTConfiguration.singleton().getMethodAddressResolutionSupport();
        GOTEntryAllocator gotEntryAllocator = HostedPLTGOTConfiguration.singleton().getGOTEntryAllocator();

        gotEntryAllocator.reserveAndLayout(((FeatureImpl.AfterCompilationAccessImpl) access).getCompilations().keySet(), methodAddressResolutionSupport);

        Set<SharedMethod> gotTable = Set.of(gotEntryAllocator.getGOT());
        ImageSingletons.add(MethodPointerRelocationProvider.class, new PLTGOTPointerRelocationProvider(gotTable::contains));
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        HostedPLTGOTConfiguration.singleton().markResolverMethodPatch();
        if (PLTGOTOptions.PrintPLTGOTCallsInfo.getValue()) {
            reportPLTGOTCallSites();
        }
    }

    @Override
    public void afterAbstractImageCreation(AfterAbstractImageCreationAccess access) {
        FeatureImpl.AfterAbstractImageCreationAccessImpl accessImpl = (FeatureImpl.AfterAbstractImageCreationAccessImpl) access;
        ObjectFile imageObjectFile = accessImpl.getImage().getObjectFile();
        SharedMethod[] got = HostedPLTGOTConfiguration.singleton().getGOTEntryAllocator().getGOT();
        /* We must create the PLT and the GOT section before we mark any relocations. */
        PLTSectionSupport pltSectionSupport = HostedPLTGOTConfiguration.singleton().getPLTSectionSupport();
        pltSectionSupport.createPLTSection(got, imageObjectFile, accessImpl.getSubstrateBackend());
        createGOTSection(got, imageObjectFile, pltSectionSupport);
        HostedPLTGOTConfiguration.singleton().getMethodAddressResolutionSupport().augmentImageObjectFile(imageObjectFile);
    }

    public static void createGOTSection(SharedMethod[] got, ObjectFile objectFile, PLTSectionSupport pltSectionSupport) {
        int wordSize = ConfigurationValues.getTarget().wordSize;
        int gotSectionSize = got.length * wordSize;
        RelocatableBuffer gotBuffer = new RelocatableBuffer(gotSectionSize, objectFile.getByteOrder());
        ObjectFile.ProgbitsSectionImpl gotBufferImpl = new BasicProgbitsSectionImpl(gotBuffer.getBackingArray());
        String name = HostedPLTGOTConfiguration.SVM_GOT_SECTION.getFormatDependentName(objectFile.getFormat());
        ObjectFile.Section gotSection = objectFile.newProgbitsSection(name, objectFile.getPageSize(), true, false, gotBufferImpl);

        ObjectFile.RelocationKind relocationKind = ObjectFile.RelocationKind.getDirect(wordSize);
        for (int gotEntryNo = 0; gotEntryNo < got.length; ++gotEntryNo) {
            int methodGOTEntryOffsetInSection = gotSectionSize + GOTAccess.getGotEntryOffsetFromHeapRegister(gotEntryNo);
            pltSectionSupport.markRelocationToPLTResolverJump(gotBufferImpl, methodGOTEntryOffsetInSection, relocationKind, got[gotEntryNo]);
        }

        objectFile.createDefinedSymbol(gotSection.getName(), gotSection, 0, 0, false, false);
        objectFile.createDefinedSymbol(GOTHeapSupport.IMAGE_GOT_BEGIN_SYMBOL_NAME, gotSection, 0, wordSize, false,
                        SubstrateOptions.InternalSymbolsAreGlobal.getValue());
        objectFile.createDefinedSymbol(GOTHeapSupport.IMAGE_GOT_END_SYMBOL_NAME, gotSection, gotSectionSize, wordSize, false,
                        SubstrateOptions.InternalSymbolsAreGlobal.getValue());

        if (PLTGOTOptions.PrintGOT.getValue()) {
            ReportUtils.report("GOT Section contents", SubstrateOptions.reportsPath(), "got", "txt", writer -> {
                writer.println("GOT Entry No | GOT Entry Offset From Image Heap Register | Method Name");
                for (int i = 0; i < got.length; ++i) {
                    writer.printf("%5X %5X %s%n", i, -GOTAccess.getGotEntryOffsetFromHeapRegister(i), got[i].toString());
                }
            });
        }
    }

    private static void reportPLTGOTCallSites() {
        CollectPLTGOTCallSitesResolutionSupport resolver = (CollectPLTGOTCallSitesResolutionSupport) HostedPLTGOTConfiguration.singleton().getMethodAddressResolutionSupport();
        Consumer<PrintWriter> reportWriter = (pw) -> {
            final String methodFormat = "%H.%n(%p)";
            try (JsonWriter writer = new JsonWriter(pw)) {
                writer.append('{').newline();
                var calleesWithUnknownCaller = resolver.getCalleesWithUnknownCaller().stream().map(m -> m.format(methodFormat)).toList();
                if (!calleesWithUnknownCaller.isEmpty()) {
                    writer.quote("UNKNOWN_CALLER").append(":[").indent().newline();
                    appendCallees(writer, calleesWithUnknownCaller.iterator());
                    writer.newline().unindent().append("]");
                }
                for (var entry : resolver.getCallerCalleesMap().entrySet()) {
                    writer.append(',').newline();
                    var caller = entry.getKey();
                    var calleesIter = entry.getValue().stream().map(m -> m.format(methodFormat))
                                    .sorted().iterator();
                    writer.quote(caller.format(methodFormat)).append(":[").indent().newline();
                    appendCallees(writer, calleesIter);
                    writer.unindent().newline().append(']');
                }
                writer.newline().append('}').newline();
            } catch (IOException e) {
                VMError.shouldNotReachHere(e);
            }
        };
        ReportUtils.report("PLT/GOT call-sites info",
                        SubstrateOptions.reportsPath(), "plt_got_call-sites_info", "json", reportWriter);
    }

    private static void appendCallees(JsonWriter writer, Iterator<String> callees) throws IOException {
        while (callees.hasNext()) {
            var callee = callees.next();
            writer.quote(callee);
            if (callees.hasNext()) {
                writer.append(',');
                writer.newline();
            }
        }
    }
}
