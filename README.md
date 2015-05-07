maven-profiledep-extention
==========================

Specify dependencies between different profiles in maven build.

You can see active profiles like this:

````
$ mvn help:active-profiles
````

Let's pretend that this will produce follwing output:

````
The following profiles are active:

 - java6 (source: groupId:artifactId:version)
````

These are default profiles configured with `activeByDefault` tag.
Now when you specify profiles on command line, default profiles are not activated,
but replaced with specified profiles. Here we should expect two profiles to be
active.

````
$ mvn '-Pjava7,bootclasspath' help:active-profiles
````

Instead 3 profiles are activated:

````
The following profiles are active:

 - java7 (source: groupId:artifactId:version)
 - java7-bootclasspath (source: groupId:artifactId:version)
 - fork-javac (source: groupId:artifactId:version)
````

Activation is based on dependency information. To specify that one profile
depends on another you put special `profiledep` property into profile
definition. `profiledep` is a list of comma separated profile ids.
You can prepend exclamation mark (`!`) to profile id in `profiledep` list to
specify _conflicts-relationship_. When conflicting profiles are activated
error is raised by this extention.

Note that there is no `bootclasspath` profile in the above list.
`bootclasspath` is a _virtual_ profile. A profile can provide any number
of virtual profiles by specifying `profileprovide` property in profile
definition. If more than one profile provides the same virtual profile
only one is actually activated.

Here is an example of profile definitions:

````xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <!-- ... -->
    <build>
        <!-- ... -->
    </build>
    <profiles>
        <profile>
            <id>java6-bootclasspath</id>
            <properties>
                <profileprovide>bootclasspath</profileprovide>
                <profiledep>java6,fork-javac</profiledep>
            </properties>
            <build>
                <!-- ... -->
            </build>
        </profile>
        <profile>
            <id>java7-bootclasspath</id>
            <properties>
                <profileprovide>bootclasspath</profileprovide>
                <profiledep>java7,fork-javac</profiledep>
            </properties>
            <build>
                <!-- ... -->
            </build>
        </profile>

        <profile>
            <id>java8-bootclasspath</id>
            <properties>
                <profileprovide>bootclasspath</profileprovide>
                <profiledep>java8,fork-javac</profiledep>
            </properties>
            <build>
                <!-- ... -->
            </build>
        </profile>

        <profile>
            <id>java6</id>
            <properties>
                <profileprovide>java-version</profileprovide>
            </properties>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <build>
                <!-- ... -->
            </build>
        </profile>
        <profile>
            <id>java7</id>
            <properties>
                <profileprovide>java-version</profileprovide>
            </properties>
            <build>
                <!-- ... -->
            </build>
        </profile>
        <profile>
            <id>java8</id>
            <properties>
                <profileprovide>java-version</profileprovide>
            </properties>
            <build>
                <!-- ... -->
            </build>
        </profile>

        <profile>
            <id>fork-javac</id>
            <build>
                <!-- ... -->
            </build>
        </profile>

    </profiles>
</project>
````

Currently dependencies works across single pom.xml. You can't depend
on profile from parent pom.

Installation
------------

### Maven 3.3.1 ###

Maven 3.3.1 allows core-dependencies to be specified in `extentions.xml` file.
See [this blog post](http://takari.io/2015/03/19/core-extensions.html).

Here is `.mvn/extensions.xml`

````xml
<?xml version="1.0" encoding="UTF-8"?>
<extensions>
  <extension>
    <groupId>com.github.sviperll</groupId>
    <artifactId>maven-profiledep-extention</artifactId>
    <version>0.1-SNAPSHOT</version>
  </extension>
</extensions>
````

### Older maven versions ###

Older versions require `maven-profiledep-extention.jar` file to be placed
in `$MAVEN_HOME/lib/ext` directory.
