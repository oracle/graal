::
:: ----------------------------------------------------------------------------------------------------
::
:: Copyright (c) 2019, <year>, Oracle and/or its affiliates. All rights reserved.
:: DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
::
:: This code is free software; you can redistribute it and/or modify it
:: under the terms of the GNU General Public License version 2 only, as
:: published by the Free Software Foundation.  Oracle designates this
:: particular file as subject to the "Classpath" exception as provided
:: by Oracle in the LICENSE file that accompanied this code.
::
:: This code is distributed in the hope that it will be useful, but WITHOUT
:: ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
:: FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
:: version 2 for more details (a copy is included in the LICENSE file that
:: accompanied this code).
::
:: You should have received a copy of the GNU General Public License version
:: 2 along with this work; if not, write to the Free Software Foundation,
:: Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
::
:: Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
:: or visit www.oracle.com if you need additional information or have any
:: questions.
::
:: ----------------------------------------------------------------------------------------------------
@echo off

setlocal enabledelayedexpansion

set location=%~dp0
set executablename=%~f0

set "relcp=<classpath>"
set "realcp="
set "cp_delim="

:nextcp
for /f "tokens=1* delims=;" %%i in ("%relcp%") do (
  set "realcp=%realcp%%cp_delim%%location%%%i"
  set "cp_delim=;"
  set "relcp_next=%relcp:*;=%"
)
if not "%relcp_next%"=="%relcp%" set "relcp=%relcp_next%" & goto :nextcp

set "jvm_args=-Dorg.graalvm.launcher.shell=true "-Dorg.graalvm.launcher.executablename=%executablename%""
set "launcher_args="
set "args_delim="

:arg_loop
if not "%~1"=="" (
  echo %* | findstr /C:%1=%2 >nul && (
    set "arg=%1=%2"
    set "u_arg=!arg!"
    shift
  ) || (
    set "arg=%1"
    set "u_arg=%~1"
  )
  shift

  rem `!arg!` is the argument as passed by the user, propagated as-is to `java`.
  rem `!u_arg!` is the unquoted argument, used to understand if `!arg!` is a JVM or a program argument.

  set "jvm_arg="
  set "wrong_cp="

  rem Unfortunately, parsing of `--jvm.*` and `--vm.*` arguments has to be done blind:
  rem Maybe some of those arguments where not really intended for the launcher but were application arguments
  if "!u_arg:~0,5!"=="--vm." (
    set "jvm_arg=-!u_arg:~5!"
  ) else if "!u_arg:~0,6!"=="--jvm." (
    set "jvm_arg=-!u_arg:~6!"
  )

  if not "!jvm_arg!"=="" (
    if "!jvm_arg!"=="-cp" (
      set "wrong_cp=true"
    ) else if "!jvm_arg!"=="-classpath" (
      set "wrong_cp=true"
    )

    if "!wrong_cp!"=="true" (
      echo "!arg!" argument must be of the form "!arg!=<classpath>", not two separate arguments
      exit /b 1
    ) else if "!jvm_arg:~0,4!"=="-cp=" (
      set "realcp=%realcp%;!jvm_arg:~4!"
    ) else if "!jvm_arg:~0,11!"=="-classpath=" (
      set "realcp=%realcp%;!jvm_arg:~11!"
    ) else (
      rem Quote all VM arguments
      set "jvm_args=!jvm_args! "!jvm_arg!""
    )
  ) else (
    set "launcher_args=!launcher_args!!args_delim!!arg!"
    set "args_delim= "
  )

  goto :arg_loop
)

if "%VERBOSE_GRAALVM_LAUNCHERS%"=="true" echo on

"%location%<jre_bin>\java" %jvm_args% <extra_jvm_args> -cp "%realcp%" <main_class> %launcher_args%
