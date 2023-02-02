# Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.

# This benchmark is derived from the URL below, which had the following
# copyright notice.
#
# Copyright 2006--2007-01-21 Paul Sladen
#
# You may use and distribute this code under any DFSG-compatible
# license (eg. BSD, GNU GPLv2).
#
# Stand-alone pure-Python DEFLATE (gzip) and bzip2 decoder/decompressor.
# This is probably most useful for research purposes/index building;  there
# is certainly some room for improvement in the Huffman bit-matcher.
#
# With the as-written implementation, there was a known bug in BWT
# decoding to do with repeated strings.  This has been worked around;
# see 'bwt_reverse()'.  Correct output is produced in all test cases
# but ideally the problem would be found...
import sys
if sys.version_info[0] > 2:
    xrange = range

class BitfieldBase(object):
    def __init__(self, x):
        if isinstance(x,BitfieldBase):
            self.f = x.f
            self.bits = x.bits
            self.bitfield = x.bitfield
            self.count = x.bitfield
        else:
            self.f = x
            self.bits = 0
            self.bitfield = 0x0
            self.count = 0
    def _read(self, n):
        s = self.f.read(n)
        if not s:
            raise "Length Error"
        self.count += len(s)
        return s
    def needbits(self, n):
        while self.bits < n:
            self._more()
    def _mask(self, n):
        return (1 << n) - 1
    def toskip(self):
        return self.bits & 0x7
    def align(self):
        self.readbits(self.toskip())
    def dropbits(self, n = 8):
        while n >= self.bits and n > 7:
            n -= self.bits
            self.bits = 0
            n -= len(self.f._read(n >> 3)) << 3
        if n:
            self.readbits(n)
        # No return value
    def dropbytes(self, n = 1):
        self.dropbits(n << 3)
    def tell(self):
        return self.count - ((self.bits+7) >> 3), 7 - ((self.bits-1) & 0x7)
    def tellbits(self):
        bytes, bits = self.tell()
        return (bytes << 3) + bits

class Bitfield(BitfieldBase):
    def _more(self):
        c = self._read(1)
        self.bitfield += ord(c) << self.bits
        self.bits += 8
    def snoopbits(self, n = 8):
        if n > self.bits:
            self.needbits(n)
        return self.bitfield & self._mask(n)
    def readbits(self, n = 8):
        if n > self.bits:
            self.needbits(n)
        r = self.bitfield & self._mask(n)
        self.bits -= n
        self.bitfield >>= n
        return r

class RBitfield(BitfieldBase):
    def _more(self):
        c = self._read(1)
        self.bitfield <<= 8
        self.bitfield += ord(c)
        self.bits += 8
    def snoopbits(self, n = 8):
        if n > self.bits:
            self.needbits(n)
        return (self.bitfield >> (self.bits - n)) & self._mask(n)
    def readbits(self, n = 8):
        if n > self.bits:
            self.needbits(n)
        r = (self.bitfield >> (self.bits - n)) & self._mask(n)
        self.bits -= n
        self.bitfield &= ~(self._mask(n) << self.bits)
        return r

def printbits(v, n):
    o = ''
    for i in range(n):
        if v & 1:
            o = '1' + o
        else:
            o = '0' + o
        v >>= 1
    return o

class HuffmanLength(object):
    def __init__(self, code, bits = 0):
        self.code = code
        self.bits = bits
        self.symbol = None
    def __repr__(self):
        return {}.format((self.code, self.bits, self.symbol, self.reverse_symbol))
    def __cmp__(self, other):
        if self.bits == other.bits:
            return cmp(self.code, other.code)
        else:
            return cmp(self.bits, other.bits)
    def __lt__(self, other):
        if self.bits == other.bits:
            return self.code < other.code
        else:
            return self.bits < other.bits

def reverse_bits(v, n):
    a = 1 << 0
    b = 1 << (n - 1)
    z = 0
    for i in range(n-1, -1, -2):
        z |= (v >> i) & a
        z |= (v << i) & b
        a <<= 1
        b >>= 1
    return z

