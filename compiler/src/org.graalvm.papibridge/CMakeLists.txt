#
# Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
#

cmake_minimum_required(VERSION 3.10)
project(papibridge)

set(SRC src/papibridge.c)

set(INCLUDE_DIRS "$ENV{JAVA_HOME}/include" "$ENV{JAVA_HOME}/include/linux")
if(DEFINED ENV{PAPI_BRIDGE_EXTRA_HEADERS})
  list(APPEND INCLUDE_DIRS "$ENV{PAPI_BRIDGE_EXTRA_HEADERS}")
endif()

add_library(papibridge SHARED ${SRC})
target_include_directories(papibridge PRIVATE ${INCLUDE_DIRS})

if(DEFINED ENV{PAPI_BRIDGE_PAPI_LIB_DIR})
  target_link_directories(papibridge PRIVATE "$ENV{PAPI_BRIDGE_PAPI_LIB_DIR}")
endif()
target_link_libraries(papibridge PUBLIC papi)

find_package(Threads REQUIRED)
target_link_libraries(papibridge PUBLIC Threads::Threads)
