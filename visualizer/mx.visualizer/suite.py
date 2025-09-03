#
# Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
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

suite = {
  "mxversion": "7.55.2",
  "name" : "visualizer",


  "licenses": {
      "GPLv2": {
          "name": "GNU General Public License, version 2",
          "url": "http://www.gnu.org/licenses/old-licenses/gpl-2.0.html"
      },
  },
  "defaultLicense" : "GPLv2",

  "projects" : {

    "IdealGraphVisualizer" : {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "checkstyle" : "Data",
      "class": "NetBeansProject",
      "dist" : "true",
    },
  },
  "distributions": {
    "IGV": {
      "native" : True,
      "relpath" : True,
      "dependencies" : [
        "IdealGraphVisualizer",
      ],
    },

    "IGV_DATA_SETTINGS": {
      "dependencies" : [
        "IGV",
      ],
      "layout" : {
        "./": [
          "file:IdealGraphVisualizer/Data/build/classes/*",
          "file:IdealGraphVisualizer/Settings/build/classes/*"
          ],
      },
      "defaultBuild" : False,
    },
  },
}
