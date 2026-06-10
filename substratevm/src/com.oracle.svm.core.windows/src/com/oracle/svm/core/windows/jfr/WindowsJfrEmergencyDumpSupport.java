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
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.jfr.AbstractJfrEmergencyDumpSupport;
import com.oracle.svm.core.jfr.JfrEmergencyDumpSupport;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFileDescriptor;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.windows.headers.FileAPI;
import com.oracle.svm.core.windows.headers.FileAPI.BY_HANDLE_FILE_INFORMATION;
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

/**
 * Windows implementation of JFR emergency dump support. Directory scanning and chunk reopening use
 * Win32 path APIs, so opened handles are revalidated to reject reparse points and to ensure chunk
 * files are still resolved under the validated repository directory.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Duplicable.class, other = PartiallyLayerAware.class)
public class WindowsJfrEmergencyDumpSupport extends AbstractJfrEmergencyDumpSupport {
    private static final char FILE_SEPARATOR = '\\';
    private static final char ALT_FILE_SEPARATOR = '/';
    private static final char WILDCARD = '*';
    private static final int NT_PATH_PREFIX_LENGTH = 4;

    private HANDLE repositoryDirectoryGuardHandle;
    private HANDLE repositoryFindHandle;

    @Platforms(Platform.HOSTED_ONLY.class)
    public WindowsJfrEmergencyDumpSupport() {
    }

    @Override
    protected void initializeRepositoryState() {
        repositoryDirectoryGuardHandle = Word.nullPointer();
        repositoryFindHandle = Word.nullPointer();
    }

    @Override
    protected byte[] toBytes(String text) {
        byte[] result = new byte[text.length() * Character.BYTES];
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int offset = i * Character.BYTES;
            result[offset] = (byte) ch;
            result[offset + 1] = (byte) (ch >>> Byte.SIZE);
        }
        return result;
    }

    @Override
    protected int elementSize() {
        return Character.BYTES;
    }

    private WCharPointer getPathBuffer() {
        return (WCharPointer) pathBuffer;
    }

    @Override
    protected void iterateRepository(GrowableWordArray gwa) {
        if (!isValidHandle(repositoryDirectoryGuardHandle)) {
            return;
        }

        /*
         * Win32 directory enumeration is path-based. The guard handle above keeps the already
         * validated non-reparse repository directory open for the duration of the scan.
         */
        WIN32_FIND_DATAW findData = StackValue.get(WIN32_FIND_DATAW.class);
        HANDLE handle = FileAPI.FindFirstFileW(createRepositorySearchPath(), findData);
        if (handle.equal(INVALID_HANDLE_VALUE())) {
            logOpenDirectoryWarning();
            return;
        }
        repositoryFindHandle = handle;

        int count = 0;
        boolean done = false;
        while (!done) {
            if (isRegularFileAttributes(findData.getFileAttributes())) {
                WCharPointer fn = findData.getFileName();
                int filenameLength = stringLength(fn);
                if (addUsableChunkFilename(gwa, (Pointer) fn, filenameLength)) {
                    count++;
                }
            }
            done = FileAPI.FindNextFileW(repositoryFindHandle, findData) == 0;
        }

        sortChunkFilenames(gwa, count);
    }

    private WCharPointer createRepositorySearchPath() {
        int idx = 0;
        idx = appendRepositoryLocationToPathBuffer(idx);
        idx = appendFileSeparatorToPathBuffer(idx);
        writePathBufferElement(idx++, WILDCARD);
        writePathBufferNull(idx);
        return getPathBuffer();
    }

    @Override
    protected RawFileDescriptor openRepositoryFile(Pointer filename) {
        WCharPointer fn = (WCharPointer) filename;
        WCharPointer path = createRepositoryFilePath(fn);
        if (path.isNull() || !hasRegularFileAttributes(path)) {
            return Word.nullPointer();
        }

        HANDLE h = FileAPI.CreateFileW(path, FileAPI.GENERIC_READ(), FileAPI.FILE_SHARE_READ() | FileAPI.FILE_SHARE_WRITE() | FileAPI.FILE_SHARE_DELETE(), Word.nullPointer(),
                        FileAPI.OPEN_EXISTING(), FileAPI.FILE_ATTRIBUTE_NORMAL() | FileAPI.FILE_FLAG_OPEN_REPARSE_POINT(), Word.nullPointer());
        if (!isValidHandle(h)) {
            return Word.nullPointer();
        }
        if (!isValidatedRegularFileHandle(h) || !isUnderValidatedRepository(h)) {
            WinBase.CloseHandle(h);
            return Word.nullPointer();
        }
        return (RawFileDescriptor) h;
    }

    private WCharPointer createRepositoryFilePath(WCharPointer fn) {
        if (isRepositoryLocationTooLong()) {
            return Word.nullPointer();
        }
        int filenameLength = stringLength(fn);
        int separatorLength = needsFileSeparator(repositoryLocationBytes) ? 1 : 0;
        if (repositoryLocationLength() + separatorLength + filenameLength >= JVM_MAXPATHLEN) {
            return Word.nullPointer();
        }

        int idx = 0;
        idx = appendRepositoryLocationToPathBuffer(idx);
        idx = appendFileSeparatorToPathBuffer(idx);
        idx = appendCharsToPathBuffer(fn, idx);
        writePathBufferNull(idx);
        return getPathBuffer();
    }

    @Override
    protected boolean openRepository() {
        WCharPointer repositoryLocation = getRepositoryLocation();
        if (repositoryLocation.isNull() || !hasDirectoryAttributes(repositoryLocation)) {
            logOpenDirectoryWarning();
            return false;
        }

        repositoryDirectoryGuardHandle = FileAPI.CreateFileW(repositoryLocation, FileAPI.GENERIC_READ(), FileAPI.FILE_SHARE_READ() | FileAPI.FILE_SHARE_WRITE(), Word.nullPointer(),
                        FileAPI.OPEN_EXISTING(), FileAPI.FILE_FLAG_BACKUP_SEMANTICS() | FileAPI.FILE_FLAG_OPEN_REPARSE_POINT(), Word.nullPointer());
        if (!isValidHandle(repositoryDirectoryGuardHandle) || !isValidatedDirectoryHandle(repositoryDirectoryGuardHandle)) {
            closeRepository();
            logOpenDirectoryWarning();
            return false;
        }
        return true;
    }

    private boolean isUnderValidatedRepository(HANDLE fileHandle) {
        WCharPointer directoryPath = getPathBuffer();
        return writeFinalPathName(repositoryDirectoryGuardHandle, directoryPath, JVM_MAXPATHLEN + 1) && hasTrailingFileSeparator(directoryPath) && finalPathStartsWith(fileHandle, directoryPath);
    }

    private static boolean writeFinalPathName(HANDLE handle, WCharPointer buffer, int bufferLength) {
        int length = FileAPI.GetFinalPathNameByHandleW(handle, buffer, bufferLength, FileAPI.FILE_NAME_NORMALIZED());
        return length > 0 && length < bufferLength;
    }

    private boolean finalPathStartsWith(HANDLE handle, WCharPointer directoryPrefix) {
        int directoryPrefixLength = stringLength(directoryPrefix);
        if (directoryPrefixLength <= NT_PATH_PREFIX_LENGTH) {
            return false;
        }
        WCharPointer filePath = UnsafeStackValue.get(JVM_MAXPATHLEN + 1, WCharPointer.class);
        if (!writeFinalPathName(handle, filePath, JVM_MAXPATHLEN + 1)) {
            return false;
        }
        for (int i = 0; i < directoryPrefixLength; i++) {
            if (normalizePathChar(readChar(filePath, i)) != normalizePathChar(readChar(directoryPrefix, i))) {
                return false;
            }
        }
        return true;
    }

    private char normalizePathChar(char ch) {
        return isFileSeparator(ch) ? FILE_SEPARATOR : ch;
    }

    private boolean hasTrailingFileSeparator(WCharPointer path) {
        int length = stringLength(path);
        if (length <= NT_PATH_PREFIX_LENGTH) {
            return false;
        }
        if (!isFileSeparator(readChar(path, length - 1))) {
            if (length + 1 >= JVM_MAXPATHLEN + 1) {
                return false;
            }
            path.write(length, FILE_SEPARATOR);
            length++;
            path.write(length, (char) 0);
        }
        return true;
    }

    private static boolean isValidatedRegularFileHandle(HANDLE handle) {
        BY_HANDLE_FILE_INFORMATION info = StackValue.get(BY_HANDLE_FILE_INFORMATION.class);
        return FileAPI.GetFileInformationByHandle(handle, info) != 0 && isRegularFileAttributes(info.getFileAttributes());
    }

    private static boolean isValidatedDirectoryHandle(HANDLE handle) {
        BY_HANDLE_FILE_INFORMATION info = StackValue.get(BY_HANDLE_FILE_INFORMATION.class);
        return FileAPI.GetFileInformationByHandle(handle, info) != 0 && isDirectoryAttributes(info.getFileAttributes());
    }

    private WCharPointer getRepositoryLocation() {
        if (isRepositoryLocationTooLong()) {
            return Word.nullPointer();
        }

        int idx = 0;
        idx = appendRepositoryLocationToPathBuffer(idx);
        writePathBufferNull(idx);
        return getPathBuffer();
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

    @Override
    protected int appendCurrentDateTimeToPathBuffer(int idx) {
        SYSTEMTIME localTime = StackValue.get(SYSTEMTIME.class);
        SysinfoAPI.GetLocalTime(localTime);
        return writeDateTimeToPathBuffer(idx, localTime.wYear(), localTime.wMonth(), localTime.wDay(), localTime.wHour(), localTime.wMinute(), localTime.wSecond());
    }

    private int appendCharsToPathBuffer(WCharPointer chars, int start) {
        int idx = start;
        int sourceIdx = 0;
        char ch;
        while ((ch = readChar(chars, sourceIdx++)) != 0) {
            writePathBufferElement(idx++, ch);
        }
        return idx;
    }

    @Override
    protected char getFileSeparator() {
        return FILE_SEPARATOR;
    }

    @Override
    protected boolean isFileSeparator(char ch) {
        return ch == FILE_SEPARATOR || ch == ALT_FILE_SEPARATOR;
    }

    private boolean needsFileSeparator(byte[] path) {
        char ch = readChar(path, repositoryLocationLength() - 1);
        return !isFileSeparator(ch);
    }

    private void writePathBufferNull(int index) {
        writePathBufferElement(index, (char) 0);
    }

    @Override
    protected void writeElement(Pointer buffer, int index, char ch) {
        ((WCharPointer) buffer).write(index, ch);
    }

    private static char readChar(byte[] buffer, int index) {
        int offset = index * Character.BYTES;
        return (char) ((buffer[offset] & 0xff) | ((buffer[offset + 1] & 0xff) << Byte.SIZE));
    }

    @Override
    protected char readElement(Pointer buffer, int index) {
        return ((WCharPointer) buffer).read(index);
    }

    private static char readChar(WCharPointer pointer, int index) {
        return pointer.read(index);
    }

    private static int stringLength(WCharPointer pointer) {
        return UnsignedUtils.safeToInt(WindowsLibC.wcslen(pointer));
    }

    @Override
    protected void closeRepository() {
        if (isValidHandle(repositoryFindHandle)) {
            FileAPI.FindClose(repositoryFindHandle);
            repositoryFindHandle = Word.nullPointer();
        }

        if (isValidHandle(repositoryDirectoryGuardHandle)) {
            WinBase.CloseHandle(repositoryDirectoryGuardHandle);
            repositoryDirectoryGuardHandle = Word.nullPointer();
        }
    }

    private static boolean isValidHandle(HANDLE handle) {
        return handle.isNonNull() && !handle.equal(INVALID_HANDLE_VALUE());
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