def reverse_bytes(v, n):
    a = 0xff << 0
    b = 0xff << (n - 8)
    z = 0
    for i in range(n-8, -8, -16):
        z |= (v >> i) & a
        z |= (v << i) & b
        a <<= 8
        b >>= 8
    return z

class HuffmanTable(object):
    def __init__(self, bootstrap):
        l = []
        start, bits = bootstrap[0]
        for finish, endbits in bootstrap[1:]:
            if bits:
                for code in range(start, finish):
                    l.append(HuffmanLength(code, bits))
            start, bits = finish, endbits
            if endbits == -1:
                break
        l.sort()
        self.table = l

    def populate_huffman_symbols(self):
        bits, symbol = -1, -1
        for x in self.table:
            symbol += 1
            if x.bits != bits:
                symbol <<= (x.bits - bits)
                bits = x.bits
            x.symbol = symbol
            x.reverse_symbol = reverse_bits(symbol, bits)
            #print(printbits(x.symbol, bits), printbits(x.reverse_symbol, bits))

    def tables_by_bits(self):
        d = {}
        for x in self.table:
            try:
                d[x.bits].append(x)
            except:
                d[x.bits] = [x]
        pass

    def min_max_bits(self):
        self.min_bits, self.max_bits = 16, -1
        for x in self.table:
            if x.bits < self.min_bits: self.min_bits = x.bits
            if x.bits > self.max_bits: self.max_bits = x.bits

    def _find_symbol(self, bits, symbol, table):
        for h in table:
            if h.bits == bits and h.reverse_symbol == symbol:
                #print("found, processing", h.code)
                return h.code
        return -1

    def find_next_symbol(self, field, reversed = True):
        cached_length = -1
        cached = None
        for x in self.table:
            if cached_length != x.bits:
                cached = field.snoopbits(x.bits)
                cached_length = x.bits
            if (reversed and x.reverse_symbol == cached) or (not reversed and x.symbol == cached):
                field.readbits(x.bits)
                return x.code
        raise "unfound symbol, even after end of table @ {}".format(field.tell())

        for bits in range(self.min_bits, self.max_bits + 1):
            #print(printbits(field.snoopbits(bits),bits))
            r = self._find_symbol(bits, field.snoopbits(bits), self.table)
            if 0 <= r:
                field.readbits(bits)
                return r
            elif bits == self.max_bits:
                raise "unfound symbol, even after max_bits"

class OrderedHuffmanTable(HuffmanTable):
    def __init__(self, lengths):
        l = len(lengths)
        z = list(zip(range(l), lengths)) + [(l, -1)]
        HuffmanTable.__init__(self, z)

def code_length_orders(i):
    return (16,17,18,0,8,7,9,6,10,5,11,4,12,3,13,2,14,1,15)[i]

def distance_base(i):
    return (1,2,3,4,5,7,9,13,17,25,33,49,65,97,129,193,257,385,513,769,1025,1537,2049,3073,4097,6145,8193,12289,16385,24577)[i]

def length_base(i):
    return (3,4,5,6,7,8,9,10,11,13,15,17,19,23,27,31,35,43,51,59,67,83,99,115,131,163,195,227,258)[i-257]

def extra_distance_bits(n):
    if 0 <= n <= 1:
        return 0
    elif 2 <= n <= 29:
        return (n >> 1) - 1
    else:
        raise "illegal distance code"

def extra_length_bits(n):
    if 257 <= n <= 260 or n == 285:
        return 0
    elif 261 <= n <= 284:
        return ((n-257) >> 2) - 1
    else:
        raise "illegal length code"

def move_to_front(l, c):
    l[:] = l[c:c+1] + l[0:c] + l[c+1:]

