# Load Big GeoJSON Extension (QuPath 0.6.0)

v0.2.1
QuPath extension to import very large GeoJSON files using a streaming parser, so memory usage stays bounded while features are loaded.

## What It Does

- Adds menu items under `Objects`:
  - `Import large GeoJSON (streaming)...`
  - `Clear existing objects before import` (toggle)
- Supports `.geojson`, `.json`, `.geojson.gz`, `.json.gz`
- Imports features in chunks (`1000` by default)
- Converts supported GeoJSON geometry to QuPath ROI objects
- Creates:
  - cell objects when both `geometry` and `nucleusGeometry` are present
  - detection objects when `properties.objectType == "detection"`
  - annotation objects otherwise
- Sets object name from `properties.name` when present, otherwise from `properties.cell_id`

## Supported Geometry Types

- `Polygon`
- `MultiPolygon`
- `Point`
- `MultiPoint`
- `LineString`
- `MultiLineString`

## Build Requirements

- Java `21` (recommended for QuPath `0.6.0` compatibility)
- Gradle wrapper in this repo (`./gradlew`)

## Build

```bash
./gradlew clean build
```

Built JAR:

`build/libs/LoadBigGeoJSON-1.0.0.jar`

## Install In QuPath

1. Build the project.
2. Copy `build/libs/LoadBigGeoJSON-1.0.0.jar` into your QuPath extensions directory.
3. Restart QuPath.
4. Open an image and go to `Objects` menu.

## Use From QuPath Menu

1. Open image in QuPath.
2. Optional: enable `Clear existing objects before import`.
3. Click `Import large GeoJSON (streaming)...`.
4. Choose your GeoJSON file.
5. Check QuPath log for import summary.

## Example Script

Annotated script:

`scripts/import_large_geojson_via_extension.groovy`

This script:

- opens a file chooser safely on JavaFX thread
- calls the extension importer by reflection
- lets you control `chunkSize` and `clearExisting`

## GeoJSON Notes

- Root must be a JSON object containing a `features` array.
- Invalid/unsupported geometries are skipped.
- For polygons, rings are automatically closed if needed.

## Troubleshooting

- `No image is open; import canceled`:
  - Open an image first, then rerun import.
- Dependency/build issues:
  - Use Java 21 and `./gradlew`.
- Large files:
  - Keep chunk size at `1000` unless you have a reason to tune.
