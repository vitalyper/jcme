#!/bin/bash
function check_for_binary {
    if [ -z `which $1` ] ; then
        echo "Binary $1 is not found on your path"
        exit 1
    fi
}

check_for_binary "java"
sc='jcmegen/src/jcmegen/main.clj' 
cd `dirname $0`
echo -e "Starting clojure script $sc"
java -cp ./jcmegen/lib/*:./jcmegen/src clojure.main "$sc" --input-file config.clj