def bwt_transform(L):
    # Semi-inefficient way to get the character counts
    F = ''.join(sorted(L))
    base = []
    for i in range(256):
        base.append(F.find(chr(i)))

    pointers = [-1] * len(L)
    for i, char in enumerate(L):
        symbol = ord(char)
        pointers[base[symbol]] = i
        base[symbol] += 1
    return pointers

def bwt_reverse(L, end):
    out = []
    if len(L):
        T = bwt_transform(L)

        # STRAGENESS WARNING: There was a bug somewhere here in that
        # if the output of the BWT resolves to a perfect copy of N
        # identical strings (think exact multiples of 255 'X' here),
        # then a loop is formed.  When decoded, the output string would
        # be cut off after the first loop, typically '\0\0\0\0\xfb'.
        # The previous loop construct was:
        #
        #  next = T[end]
        #  while next != end:
        #      out += L[next]
        #      next = T[next]
        #  out += L[next]
        #
        # For the moment, I've instead replaced it with a check to see
        # if there has been enough output generated.  I didn't figured
        # out where the off-by-one-ism is yet---that actually produced
        # the cyclic loop.

        for i in xrange(len(L)):
            end = T[end]
            out.append(L[end])

    return "".join(out)

def compute_used(b):
    huffman_used_map = b.readbits(16)
    #print('used map', hex(huffman_used_map))
    map_mask = 1 << 15
    used = []
    while map_mask > 0:
        if huffman_used_map & map_mask:
            huffman_used_bitmap = b.readbits(16)
            bit_mask = 1 << 15
            while bit_mask > 0:
                if huffman_used_bitmap & bit_mask:
                    #print('hit', len(used))
                    pass
                used += [bool(huffman_used_bitmap & bit_mask)]
                bit_mask >>= 1
        else:
            used += [False] * 16
        map_mask >>= 1
    return used

def compute_selectors_list(b, huffman_groups):
    selectors_used = b.readbits(15)
    #print('selectors used', selectors_used)
    mtf = list(range(huffman_groups))
    selectors_list = []
    for i in range(selectors_used):
        # zero-terminated bit runs (0..62) of MTF'ed huffman table
        c = 0
        while b.readbits(1):
            c += 1
            if c >= huffman_groups:
                raise "Bzip2 chosen selector greater than number of groups (max 6)"
        if c >= 0:
            move_to_front(mtf, c)
        #print(c, mtf)
        selectors_list.append(mtf[0])
    return selectors_list

def compute_tables(b, huffman_groups, symbols_in_use):
    groups_lengths = []
    for j in range(huffman_groups):
        length = start_huffman_length = b.readbits(5)
        #print('start_huffman_length', start_huffman_length)
        lengths = []
        for i in range(symbols_in_use):
            if not 0 <= length <= 20:
                raise "Bzip2 Huffman length code outside range 0..20"
            while b.readbits(1):
                length -= (b.readbits(1) * 2) - 1
            lengths += [length]
        groups_lengths += [lengths]
        #print(groups_lengths)

    tables = []
    for g in groups_lengths:
        codes = OrderedHuffmanTable(g)
        codes.populate_huffman_symbols()
        codes.min_max_bits()
        tables.append(codes)
    return tables

