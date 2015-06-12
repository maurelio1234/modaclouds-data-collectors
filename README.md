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
MODACLOUDS_TOWER4CLOUDS_MANAGER_IP (e.g., "127.0.0.1")
MODACLOUDS_TOWER4CLOUDS_MANAGER_PORT (e.g., "8170") 
MODACLOUDS_TOWER4CLOUDS_DC_SYNC_PERIOD (e.g., 30)
MODACLOUDS_TOWER4CLOUDS_RESOURCES_KEEP_ALIVE_PERIOD (e.g., 60)
MODACLOUDS_TOWER4CLOUDS_CLOUD_PROVIDER_ID (e.g., "amazon")
MODACLOUDS_TOWER4CLOUDS_CLOUD_PROVIDER_TYPE (e.g., "iaas")
MODACLOUDS_TOWER4CLOUDS_VM_ID (e.g., "VM1")
MODACLOUDS_TOWER4CLOUDS_VM_TYPE (e.g., "Frontend")
MODACLOUDS_TOWER4CLOUDS_INTERNAL_COMPONENT_ID (e.g., "App-frontend")
MODACLOUDS_TOWER4CLOUDS_INTERNAL_COMPONENT_TYPE (e.g., "App")
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
