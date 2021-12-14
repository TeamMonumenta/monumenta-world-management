# monumenta-world-management

Bungeecord & network aware world management / instancing system. Depends on MonumentaNetworkRelay and MonumentaRedisSync.

Provides tools for loading, unloading, copying, and moving players between worlds.

Also has an instance awareness system that is scoreboard based and configurable, allowing instances to be created on demand.

Because of integration with MonumentaRedisSync, worlds are loaded seamlessly when players join, there is no transition where they briefly land on a default world and need sorting.

There is also a full-featured API to allow other plugins to use these same features.

As of 1.2 this plugin is stable and usable.

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
            <version>1.3</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
```
