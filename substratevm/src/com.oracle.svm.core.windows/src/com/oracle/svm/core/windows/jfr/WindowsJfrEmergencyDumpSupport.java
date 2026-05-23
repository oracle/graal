/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, 2026, IBM Inc. All rights reserved.
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

package com.oracle.svm.core.windows.jfr;

import static com.oracle.svm.core.windows.headers.WinBase.INVALID_HANDLE_VALUE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.collections.GrowableWordArray;
import com.oracle.svm.core.collections.GrowableWordArrayAccess;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jfr.AbstractJfrEmergencyDumpSupport;
import com.oracle.svm.core.jfr.JfrEmergencyDumpSupport;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFileDescriptor;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFilePath;
import com.oracle.svm.core.windows.headers.FileAPI;
import com.oracle.svm.core.windows.headers.FileAPI.WIN32_FIND_DATAW;
import com.oracle.svm.core.windows.headers.SysinfoAPI;
import com.oracle.svm.core.windows.headers.SysinfoAPI.SYSTEMTIME;
import com.oracle.svm.core.windows.headers.WinBase;
import com.oracle.svm.core.windows.headers.WinBase.HANDLE;
import com.oracle.svm.core.windows.headers.WindowsLibC;
import com.oracle.svm.core.windows.headers.WindowsLibC.WCharPointer;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.Duplicable;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.BasedOnJDKFile;

@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25-ga/src/hotspot/share/jfr/recorder/repository/jfrEmergencyDump.cpp#L43-L445")
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Duplicable.class, other = PartiallyLayerAware.class)
public class WindowsJfrEmergencyDumpSupport extends AbstractJfrEmergencyDumpSupport {
    private static final char FILE_SEPARATOR = '\\';
    private static final char ALT_FILE_SEPARATOR = '/';
    private static final char WILDCARD = '*';
    private static final char[] DUMP_FILE_PREFIX_CHARS = DUMP_FILE_PREFIX.toCharArray();
    private static final char[] CHUNKFILE_EXTENSION_CHARS = CHUNKFILE_EXTENSION.toCharArray();

    private char[] pidChars;
    private char[] cwdChars;
    private char[] dumpPathChars;
    private char[] repositoryLocationChars;
    private WCharPointer pathBuffer;
    private boolean pathBufferInitialized;
    private HANDLE repositoryDirectoryGuardHandle;
    private HANDLE repositoryFindHandle;

    @Platforms(Platform.HOSTED_ONLY.class)
    public WindowsJfrEmergencyDumpSupport() {
    }

    @Override
    public void initialize() {
        savePid();
        if (!pathBufferInitialized) {
            pathBuffer = NativeMemory.calloc(Word.unsigned(JVM_MAXPATHLEN + 1).multiply(Character.BYTES), NmtCategory.JFR);
            pathBufferInitialized = true;
        }
        repositoryDirectoryGuardHandle = Word.nullPointer();
        repositoryFindHandle = Word.nullPointer();
        saveCwd();
    }

    @Override
    protected void setPid(String pid) {
        pidChars = pid.toCharArray();
    }

    @Override
    protected void setSavedCwdText(String cwd) {
        cwdChars = cwd.toCharArray();
    }

    @Override
    protected void setDumpPathText(String dumpPath) {
        dumpPathChars = dumpPath == null ? null : dumpPath.toCharArray();
    }

    @Override
    protected void setDumpPathToSavedCwd() {
        dumpPathChars = cwdChars;
    }

    @Override
    protected void setRepositoryLocationText(String repositoryLocation) {
        repositoryLocationChars = repositoryLocation.toCharArray();
    }

    @Override
    protected long getPathBufferAddress() {
        if (!pathBufferInitialized) {
            return 0L;
        }
        return pathBuffer.rawValue();
    }

    @Override
    protected int pidLength() {
        return pidChars.length;
    }

    @Override
    protected int dumpPathLength() {
        return dumpPathChars == null ? -1 : dumpPathChars.length;
    }

    @Override
    protected int repositoryLocationLength() {
        return repositoryLocationChars == null ? -1 : repositoryLocationChars.length;
    }

