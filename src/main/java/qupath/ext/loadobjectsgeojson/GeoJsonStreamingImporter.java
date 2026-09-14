package qupath.ext.loadobjectsgeojson;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.interfaces.ROI;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/**
 * Standalone streaming GeoJSON importer, callable directly (no reflection needed)
 * via {@code import qupath.ext.loadobjectsgeojson.GeoJsonStreamingImporter} from a
 * Groovy script or QuPath pipeline, as well as from {@link LoadObjectsGeojsonExtension}'s
 * menu item.
 */
public class GeoJsonStreamingImporter {

    private static final Logger logger = LoggerFactory.getLogger(GeoJsonStreamingImporter.class);

    // Keep memory bounded when importing very large feature collections.
    public static final int DEFAULT_CHUNK_SIZE = 1000;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    private GeoJsonStreamingImporter() {
        // static utility class, not to be instantiated
    }

    /**
     * Import GeoJSON objects by streaming the features array to avoid loading the full file in memory.
     *
     * <pre>{@code
     * import qupath.ext.loadobjectsgeojson.GeoJsonStreamingImporter
     *
     * int imported = GeoJsonStreamingImporter.importObjectsStreaming(
     *         imageData, file.toPath(), 1000, false)
     * print "Imported ${imported} objects"
     * }</pre>
     */
    public static int importObjectsStreaming(
            ImageData<?> imageData,
            Path geoJsonPath,
            int chunkSize,
            boolean clearExisting
    ) throws IOException {

        Objects.requireNonNull(imageData, "imageData must not be null");
        Objects.requireNonNull(geoJsonPath, "geoJsonPath must not be null");

        PathObjectHierarchy hierarchy = imageData.getHierarchy();
        if (clearExisting) {
            hierarchy.clearAll();
        }

        int totalImported = 0;
        List<PathObject> batch = new ArrayList<>(chunkSize);

        try (InputStream raw = Files.newInputStream(geoJsonPath);
             InputStream in = wrapMaybeGzip(raw, geoJsonPath);
             JsonParser parser = JSON_FACTORY.createParser(in)) {

            JsonToken token = parser.nextToken();
            if (token != JsonToken.START_OBJECT) {
                throw new IOException("GeoJSON root must be a JSON object");
            }

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.getCurrentName();
                if (fieldName == null) {
                    continue;
                }

                parser.nextToken();
                if (!"features".equals(fieldName)) {
                    parser.skipChildren();
                    continue;
                }

                if (parser.currentToken() != JsonToken.START_ARRAY) {
                    throw new IOException("GeoJSON 'features' must be an array");
                }

                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    Feature feature = MAPPER.readValue(parser, Feature.class);
                    PathObject pathObject = toPathObject(feature);
                    if (pathObject == null) {
                        continue;
                    }

                    batch.add(pathObject);
                    if (batch.size() >= chunkSize) {
                        hierarchy.addObjects(new ArrayList<>(batch));
                        totalImported += batch.size();
                        batch.clear();
                    }
                }
            }
        }

        if (!batch.isEmpty()) {
            hierarchy.addObjects(batch);
            totalImported += batch.size();
        }

        logger.info("Imported {} objects from {}", totalImported, geoJsonPath);
        return totalImported;
    }

    private static InputStream wrapMaybeGzip(InputStream raw, Path path) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(raw);
        buffered.mark(2);
        int first = buffered.read();
        int second = buffered.read();
        buffered.reset();

        boolean gzipMagic = first == 0x1f && second == 0x8b;
        boolean hasGzExt = path.toString().endsWith(".gz");
        return (gzipMagic || hasGzExt) ? new GZIPInputStream(buffered, 64 * 1024) : buffered;
    }

    private static PathObject toPathObject(Feature feature) {
        if (feature == null || feature.geometry == null) {
            return null;
        }

        ROI roi = toRoi(feature.geometry);
        if (roi == null) {
            return null;
        }

        String objectType = feature.properties == null
                ? null
                : asString(feature.properties.get("objectType"));

        ROI nucleusRoi = toRoi(feature.nucleusGeometry);

        PathObject pathObject;
        if (nucleusRoi != null) {
            pathObject = PathObjects.createCellObject(roi, nucleusRoi);
        } else if ("detection".equalsIgnoreCase(objectType)) {
            pathObject = PathObjects.createDetectionObject(roi);
        } else {
            pathObject = PathObjects.createAnnotationObject(roi);
        }

        setObjectName(pathObject, feature.properties);
        setObjectClassification(pathObject, feature.properties);

        return pathObject;
    }

    private static void setObjectName(PathObject pathObject, Map<String, Object> properties) {
        if (properties == null) {
            return;
        }

        Object name = properties.get("name");
        if (name == null) {
            name = properties.get("cell_id");
        }

        if (name != null) {
            pathObject.setName(String.valueOf(name));
        }
    }

    private static void setObjectClassification(PathObject pathObject, Map<String, Object> properties) {
        if (properties == null) {
            return;
        }
    
        Object classificationObj = properties.get("classification");
        if (classificationObj instanceof Map<?, ?> classMap) {
            Object name = classMap.get("name");
            if (name != null) {
                pathObject.setPathClass(PathClass.fromString(String.valueOf(name)));
            }
        } else if (classificationObj instanceof String className) {
            pathObject.setPathClass(PathClass.fromString(className));
        }
    }

    private static ROI toRoi(GeometryNode geometryNode) {
        Geometry geometry = toJtsGeometry(geometryNode);
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }
        return GeometryTools.geometryToROI(geometry, null);
    }

    private static Geometry toJtsGeometry(GeometryNode geometryNode) {
        if (geometryNode == null || geometryNode.type == null || geometryNode.coordinates == null) {
            return null;
        }

        JsonNode coords = geometryNode.coordinates;
        switch (geometryNode.type) {
            case "Polygon":
                return toPolygon(coords);
            case "MultiPolygon":
                return toMultiPolygon(coords);
            case "Point":
                return toPoint(coords);
            case "MultiPoint":
                return toMultiPoint(coords);
            case "LineString":
                return toLineString(coords);
            case "MultiLineString":
                return toMultiLineString(coords);
            default:
                return null;
        }
    }

    private static Geometry toPolygon(JsonNode coords) {
        if (!coords.isArray() || coords.isEmpty()) {
            return null;
        }

        LinearRing shell = toRing(coords.get(0));
        if (shell == null) {
            return null;
        }

        List<LinearRing> holes = new ArrayList<>();
        for (int i = 1; i < coords.size(); i++) {
            LinearRing hole = toRing(coords.get(i));
            if (hole != null) {
                holes.add(hole);
            }
        }

        return GEOMETRY_FACTORY.createPolygon(shell, holes.toArray(new LinearRing[0]));
    }

    private static Geometry toMultiPolygon(JsonNode coords) {
        if (!coords.isArray()) {
            return null;
        }

        List<Polygon> polygons = new ArrayList<>();
        for (JsonNode polygonCoords : coords) {
            Geometry polygon = toPolygon(polygonCoords);
            if (polygon instanceof Polygon) {
                polygons.add((Polygon) polygon);
            }
        }

        return GEOMETRY_FACTORY.createMultiPolygon(polygons.toArray(new Polygon[0]));
    }

    private static Geometry toPoint(JsonNode coords) {
        Coordinate coordinate = toCoordinate(coords);
        return coordinate == null ? null : GEOMETRY_FACTORY.createPoint(coordinate);
    }

    private static Geometry toMultiPoint(JsonNode coords) {
        if (!coords.isArray()) {
            return null;
        }
        List<Coordinate> coordinates = new ArrayList<>();
        for (JsonNode node : coords) {
            Coordinate c = toCoordinate(node);
            if (c != null) {
                coordinates.add(c);
            }
        }
        return GEOMETRY_FACTORY.createMultiPointFromCoords(coordinates.toArray(new Coordinate[0]));
    }

    private static Geometry toLineString(JsonNode coords) {
        Coordinate[] coordinates = toCoordinateArray(coords);
        return coordinates.length < 2 ? null : GEOMETRY_FACTORY.createLineString(coordinates);
    }

    private static Geometry toMultiLineString(JsonNode coords) {
        if (!coords.isArray()) {
            return null;
        }

        List<org.locationtech.jts.geom.LineString> lines = new ArrayList<>();
        for (JsonNode line : coords) {
            Coordinate[] lineCoords = toCoordinateArray(line);
            if (lineCoords.length >= 2) {
                lines.add(GEOMETRY_FACTORY.createLineString(lineCoords));
            }
        }

        return GEOMETRY_FACTORY.createMultiLineString(lines.toArray(new org.locationtech.jts.geom.LineString[0]));
    }

    private static LinearRing toRing(JsonNode ringNode) {
        Coordinate[] coordinates = toCoordinateArray(ringNode);
        if (coordinates.length < 4) {
            return null;
        }

        Coordinate first = coordinates[0];
        Coordinate last = coordinates[coordinates.length - 1];
        if (!first.equals2D(last)) {
            Coordinate[] closed = new Coordinate[coordinates.length + 1];
            System.arraycopy(coordinates, 0, closed, 0, coordinates.length);
            closed[closed.length - 1] = new Coordinate(first);
            coordinates = closed;
        }

        return GEOMETRY_FACTORY.createLinearRing(coordinates);
    }

    private static Coordinate[] toCoordinateArray(JsonNode node) {
        if (!node.isArray()) {
            return new Coordinate[0];
        }

        List<Coordinate> coordinates = new ArrayList<>(node.size());
        for (JsonNode coordNode : node) {
            Coordinate c = toCoordinate(coordNode);
            if (c != null) {
                coordinates.add(c);
            }
        }
        return coordinates.toArray(new Coordinate[0]);
    }

    private static Coordinate toCoordinate(JsonNode node) {
        if (!node.isArray() || node.size() < 2) {
            return null;
        }

        JsonNode xNode = node.get(0);
        JsonNode yNode = node.get(1);
        if (!xNode.isNumber() || !yNode.isNumber()) {
            return null;
        }

        return new Coordinate(xNode.asDouble(), yNode.asDouble());
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static class Feature {
        public String type;
        public String id;
        public GeometryNode geometry;
        public GeometryNode nucleusGeometry;
        public Map<String, Object> properties;
    }

    private static class GeometryNode {
        public String type;
        public JsonNode coordinates;
    }
}
