#!/bin/sh
MAIN_CLASS=$1
mx native-image -cp ~/graal/absint-tests/out $MAIN_CLASS -H:+ReportExceptionStackTraces -H:Log=AbstractInterpretation -H:Dump=:2 -H:PrintGraph=Network -H:MethodFilter=$MAIN_CLASS.*
