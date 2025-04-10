/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.substitute.system;

/*
 * Checkstyle: stop method name check
 * Method names have to match the target class and are not under our control
 */

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Substitutions for Windows-specific implementation of IO classes.
 */
public class WebImageWindowsIOSubstitutions {
}

@TargetClass(className = "sun.nio.fs.WindowsNativeDispatcher$FirstFile", onlyWith = IsWindows.class)
@SuppressWarnings("all")
final class Target_sun_nio_fs_WindowsNativeDispatcher_FirstFile_Web {
}

@TargetClass(className = "sun.nio.fs.WindowsNativeDispatcher$FirstStream", onlyWith = IsWindows.class)
@SuppressWarnings("all")
final class Target_sun_nio_fs_WindowsNativeDispatcher_FirstStream_Web {
}

@TargetClass(className = "sun.nio.fs.WindowsNativeDispatcher$VolumeInformation", onlyWith = IsWindows.class)
@SuppressWarnings("all")
final class Target_sun_nio_fs_WindowsNativeDispatcher_VolumeInformation_Web {
}

@TargetClass(className = "sun.nio.fs.WindowsNativeDispatcher$DiskFreeSpace", onlyWith = IsWindows.class)
@SuppressWarnings("all")
final class Target_sun_nio_fs_WindowsNativeDispatcher_DiskFreeSpace_Web {
}

@TargetClass(className = "sun.nio.fs.WindowsNativeDispatcher$AclInformation", onlyWith = IsWindows.class)
@SuppressWarnings("all")
final class Target_sun_nio_fs_WindowsNativeDispatcher_AclInformation_Web {
}

@TargetClass(className = "sun.nio.fs.WindowsNativeDispatcher$CompletionStatus", onlyWith = IsWindows.class)
@SuppressWarnings("all")
final class Target_sun_nio_fs_WindowsNativeDispatcher_CompletionStatus_Web {
}

@TargetClass(className = "sun.nio.fs.WindowsNativeDispatcher$Account", onlyWith = IsWindows.class)
@SuppressWarnings("all")
final class Target_sun_nio_fs_WindowsNativeDispatcher_Account_Web {
}

@TargetClass(className = "sun.nio.fs.WindowsNativeDispatcher", onlyWith = IsWindows.class)
@SuppressWarnings("all")
final class Target_sun_nio_fs_WindowsNativeDispatcher_Web {

