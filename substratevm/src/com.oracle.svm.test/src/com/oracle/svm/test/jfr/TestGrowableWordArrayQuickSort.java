///*
// * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
// * Copyright (c) 2021, 2022, Red Hat Inc. All rights reserved.
// * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
// *
// * This code is free software; you can redistribute it and/or modify it
// * under the terms of the GNU General Public License version 2 only, as
// * published by the Free Software Foundation.  Oracle designates this
// * particular file as subject to the "Classpath" exception as provided
// * by Oracle in the LICENSE file that accompanied this code.
// *
// * This code is distributed in the hope that it will be useful, but WITHOUT
// * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
// * version 2 for more details (a copy is included in the LICENSE file that
// * accompanied this code).
// *
// * You should have received a copy of the GNU General Public License version
// * 2 along with this work; if not, write to the Free Software Foundation,
// * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
// *
// * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
// * or visit www.oracle.com if you need additional information or have any
// * questions.
// */
//
//package com.oracle.svm.test.jfr;
//
//import com.oracle.svm.test.jfr.events.ClassEvent;
//import jdk.jfr.Recording;
//import jdk.jfr.consumer.RecordedEvent;
//import org.junit.Test;
//
//import java.util.List;
//
//import com.oracle.svm.core.SubstrateUtil;
//import com.oracle.svm.core.headers.LibC;
//import com.oracle.svm.core.nmt.NmtCategory;
//import com.oracle.svm.core.posix.headers.Dirent;
//import com.oracle.svm.core.posix.headers.Errno;
//import com.oracle.svm.core.posix.headers.Fcntl;
//import com.oracle.svm.core.posix.headers.Unistd;
//import org.graalvm.word.Pointer;
//import jdk.graal.compiler.word.Word;
//import org.graalvm.word.WordFactory;
////import jdk.jfr.internal.LogLevel;
////import jdk.jfr.internal.LogTag;
////import jdk.jfr.internal.Logger;
//import org.graalvm.nativeimage.c.type.CCharPointer;
//import org.graalvm.nativeimage.ImageSingletons;
//import org.graalvm.nativeimage.Platform;
//import org.graalvm.nativeimage.Platforms;
//import org.graalvm.nativeimage.StackValue;
//
//import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
//import com.oracle.svm.core.Uninterruptible;
//import com.oracle.svm.core.jfr.JfrEmergencyDumpFeature;
//import com.oracle.svm.core.jfr.JfrEmergencyDumpSupport;
//import com.oracle.svm.core.memory.NativeMemory;
//import com.oracle.svm.core.memory.NullableNativeMemory;
//import com.oracle.svm.core.os.RawFileOperationSupport;
//import com.oracle.svm.core.os.RawFileOperationSupport.FileAccessMode;
//import com.oracle.svm.core.os.RawFileOperationSupport.FileCreationMode;
//import com.oracle.svm.core.os.RawFileOperationSupport.RawFileDescriptor;
//import com.oracle.svm.core.util.BasedOnJDKFile;
//import com.oracle.svm.core.collections.GrowableWordArray;
//import com.oracle.svm.core.collections.GrowableWordArrayAccess;
//
//import jdk.graal.compiler.api.replacements.Fold;
//
//import java.nio.charset.StandardCharsets;
//
//import static com.oracle.svm.core.posix.headers.Fcntl.O_NOFOLLOW;
//import static com.oracle.svm.core.posix.headers.Fcntl.O_RDONLY;
//
//import static org.junit.Assert.assertEquals;
//
//public class TestGrowableWordArrayQuickSort {
//    @Test
//    public void test() throws Throwable {
//        GrowableWordArray gwa = StackValue.get(GrowableWordArray.class);
//        GrowableWordArrayAccess.initialize(gwa);
//        GrowableWordArrayAccess.add(gwa, WordFactory.unsigned(3), NmtCategory.JFR);
//        GrowableWordArrayAccess.add(gwa, WordFactory.unsigned(1), NmtCategory.JFR);
//        GrowableWordArrayAccess.add(gwa, WordFactory.unsigned(5), NmtCategory.JFR);
//        GrowableWordArrayAccess.add(gwa, WordFactory.unsigned(4), NmtCategory.JFR);
//        GrowableWordArrayAccess.add(gwa, WordFactory.unsigned(2), NmtCategory.JFR);
//        GrowableWordArrayAccess.add(gwa, WordFactory.unsigned(4), NmtCategory.JFR);
//        qsort(gwa, 0,5);
//        for (int i=0; i < 6; i ++){
//            System.out.println(GrowableWordArrayAccess.read(gwa, i).rawValue());
//        }
//    }
//
//    private static void qsort(GrowableWordArray gwa, int low, int high) {
//        if (low < high) {
//            int pivotIndex = partition(gwa, low, high);
//
//            qsort(gwa, low, pivotIndex - 1);
//            qsort(gwa, pivotIndex + 1, high);
//        }
//    }
//
//    private static int partition(GrowableWordArray gwa, int low, int high) {
//        // Choose the last element as pivot
//        CCharPointer pivot = GrowableWordArrayAccess.read(gwa, high);
//
//        // Pointer for the greater element
//        int i = low - 1;
//
//        // Traverse through all elements
//        // If element is smaller than or equal to pivot, swap it
//        for (int j = low; j < high; j++) {
//            if (compare(GrowableWordArrayAccess.read(gwa, j), pivot)) {
//                i++;
//
//                // Swap elements at i and j
//                CCharPointer temp = GrowableWordArrayAccess.read(gwa, i);
//                GrowableWordArrayAccess.write(gwa,i, GrowableWordArrayAccess.read(gwa, j));
//                GrowableWordArrayAccess.write(gwa, j, temp);
//            }
//        }
//
//        // Swap the pivot element with the element at i+1
//        CCharPointer temp = GrowableWordArrayAccess.read(gwa, i + 1);
//        GrowableWordArrayAccess.write(gwa, i + 1, GrowableWordArrayAccess.read(gwa, high));
//        GrowableWordArrayAccess.write(gwa, high, temp);
//
//        // Return the partition index
//        return i + 1;
//    }
//
////    static boolean compare(CCharPointer a, CCharPointer b) {
////        // Use strcmp(CCharPointer s1, CCharPointer s2)  and  strchr(CCharPointer str, int c)
////
////        return a.read() <= b.read();
////    }
//
//    static boolean compare(CCharPointer a, CCharPointer b) {
//        // Use strcmp(CCharPointer s1, CCharPointer s2)  and  strchr(CCharPointer str, int c)
//
//        return a.rawValue() <= b.rawValue();
//    }
//
//}
