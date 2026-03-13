#!/bin/bash

SUITE=$1
javac -d ~/graal/absint-tests/out ~/graal/absint-tests/src/$SUITE/intra/*.java
javac -d ~/graal/absint-tests/out ~/graal/absint-tests/src/$SUITE/inter/*.java
