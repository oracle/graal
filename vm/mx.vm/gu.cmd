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
set gu_post_delete_list=%unique%.delete
set gu_post_copy_contents=%unique%.copy

if exist %gu_post_delete_list% (
  del /f /q %gu_post_delete_list% >nul
)
if exist %gu_post_copy_contents% (
  del /f /q %gu_post_copy_contents% >nul
)

if "%VERBOSE_GRAALVM_LAUNCHERS%"=="true" echo on

"%root_dir%\bin\java" %GU_OPTS% -cp "%realcp%" "-DGRAAL_HOME=%root_dir%" org.graalvm.component.installer.ComponentInstaller %*

if errorlevel 11 (
  echo Retrying operations on locked files...
  for /f "delims=|" %%f in (%gu_post_delete_list%) do (
    del /f /s /q "%%f" >nul
    if exist "%%f\" (
      rd /s /q "%%f" >nul
    )
  )

  for /f "delims=| tokens=1,2" %%f in (%gu_post_copy_contents%) do (
    copy /y /b "%%g\*.*" "%%f" >nul
    del /f /s /q "%%g" >nul
    rd /s /q "%%g" >nul
  )
)
:end
