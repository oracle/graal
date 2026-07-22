/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.imagelayer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.GraalError;

public final class ImageLayerGraphStore {
    private final FileChannel channel;
    private final AtomicLong currentOffset;

    private ImageLayerGraphStore(FileChannel channel, AtomicLong currentOffset) {
        this.channel = channel;
        this.currentOffset = currentOffset;
    }

    public static ImageLayerGraphStore openForWriting(Path snapshotGraphsPath) {
        try {
            Files.createFile(snapshotGraphsPath);
            return new ImageLayerGraphStore(FileChannel.open(snapshotGraphsPath, EnumSet.of(StandardOpenOption.WRITE)), new AtomicLong(0));
        } catch (IOException e) {
            throw VMError.shouldNotReachHere("Error opening temporary graphs file " + snapshotGraphsPath, e);
        }
    }

    public static ImageLayerGraphStore openForReading(Path snapshotGraphsPath) {
        try {
            return new ImageLayerGraphStore(FileChannel.open(snapshotGraphsPath), null);
        } catch (IOException e) {
            throw VMError.shouldNotReachHere("Error opening layer snapshot graphs file " + snapshotGraphsPath, e);
        }
    }

    public String write(byte[] encodedGraph) {
        AnalysisError.guarantee(currentOffset != null, "Cannot write to a graph store opened for reading.");
        long offset = currentOffset.getAndAdd(encodedGraph.length);
        try {
            channel.write(ByteBuffer.wrap(encodedGraph), offset);
        } catch (Exception e) {
            throw GraalError.shouldNotReachHere(e, "Error during graphs file dumping.");
        }
        return toLocation(offset, encodedGraph.length);
    }

    public byte[] read(String location) {
        Location parsedLocation = parseLocation(location);
        ByteBuffer bb = ByteBuffer.allocate(NumUtil.safeToInt(parsedLocation.nbytes()));
        try {
            channel.read(bb, parsedLocation.offset());
        } catch (IOException e) {
            throw AnalysisError.shouldNotReachHere("Failed reading a graph from location: " + location, e);
        }
        return bb.array();
    }

    public void close() {
        try {
            channel.close();
        } catch (Exception e) {
            throw VMError.shouldNotReachHere("Error closing graphs file.", e);
        }
    }

    private static String toLocation(long offset, int length) {
        return "@" + offset + "[" + length + "]";
    }

    private static Location parseLocation(String location) {
        int closingBracketAt = location.length() - 1;
        AnalysisError.guarantee(location.charAt(0) == '@' && location.charAt(closingBracketAt) == ']', "Location must start with '@' and end with ']': %s", location);
        int openingBracketAt = location.indexOf('[', 1, closingBracketAt);
        AnalysisError.guarantee(openingBracketAt < closingBracketAt, "Location does not contain '[' at expected location: %s", location);
        try {
            long offset = Long.parseUnsignedLong(location.substring(1, openingBracketAt));
            long nbytes = Long.parseUnsignedLong(location.substring(openingBracketAt + 1, closingBracketAt));
            return new Location(offset, nbytes);
        } catch (NumberFormatException e) {
            throw AnalysisError.shouldNotReachHere("Location contains invalid positive integer(s): " + location);
        }
    }

    private record Location(long offset, long nbytes) {
    }
}