    @Substitute
    static long CreateEvent(boolean bManualReset, boolean bInitialState) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher./");
    }

    @Substitute
    private static long CreateFile0(long lpFileName, int dwDesiredAccess, int dwShareMode, long lpSecurityAttributes, int dwCreationDisposition, int dwFlagsAndAttributes) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.CreateFile0");
    };

    @Substitute
    static void CloseHandle(long handle) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.CloseHandle");
    }

    @Substitute
    private static void DeleteFile0(long lpFileName) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.DeleteFile0");
    }

    @Substitute
    private static void CreateDirectory0(long lpFileName, long lpSecurityAttributes) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.CreateDirectory0");
    }

    @Substitute
    private static void RemoveDirectory0(long lpFileName) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.RemoveDirectory0");
    }

    @Substitute
    static void DeviceIoControlSetSparse(long handle) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.DeviceIoControlSetSparse");
    }

    @Substitute
    static void DeviceIoControlGetReparsePoint(long handle, long bufferAddress, int bufferSize) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.DeviceIoControlGetReparsePoint");
    }

    @Substitute
    static long GetFileSizeEx(long handle) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetFileSizeEx");
    }

    @Substitute
    private static void FindFirstFile0(long lpFileName, Target_sun_nio_fs_WindowsNativeDispatcher_FirstFile_Web obj) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.FindFirstFile0");
    }

    @Substitute
    private static long FindFirstFile1(long lpFileName, long address) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.FindFirstFile1");
    }

    @Substitute
    private static String FindNextFile0(long handle, long address) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.FindNextFile0");
    }

    @Substitute
    private static void FindFirstStream0(long lpFileName, Target_sun_nio_fs_WindowsNativeDispatcher_FirstStream_Web obj) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.FindFirstStream0");
    }

    @Substitute
    private static String FindNextStream0(long handle) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.FindNextStream0");
    }

    @Substitute
    static void FindClose(long handle) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.FindClose");
    }

    @Substitute
    private static void GetFileInformationByHandle0(long handle, long address) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetFileInformationByHandle0");
    }

    @Substitute
    private static void CopyFileEx0(long existingAddress, long newAddress, int flags, long addressToPollForCancel) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.CopyFileEx0");
    }

    @Substitute
    private static void MoveFileEx0(long existingAddress, long newAddress, int flags) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.MoveFileEx0");
    }

    @Substitute
    private static int GetFileAttributes0(long lpFileName) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetFileAttributes0");
    }

    @Substitute
    private static void SetFileAttributes0(long lpFileName, int dwFileAttributes) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.SetFileAttributes0");
    }

    @Substitute
    private static void GetFileAttributesEx0(long lpFileName, long address) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetFileAttributesEx0");
    }

    @Substitute
    private static void SetFileTime0(long handle, long createTime, long lastAccessTime, long lastWriteTime) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.SetFileTime0");
    }

    @Substitute
    static void SetEndOfFile(long handle) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.SetEndOfFile");
    }

    @Substitute
    static int GetLogicalDrives() {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetLogicalDrives");
    }

    @Substitute
    private static void GetVolumeInformation0(long lpRoot, Target_sun_nio_fs_WindowsNativeDispatcher_VolumeInformation_Web obj) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetVolumeInformation0");
    }

    @Substitute
    private static int GetDriveType0(long lpRoot) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetDriveType0");
    }

    @Substitute
    private static void GetDiskFreeSpaceEx0(long lpDirectoryName, Target_sun_nio_fs_WindowsNativeDispatcher_DiskFreeSpace_Web obj) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetDiskFreeSpaceEx0");
    }

    @Substitute
    private static void GetDiskFreeSpace0(long lpRootPathName, Target_sun_nio_fs_WindowsNativeDispatcher_DiskFreeSpace_Web obj) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetDiskFreeSpace0");
    }

    @Substitute
    private static String GetVolumePathName0(long lpFileName) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetVolumePathName0");
    }

    @Substitute
    static void InitializeSecurityDescriptor(long sdAddress) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.InitializeSecurityDescriptor");
    }

    @Substitute
    static void InitializeAcl(long aclAddress, int size) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.InitializeAcl");
    }

    @Substitute
    private static int GetFileSecurity0(long lpFileName, int requestedInformation, long pSecurityDescriptor, int nLength) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetFileSecurity0");
    }

    @Substitute
    static void SetFileSecurity0(long lpFileName, int securityInformation, long pSecurityDescriptor) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.SetFileSecurity0");
    }

    @Substitute
    static long GetSecurityDescriptorOwner(long pSecurityDescriptor) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetSecurityDescriptorOwner");
    }

    @Substitute
    static void SetSecurityDescriptorOwner(long pSecurityDescriptor, long pOwner) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.SetSecurityDescriptorOwner");
    }

    @Substitute
    static long GetSecurityDescriptorDacl(long pSecurityDescriptor) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetSecurityDescriptorDacl");
    }

    @Substitute
    static void SetSecurityDescriptorDacl(long pSecurityDescriptor, long pAcl) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.SetSecurityDescriptorDacl");
    }

    @Substitute
    private static void GetAclInformation0(long aclAddress, Target_sun_nio_fs_WindowsNativeDispatcher_AclInformation_Web obj) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetAclInformation0");
    }

    @Substitute
    static long GetAce(long aclAddress, int aceIndex) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetAce");
    }

    @Substitute
    static void AddAccessAllowedAceEx(long aclAddress, int flags, int mask, long sidAddress) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.AddAccessAllowedAceEx");
    }

    @Substitute
    static void AddAccessDeniedAceEx(long aclAddress, int flags, int mask, long sidAddress) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.AddAccessDeniedAceEx");
    }

    @Substitute
    private static void LookupAccountSid0(long sidAddress, Target_sun_nio_fs_WindowsNativeDispatcher_Account_Web obj) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.LookupAccountSid0");
    }

    @Substitute
    private static int LookupAccountName0(long lpAccountName, long pSid, int cbSid) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.LookupAccountName0");
    }

    @Substitute
    static int GetLengthSid(long sidAddress) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetLengthSid");
    }

    @Substitute
    static String ConvertSidToStringSid(long sidAddress) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.ConvertSidToStringSid");
    }

    @Substitute
    private static long ConvertStringSidToSid0(long lpStringSid) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.ConvertStringSidToSid0");
    }

    @Substitute
    static long GetCurrentProcess() {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetCurrentProcess");
    }

    @Substitute
    static long GetCurrentThread() {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetCurrentThread");
    }

    @Substitute
    static long OpenProcessToken(long hProcess, int desiredAccess) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.OpenProcessToken");
    }

    @Substitute
    static long OpenThreadToken(long hThread, int desiredAccess, boolean openAsSelf) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.OpenThreadToken");
    }

    @Substitute
    static long DuplicateTokenEx(long hThread, int desiredAccess) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.DuplicateTokenEx");
    }

    @Substitute
    static void SetThreadToken(long thread, long hToken) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.SetThreadToken");
    }

    @Substitute
    static int GetTokenInformation(long token, int tokenInfoClass, long pTokenInfo, int tokenInfoLength) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetTokenInformation");
    }

    @Substitute
    static void AdjustTokenPrivileges(long token, long luid, int attributes) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.AdjustTokenPrivileges");
    }

    @Substitute
    static boolean AccessCheck(long token, long securityInfo, int accessMask, int genericRead, int genericWrite, int genericExecute, int genericAll) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.AccessCheck");
    }

    @Substitute
    private static long LookupPrivilegeValue0(long lpName) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.LookupPrivilegeValue0");
    }

    @Substitute
    private static void CreateSymbolicLink0(long linkAddress, long targetAddress, int flags) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.CreateSymbolicLink0");
    }

    @Substitute
    private static void CreateHardLink0(long newFileBuffer, long existingFileBuffer) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.CreateHardLink0");
    }

    @Substitute
    private static String GetFullPathName0(long pathAddress) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetFullPathName0");
    }

    @Substitute
    static String GetFinalPathNameByHandle(long handle) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetFinalPathNameByHandle");
    }

    @Substitute
    static String FormatMessage(int errorCode) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.FormatMessage");
    }

    @Substitute
    static void LocalFree(long address) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.LocalFree");
    }

    @Substitute
    static long CreateIoCompletionPort(long fileHandle, long existingPort, long completionKey) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.CreateIoCompletionPort");
    }

    @Substitute
    private static void GetQueuedCompletionStatus0(long completionPort, Target_sun_nio_fs_WindowsNativeDispatcher_CompletionStatus_Web status) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetQueuedCompletionStatus0");
    }

    @Substitute
    static void PostQueuedCompletionStatus(long completionPort, long completionKey) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.PostQueuedCompletionStatus");
    }

    @Substitute
    static void ReadDirectoryChangesW(long hDirectory, long bufferAddress, int bufferLength, boolean watchSubTree, int filter, long bytesReturnedAddress, long pOverlapped) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.ReadDirectoryChangesW");
    }

    @Substitute
    static void CancelIo(long hFile) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.CancelIo");
    }

    @Substitute
    static int GetOverlappedResult(long hFile, long lpOverlapped) {
        throw new UnsupportedOperationException("WindowsNativeDispatcher.GetOverlappedResult");
    }
}
