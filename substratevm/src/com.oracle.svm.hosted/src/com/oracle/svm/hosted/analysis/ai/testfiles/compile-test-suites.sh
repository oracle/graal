#!/bin/bash

SUITE=$1
javac -d ~/graal/absint-tests/out ~/graal/absint-tests/src/$SUITE/*.java