    @Override
    protected RawFileDescriptor createEmergencyChunkPath() {
        if (isRepositoryLocationTooLong()) {
            return Word.nullPointer();
        }
        clearPathBuffer();
        int idx = 0;
        idx = appendCharsToPathBuffer(repositoryLocationChars, idx);
        idx = appendFileSeparatorToPathBufferIfNeeded(idx);
        idx = appendDateTimeToPathBuffer(idx);
        return createEmergencyChunkPath((RawFilePath) pathBuffer, idx);
    }

    @Override
    protected RawFilePath createEmergencyDumpPath() {
        int idx = 0;
        clearPathBuffer();

        if (isDumpPathTooLong()) {
            return Word.nullPointer();
        }
        if (dumpPathChars != null) {
            idx = appendCharsToPathBuffer(dumpPathChars, idx);
            idx = appendFileSeparatorToPathBufferIfNeeded(idx);
        }

        idx = appendCharsToPathBuffer(DUMP_FILE_PREFIX_CHARS, idx);
        idx = appendCharsToPathBuffer(pidChars, idx);
        idx = appendCharsToPathBuffer(CHUNKFILE_EXTENSION_CHARS, idx);
        writePathBufferNull(idx);
        return (RawFilePath) pathBuffer;
    }

    @Override
    protected void iterateRepository(GrowableWordArray gwa) {
        if (!isValidHandle(repositoryDirectoryGuardHandle)) {
            return;
        }

        WIN32_FIND_DATAW findData = StackValue.get(WIN32_FIND_DATAW.class);
        /*
         * Win32 directory enumeration is path-based. The guard handle above keeps the already
         * validated non-reparse repository directory open for the duration of the scan.
         */
        HANDLE handle = FileAPI.FindFirstFileW(createRepositorySearchPath(), findData);
        if (handle.equal(INVALID_HANDLE_VALUE())) {
            SubstrateJVM.getLogging().logJfrSystemError(getOpenDirectoryWarning());
            return;
        }
        repositoryFindHandle = handle;

        int count = 0;
        boolean done = false;
        while (!done) {
            if (filter(findData)) {
                WCharPointer fnCopy = copyFilename(findData.getFileName());
                if (addChunkFilename(gwa, (Word) (Pointer) fnCopy)) {
                    count++;
                }
            }
            done = FileAPI.FindNextFileW(repositoryFindHandle, findData) == 0;
        }

        if (count > 0) {
            GrowableWordArrayAccess.qsort(gwa, 0, count - 1, chunkFilenameComparator());
        }
    }

    private WCharPointer createRepositorySearchPath() {
        clearPathBuffer();
        int idx = 0;
        idx = appendCharsToPathBuffer(repositoryLocationChars, idx);
        idx = appendFileSeparatorToPathBufferIfNeeded(idx);
        writePathBufferChar(idx++, WILDCARD);
        writePathBufferNull(idx);
        return pathBuffer;
    }

    @Override
    protected RawFileDescriptor openRepositoryFile(Word filename) {
        return openRepositoryFile((WCharPointer) ((Pointer) filename));
    }

    private RawFileDescriptor openRepositoryFile(WCharPointer fn) {
        WCharPointer path = createRepositoryFilePath(fn);
        if (path.isNull() || !hasRegularFileAttributes(path)) {
            return Word.nullPointer();
        }
        HANDLE h = FileAPI.CreateFileW(path, FileAPI.GENERIC_READ(), FileAPI.FILE_SHARE_READ() | FileAPI.FILE_SHARE_WRITE() | FileAPI.FILE_SHARE_DELETE(), Word.nullPointer(),
                        FileAPI.OPEN_EXISTING(), FileAPI.FILE_ATTRIBUTE_NORMAL() | FileAPI.FILE_FLAG_OPEN_REPARSE_POINT(), Word.nullPointer());
        if (h.equal(INVALID_HANDLE_VALUE())) {
            return Word.nullPointer();
        }
        return (RawFileDescriptor) Word.pointer(h.rawValue());
    }

