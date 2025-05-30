::
:: ----------------------------------------------------------------------------------------------------
::
:: Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

call :getScriptLocation location


:: The two white lines above this comment are significant.

set "jvm_args=--add-modules=ALL-DEFAULT"
set "javac_args="

call :escape_args %*
for %%a in (%args%) do (
  call :unescape_arg %%a
  call :process_arg !arg!
  if errorlevel 1 exit /b 1
)

if "%VERBOSE_GRAALVM_LAUNCHERS%"=="true" echo on

"%location%\espresso" %jvm_args% -m jdk.compiler/com.sun.tools.javac.Main %javac_args%

exit /b %errorlevel%
:: Function are defined via labels, so have to be defined at the end of the file and skipped
:: in order not to be executed.

:escape_args
    set "args=%*"
    :: Without early exit on empty contents, substitutions fail.
    if "!args!"=="" exit /b 0
    set "args=%args:,=##GR_ESC_COMMA##%"
    set "args=%args:;=##GR_ESC_SEMI##%"
    :: Temporarily, so that args are split on '=' only.
    set "args=%args: =##GR_ESC_SPACE##%"
    :: Temporarily, otherwise we won't split on '=' inside quotes.
    set "args=%args:"=##GR_ESC_QUOTE##%"
    :: We can't replace equal using the variable substitution syntax.
    call :replace_equals %args%
    set "args=%args:##GR_ESC_SPACE##= %"
    set "args=%args:##GR_ESC_QUOTE##="%"
    exit /b 0

:replace_equals
    setlocal
    :: The argument passed to this function was split on =, because all other
    :: delimiters were replaced in escape_args.
    set "arg=%1"
    if "!arg!"=="" goto :end_replace_equals
    set "args=%1"
    shift
    :loop_replace_equals
        set "arg=%1"
        if "!arg!"=="" goto :end_replace_equals
        set "args=%args%##GR_ESC_EQUAL##%arg%"
        shift
        goto :loop_replace_equals
    :end_replace_equals
        endlocal & ( set "args=%args%" )
        exit /b 0

:unescape_arg
    set "arg=%*"
    set "arg=%arg:##GR_ESC_COMMA##=,%"
    set "arg=%arg:##GR_ESC_SEMI##=;%"
    set "arg=%arg:##GR_ESC_EQUAL##==%"
    exit /b 0

:is_quoted
    setlocal
    set "args=%*"
    set /a argslen=0
    for %%a in (%args%) do set /a argslen+=1
    if %argslen% gtr 1 (
        set "quoted=false"
    ) else (
        if "!args:~0,1!!args:~-1!"=="""" ( set "quoted=true" ) else ( set "quoted=false" )
    )
    endlocal & ( set "quoted=%quoted%" )
    exit /b 0

:unquote_arg
    :: Sets %arg% to a version of the argument with outer quotes stripped, if present.
    call :is_quoted %*
    setlocal
    set "maybe_quoted=%*"
    if %quoted%==true ( set "arg=%~1" ) else ( set "arg=!maybe_quoted!" )
    endlocal & ( set "arg=%arg%" )
    exit /b 0

:process_vm_arg
    if %arg_quoted%==false (
        call :is_quoted %*
        set "arg_quoted=%quoted%"
    )
    call :unquote_arg %*
    set "vm_arg=%arg%"

    if %arg_quoted%==true ( set "arg="%vm_arg%"" ) else ( set "arg=%vm_arg%" )
    set "jvm_args=%jvm_args% !arg!"
    exit /b 0

:process_arg
    set "original_arg=%*"
    call :unquote_arg !original_arg!
    set "arg_quoted=%quoted%"

    if "!arg:~0,2!"=="-J" (
        set prefix=vm
        call :unquote_arg !arg:~2!
        call :process_vm_arg !arg!
        if errorlevel 1 exit /b 1
    ) else (
        :: Use !original_arg! instead of !arg! to preserve surrounding quotes if present.
        set "launcher_args=%launcher_args% !original_arg!"
    )
    exit /b 0

:: If this script is in `%PATH%` and called quoted without a full path (e.g., `"java"`), `%~dp0` is expanded to `cwd`
:: rather than the path to the script.
:: This does not happen if `%~dp0` is accessed in a subroutine.
:getScriptLocation variableName
    set "%~1=%~dp0"
    exit /b 0

endlocal
