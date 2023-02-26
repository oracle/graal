/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test;

import com.oracle.svm.core.containers.CgroupSubsystemFactory;
import com.oracle.svm.core.containers.CgroupSubsystemFactory.CgroupTypeResult;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Optional;

public class CgroupSubsystemFactoryTest {

    private static final String MOUNT_INFO_CG2 = "29 23 0:26 / /sys/fs/cgroup rw,nosuid,nodev,noexec,relatime shared:4 - cgroup2 cgroup2 rw,seclabel,nsdelegate,memory_recursiveprot\n" +
                    "23 60 0:22 / /sys rw,nosuid,nodev,noexec,relatime shared:2 - sysfs sysfs rw,seclabel\n";
    private static final String CGROUPS_CG2 = "#subsys_name  hierarchy   num_cgroups enabled\n" + "cpuset\t0\t142\t1\n" + "cpu\t0\t142\t1\n" + "cpuacct\t0\t142\t1\n" + "blkio\t0\t142\t1\n" +
                    "memory\t0\t142\t1\n" + "devices\t0\t142\t1\n" + "freezer\t0\t142\t1\n" + "net_cls\t0\t142\t1\n" + "perf_event\t0\t142\t1\n" + "net_prio\t0\t142\t1\n" + "hugetlb\t0\t142\t1\n" +
                    "pids\t0\t142\t1\n" + "misc\t0\t142\t1\n";

    private static final String MOUNT_INFO_CG1 = "23 60 0:22 / /sys rw,nosuid,nodev,noexec,relatime shared:2 - sysfs sysfs rw,seclabel\n" +
                    "30 24 0:27 / /sys/fs/cgroup ro,nosuid,nodev,noexec shared:4 - tmpfs tmpfs ro,seclabel,size=4096k,nr_inodes=1024,mode=755,inode64\n" +
                    "31 30 0:28 / /sys/fs/cgroup/unified rw,nosuid,nodev,noexec,relatime shared:5 - cgroup2 cgroup2 rw,seclabel,nsdelegate\n" +
                    "32 30 0:29 / /sys/fs/cgroup/systemd rw,nosuid,nodev,noexec,relatime shared:6 - cgroup cgroup rw,seclabel,xattr,name=systemd\n" +
                    "35 30 0:32 / /sys/fs/cgroup/cpuset rw,nosuid,nodev,noexec,relatime shared:7 - cgroup cgroup rw,seclabel,cpuset\n" +
                    "36 30 0:33 / /sys/fs/cgroup/perf_event rw,nosuid,nodev,noexec,relatime shared:8 - cgroup cgroup rw,seclabel,perf_event\n" +
                    "37 30 0:34 / /sys/fs/cgroup/freezer rw,nosuid,nodev,noexec,relatime shared:9 - cgroup cgroup rw,seclabel,freezer\n" +
                    "38 30 0:35 / /sys/fs/cgroup/net_cls,net_prio rw,nosuid,nodev,noexec,relatime shared:10 - cgroup cgroup rw,seclabel,net_cls,net_prio\n" +
                    "39 30 0:36 / /sys/fs/cgroup/pids rw,nosuid,nodev,noexec,relatime shared:11 - cgroup cgroup rw,seclabel,pids\n" +
                    "40 30 0:37 / /sys/fs/cgroup/blkio rw,nosuid,nodev,noexec,relatime shared:12 - cgroup cgroup rw,seclabel,blkio\n" +
                    "41 30 0:38 / /sys/fs/cgroup/hugetlb rw,nosuid,nodev,noexec,relatime shared:13 - cgroup cgroup rw,seclabel,hugetlb\n" +
                    "42 30 0:39 / /sys/fs/cgroup/misc rw,nosuid,nodev,noexec,relatime shared:14 - cgroup cgroup rw,seclabel,misc\n" +
                    "43 30 0:40 / /sys/fs/cgroup/cpu,cpuacct rw,nosuid,nodev,noexec,relatime shared:15 - cgroup cgroup rw,seclabel,cpu,cpuacct\n" +
                    "44 30 0:41 / /sys/fs/cgroup/devices rw,nosuid,nodev,noexec,relatime shared:16 - cgroup cgroup rw,seclabel,devices\n" +
                    "45 30 0:42 / /sys/fs/cgroup/memory rw,nosuid,nodev,noexec,relatime shared:17 - cgroup cgroup rw,seclabel,memory\n";
    private static final String CGROUPS_CG1 = "#subsys_name  hierarchy   num_cgroups enabled\n" + "cpuset\t2\t9\t1\n" + "cpu\t10\t21\t1\n" + "cpuacct\t10\t21\t1\n" + "blkio\t7\t16\t1\n" +
                    "memory\t12\t98\t1\n" + "devices\t11\t87\t1\n" + "freezer\t4\t4\t1\n" + "net_cls\t5\t4\t1\n" + "perf_event\t3\t4\t1\n" + "net_prio\t5\t4\t1\n" + "hugetlb\t8\t1\t1\n" +
                    "pids\t6\t90\t1\n" + "misc\t9\t1\t1\n";

