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
# copyright notice and was originally under MIT.
#
# Copyright Callum and Tony Garnock-Jones, 2008.
# This file contains definitions for a simple raytracer.
# This file may be freely redistributed under the MIT license.

import math

EPSILON = 0.00001
INF = 1.0e9

class Vector(object):
    def __init__(self, initx, inity, initz):
        self.x = initx
        self.y = inity
        self.z = initz

    def __str__(self):
        return '(%s,%s,%s)' % (self.x, self.y, self.z)

    def __repr__(self):
        return 'Vector(%s,%s,%s)' % (self.x, self.y, self.z)

    def magnitude(self):
        return math.sqrt(self.dot(self))

    def __add__(self, other):
        return Vector(self.x + other.x, self.y + other.y, self.z + other.z)

    def __sub__(self, other):
        return Vector(self.x - other.x, self.y - other.y, self.z - other.z)

    def scale(self, factor):
        return Vector(factor * self.x, factor * self.y, factor * self.z)

    def dot(self, other):
        return (self.x * other.x) + (self.y * other.y) + (self.z * other.z)

    def cross(self, other):
        return Vector(self.y * other.z - self.z * other.y,
                      self.z * other.x - self.x * other.z,
                      self.x * other.y - self.y * other.x)

    def normalized(self):
        return self.scale(1.0 / self.magnitude())

    def negated(self):
        return self.scale(-1)

    def __eq__(self, other):
        return (self.x == other.x) and (self.y == other.y) and (self.z == other.z)

    def isVector(self):
        return True

    def isPoint(self):
        return False

    def reflectThrough(self, normal):
        d = normal.scale(self.dot(normal))
        return self - d.scale(2)

VZERO = Vector(0,0,0)
VRIGHT = Vector(1,0,0)
VUP = Vector(0,1,0)
VOUT = Vector(0,0,1)

if not (VRIGHT.reflectThrough(VUP) == VRIGHT):
    print(1/0)
if not (Vector(-1,-1,0).reflectThrough(VUP) == Vector(-1,1,0)):
    print(1/0)

class Point(object):
    def __init__(self, initx, inity, initz):
        self.x = initx
        self.y = inity
        self.z = initz

    def __str__(self):
        return '(%s,%s,%s)' % (self.x, self.y, self.z)

    def __repr__(self):
        return 'Point(%s,%s,%s)' % (self.x, self.y, self.z)

    def __add__(self, other):
        return Point(self.x + other.x, self.y + other.y, self.z + other.z)

    def __sub__(self, other):
        return Vector(self.x - other.x, self.y - other.y, self.z - other.z)

    def isVector(self):
        return False

    def isPoint(self):
        return True

class Sphere(object):
    def __init__(self, centre, radius):
        self.centre = centre
        self.radius = radius

    def __repr__(self):
        return 'Sphere(%s,%s)' % (repr(self.centre), self.radius)

    def intersectionTime(self, ray):
        cp = self.centre - ray.point
        v = cp.dot(ray.vector)
        discriminant = (self.radius * self.radius) - (cp.dot(cp) - v*v)
        if discriminant < 0:
            return INF + 1
        else:
            return v - math.sqrt(discriminant)

    def normalAt(self, p):
        return (p - self.centre).normalized()

class Halfspace(object):
    def __init__(self, point, normal):
        self.point = point
        self.normal = normal.normalized()

    def __repr__(self):
        return 'Halfspace(%s,%s)' % (repr(self.point), repr(self.normal))

    def intersectionTime(self, ray):
        v = ray.vector.dot(self.normal)
        if v:
            return 1 / -v
        else:
            return INF + 1

    def normalAt(self, p):
        return self.normal

class Ray(object):
    def __init__(self, point, vector):
        self.point = point
        self.vector = vector.normalized()

    def __repr__(self):
        return 'Ray(%s,%s)' % (repr(self.point), repr(self.vector))

    def pointAtTime(self, t):
        return self.point + self.vector.scale(t)

PZERO = Point(0,0,0)

a = Vector(3,4,12)
b = Vector(1,1,1)