def decode_huffman_block(b, out):
    #print('bzip2 Huffman block')
    randomised = b.readbits(1)
    if randomised:
        raise "Bzip2 randomised support not implemented"
    pointer = b.readbits(24)
    #print('pointer', pointer, hex(pointer))
    used = compute_used(b)

    huffman_groups = b.readbits(3)
    #print('huffman groups', huffman_groups)
    if not 2 <= huffman_groups <= 6:
        raise "Bzip2: Number of Huffman groups not in range 2..6"

    selectors_list = compute_selectors_list(b, huffman_groups)
    symbols_in_use = sum(used) + 2  # remember RUN[AB] RLE symbols
    tables = compute_tables(b, huffman_groups, symbols_in_use)

    #favourites = map(chr,range(sum(used)))
    #favourites = string.join([y for x,y in map(None,used,map(chr,range(len(used)))) if x],'')
    favourites = list([chr(i) for i, x in enumerate(used) if x])

    data_start = b.tellbits()
    selector_pointer = 0
    decoded = 0
    # Main Huffman loop
    repeat = repeat_power = 0
    buffer = []
    t = None
    while True:
        decoded -= 1
        if decoded <= 0:
            #print('RETABLE TIME', selectors_list[selector_pointer])
            decoded = 50 # Huffman table re-evaluate/switch length
            if selector_pointer <= len(selectors_list):
                t = tables[selectors_list[selector_pointer]]
                selector_pointer += 1
            #print('tables changed', tables[0].table)
        #print(b.tell())
        r = t.find_next_symbol(b, False)
        #print('symbol', r)
        if 0 <= r <= 1:
            if repeat == 0:
                repeat_power = 1
            #print('run', repeat)
            repeat += repeat_power << r
            repeat_power <<= 1
            continue
        elif repeat > 0:
            # Remember kids: If there is only one repeated
            # real symbol, it is encoded with *zero* Huffman
            # bits and not output... so buffer[-1] doesn't work.
            #print('runfinal', repeat)
            buffer.append(favourites[0] * repeat)
            repeat = 0
        if r == symbols_in_use - 1:
            #print('finished {} .. {}'.format(buffer[:10], buffer[-10:]), 'len', len(buffer))
            break
        else:
            o = favourites[r-1]
            #print('pre {}'.format(favourites))
            move_to_front(favourites, r-1)
            #print('post {}'.format(favourites))
            #print('output {}.format(o))
            buffer.append(o)
            pass
    #print('huffman {}'.format(buffer), pointer, len(buffer))
    #nearly_there = bwt_reverse(buffer, len(buffer)-pointer-1)
    nt = nearly_there = bwt_reverse("".join(buffer), pointer)
    #print('nearly there {}'.format(nearly_there))
    i = 0
    # Pointless/irritating run-length encoding step
    while i < len(nearly_there):
        #print('RLE decode {}'.format(nt[i:]))
        if i < len(nearly_there) - 4 and nt[i] == nt[i+1] == nt[i+2] == nt[i+3]:
            out.append(nearly_there[i] * (ord(nearly_there[i+4]) + 4))
            i += 5
        else:
            out.append(nearly_there[i])
            i += 1
    #print('done {} .. {}'.format(done[:10], done[-10:]), 'len', len(done))

    #raise "Bip2 block support not implemented"

# Sixteen bits of magic have been removed by the time we start decoding
def bzip2_main(input):
    b = RBitfield(input)

    method = b.readbits(8)
    if method != ord('h'):
        raise "Unknown (not type 'h'uffman Bzip2) compression method"

    blocksize = b.readbits(8)
    if ord('1') <= blocksize <= ord('9'):
        blocksize = blocksize - ord('0')
    else:
        raise "Unknown (not size '0'-'9') Bzip2 blocksize"

    out = []
    while True:
        #header_start = b.tellbits()
        blocktype = b.readbits(48)
        crc = b.readbits(32)
        #print(hex(blocktype))
        #print(hex(crc))
        if blocktype == 0x314159265359: # (pi)
            decode_huffman_block(b, out)
        elif blocktype == 0x177245385090: # sqrt(pi)
            #print('bzip2 end-of-stream block')
            b.align()
            break
        else:
            raise "Illegal Bzip2 blocktype"
    #print(len(out), set([len(s) for s in out]))
    return ''.join(out)

