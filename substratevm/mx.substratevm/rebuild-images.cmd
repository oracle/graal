@echo off

rem Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
rem DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
rem
rem This code is free software; you can redistribute it and/or modify it
rem under the terms of the GNU General Public License version 2 only, as
rem published by the Free Software Foundation.  Oracle designates this
rem particular file as subject to the "Classpath" exception as provided
rem by Oracle in the LICENSE file that accompanied this code.
rem
rem This code is distributed in the hope that it will be useful, but WITHOUT
rem ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
rem FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
rem version 2 for more details (a copy is included in the LICENSE file that
rem accompanied this code).
rem
rem You should have received a copy of the GNU General Public License version
rem 2 along with this work; if not, write to the Free Software Foundation,
rem Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
rem
rem Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
rem or visit www.oracle.com if you need additional information or have any
rem questions.

setlocal enabledelayedexpansion

set "rebuild_images=%~dpnx0"
call :dirname "%rebuild_images%" bin_dir
rem We assume we are in `lib\svm\bin`
set "graalvm_home=%bin_dir%\..\..\.."

set "to_build="
set "custom_args="

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

  set "_tb="
  set "_h="

  if "!u_arg!"=="polyglot" (
    set "_tb=!u_arg!"
  ) else if "!u_arg!"=="libpolyglot" (
    set "_tb=!u_arg!"
  ) else if "!u_arg!"=="js" (
    set "_tb=!u_arg!"
  ) else if "!u_arg!"=="llvm" (
    set "_tb=!u_arg!"
  ) else if "!u_arg!"=="python" (
    set "_tb=!u_arg!"
  ) else if "!u_arg!"=="ruby" (
    set "_tb=!u_arg!"
  ) else if "!u_arg!"=="--help" (
    set "_h=true"
  ) else if "!u_arg!"=="-h" (
    set "_h=true"
  )

  if defined _tb (
    if defined to_build (
      set "to_build=!to_build! !_tb!
    ) else (
      set "to_build=!_tb!
    )
  ) else if defined _h (
    echo Rebuilds native images in place
    call :usage
    exit /b 0
  ) else if "!u_arg!"=="--verbose" (
    set "verbose=true"
  ) else if "!u_arg!"=="-v" (
    set "verbose=true"
  ) else if defined custom_args (
    set "custom_args=!custom_args! !arg!"
  ) else (
    set "custom_args=!arg!"
  )

  goto :arg_loop
)

if not defined to_build (
  echo Nothing to build
  call :usage
  exit /b 0
)

rem The list of components to be built does not contain special characters
for %%f in (%to_build%) do (
  set "cmd_line="%graalvm_home%\bin\native-image""

  if "%%f"=="polyglot" (
    call :launcher polyglot cmd_line
  ) else if "%%f"=="libpolyglot" (
    call :libpolyglot cmd_line
  ) else if "%%f"=="js" (
    call :launcher js cmd_line
  ) else if "%%f"=="llvm" (
    call :launcher lli cmd_line
  ) else if "%%f"=="python" (
    call :launcher graalpython cmd_line
  ) else if "%%f"=="ruby" (
    call :launcher truffleruby cmd_line
  ) else (
    echo Should not reach here
    exit /b 1
  )
  echo Building %%f...
  if defined verbose echo !cmd_line!
  call !cmd_line!
)

goto :eof

:dirname file output
  setlocal
  set "dir=%~dp1"
  set "dir=%dir:~0,-1%"
  endlocal & set "%2=%dir%"
  exit /b 0

:usage
  echo Usage: "%~nx0 [-v|--verbose] polyglot|libpolyglot|js|llvm|python|ruby... [custom native-image args]..."
  exit /b 0

:common cmd_line
  setlocal
  if defined custom_args (
    set "cmd_line=%cmd_line% %custom_args%"
  )
  for /f "tokens=* usebackq" %%l in (`"%graalvm_home%\bin\native-image" --help-extra`) do (
    set "line=%%l"
    if not "!line:--no-server=!"=="!line!" (
      set "cmd_line=%cmd_line% --no-server"
    )
  )
  if exist "%graalvm_home%\lib\svm\builder\svm-enterprise.jar" (
    set "cmd_line=%cmd_line% -g"
  )
  endlocal & set "%1=%cmd_line%"
  exit /b 0

:polyglot_common cmd_line
  set "%1=%cmd_line% --language:all"
  exit /b 0

:libpolyglot cmd_line
  call :common cmd_line
  call :polyglot_common cmd_line
  set "%1=%cmd_line% --macro:polyglot-library"
  exit /b 0

:launcher cmd cmd_line
  call :common cmd_line
  setlocal
  set "cmd_line=%cmd_line% --macro:%1-launcher"
  if "%1"=="polyglot" (
    call :polyglot_common cmd_line
  )
  endlocal & set "%2=%cmd_line%"
  exit /b 0