    private WCharPointer createRepositoryFilePath(WCharPointer fn) {
        if (repositoryLocationChars == null || isRepositoryLocationTooLong()) {
            return Word.nullPointer();
        }
        int filenameLength = stringLength(fn);
        int separatorLength = needsFileSeparator(repositoryLocationChars) ? 1 : 0;
        if (repositoryLocationChars.length + separatorLength + filenameLength >= JVM_MAXPATHLEN) {
            return Word.nullPointer();
        }
        clearPathBuffer();
        int idx = 0;
        idx = appendCharsToPathBuffer(repositoryLocationChars, idx);
        idx = appendFileSeparatorToPathBufferIfNeeded(idx);
        idx = appendCharsToPathBuffer(fn, idx);
        writePathBufferNull(idx);
        return pathBuffer;
    }

    private boolean filter(WIN32_FIND_DATAW findData) {
        if (!isRegularFileAttributes(findData.getFileAttributes())) {
            return false;
        }

        WCharPointer fn = findData.getFileName();
        int filenameLength = stringLength(fn);
        return isUsableChunkFile((Word) (Pointer) fn, filenameLength);
    }

    @Override
    protected boolean openRepository() {
        WCharPointer repositoryLocation = getRepositoryLocation();
        if (repositoryLocation.isNull() || !hasDirectoryAttributes(repositoryLocation)) {
            SubstrateJVM.getLogging().logJfrSystemError(getOpenDirectoryWarning());
            return false;
        }

        repositoryDirectoryGuardHandle = FileAPI.CreateFileW(repositoryLocation, FileAPI.GENERIC_READ(), FileAPI.FILE_SHARE_READ() | FileAPI.FILE_SHARE_WRITE(), Word.nullPointer(),
                        FileAPI.OPEN_EXISTING(), FileAPI.FILE_FLAG_BACKUP_SEMANTICS() | FileAPI.FILE_FLAG_OPEN_REPARSE_POINT(), Word.nullPointer());
        if (!isValidHandle(repositoryDirectoryGuardHandle)) {
            SubstrateJVM.getLogging().logJfrSystemError(getOpenDirectoryWarning());
            return false;
        }
        return true;
    }

    private WCharPointer getRepositoryLocation() {
        if (repositoryLocationChars == null || isRepositoryLocationTooLong()) {
            return Word.nullPointer();
        }
        clearPathBuffer();
        int idx = appendCharsToPathBuffer(repositoryLocationChars, 0);
        writePathBufferNull(idx);
        return pathBuffer;
    }

    private static boolean hasRegularFileAttributes(WCharPointer path) {
        int attributes = FileAPI.GetFileAttributesW(path);
        return attributes != FileAPI.INVALID_FILE_ATTRIBUTES() && isRegularFileAttributes(attributes);
    }

    private static boolean hasDirectoryAttributes(WCharPointer path) {
        int attributes = FileAPI.GetFileAttributesW(path);
        return attributes != FileAPI.INVALID_FILE_ATTRIBUTES() && isDirectoryAttributes(attributes);
    }

    private static boolean isRegularFileAttributes(int attributes) {
        return (attributes & FileAPI.FILE_ATTRIBUTE_DIRECTORY()) == 0 && (attributes & FileAPI.FILE_ATTRIBUTE_REPARSE_POINT()) == 0;
    }

    private static boolean isDirectoryAttributes(int attributes) {
        return (attributes & FileAPI.FILE_ATTRIBUTE_DIRECTORY()) != 0 && (attributes & FileAPI.FILE_ATTRIBUTE_REPARSE_POINT()) == 0;
    }

    private int appendDateTimeToPathBuffer(int idx) {
        SYSTEMTIME localTime = StackValue.get(SYSTEMTIME.class);
        SysinfoAPI.GetLocalTime(localTime);
        return writeDateTimeToPathBuffer(idx, localTime.wYear(), localTime.wMonth(), localTime.wDay(), localTime.wHour(), localTime.wMinute(), localTime.wSecond());
    }

    private void clearPathBuffer() {
        WindowsLibC.memset(pathBuffer, Word.signed(0), Word.unsigned(JVM_MAXPATHLEN + 1).multiply(Character.BYTES));
    }

