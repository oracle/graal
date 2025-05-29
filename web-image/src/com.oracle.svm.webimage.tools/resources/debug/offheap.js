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

const heapDebug = {
    /** Renders the state of the allocator into the canvas element with the specified ID.
     *  The rendering includes the segments, pointers of the segment list and the free-segment list,
     *  sentinels, and the allocation bitset.
     *  This is used for debugging purposes only.
     *
     *  To make the canvas allocatable for larger heaps (> 2^19), its contents are scrollable,
     *  which is controlled with the topOffset parameter.
     *  This can be optimized to clip pages that are outside of the canvas (can be done later).
     */
    render: (canvasId, topOffset, maxCanvasHeight) => {
        const canvas = document.getElementById(canvasId);
        const ctx = canvas.getContext("2d");
        const fontMetrics = ctx.measureText("#123abcABC W");
        const fontHeight = fontMetrics.fontBoundingBoxAscent + fontMetrics.fontBoundingBoxDescent;
        const padding = 6;
        const pageHeight = 120;
        const memoryWidth = 100;
        const memoryHeight = (heap.byteSize() / PAGE_SIZE) * pageHeight;
        const pathOffsets = [30, 45, 60, 75];
        const leftOffset = 80;
        const minCanvasWidth = 480;
        const maxBitsetColumns = 32;
        const bitsetWidth = Math.min(maxBitsetColumns, heap._pageCount()) * fontHeight;
        const bitsetRowHeight = 24;
        const bucketFragmentWidth = 32;
        const bucketFragmentHeight = 20;
        const bucketRowHeight = bucketFragmentHeight;
        const bucketMaxHeight = pageHeight - 2 * padding;
        const bucketMaxRowCount = Math.floor(bucketMaxHeight / bucketRowHeight);
        const bucketHeaderWidth = 60;
        const bucketMaxColumnCount = Math.ceil(MAX_BUCKET_FRAGMENTS / bucketMaxRowCount);
        const bucketMaxWidth = bucketHeaderWidth + bucketMaxColumnCount * bucketFragmentWidth;
        const contentWidth = Math.max(bitsetWidth, bucketMaxWidth);
        const freeBucketCellWidth = 32;
        const freeBucketCellHeight = 16;
        const freeBucketListHeadsOffsetX = 16;
        const freeBucketListTailsOffsetX = 16 + freeBucketCellWidth / 2;
        const freeBucketListWidth =
            (MAX_FRAGMENT_SIZE_EXPONENT - MIN_FRAGMENT_SIZE_EXPONENT + 1) * freeBucketCellWidth +
            Math.max(freeBucketListHeadsOffsetX, freeBucketListTailsOffsetX);
        const memoryOffset = memoryWidth + pathOffsets[3] + padding;
        const contentOffset = memoryOffset + contentWidth + padding;
        const freeBucketListOffset = contentOffset + freeBucketListWidth + padding;
        const requiredWidth = leftOffset + freeBucketListOffset;
        const canvasWidth = Math.max(minCanvasWidth, requiredWidth);
        const canvasHeight = memoryHeight + (heap._pageCount() / maxBitsetColumns) * bitsetRowHeight;
        const textLeftOffset = 15;
        const textTopOffset = 15;
        const pointerOffset = 6;
        const fontOffset = 3;
        const arrowOffset = 7;
        const colorFree = "rgba(50, 200, 50, 0.1)";
        const colorFreeHighlight = "rgba(50, 200, 50, 0.2)";
        const colorAllocated = "rgba(200, 50, 50, 0.1)";
        const colorAllocatedHighlight = "rgba(200, 50, 50, 0.3)";
        const colorError = "rgba(250, 50, 50, 0.9)";
        canvas.width = canvasWidth;
        canvas.height = Math.min(canvasHeight + 2, maxCanvasHeight);

        ctx.translate(leftOffset, topOffset);

        // Outline and pages.
        function cellOffset(pageIndex) {
            return pageIndex * pageHeight + textTopOffset + 2 * fontHeight;
        }
        function readCell(pageIndex, cellIndex) {
            return heap._memory.getUint32(pageIndex * PAGE_SIZE + cellIndex * 4);
        }
        function renderPageCell(pageIndex, cellIndex, text) {
            const y = cellOffset(pageIndex) + cellIndex * fontHeight;
            ctx.strokeRect(0, y - fontHeight + fontOffset, memoryWidth, fontHeight);
            ctx.fillText(text, textLeftOffset, y);
        }

        ctx.lineWidth = 1;
        for (let i = 0; i < heap._pageCount(); i++) {
            ctx.strokeStyle = "#bbbbbb";
            const address = i * PAGE_SIZE;
            ctx.strokeRect(0, i * pageHeight, memoryWidth, pageHeight);
            ctx.fillText("#" + i + " at 0x" + address.toString(16), textLeftOffset, i * pageHeight + textTopOffset);
            const sizeText = "size " + segment.size(address);
            ctx.fillText(sizeText, textLeftOffset, i * pageHeight + textTopOffset + fontHeight);

            // Render raw page data.
            ctx.strokeStyle = "#eeeeee";
            renderPageCell(i, 0, "0x" + readCell(i, 0).toString(16));
            renderPageCell(i, 1, "0x" + readCell(i, 1).toString(16));
            renderPageCell(i, 2, "0x" + readCell(i, 2).toString(16));
            renderPageCell(i, 3, "0x" + readCell(i, 3).toString(16));
        }
        ctx.lineWidth = 1;
        ctx.strokeStyle = "#000000";
        ctx.strokeRect(1, 1, memoryWidth, memoryHeight);

        // Segments.
        let cur = segment.leftSentinel();
        while (cur < heap.byteSize()) {
            const free = segment.isFree(cur);
            const size = segment.size(cur);
            if (free) {
                ctx.fillStyle = colorFree;
            } else {
                ctx.fillStyle = colorAllocated;
            }
            ctx.fillRect(0, (cur / PAGE_SIZE) * pageHeight, memoryWidth, (size / PAGE_SIZE) * pageHeight);
            ctx.lineWidth = 2;
            ctx.strokeRect(1, (cur / PAGE_SIZE) * pageHeight, memoryWidth, (size / PAGE_SIZE) * pageHeight);
            const next = segment.next(cur);
            if (next <= cur) {
                ctx.fillStyle = colorError;
                ctx.fillText("Corrupted heap: invalid segment list.", 2, (cur / PAGE_SIZE + 1) * pageHeight - 2);
                return;
            }
            cur = next;
        }

        function renderPointer(pageIndex, cellIndex, targetPageAddress, pathOffset, destinationOffset, down) {
            const targetPageIndex = targetPageAddress / PAGE_SIZE;
            const ysrc = cellOffset(pageIndex) + cellIndex * fontHeight - fontHeight / 4;
            let outOfBounds = false;
            let ydst = targetPageIndex * pageHeight + fontHeight / 2 + destinationOffset;
            if (targetPageAddress === NON_FREE_SEGMENT) {
                ydst = down ? (heap.byteSize() / PAGE_SIZE) * pageHeight : 0;
                outOfBounds = true;
            } else if (targetPageAddress >= heap.byteSize()) {
                ydst = (heap.byteSize() / PAGE_SIZE) * pageHeight;
                outOfBounds = true;
            }
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(memoryWidth - pointerOffset, ysrc);
            ctx.lineTo(memoryWidth + pathOffset, ysrc);
            ctx.lineTo(memoryWidth + pathOffset, ydst);
            if (outOfBounds) {
                ctx.lineTo(memoryWidth + pathOffset + arrowOffset / 2, ydst + arrowOffset * (down ? -1 : 1));
                ctx.moveTo(memoryWidth + pathOffset, ydst);
                ctx.lineTo(memoryWidth + pathOffset - arrowOffset / 2, ydst + arrowOffset * (down ? -1 : 1));
                ctx.stroke();
                ctx.fillText("OOB", memoryWidth + pathOffset + 4, ydst + (down ? 0 : fontHeight));
            } else {
                ctx.lineTo(memoryWidth, ydst);
                ctx.lineTo(memoryWidth + arrowOffset, ydst - arrowOffset / 2);
                ctx.moveTo(memoryWidth, ydst);
                ctx.lineTo(memoryWidth + arrowOffset, ydst + arrowOffset / 2);
                ctx.stroke();
            }
            ctx.beginPath();
            ctx.arc(memoryWidth - pointerOffset, ysrc, 2.5, 0, 2 * Math.PI, true);
            ctx.fill();
        }

        // Segment list.
        cur = segment.leftSentinel();
        let segidx = 0;
        ctx.fillStyle = "rgba(0, 0, 0, 1.0)";
        ctx.strokeStyle = "#000000";
        while (cur < heap.byteSize()) {
            const next = segment.next(cur);
            const prev = segment.prev(cur);
            const pageIndex = cur / PAGE_SIZE;
            const alter = segidx % 2;
            renderPointer(pageIndex, 2, next, pathOffsets[0], 0, true);
            renderPointer(pageIndex, 3, prev, pathOffsets[1 + alter], (1 + alter) * 1.6 * arrowOffset, false);
            cur = segment.next(cur);
            segidx++;
        }

        // Free-segment list.
        ctx.save();
        ctx.scale(-1, 1);
        ctx.translate(-memoryWidth, 0);
        cur = segment.leftSentinel();
        segidx = 0;
        while (cur !== NON_FREE_SEGMENT) {
            const next = segment.nextFree(cur);
            const prev = segment.prevFree(cur);
            const pageIndex = cur / PAGE_SIZE;
            const alter = segidx % 2;
            renderPointer(pageIndex, 0, next, pathOffsets[0], 0, true);
            renderPointer(pageIndex, 1, prev, pathOffsets[1 + alter], (1 + alter) * 1.6 * arrowOffset, false);
            cur = segment.nextFree(cur);
            segidx++;
        }
        ctx.restore();

        function rotateText(text, translationX, translationY, x, y) {
            ctx.translate(-translationX + x, -translationY + y);
            ctx.rotate(-Math.PI / 2);
            ctx.translate(translationX - x, translationY - y);
            ctx.fillText(text, x, y);
            ctx.translate(-translationX + x, -translationY + y);
            ctx.rotate(Math.PI / 2);
            ctx.translate(translationX - x, translationY - y);
        }

        // Right sentinel state.
        const rightSentinelAddress = segment.rightSentinel();
        const rightSentinelOffset = (rightSentinelAddress / PAGE_SIZE) * pageHeight;
        ctx.save();
        ctx.translate(memoryOffset, 0);
        segidx = 0;
        ctx.strokeStyle = "#dddddd";
        while (segidx < heap._pageCount()) {
            const bitsetx = (segidx % maxBitsetColumns) * fontHeight;
            const bitsety = rightSentinelOffset + Math.floor(segidx / maxBitsetColumns) * bitsetRowHeight;
            ctx.strokeRect(bitsetx, bitsety + fontHeight, fontHeight, bitsetRowHeight);
            if (heap._allocBit(segidx * PAGE_SIZE) === 1) {
                ctx.fillStyle = colorAllocated;
            } else {
                ctx.fillStyle = colorFree;
            }
            ctx.strokeRect(bitsetx, bitsety + fontHeight, fontHeight, bitsetRowHeight);
            ctx.fillRect(bitsetx, bitsety + fontHeight, fontHeight, bitsetRowHeight);
            ctx.fillStyle = "#000000";
            rotateText(segidx, -fontHeight / 2, 0, bitsetx + fontHeight / 3, bitsety + bitsetRowHeight);
            segidx++;
        }
        ctx.strokeStyle = "#000000";
        ctx.strokeRect(
            0,
            rightSentinelOffset + fontHeight,
            Math.min(maxBitsetColumns, segidx) * fontHeight,
            Math.floor(segidx / maxBitsetColumns) * bitsetRowHeight
        );
        ctx.fillStyle = "#000000";
        ctx.fillText("alloc bitset", 0, rightSentinelOffset + fontHeight / 2);
        ctx.restore();

        // Buckets and free-lists.
        const freeListWidth = bucketMaxColumnCount * bucketFragmentWidth;
        const bucketHeaderHeight = 2 * fontHeight;

        function renderBucketCell(yoffset, cellIndex, text) {
            const y = yoffset + bucketHeaderHeight + fontHeight * cellIndex;
            ctx.strokeRect(freeListWidth, y, bucketHeaderWidth, fontHeight);
            ctx.fillText(text, freeListWidth + padding, y + fontHeight - fontOffset);
        }

        ctx.save();
        ctx.translate(memoryOffset, 0);
        cur = segment.leftSentinel();
        const fragmentFree = new Uint8Array(MAX_BUCKET_FRAGMENTS);
        while (cur < heap.byteSize()) {
            if (segment.isBucket(cur)) {
                fragmentFree.fill(0, 0, MAX_BUCKET_FRAGMENTS);
                const bucketAddress = cur + SEGMENT_HEADER_SIZE;
                const fragmentSizeExponent = bucket.fragmentSizeExponent(bucketAddress);
                const bucketWidth = bucketHeaderWidth + freeListWidth;
                const bucketHeight = bucketMaxRowCount * bucketFragmentHeight;
                ctx.strokeStyle = "#dddddd";
                ctx.fillStyle = colorFree;
                const bucketY = padding + (cur / PAGE_SIZE) * pageHeight;
                ctx.strokeRect(0, bucketY, freeListWidth, bucketHeight);
                ctx.fillRect(0, bucketY, freeListWidth, bucketHeight);
                ctx.strokeRect(freeListWidth, bucketY, bucketHeaderWidth, bucketHeight);
                ctx.fillRect(freeListWidth, bucketY, bucketHeaderWidth, bucketHeight);
                ctx.fillStyle = "#000000";
                renderBucketCell(bucketY, 0, "0x" + bucket.next(bucketAddress).toString(16));
                renderBucketCell(bucketY, 1, "0x" + bucket.prev(bucketAddress).toString(16));
                const freeListHead = bucket.freeList(bucketAddress);
                renderBucketCell(bucketY, 2, "head " + (freeListHead === FREE_LIST_NULL ? "NULL" : freeListHead));
                renderBucketCell(bucketY, 3, bucket.freeCount(bucketAddress) + " free");
                renderBucketCell(bucketY, 4, "2^" + bucket.fragmentSizeExponent(bucketAddress) + " B");

                const sizeExponentiator = fragmentSizeExponent - MIN_FRAGMENT_SIZE_EXPONENT;
                const trueMaxColumnCount = Math.ceil(bucketMaxColumnCount / (1 << sizeExponentiator));
                const trueFragmentWidth = freeListWidth / trueMaxColumnCount;

                const xyFragment = (yoffset, offset) => {
                    const idx = offset >> fragmentSizeExponent;
                    const xidx = idx % trueMaxColumnCount;
                    const yidx = Math.floor(idx / trueMaxColumnCount);
                    const x = xidx * trueFragmentWidth;
                    const y = yoffset + yidx * bucketFragmentHeight;
                    return [x, y];
                };

                const renderFragment = (yoffset, offset, mustFill) => {
                    const xy = xyFragment(yoffset, offset);
                    const x = xy[0];
                    const y = xy[1];
                    ctx.strokeStyle = "#dddddd";
                    ctx.strokeRect(x, y, trueFragmentWidth, bucketFragmentHeight);
                    if (mustFill) {
                        ctx.fillStyle = colorAllocated;
                        ctx.fillRect(x, y, trueFragmentWidth, bucketFragmentHeight);
                    }
                    ctx.fillStyle = "#777777";
                    ctx.fillText(offset, x + 2 * fontOffset, y + bucketFragmentHeight - fontOffset);
                };

                const renderFreeListPointer = (yoffset, src, dst) => {
                    const srcxy = xyFragment(yoffset, src);
                    const x = srcxy[0] + trueFragmentWidth - padding + 0.5;
                    const y = srcxy[1] + bucketFragmentHeight / 2 + 0.5;
                    ctx.strokeStyle = "#aaaaaa";
                    ctx.lineWidth = 1;
                    ctx.moveTo(x, y);
                    if (dst === FREE_LIST_NULL) {
                        ctx.lineTo(x, y + bucketFragmentHeight / 4);
                        ctx.lineTo(x + padding / 2, y + bucketFragmentHeight / 4);
                        ctx.lineTo(x - padding / 2, y + bucketFragmentHeight / 4);
                    } else {
                        const dstxy = xyFragment(yoffset, dst);
                        const dstx = dstxy[0] + padding - 0.5;
                        const dsty = dstxy[1] + bucketFragmentHeight / 2 + 0.5;
                        ctx.lineTo(dstx, dsty);
                        const dx = dstx - x;
                        const dy = dsty - y;
                        const dist = Math.sqrt(dx * dx + dy * dy);
                        const nx = dx / dist;
                        const ny = dy / dist;
                        const arrow1x = (+0.93 * nx - 0.34 * ny) * arrowOffset;
                        const arrow1y = (+0.34 * nx + 0.93 * ny) * arrowOffset;
                        const arrow2x = (+0.93 * nx + 0.34 * ny) * arrowOffset;
                        const arrow2y = (-0.34 * nx + 0.93 * ny) * arrowOffset;
                        ctx.lineTo(dstx - arrow1x, dsty - arrow1y);
                        ctx.moveTo(dstx, dsty);
                        ctx.lineTo(dstx - arrow2x, dsty - arrow2y);
                    }
                    ctx.stroke();
                    ctx.beginPath();
                    ctx.arc(x, y, 2, 0, 2 * Math.PI, true);
                    ctx.fill();
                };

                // Free-list.
                // Bucket allocation bitset.
                const freeListAddress = bucketAddress + BUCKET_HEADER_SIZE;
                let head = bucket.freeList(bucketAddress);
                while (head !== FREE_LIST_NULL) {
                    fragmentFree[head >> fragmentSizeExponent] = 1;
                    head = freeList.tail(freeListAddress, head);
                }
                const bucketBitsetOffsetX = freeListWidth + 1;
                const bucketBitsetOffsetY = bucketY + bucketHeaderHeight + 5 * fontHeight + 1;
                const bucketBitsetBitSize = 4;
                const bucketBitsetColumns = Math.min(
                    trueMaxColumnCount,
                    Math.floor(bucketHeaderWidth / bucketBitsetBitSize)
                );
                let fragoffset = 0;
                while (fragoffset + (1 << fragmentSizeExponent) < BUCKET_CAPACITY) {
                    const fragidx = fragoffset >> fragmentSizeExponent;
                    if (fragmentFree[fragidx] === 0) {
                        renderFragment(bucketY, fragoffset, true);
                    }
                    if (bucket.allocBit(bucketAddress, fragidx) === 1) {
                        ctx.fillStyle = colorAllocatedHighlight;
                    } else {
                        ctx.fillStyle = colorFreeHighlight;
                    }
                    ctx.strokeStyle = "#d3d3d3";
                    const bx = bucketBitsetOffsetX + (fragidx % bucketBitsetColumns) * bucketBitsetBitSize;
                    const by = bucketBitsetOffsetY + Math.floor(fragidx / bucketBitsetColumns) * bucketBitsetBitSize;
                    ctx.strokeRect(bx, by, bucketBitsetBitSize, bucketBitsetBitSize);
                    ctx.fillRect(bx, by, bucketBitsetBitSize, bucketBitsetBitSize);
                    fragoffset += 1 << fragmentSizeExponent;
                }
                head = bucket.freeList(bucketAddress);
                while (head !== FREE_LIST_NULL) {
                    renderFragment(bucketY, head, false);
                    head = freeList.tail(freeListAddress, head);
                }
                head = bucket.freeList(bucketAddress);
                ctx.fillStyle = "#aaaaaa";
                while (head !== FREE_LIST_NULL) {
                    const next = freeList.tail(freeListAddress, head);
                    renderFreeListPointer(bucketY, head, next);
                    head = next;
                }

                ctx.strokeStyle = "#000000";
                ctx.strokeRect(0, padding + (cur / PAGE_SIZE) * pageHeight, bucketWidth, bucketHeight);
            }
            cur = segment.next(cur);
        }
        ctx.restore();

        // Free-bucket lists.
        function renderLPointer(xoffset, yoffset, xytarget) {
            ctx.fillStyle = "#000000";
            ctx.beginPath();
            ctx.arc(xoffset, yoffset, 2.5, 0, 2 * Math.PI, true);
            ctx.fill();
            ctx.moveTo(xoffset, yoffset);
            if (xytarget == null) {
                ctx.lineTo(xoffset, yoffset + 2 * padding);
                ctx.lineTo(xoffset + arrowOffset, yoffset + 2 * padding);
                ctx.lineTo(xoffset - arrowOffset, yoffset + 2 * padding);
            } else {
                ctx.lineTo(xoffset, xytarget[1]);
                ctx.lineTo(xytarget[0], xytarget[1]);
                ctx.lineTo(xytarget[0] + arrowOffset, xytarget[1] - arrowOffset / 2);
                ctx.moveTo(xytarget[0], xytarget[1]);
                ctx.lineTo(xytarget[0] + arrowOffset, xytarget[1] + arrowOffset / 2);
            }
            ctx.stroke();
        }

        function renderCPointer(xoffset, yoffset, xytarget, distance) {
            ctx.fillStyle = "#000000";
            ctx.beginPath();
            ctx.arc(xoffset, yoffset, 2.5, 0, 2 * Math.PI, true);
            ctx.fill();
            ctx.moveTo(xoffset, yoffset);
            if (xytarget == null) {
                ctx.lineTo(xoffset + 2.0 * padding + 0.5, yoffset);
                ctx.lineTo(xoffset + 2.0 * padding + 0.5, yoffset + (padding * 2) / 3);
                ctx.lineTo(xoffset + 1.5 * padding + 0.5, yoffset + (padding * 2) / 3);
                ctx.lineTo(xoffset + 2.5 * padding + 0.5, yoffset + (padding * 2) / 3);
            } else {
                ctx.lineTo(xoffset + distance, yoffset);
                ctx.lineTo(xoffset + distance, xytarget[1]);
                ctx.lineTo(xytarget[0], xytarget[1]);
                ctx.lineTo(xytarget[0] + arrowOffset, xytarget[1] - arrowOffset / 2);
                ctx.moveTo(xytarget[0], xytarget[1]);
                ctx.lineTo(xytarget[0] + arrowOffset, xytarget[1] + arrowOffset / 2);
            }
            ctx.stroke();
        }

        function xyBucket(address, dx, dy) {
            const yoffset = Math.floor((address - SEGMENT_HEADER_SIZE) / PAGE_SIZE) * pageHeight + padding;
            return [-padding + dx, Math.floor(yoffset + dy) + 0.5];
        }

        ctx.save();
        ctx.translate(contentOffset, 0);

        function renderFreeBucketLists(getStart, getNext, sourceXOffset, sourceYOffset, targetYOffset, cellIndex) {
            for (let rank = MIN_FRAGMENT_SIZE_EXPONENT; rank <= MAX_FRAGMENT_SIZE_EXPONENT; rank++) {
                const x = (rank - MIN_FRAGMENT_SIZE_EXPONENT) * freeBucketCellWidth + sourceXOffset + 0.5;
                const y = sourceYOffset + 0.5;
                ctx.strokeStyle = "#000000";
                ctx.fillStyle = colorAllocated;
                ctx.strokeRect(x, y, freeBucketCellWidth, freeBucketCellHeight);
                ctx.fillRect(x, y, freeBucketCellWidth, freeBucketCellHeight);
                ctx.fillStyle = "#000000";
                ctx.fillText(rank, x + fontOffset, y + freeBucketCellHeight - fontOffset);
                let cur = getStart(rank);
                renderLPointer(
                    x + (freeBucketCellWidth * 3) / 4,
                    y + freeBucketCellHeight / 2,
                    cur === 0 ? null : xyBucket(cur, 0, bucketHeaderHeight / 4 + targetYOffset)
                );
                let idx = 0;
                while (cur !== 0) {
                    const xy = xyBucket(cur, -padding, bucketHeaderHeight + cellIndex * fontHeight + fontHeight / 2);
                    const next = getNext(cur);
                    const stepSize = 8;
                    const stepFrequency = Math.floor(freeBucketCellWidth / stepSize);
                    const distance = x + (idx % stepFrequency) * stepSize;
                    renderCPointer(xy[0], xy[1], next == 0 ? null : xyBucket(next, 0, targetYOffset), distance);
                    cur = next;
                    idx++;
                }
            }
        }
        ctx.fillStyle = "#000000";
        ctx.fillText("free-bucket heads", 0, fontHeight - fontOffset);
        renderFreeBucketLists(
            (rank) => freeBucket.head(rank - MIN_FRAGMENT_SIZE_EXPONENT),
            (cur) => bucket.next(cur),
            freeBucketListHeadsOffsetX,
            fontHeight,
            0,
            0
        );
        ctx.fillText(
            "free-bucket tails",
            freeBucketListTailsOffsetX,
            canvasHeight - freeBucketCellHeight - fontHeight + fontOffset
        );
        renderFreeBucketLists(
            (rank) => freeBucket.tail(rank - MIN_FRAGMENT_SIZE_EXPONENT),
            (cur) => bucket.prev(cur),
            freeBucketListTailsOffsetX,
            canvasHeight - freeBucketCellHeight - padding,
            fontHeight,
            1
        );

        ctx.restore();
    },

    /** Updates the fields with the state of the allocator.
     */
    summarize: (
        sizeBytesId,
        sizePagesId,
        segmentsId,
        lastSegmentSizeId,
        segmentOccupancyId,
        occupancyId,
        internalFragmentationId,
        externalFragmentationId,
        errorMessageId
    ) => {
        document.getElementById(sizeBytesId).innerHTML = heap.byteSize() + " B";
        document.getElementById(sizePagesId).innerHTML = heap._pageCount() + "";

        let segmentCount = 0;
        let allocatedSize = 0;
        let allocatedSegmentSize = 0;
        let largestFreeRegionSize = 0;
        let freeSize = 0;
        let cur = segment.leftSentinel();
        while (cur < heap.byteSize()) {
            segmentCount++;
            const segSize = segment.size(cur);
            if (!segment.isFree(cur)) {
                if (segment.isBucket(cur)) {
                    const bucketAddress = cur + SEGMENT_HEADER_SIZE;
                    const fragmentSizeExponent = bucket.fragmentSizeExponent(bucketAddress);
                    const fragmentSize = 1 << fragmentSizeExponent;
                    const fragmentCount = bucket.fragmentCount(bucketAddress);
                    for (let i = 0; i < fragmentCount; i++) {
                        if (bucket.allocBit(bucketAddress, i) === 1) {
                            allocatedSize += fragmentSize;
                        } else {
                            freeSize += fragmentSize;
                            largestFreeRegionSize = Math.max(largestFreeRegionSize, fragmentSize);
                        }
                    }
                } else {
                    allocatedSize += segSize;
                }
                allocatedSegmentSize += segSize;
            } else {
                freeSize += segSize;
                largestFreeRegionSize = Math.max(largestFreeRegionSize, segSize);
            }
            const next = segment.next(cur);
            if (next <= cur) {
                // May not be able to progress -- corrupted heap.
                document.getElementById(errorMessageId).innerText = "Possibly corrupted heap, segment-list invalid.";
                break;
            }
            cur = next;
        }
        document.getElementById(segmentsId).innerHTML = segmentCount + "";

        const lastSegmentSize = segment.size(segment.rightSentinel());
        document.getElementById(lastSegmentSizeId).innerHTML = lastSegmentSize + " B";

        const segmentOccupancy = Math.round((allocatedSegmentSize / heap.byteSize()) * 100 * 100) / 100;
        document.getElementById(segmentOccupancyId).innerHTML = segmentOccupancy + "%";

        const occupancy = Math.round((allocatedSize / heap.byteSize()) * 100 * 100) / 100;
        document.getElementById(occupancyId).innerHTML = occupancy + "%";

        const externalFragmentation = 1 - (freeSize === 0 ? 1 : largestFreeRegionSize / freeSize);
        const externalFragmentationPercent = Math.round(externalFragmentation * 100 * 100) / 100;
        document.getElementById(externalFragmentationId).innerHTML = externalFragmentationPercent + "%";

        const internalFragmentation = (allocatedSize - preciseAllocatedSize) / heap.byteSize();
        const internalFragmentationPercent = Math.round(internalFragmentation * 100 * 100) / 100;
        document.getElementById(internalFragmentationId).innerHTML = internalFragmentationPercent + "%";

        try {
            heap._checkInvariants();
        } catch (e) {
            document.getElementById(errorMessageId).innerText = "Heap corrupted. " + e.message;
        }
    },
};
