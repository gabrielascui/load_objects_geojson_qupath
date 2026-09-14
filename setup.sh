#!/usr/bin/env bash



cd ./load_objects_geojson_qupath

##------------- make directories
mkdir -p ./{src/{main/{java/qupath/ext/load_obj_chunk,resources},test/java/qupath/ext/load_obj_chunk}}

##------------- touch files
touch README.md

##------------- cat files

## build.gradle
echo "plugins {
    id 'java'
    id 'application'
}

group = 'qupath.ext'
version = '1.0.0'

sourceCompatibility = '17'
targetCompatibility = '17'

repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' } // Optional if using QuPath snapshots
}

dependencies {
    // QuPath core dependency (provided because QuPath already runs in JVM)
    implementation 'org.qupath:qupath-core:0.4.2' 
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
}

test {
    useJUnitPlatform()
}

// Optional: build a fat jar to drop into QuPath's extensions folder
jar {
    manifest {
        attributes(
            'Main-Class': 'qupath.ext.load_obj_chunk.LoadBigGeoJSON'
        )
    }
    from {
        configurations.runtimeClasspath.collect { it.isDirectory() ? it : zipTree(it) }
    }
}" > build.gradle

## settings.gradle
echo "rootProject.name = 'LoadBigGeoJSON'" > settings.gradle

## plugin.properties
echo "name=Load Big GeoJSON Extension
version=1.0.0
author=Gabriel Ascui
description=Load big GeoJSON files plugin for QuPath 0.6.0
mainClass=qupath.ext.load_obj_chunk.LoadBigGeoJSON" > src/main/resources/plugin.properties

