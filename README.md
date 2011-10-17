# jcme
The purpose of this project is to compare/contrast java and clojure implementations
of naive matching (stock) price engine. 

Expected hyposis is that:
1. java implementation should be a bit faster (2-9 times)
2. clojure implementation should be much shorter and modular

## Project structure
* bin - fat jars for the implementations
* jcmec - clojure implementation
    * builds with leiningen
    * uses ring (jetty 6x), moustache
* jcmegen - clojure client that generates input, calls into web service/s, verifies and prints results
* jcmej - java implementation
    * builds with maven
    * uses CXF (jetty 7x)
* build.sh - bash script that builds the implementations
* buid_run.sh - bash script wrapper that invokes build.sh and then run.sh
* config.clj - input configuration in clojure reader compatible format
* README.md - this file
* run.sh - invokes main of jcmegen against pre-build jars in bin

## Matching engine requirements
1. REST JSON interface
    * POST /match
        * json: orderId (B|S plus sequence number), price
    * GET /max/B|S
    * GET /results
    * GET /reset
2. Support concurrent requests for match and get max buy and sell price.
3. Return unmatched items.
4. Reset internal state so matching session can be repeated.

## To run
./run.sh

## To build and run (if you modified either implementation)
Make sure maven and leiningen are installed and on the PATH
./build_run.sh

## Todo List
1. Port bash scripts to Windows bat
2. Implement scala version
3. Implement haskell version
4. Collect run statistics: jcmegen will call into public web app which will store and report on
run statistics.

## License
Copyright (C) 2011 Vitaly Peressada

Distributed under the Eclipse Public License, the same as Clojure.
