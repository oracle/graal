# The MIT License (MIT)
#
# Copyright (c) 2017, 2018 Oracle and/or its affiliates.
# Copyright (c) 2013 Pablo Mouzo
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

from array import array
from math import sqrt, atan2, sqrt, sin, cos, ceil, floor


class Image(object):
    def __init__(self, width, height, data=None):
        self.width = width
        self.height = height
        if data:
            self.data = data
        else:
            self.data = [0] * (width * height)

    def _idx(self, x, y):
        if 0 <= x < self.width and 0 <= y < self.height:
            return y * self.width + x
        raise IndexError

    def __getitem__(self, t):
        x, y = t
        return self.data[self._idx(x, y)]

    def __setitem__(self, t, val):
        x, y = t
        self.data[self._idx(x, y)] = val

    def pixels(self, border=0):
        for y in xrange(border, self.height - border):
            for x in xrange(border, self.width - border):
                yield x, y

    def sobel(self, horizontal=True, vertical=True):
        out = Image(self.width, self.height)
        for y in range(1, self.height - 1):
            for x in range(1, self.width - 1):
                if horizontal:
                    dx = -1.0 * self[x - 1, y - 1] + 1.0 * self[x + 1, y - 1] + \
                         -2.0 * self[x - 1, y]     + 2.0 * self[x + 1, y] + \
                         -1.0 * self[x - 1, y + 1] + 1.0 * self[x + 1, y + 1]
                else:
                    dx = self[x, y]
                if vertical:
                    dy = -1.0 * self[x - 1, y - 1] - 2.0 * self[x, y - 1] - 1.0 * self[x + 1, y - 1] + \
                         1.0 * self[x - 1, y + 1] + 2.0 * self[x, y + 1] + 1.0 * self[x + 1, y + 1]
                else:
                    dy = self[x, y]
                out[x, y] = min(int(sqrt(dx * dx + dy * dy) / 4.0), 255)
        return out

    def fisheye(img, fraction=2, bilinear=False):
        if bilinear:
            img = BilinImage(img.width, img.height, data=img.data)
        else:
            img = NNImage(img.width, img.height, data=img.data)
        out = Image(img.width, img.height, data=img.data[:])
        maxr = img.height / (fraction + 1)
        for y in range(int(img.height / 2 - maxr), int(img.height / 2 + maxr)):
            for x in range(int(img.width / 2 - maxr), int(img.width / 2 + maxr)):
                dx, dy = x - img.width / 2, y - img.height / 2
                a = atan2(dy, dx)
                r = sqrt(dx ** 2 + dy ** 2)
                if r < maxr:
                    nr = r * r / maxr
                    nx, ny = nr * cos(a), nr * sin(a)
                    out[x,y] = min(int(img[nx + img.width / 2, ny + img.height / 2]), 255)
                else:
                    out[x,y] = img[x,y]
        return out


class NNImage(Image):
    def __getitem__(self, t):
        x, y = t
        return Image.__getitem__(self, (int(x + 0.5), int(y + 0.5)))


class BilinImage(Image):
    def __getitem__(self, t):
        x, y = t
        if isinstance(x, float) and isinstance(y, float):
            x0, x1 = int(floor(x)), int(ceil(x))
            y0, y1 = int(floor(y)), int(ceil(y))
            xoff, yoff = x - x0, y - y0
            return (1.0 - xoff) * (1.0 - yoff) * self[x0, y0] + \
                   (1.0 - xoff) * (      yoff) * self[x0, y1] + \
                   (      xoff) * (1.0 - yoff) * self[x1, y0] + \
                   (      xoff) * (      yoff) * self[x1, y1]
        else:
            return Image.__getitem__(self, (x, y))


if __name__ == '__main__':
    import sys
    if sys.implementation.name == "graalpython" or "test" in sys.argv:
        img = Image(5, 5, data=(
            [11, 12, 13, 14, 15] +
            [21, 22, 23, 24, 25] +
            [31, 32, 33, 34, 35] +
            [41, 42, 43, 44, 45] +
            [51, 52, 53, 54, 55]
        ))
        print(img.sobel().data)
        print(img.fisheye().data)
        print(img.fisheye(bilinear=True).data)
    else:
        import re, subprocess
        from time import time

        def mplayer(Image, fn='tv://', options=''):
            f = subprocess.Popen(
                'mplayer -really-quiet -noframedrop ' + options + ' ' '-vo yuv4mpeg:file=/dev/stdout 2>/dev/null </dev/null ' + fn,
                universal_newlines=False,
                shell=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            ).stdout
            hdr = f.readline()
            m = re.search('W(\d+) H(\d+)', str(hdr))
            w, h = int(m.group(1)), int(m.group(2))
            while True:
                hdr = f.readline()
                if hdr != b'FRAME\n':
                    break
                data = array('B')
                data.fromfile(f, w * h)
                yield Image(w, h, data=list(data))
                f.read(w * h // 2) # Color data

        class MplayerViewer(object):
            def __init__(self):
                self.width = self.height = None

            def view(self, img):
                from plain import Image
                imgdata = array('B', [0]) * (img.width * img.height)
                for idx, px in enumerate(img.data):
                    imgdata[idx] = px
                if not self.width:
                    self.mplayer = subprocess.Popen(
                        'mplayer -really-quiet -noframedrop - 2> /dev/null ',
                        stdin=subprocess.PIPE,
                        stdout=subprocess.DEVNULL,
                        universal_newlines=False,
                        shell=True,
                    ).stdin
                    self.mplayer.write(b'YUV4MPEG2 W%d H%d F100:1 Ip A1:1\n' %
                                       (img.width, img.height))
                    self.width = img.width
                    self.height = img.height
                    self.color_data = array('B', [127]) * (img.width * img.height // 2)
                assert self.width == img.width
                assert self.height == img.height
                self.mplayer.write(b'FRAME\n')
                imgdata.tofile(self.mplayer)
                self.color_data.tofile(self.mplayer)

        default_viewer = MplayerViewer()

        def view(img):
            default_viewer.view(img)

        start = start0 = time()
        for fcnt, img in enumerate(mplayer(Image, 'test.avi -vf scale=640:480 -benchmark')):
            img = img.sobel(horizontal=('vertical' not in sys.argv), vertical=('horizontal' not in sys.argv))
            if 'no-fisheye' not in sys.argv:
                img = img.fisheye(bilinear=("bilinear" in sys.argv), fraction=3)
            view(img)
            print(1.0 / (time() - start), 'fps, ', (fcnt-2) / (time() - start0), 'average fps')
            start = time()
            if fcnt==2:
                start0 = time()
