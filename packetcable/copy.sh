#!/bin/sh


NOW=$(date +"%Y%m%d-%H%M")

cp target/protocol_plugins.packetcable-0.4.0-SNAPSHOT.jar ../../controller/opendaylight/distribution/opendaylight/target/distribution.opendaylight-0.1.1-SNAPSHOT-osgipackage/opendaylight/plugins/
cp target/protocol_plugins.packetcable-0.4.0-SNAPSHOT.jar ../../plugin.archive/protocol_plugins.packetcable-0.4.0-SNAPSHOT.$NOW.jar

