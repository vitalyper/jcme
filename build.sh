#!/bin/bash
function check_for_binary {
    if [ -z `which $1` ] ; then
        echo "Binary $1 is not found on your path"
        exit 1
    fi
}

echo -e "In order to build you will need maven and leiningen on your PATH.\nDo you want to continue? [y|n]:\c"
read p1
p1=`echo $p1 | tr '[A-Z]' '[a-z]'`
if [ ! "$p1" == "y" ] ; then
    echo "Skipping build."
    exit 0
fi

check_for_binary "mvn"
check_for_binary "lein"

cd `dirname $0`
# Build projects
# jcmej
echo "Building java implementation from jcmej subdir"
pushd jcmej > /dev/null
mvn clean package
cp target/jcmej-*-jar-with-dependencies.jar ../bin/jcmej.jar

# jcmec
popd > /dev/null
pushd jcmec > /dev/null
echo -e "\nBuilding clojure implementation from jcmec subdir" 
lein uberjar
cp jcmec-*-standalone.jar ../bin/jcmec.jar
popd > /dev/null
chmod -R a+rx bin/*

echo -e "\nBuild finished.\n"
