Modaclouds-DataCollectors
=========================
## Requirements

Please download and install the following pieces of software before using the Data Collectors component:
* JRE 1.7 or higher from http://www.java.com/
* Collectl 3.6.7 or higher from http://collectl.sourceforge.net/
* Sigar 1.6.4 or higher from http://sourceforge.net/projects/sigar/

## Installation
First, Compile the code or download the latest release from [https://github.com/imperial-modaclouds/Modaclouds-DataCollectors/releases](https://github.com/imperial-modaclouds/Modaclouds-DataCollectors/releases).
Then run it as: 
```java
java -Djava.library.path=<path>/dcs/hyperic-sigar-1.6.4/sigar-bin/lib/ -jar data-collector-1.0.jar 
```


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
