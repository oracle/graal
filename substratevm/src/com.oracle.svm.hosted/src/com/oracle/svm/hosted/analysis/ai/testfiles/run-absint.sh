#!/bin/sh

FILE_NAME=$1
mx native-image -cp ~/graal/absint-tests/out $FILE_NAME -H:Log=AbstractInterpretation -H:Dump=:2 -H:PrintGraph=Network -H:MethodFilter=$FILE_NAME.*

