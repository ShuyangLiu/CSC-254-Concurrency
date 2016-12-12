#!/bin/bash

rm output.txt

for i in {1..32}
do
  #printf "\n%d\n" $i >> output.txt
  #echo $i >> output.txt
  printf "\n" "" >> output.txt
  timeout 1 java -cp ./bin Life --glider --headless -s 250 -t $i >> output.txt
done