    private int appendCharsToPathBuffer(char[] chars, int start) {
        int idx = start;
        for (char ch : chars) {
            writePathBufferChar(idx++, ch);
        }
        return idx;
    }

    private int appendCharsToPathBuffer(WCharPointer chars, int start) {
        int idx = start;
        int sourceIdx = 0;
        char ch;
        while ((ch = charAt(chars, sourceIdx++)) != 0) {
            writePathBufferChar(idx++, ch);
        }
        return idx;
    }

    private int appendFileSeparatorToPathBufferIfNeeded(int idx) {
        int result = idx;
        if (idx == 0 || needsFileSeparator(idx)) {
            writePathBufferChar(result, FILE_SEPARATOR);
            result++;
        }
        return result;
    }

    private boolean needsFileSeparator(int idx) {
        return idx == 0 || !isFileSeparator(charAt(pathBuffer, idx - 1));
    }

    private static boolean needsFileSeparator(char[] path) {
        return path.length == 0 || !isFileSeparator(path[path.length - 1]);
    }

    private static boolean isFileSeparator(char ch) {
        return ch == FILE_SEPARATOR || ch == ALT_FILE_SEPARATOR;
    }

    @Override
    protected void writePathBufferChar(int index, int ch) {
        ((Pointer) pathBuffer).writeChar(index * Character.BYTES, (char) ch);
    }

    private void writePathBufferNull(int index) {
        writePathBufferChar(index, (char) 0);
    }

    private static char charAt(WCharPointer pointer, int index) {
        return ((Pointer) pointer).readChar(index * Character.BYTES);
    }

    private static int stringLength(WCharPointer pointer) {
        return (int) WindowsLibC.wcslen(pointer).rawValue();
    }

    private static int indexOf(WCharPointer pointer, int needle) {
        int idx = 0;
        char ch;
        while ((ch = charAt(pointer, idx)) != 0) {
            if (ch == needle) {
                return idx;
            }
            idx++;
        }
        return idx;
    }

    private static WCharPointer copyFilename(WCharPointer fn) {
        int filenameLength = stringLength(fn);
        WCharPointer copy = NullableNativeMemory.malloc(Word.unsigned(filenameLength + 1).multiply(Character.BYTES), NmtCategory.JFR);
        if (copy.isNull()) {
            return Word.nullPointer();
        }
        Pointer copyPtr = (Pointer) copy;
        for (int i = 0; i < filenameLength; i++) {
            copyPtr.writeChar(i * Character.BYTES, charAt(fn, i));
        }
        copyPtr.writeChar(filenameLength * Character.BYTES, (char) 0);
        return copy;
    }

    @Override
    protected int filenameCharAt(Word filename, int index) {
        return charAt((WCharPointer) ((Pointer) filename), index);
    }

    @Override
    protected int filenameIndexOf(Word filename, int needle) {
        return indexOf((WCharPointer) ((Pointer) filename), needle);
    }

    @Override
    protected void freeChunkFilename(Word filename) {
        NullableNativeMemory.free(filename);
    }

    private void closeFindHandle() {
        if (isValidHandle(repositoryFindHandle)) {
            FileAPI.FindClose(repositoryFindHandle);
            repositoryFindHandle = Word.nullPointer();
        }
    }

    @Override
    protected void closeRepository() {
        closeFindHandle();
        if (isValidHandle(repositoryDirectoryGuardHandle)) {
            WinBase.CloseHandle(repositoryDirectoryGuardHandle);
            repositoryDirectoryGuardHandle = Word.nullPointer();
        }
    }

    private static boolean isValidHandle(HANDLE handle) {
        return handle.isNonNull() && !handle.equal(INVALID_HANDLE_VALUE());
    }

    @Override
    public void teardown() {
        closeEmergencyDumpFile();
        closeRepository();
        if (pathBufferInitialized) {
            NativeMemory.free(pathBuffer);
            pathBufferInitialized = false;
        }
    }
}

@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = PartiallyLayerAware.class)
class WindowsJfrEmergencyDumpFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return VMInspectionOptions.hasJfrSupport() && Platform.includedIn(Platform.WINDOWS.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(JfrEmergencyDumpSupport.class, new WindowsJfrEmergencyDumpSupport());
    }
}