    private static final String MOUNT_INFO_NO_CGFS = "23 60 0:22 / /sys rw,nosuid,nodev,noexec,relatime shared:2 - sysfs sysfs rw,seclabel\n";

    @Test
    public void determineTypeCgroupsV2() {
        try (CgroupMountInfoStub fixture = new CgroupMountInfoStub(MOUNT_INFO_CG2, CGROUPS_CG2)) {
            Path cgroups = fixture.createCgroups();
            Path mountinfo = fixture.createMountInfo();
            Optional<CgroupTypeResult> result = CgroupSubsystemFactory.determineType(mountinfo.toString(), cgroups.toString());
            Assert.assertTrue("Expected a non-empty result", result.isPresent());
            CgroupTypeResult actual = result.get();
            Assert.assertNotNull(actual);
            Assert.assertTrue("Expected cgroup v2", actual.isCgroupV2());
        } catch (IOException e) {
            fail("Unexpected failure " + e.getMessage());
        }
    }

    @Test
    public void determineTypeCgroupsV1() {
        try (CgroupMountInfoStub fixture = new CgroupMountInfoStub(MOUNT_INFO_CG1, CGROUPS_CG1)) {
            Path cgroups = fixture.createCgroups();
            Path mountinfo = fixture.createMountInfo();
            Optional<CgroupTypeResult> result = CgroupSubsystemFactory.determineType(mountinfo.toString(), cgroups.toString());
            Assert.assertTrue("Expected a non-empty result", result.isPresent());
            CgroupTypeResult actual = result.get();
            Assert.assertNotNull(actual);
            Assert.assertTrue("Expected cgroup v1", !actual.isCgroupV2());
        } catch (IOException e) {
            fail("Unexpected failure " + e.getMessage());
        }
    }

    @Test
    public void determineTypeCgroupsV1NoCgroupMount() {
        try (CgroupMountInfoStub fixture = new CgroupMountInfoStub(MOUNT_INFO_NO_CGFS, CGROUPS_CG2)) {
            Path cgroups = fixture.createCgroups();
            Path mountinfo = fixture.createMountInfo();
            Optional<CgroupTypeResult> result = CgroupSubsystemFactory.determineType(mountinfo.toString(), cgroups.toString());
            Assert.assertFalse("Expected a empty result", result.isPresent());
        } catch (IOException e) {
            fail("Unexpected failure " + e.getMessage());
        }
    }

    public static class CgroupMountInfoStub implements AutoCloseable {

        private final String mountInfoContent;
        private final String cgroupsContent;
        private final Path tmpDir;
        private Path mountInfo;
        private Path cgroups;

        public CgroupMountInfoStub(String mountInfoContent, String cgroupsContent) throws IOException {
            this.mountInfoContent = mountInfoContent;
            this.cgroupsContent = cgroupsContent;
            this.tmpDir = Files.createTempDirectory(CgroupSubsystemFactoryTest.class.getSimpleName());
        }

        synchronized Path createMountInfo() throws IOException {
            if (mountInfo == null) {
                mountInfo = writeTmpFile(tmpDir, "proc-self-mountinfo", mountInfoContent);
            }
            return mountInfo;
        }

        synchronized Path createCgroups() throws IOException {
            if (cgroups == null) {
                cgroups = writeTmpFile(tmpDir, "proc-cgroups", cgroupsContent);
            }
            return cgroups;
        }

        private static Path writeTmpFile(Path parent, String name, String content) throws IOException {
            Path file = Objects.requireNonNull(parent).resolve(name);
            Files.writeString(file, content);
            return file;
        }

        @Override
        public void close() throws IOException {
            // Recursively delete the temporary directory of stub files
            Files.walkFileTree(tmpDir, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }

            });

        }
    }
}
