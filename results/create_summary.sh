#!/bin/sh

cd "$1" # path to .txt logs

for file in *.txt
do
    cat "$file" | grep "+-"
done
