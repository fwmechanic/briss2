# How I (fwmechanic) build Briss (in 2020.03)

## Build tools:

I build and run Briss on Windows:
* [Zulu OpenJDK 11](https://www.azul.com/downloads/zulu-community/?version=java-11-lts&package=jdk) currently: zulu11.37.17-ca-jdk11.0.6-win_x64.zip
* [Maven](https://maven.apache.org/download.cgi)           currently: apache-maven-3.6.3-bin.zip

## Separately/locally Built Prerequisites

### JPedal:
* `git clone -b jdk11_build https://github.com/fwmechanic/JPedal.git`
   * fork of https://github.com/Lonzak/JPedal.git that builds using JDK 11
* `mvn clean install`

## Building Briss:
* `mvn clean package`
   * this writes 2 .jar files into `$thisreporoot/target/`: a smaller `.jar` with name-prefix `original-` and a larger `.jar` lacking that prefix; the larger is an "uber-jar" containing all prereqs; all you need to run it is a Java 11's `java`, demonstated next).

### Running Briss:

`java -jar "$thisreporoot/target/briss-1.0-rc.2.jar" "$fnm_of_pdf_to_crop"`