# Sixteen bits of magic have been removed by the time we start decoding
def gzip_main(field):
    b = Bitfield(field)
    method = b.readbits(8)
    if method != 8:
        raise "Unknown (not type eight DEFLATE) compression method"

    # Use flags, drop modification time, extra flags and OS creator type.
    flags = b.readbits(8)
    #print('flags', hex(flags))
    mtime = b.readbits(32)
    #print('mtime', hex(mtime))
    extra_flags = b.readbits(8)
    #print('extra_flags', hex(extra_flags))
    os_type = b.readbits(8)
    #print('os_type', hex(os_type))

    if flags & 0x04: # structured GZ_FEXTRA miscellaneous data
        xlen = b.readbits(16)
        b.dropbytes(xlen)
    while flags & 0x08: # original GZ_FNAME filename
        if not b.readbits(8):
            break
    while flags & 0x10: # human readable GZ_FCOMMENT
        if not b.readbits(8):
            break
    if flags & 0x02: # header-only GZ_FHCRC checksum
        b.readbits(16)

    #print("gzip header skip", b.tell())
    out = []

    #print('header 0 count 0 bits', b.tellbits())

    while True:
        header_start = b.tell()
        bheader_start = b.tellbits()
        #print('new block at', b.tell())
        lastbit = b.readbits(1)
        #print("last bit", hex(lastbit))
        blocktype = b.readbits(2)
        #print("deflate-blocktype", blocktype, 'beginning at', header_start)

        #print('raw block data at', b.tell())
        if blocktype == 0:
            b.align()
            length = b.readbits(16)
            if length & b.readbits(16):
                raise "stored block lengths do not match each other"
            #print("stored block of length", length)
            #print('raw data at', b.tell(), 'bits', b.tellbits() - bheader_start)
            #print('header 0 count 0 bits', b.tellbits() - bheader_start)
            for i in range(length):
                out.append(chr(b.readbits(8)))
            #print('linear', b.tell()[0], 'count', length, 'bits', b.tellbits() - bheader_start)

        elif blocktype == 1 or blocktype == 2: # Huffman
            main_literals, main_distances = None, None

            if blocktype == 1: # Static Huffman
                static_huffman_bootstrap = [(0, 8), (144, 9), (256, 7), (280, 8), (288, -1)]
                static_huffman_lengths_bootstrap = [(0, 5), (32, -1)]
                main_literals = HuffmanTable(static_huffman_bootstrap)
                main_distances = HuffmanTable(static_huffman_lengths_bootstrap)

            elif blocktype == 2: # Dynamic Huffman
                literals = b.readbits(5) + 257
                distances = b.readbits(5) + 1
                code_lengths_length = b.readbits(4) + 4

                l = [0] * 19
                for i in range(code_lengths_length):
                    l[code_length_orders(i)] = b.readbits(3)

                dynamic_codes = OrderedHuffmanTable(l)
                dynamic_codes.populate_huffman_symbols()
                dynamic_codes.min_max_bits()

                # Decode the code_lengths for both tables at once,
                # then split the list later

                code_lengths = []
                n = 0
                while n < (literals + distances):
                    r = dynamic_codes.find_next_symbol(b)
                    if 0 <= r <= 15: # literal bitlength for this code
                        count = 1
                        what = r
                    elif r == 16: # repeat last code
                        count = 3 + b.readbits(2)
                        # Is this supposed to default to '0' if in the zeroth position?
                        what = code_lengths[-1]
                    elif r == 17: # repeat zero
                        count = 3 + b.readbits(3)
                        what = 0
                    elif r == 18: # repeat zero lots
                        count = 11 + b.readbits(7)
                        what = 0
                    else:
                        raise "next code length is outside of the range 0 <= r <= 18"
                    code_lengths += [what] * count
                    n += count

                main_literals = OrderedHuffmanTable(code_lengths[:literals])
                main_distances = OrderedHuffmanTable(code_lengths[literals:])

            # Common path for both Static and Dynamic Huffman decode now

            data_start = b.tell()
            #print('raw data at', data_start, 'bits', b.tellbits() - bheader_start)
            #print('header 0 count 0 bits', b.tellbits() - bheader_start)

            main_literals.populate_huffman_symbols()
            main_distances.populate_huffman_symbols()

            main_literals.min_max_bits()
            main_distances.min_max_bits()

            literal_count = 0
            literal_start = 0

            while True:
                lz_start = b.tellbits()
                r = main_literals.find_next_symbol(b)
                if 0 <= r <= 255:
                    if literal_count == 0:
                        literal_start = lz_start
                    literal_count += 1
                    #print('found literal {}'.format(chr(r)))
                    out.append(chr(r))
                elif r == 256:
                    if literal_count > 0:
                        #print('add 0 count', literal_count, 'bits', lz_start-literal_start, 'dat) {}'.format(out[-literal_count:]))
                        literal_count = 0
                    #print('eos 0 count 0 bits', b.tellbits() - lz_start)
                    #print('end of Huffman block encountered')
                    break
                elif 257 <= r <= 285: # dictionary lookup
                    if literal_count > 0:
                        #print('add 0 count', literal_count, 'bits', lz_start-literal_start, 'data {}'.format(out[-literal_count:]))
                        literal_count = 0
                    length_extra = b.readbits(extra_length_bits(r))
                    length = length_base(r) + length_extra
                    #print('dictionary lookup: length', length,)

                    r1 = main_distances.find_next_symbol(b)
                    if 0 <= r1 <= 29:
                        distance = distance_base(r1) + b.readbits(extra_distance_bits(r1))
                        cached_length = length
                        while length > distance:
                            out += out[-distance:]
                            length -= distance
                        if length == distance:
                            out += out[-distance:]
                        else:
                            out += out[-distance:length-distance]
                        #print('copy', -distance, 'count', cached_length, 'bits', b.tellbits() - lz_start, 'data {}'.format(out[-cached_length:]))
                    elif 30 <= r1 <= 31:
                        raise "illegal unused distance symbol in use @ {}".format(b.tell())
                elif 286 <= r <= 287:
                    raise "illegal unused literal/length symbol in use @ {}".format(b.tell())
        elif blocktype == 3:
            raise "illegal unused blocktype in use @ {}".format(b.tell())

        if lastbit:
            #print("this was the last block, time to leave", b.tell())
            break

    footer_start = b.tell()
    bfooter_start = b.tellbits()
    b.align()
    crc = b.readbits(32)
    final_length = b.readbits(32)
    #print(len(out))
    next_unused = b.tell()
    #print('deflate-end-of-stream', 5, 'beginning at', footer_start, 'raw data at', next_unused, 'bits', b.tellbits() - bfooter_start)
    #print('deflate-end-of-stream')
    #print('crc', hex(crc), 'final length', final_length)
    #print('header 0 count 0 bits', b.tellbits()-bfooter_start)

    return "".join(out)

