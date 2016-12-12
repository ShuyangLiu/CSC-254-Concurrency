
# mkdir data_part1
# javac -d data_part1 *.java

# for i in {1..32}
#   do
#
#     java -cp ./data_part1 Life --headless --glider -s 500 -t #i >> output.txt
#   done


timeout() {

    time=$1

    # start the command in a subshell to avoid problem with pipes
    # (spawn accepts one command)
    command="/bin/sh -c \"$2\""

    expect -c "set echo \"-noecho\"; set timeout $time; spawn -noecho $command; expect timeout { exit 1 } eof { exit 0 }"

    if [ $? = 1 ] ; then
        echo "Timeout after ${time} seconds"
    fi

}


timeout 10 while true;  do echo "Still going..."; sleep 15; done;
