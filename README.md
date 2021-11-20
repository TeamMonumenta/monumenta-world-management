# monumenta-world-management

Bungeecord & network aware world management / instancing system. Depends on MonumentaNetworkRelay and MonumentaRedisSync.

Currently provides tools for loading & unloading worlds automatically when players join with nonzero instance scores.

This plugin will eventually be a generic tool useful for any server, but specifically developed to support large world instancing like Monumenta's dungeons.

More is planned in the future, this should be considered unstable / WIP for now

## Download
You can download the latest version of this plugin from [GitHub Packages](https://github.com/TeamMonumenta/monumenta-world-management/packages).

## Maven dependency
```xml
    <repositories>
        <repository>
            <id>monumenta-world-management</id>
            <url>https://raw.githubusercontent.com/TeamMonumenta/monumenta-world-management/master/mvn-repo/</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>com.playmonumenta</groupId>
            <artifactId>worlds</artifactId>
            <version>1.1</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
```
