#!/bin/bash

rm -f output.txt

javac -d bin *.java

threads=( 1 2 3 4 6 8 12 16 24 32 48 )

for i in "${threads[@]}" 
do
  printf "\n" "" >> output.txt
  java -cp ./bin Life --glider --headless -s 250 -t $i >> output.txt
done