class PpmCanvas(object):
    def __init__(self, width, height, filenameBase):
        self.bytes = [0] * (width * height * 3)
        for i in range(width * height):
            self.bytes[i * 3 + 2] = 255
        self.width = width
        self.height = height
        self.filenameBase = filenameBase

    def plot(self, x, y, r, g, b):
        i = ((self.height - y - 1) * self.width + x) * 3
        self.bytes[i  ] = max(0, min(255, int(r * 255)))
        self.bytes[i+1] = max(0, min(255, int(g * 255)))
        self.bytes[i+2] = max(0, min(255, int(b * 255)))

    def save(self):
        with open(self.filenameBase + '.ppm', 'wb') as f:
            f.write('P6 %d %d 255\n' % (self.width, self.height))
            l = []
            for c in self.bytes:
                l.append(chr(c))
            f.write(''.join(l))

def firstIntersection(intersections):
    result = intersections[0][0], INF+1, intersections[0][2]
    for i in intersections:
        candidateT = i[1]
        if candidateT < INF and candidateT > -EPSILON:
            if result[1] > INF or candidateT < result[1]:
                result = i
    return result

class Scene(object):
    def __init__(self):
        self.objects = []
        self.lightPoints = []
        self.position = Point(0, 1.8, 10)
        self.lookingAt = PZERO
        self.fieldOfView = 45
        self.recursionDepth = 0

    def lookAt(self, p):
        self.lookingAt = p

    def addObject(self, on, oi, sc):
        self.objects.append((on, oi, sc))

    def addLight(self, p):
        self.lightPoints.append(p)

    def render(self, canvas):
        #print 'Computing field of view'
        fovRadians = math.pi * (self.fieldOfView / 2.0) / 180.0
        halfWidth = math.tan(fovRadians)
        halfHeight = 0.75 * halfWidth
        width = halfWidth * 2
        height = halfHeight * 2
        pixelWidth = width / (canvas.width - 1)
        pixelHeight = height / (canvas.height - 1)

        eye = Ray(self.position, self.lookingAt - self.position)
        vpRight = eye.vector.cross(VUP).normalized()
        vpUp = vpRight.cross(eye.vector).normalized()

        #print 'Looping over pixels'
        previousfraction = 0.0
        for y in range(canvas.height):
            currentfraction = 1.0 * y / canvas.height
            if currentfraction - previousfraction > 0.05:
                # print('%d%% complete' % int(currentfraction * 100))
                previousfraction = currentfraction
            for x in range(canvas.width):
                xcomp = vpRight.scale(x * pixelWidth - halfWidth)
                ycomp = vpUp.scale(y * pixelHeight - halfHeight)
                ray = Ray(eye.point, eye.vector + xcomp + ycomp)
                colour = self.rayColour(ray)
                canvas.plot(x,y,colour[0], colour[1], colour[2])

        # print('Complete.')
        # canvas.save()

    def rayColour(self, ray):
        if self.recursionDepth > 3:
            return (0.0,0.0,0.0)

        self.recursionDepth = self.recursionDepth + 1
        intersections = []
        for on, oi, sc in self.objects:
            intersections.append((on, oi(ray), sc))
        # intersections = [(on, oi(ray), sc) for (on, oi, sc) in self.objects]
        i = firstIntersection(intersections)
        if i[1] > INF:
            self.recursionDepth = self.recursionDepth - 1
            return (0.0,0.0,0.0) ## the background colour
        else:
            (o, t, s) = i
            p = ray.pointAtTime(t)
            r = s(self, ray, p, o(p))
            self.recursionDepth = self.recursionDepth - 1
            return r

    def _lightIsVisible(self, l, p):
        for (on, oi, sc) in self.objects:
            t = oi(Ray(p,l - p))
            if t < INF and t > EPSILON:
                return False
        return True

    def visibleLights(self, p):
        result = []
        for l in self.lightPoints:
            if self._lightIsVisible(l, p):
                result.append(l)
        return result

def addColours(a, scale, b):
    return (a[0] + scale * b[0],
            a[1] + scale * b[1],
            a[2] + scale * b[2])

