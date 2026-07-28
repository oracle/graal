/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.extractor;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

import com.oracle.svm.configure.ConfigurationUsageException;

/**
 * Abstract class that locates the offset and size of an embedded resource in an image. The embedded
 * resource has exported symbols pointing to its location and size. A {@link ResourceLocator} parses
 * the file format to find the file offset and size of the resource.
 */
abstract class ResourceLocator {
    protected final MemorySegment segment;
    private final long fileSize;
    private static final String SYMBOL_LENGTH_SUFFIX = "_length";

    protected record SymbolAddresses(long resourceVirtualAddress, long lengthVirtualAddress) {
    }

    ResourceLocator(MemorySegment segment) {
        this.segment = segment;
        this.fileSize = segment.byteSize();
    }

    /**
     * Checks if the provided memory segment matches the magic bytes for this locator's supported
     * format. This must be checked to return true before calling {@link #locateResource(String)} on
     * this instance.
     *
     * @return true if the segment matches this locator's format, false otherwise
     */
    abstract boolean matchesMagic();

    /**
     * Parses the image to locate the offset and size of the resource identifiable by its symbol.
     *
     * Check the magic before calling this method via {@link ResourceLocator#matchesMagic()}. Use
     * the {@link ResourceLocatorFactory} to create a suitable {@link ResourceLocator resource
     * locator}.
     *
     * @param symbol the name of the symbol to locate
     * @return a {@link ResourceLocation} containing the resource offset and size
     * @throws ConfigurationUsageException if parsing fails or symbols are not found
     */
    ResourceLocation locateResource(String symbol) throws ConfigurationUsageException {
        assert matchesMagic() : "Magic does not match the expected value for file format %s.".formatted(getSupportedFileFormat());
        ResourceLocation resourceLocation = locateResourceImpl(symbol);
        validateResourceLocation(resourceLocation);
        return resourceLocation;
    }

    protected abstract ResourceLocation locateResourceImpl(String symbol) throws ConfigurationUsageException;

    /**
     * Returns a human-readable description of the file format(s) supported by this locator.
     *
     * @return the supported file format(s), e.g., "Mach-O 64-bit"
     */
    abstract String getSupportedFileFormat();

    /**
     * Determines the endianness used for the {@code getUInt} methods.
     */
    abstract ByteOrder getByteOrder();

    /**
     * Returns the symbol name that points to the resource size in bytes.
     */
    protected static String lengthSymbolName(String symbol) {
        return symbol + SYMBOL_LENGTH_SUFFIX;
    }

    protected int getUInt8(long pos) {
        return getUInt8(segment, pos, getByteOrder());
    }

    private static int getUInt8(MemorySegment segment, long pos, ByteOrder byteOrder) {
        return Byte.toUnsignedInt(segment.get(ValueLayout.JAVA_BYTE.withOrder(byteOrder), pos));
    }

    protected int getUInt16(long pos) {
        return getUInt16(segment, pos, getByteOrder());
    }

    private static int getUInt16(MemorySegment segment, long pos, ByteOrder byteOrder) {
        return Short.toUnsignedInt(segment.get(ValueLayout.JAVA_SHORT.withOrder(byteOrder), pos));
    }

    protected long getUInt32(long pos) {
        return getUInt32(segment, pos, getByteOrder());
    }

    private static long getUInt32(MemorySegment segment, long pos, ByteOrder byteOrder) {
        return Integer.toUnsignedLong(segment.get(ValueLayout.JAVA_INT.withOrder(byteOrder), pos));
    }

    protected long getUInt64(long pos) {
        return segment.get(ValueLayout.JAVA_LONG.withOrder(getByteOrder()), pos);
    }

    protected String getNullTerminatedString(long pos) {
        try {
            return getNullTerminatedString(pos, Long.MAX_VALUE, false);
        } catch (IndexOutOfBoundsException e) {
            throw new ConfigurationUsageException("String was not null-terminated.");
        }
    }

    protected String getNullTerminatedString(long pos, long maxLength) {
        return getNullTerminatedString(pos, maxLength, true);
    }

    /**
     * Reads a null-terminated string starting at the given position, up to the specified maximum
     * length.
     *
     * @param pos the starting position in the memory segment
     * @param maxLength the maximum number of bytes to read before throwing an exception if no null
     *            terminator is found
     * @param validateRange boolean deciding if range verification performed on pos and maxLength
     * @return the string read from the segment
     * @throws ConfigurationUsageException if no null terminator is found within maxLength or if
     *             offsets are invalid
     */
    protected String getNullTerminatedString(long pos, long maxLength, boolean validateRange) {
        if (validateRange) {
            validateRange(pos, maxLength);
        }
        StringBuilder sb = new StringBuilder();
        for (long i = 0; i < maxLength; i++) {
            long charPos = Math.addExact(pos, i);
            byte b = segment.get(ValueLayout.JAVA_BYTE, charPos);
            if (b == 0) {
                return sb.toString();
            }
            sb.append((char) b);
        }
        throw new ConfigurationUsageException("Expected null terminated string was not null terminated.");
    }

    protected String getFixedLengthString(long pos, int length) {
        validateRange(pos, length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            long charPos = Math.addExact(pos, i);
            byte b = segment.get(ValueLayout.JAVA_BYTE, charPos);
            sb.append((char) b);
        }
        return sb.toString();
    }

    protected static ConfigurationUsageException symbolsNotFoundException(String resourceSymbol, String resourceLengthSymbol) {
        return new ConfigurationUsageException("Could not find '%s' and '%s' symbols. This likely means the image does not contain the embedded resource."
                        .formatted(resourceSymbol, resourceLengthSymbol));
    }

    private void validateResourceLocation(ResourceLocation resourceLocation) {
        validateRange(resourceLocation.offset(), resourceLocation.size());
        if (resourceLocation.size() <= 0) {
            throw new ConfigurationUsageException("Invalid resource size: %s.".formatted(resourceLocation.size()));
        }
    }

    protected void validateRange(long offset, long length) {
        validateRange(offset, length, fileSize);
    }

    /**
     * Validates that the given offset and length form a valid range within the file's size.
     *
     * @param offset the starting offset
     * @param length the length of the range
     * @param fileSize the total size of the file
     * @throws ConfigurationUsageException if the range is invalid (negative, overflow, or exceeds
     *             file size)
     */
    private static void validateRange(long offset, long length, long fileSize) {
        try {
            if (offset < 0) {
                throw new ConfigurationUsageException("Negative offset.");
            }
            if (length < 0) {
                throw new ConfigurationUsageException("Negative length.");
            }
            long addedOffset = Math.addExact(offset, length);
            if (addedOffset > fileSize) {
                throw new ConfigurationUsageException("Offset exceeds file size (%s > %s).".formatted(addedOffset, fileSize));
            }
        } catch (ArithmeticException e) {
            throw new ConfigurationUsageException("Invalid offset or address.");
        }
    }
}
