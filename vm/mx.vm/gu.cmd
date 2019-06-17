::
:: ----------------------------------------------------------------------------------------------------
::
:: Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
setlocal enableDelayedExpansion
set location=%~dp0
set root_dir=%location%..\..\..
set relcp=%location%..\*.jar

set realcp=
set delim=
for %%i in ("%relcp%") do (
  set realcp=!realcp!!delim!%%i
  set delim=;
)

set unique=%temp%\%~nx0
set GU_POST_DELETE_LIST=%unique%.delete
set GU_POST_COPY_CONTENTS=%unique%.copy

DEL /f /q %GU_POST_DELETE_LIST% >NUL
DEL /f /q %GU_POST_COPY_CONTENTS% >NUL

if "%VERBOSE_GRAALVM_LAUNCHERS%"=="true" echo on

"%root_dir%\bin\java" %GU_OPTS% -cp "%realcp%" "-DGRAAL_HOME=%root_dir%" org.graalvm.component.installer.ComponentInstaller %*

if errorlevel 11 (
  echo Retrying operations on locked files...
  for /f "delims=|" %%F in (%GU_POST_DELETE_LIST%) DO (
    DEL /F /S /Q "%%F" >NUL
    IF EXIST "%%F\" (
      RD /S /Q "%%F" >NUL
    )
  )

  for /f "delims=| tokens=1,2" %%F in (%GU_POST_COPY_CONTENTS%) DO (
    COPY /Y /B "%%G\*.*" "%%F" >NUL
    DEL /F /S /Q "%%G" >NUL
    RD /S /Q "%%G" >NUL
  )
)
:end
