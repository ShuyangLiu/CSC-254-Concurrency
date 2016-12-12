
mkdir data_part1
javac -d data_part1 *.java

for i in {1..32}
  do
    java -cp ./data_part1 Life --headless --glider -s 500 -t #i >> output.txt
  done
