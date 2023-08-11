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
  "mxversion": "6.27.1",
  "name" : "visualizer",


  "licenses": {
      "GPLv2": {
          "name": "GNU General Public License, version 2",
          "url": "http://www.gnu.org/licenses/old-licenses/gpl-2.0.html"
      },
  },
  "defaultLicense" : "GPLv2",

  "libraries" : {
    "SPOTBUGS_3.1.11" : {
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/spotbugs-3.1.11.zip",
        "https://repo.maven.apache.org/maven2/com/github/spotbugs/spotbugs/3.1.11/spotbugs-3.1.11.zip",
      ],
      "sha1" : "8f961e0ddd445cc4e89b18563ac5730766d220f1",
    },
    "APACHE_BATIK_SVGGEN_1.12" : {
      "sha1" : "53364380c4fb33ba49ddf25a56852bf405f698df",
      "maven" : {
        "groupId" : "org.apache.xmlgraphics",
        "artifactId" : "batik-svggen",
        "version" : "1.12",
      },
      "license" : "Apache-2.0"
    },
    "OPENIDE_UTIL_LOOKUP" : {
      "moduleName" : "org.openide.util.Lookup",
      "sha1" : "6256fe5f371593f8d1d7cd4e732da5979e9577b5",
      "maven" : {
        "groupId" : "org.netbeans.api",
        "artifactId" : "org-openide-util-lookup",
        "version" : "RELEASE122",
      },
      "license" : "Apache-2.0"
    },
    "OPENIDE_UTIL" : {
      "moduleName" : "org.openide.util",
      "sha1" : "1a22c1ff81b4c2a6d9bd52a32270d9087c767a04",
      "maven" : {
        "groupId" : "org.netbeans.api",
        "artifactId" : "org-openide-util",
        "version" : "RELEASE122",
      },
      "license" : "Apache-2.0"
    },

    "NETBEANS_14" : {
      "digest" : "sha512:004b943a2542bd98784a3821411da3f0b048b9b101cc4699a96b1bf7789c654f4ad6c73a75d51a9cb0f436c421ce32baa0ad440efe461bd3e6a8e5e8e927f5c6",
      "urls" : ["https://archive.apache.org/dist/netbeans/netbeans/14/netbeans-14-bin.zip"],
    },
  },

  "projects" : {

    "IdealGraphVisualizer" : {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "checkstyle" : "Data",
      "class": "NetBeansProject",
      "dist" : "true",
      "dependencies" : [
        "libs.batik",
      ],
      "buildCommands" : ["build-zip"]
    },

    "libs.batik" : {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "checkstyle" : "Data",
      "class": "NetBeansProject",
      "mxLibs" : [
        "APACHE_BATIK_SVGGEN_1.12",
      ]
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

    "IGV_JSONEXPORTER": {
      "dependencies" : [
        "IdealGraphVisualizer",
      ],
      "distDependencies": [
        "IGV_DATA_SETTINGS",
        "OPENIDE_UTIL_LOOKUP",
        "OPENIDE_UTIL",
      ],
      "defaultBuild" : False,
    },
  },
}