class SimpleSurface(object):
    def __init__(self, baseColour):
        self.baseColour = baseColour
        self.specularCoefficient = 0.2
        self.lambertCoefficient = 0.6
        self.ambientCoefficient = 1.0 - self.specularCoefficient - self.lambertCoefficient

    def baseColourAt(self, p):
        return self.baseColour

    def colourAt(self, scene, ray, p, normal):
        b = self.baseColourAt(p)

        c = (0.0, 0.0, 0.0)
        if self.specularCoefficient > 0:
            reflectedRay = Ray(p, ray.vector.reflectThrough(normal))
            #print p, normal, ray.vector, reflectedRay.vector
            reflectedColour = scene.rayColour(reflectedRay)
            c = addColours(c, self.specularCoefficient, reflectedColour)

        if self.lambertCoefficient > 0:
            lambertAmount = 0.0
            for lightPoint in scene.visibleLights(p):
                contribution = (lightPoint - p).normalized().dot(normal)
                if contribution > 0:
                    lambertAmount = lambertAmount + contribution
            lambertAmount = min(1,lambertAmount)
            c = addColours(c, self.lambertCoefficient * lambertAmount, b)

        if self.ambientCoefficient > 0:
            c = addColours(c, self.ambientCoefficient, b)

        return c

class CheckerboardSurface(object):
    def __init__(self):
        self.baseColour = (1.0, 1.0, 1.0)
        self.specularCoefficient = 0.2
        self.lambertCoefficient = 0.6
        self.ambientCoefficient = 1.0 - self.specularCoefficient - self.lambertCoefficient
        self.otherColour = (0.0, 0.0, 0.0)
        self.checkSize = 1

    def baseColourAt(self, p):
        v = p - PZERO
        v.scale(1.0 / self.checkSize)
        if (int(abs(v.x) + 0.5) + \
            int(abs(v.y) + 0.5) + \
            int(abs(v.z) + 0.5)) \
           % 2:
            return self.otherColour
        else:
            return self.baseColour

    def colourAt(self, scene, ray, p, normal):
        b = self.baseColourAt(p)

        c = (0.0,0.0,0.0)
        if self.specularCoefficient > 0:
            reflectedRay = Ray(p, ray.vector.reflectThrough(normal))
            #print p, normal, ray.vector, reflectedRay.vector
            reflectedColour = scene.rayColour(reflectedRay)
            c = addColours(c, self.specularCoefficient, reflectedColour)

        if self.lambertCoefficient > 0:
            lambertAmount = 0.0
            for lightPoint in scene.visibleLights(p):
                contribution = (lightPoint - p).normalized().dot(normal)
                if contribution > 0:
                    lambertAmount = lambertAmount + contribution
            lambertAmount = min(1,lambertAmount)
            c = addColours(c, self.lambertCoefficient * lambertAmount, b)

        if self.ambientCoefficient > 0:
            c = addColours(c, self.ambientCoefficient, b)

        return c

def _main():
    Canvas = PpmCanvas
    # c = Canvas(4,2,'test_raytrace_tiny')
    # c = Canvas(80,60,'test_raytrace_small')
    # c = Canvas(160,120,'test_raytrace')
    c = Canvas(320,240,'test_raytrace')
    # c = Canvas(640,480,'test_raytrace_big')
    s = Scene()
    s.addLight(Point(30, 30, 10))
    s.addLight(Point(-10, 100, 30))
    s.lookAt(Point(0, 2, 0))

    obj = Sphere(Point(1,3,-10), 2)
    surf = SimpleSurface((1.0,1.0,0.0))
    s.addObject(obj.normalAt, obj.intersectionTime, surf.colourAt)
    for y in range(6):
        obj = Sphere(Point(-3 - y * 0.4, 2.3, -5), 0.4)
        surf = SimpleSurface((y / 6.0, 1 - y / 6.0, 0.5))
        s.addObject(obj.normalAt, obj.intersectionTime, surf.colourAt)
    obj = Halfspace(Point(0,0,0), VUP)
    surf = CheckerboardSurface()
    s.addObject(obj.normalAt, obj.intersectionTime, surf.colourAt)
    s.render(c)

run = _main


def main(warmup, iterations):
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


import os, sys
if __name__ == "__main__" and sys.implementation.name != "graalpy":
    print(f"Running {os.path.basename(__file__)}")
    main(20, 30)