import sys, os

def _main():
    filename = os.path.join(os.path.dirname(__file__), "graalpython-pyflate-benchmark-resource.tar.gz")
    input = open(filename, 'rb')
    field = RBitfield(input)

    magic = field.readbits(16)
    if magic == 0x1f8b: # GZip
        out = gzip_main(field)
    elif magic == 0x425a: # BZip2
        out = bzip2_main(field)
    else:
        raise "Unknown file magic "+hex(magic)+", not a gzip/bzip2 file"

    from hashlib import md5
    # print(md5(out.encode('utf-8')).hexdigest())
    assert md5(out.encode('utf-8')).hexdigest() == "0d8772135b981f5cfd9acbf23ba643d7"
    input.close()

run = _main


def main(warmup=20, iterations=30):
    import time
    for i in range(warmup):
        t1 = time.time()
        _main()
        t2 = time.time() - t1
        print("Warmup", i, ":", t2 * 1000, "ms")
    for i in range(iterations):
        t1 = time.time()
        _main()
        t2 = time.time() - t1
        print("Iteration", i, ":", t2 * 1000, "ms")


if __name__ == "__main__" and sys.implementation.name != "graalpy":
    print(f"Running {os.path.basename(__file__)}")
    times = main(20, 30)
    for idx, time in enumerate(times):
        print("Iteration", idx, ":", time, "ms")
