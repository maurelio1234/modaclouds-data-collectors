Modaclouds-DataCollectors
=========================
## Requirements

Please download and install the following pieces of software before using the Data Collectors component:
* JRE 1.7 or higher from http://www.java.com/
* Collectl 3.6.7 or higher from http://collectl.sourceforge.net/
* Sigar 1.6.4 or higher from http://sourceforge.net/projects/sigar/

## Installation
* First, Compile the code or download the latest release from [https://github.com/imperial-modaclouds/Modaclouds-DataCollectors/releases](https://github.com/imperial-modaclouds/Modaclouds-DataCollectors/releases).
* Then set the following system environments:
```
MODACLOUDS_KNOWLEDGEBASE_ENDPOINT_IP=<KB_IP> (e.g., "127.0.0.1")
MODACLOUDS_KNOWLEDGEBASE_ENDPOINT_PORT=<KB_PORT> (e.g., "3030") 
MODACLOUDS_KNOWLEDGEBASE_DATASET_PATH=<KB_PATH> (e.g., "/modaclouds/kb")
MODACLOUDS_MONITORING_DDA_ENDPOINT_IP=<DDA_IP> (e.g., "127.0.0.1")
MODACLOUDS_MONITORING_DDA_ENDPOINT_PORT=<DDA_PORT> (e.g., "8175")
MODACLOUDS_KNOWLEDGEBASE_SYNC_PERIOD=<SYNC_PERIOD> (in seconds, e.g., "10")
MODACLOUDS_MONITORED_APP_ID=<APP_ID> (e.g., "mic1")
MODACLOUDS_MONITORED_VM_ID=<VM_ID> (e.g., "frontend1")
```
* Then run it as: 
```java
java -Djava.library.path=<path>/dcs/hyperic-sigar-1.6.4/sigar-bin/lib/ -jar data-collector-VERSION.jar mode
```
where "tower4clouds" means to use the knowledge base and "file" means to use the configuration files. If "file" mode is selected, the collectors that need to start should also be provided, such as sigar,cloudwatch.

One thing to be noticed is that the "tools.jar" has to be installed locally since it is not in any public maven repository.

## Maven repository

Release repository

```xml
<repositories>
    ...
    <repository>
        <id>imperial-modaclouds-releases</id>
        <url>https://github.com/imperial-modaclouds/imperial-modaclouds-mvn-repo/raw/master/releases</url>
    </repository>
    ...
</repositories>
```
