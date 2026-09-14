import qupath.ext.loadobjectsgeojson.GeoJsonStreamingImporter
 
/*
 * Example usage script for LoadBigGeoJSON extension.
 *
 * Requirements:
 * 1) Extension JAR installed in QuPath.
 * 2) An image open in QuPath.
 *
 * Behavior:
 * - Prompts for a GeoJSON/GeoJSON.gz file.
 * - Calls GeoJsonStreamingImporter.importObjectsStreaming() directly
 */

// --- Configuration ---
// - chunkSize: how many objects to add per hierarchy update.
// - clearExisting: remove existing objects before import.

int chunkSize = 1000
boolean clearExisting = false
 
// Validate QuPath context.
def imageData = getCurrentImageData()
if (imageData == null) {
    print 'No image open. Open an image in QuPath first.'
    return
}

// promptForFile is inherited from QPEx and is available directly in scripts.
// Extensions are given without the leading dot; it also happily matches
// "*.geojson.gz" / "*.json.gz" since it filters by suffix.
def file = promptForFile("geojson", "json", "gz")
if (file == null) {
    print "No file selected"
    return
}
 
// Execute import and report summary.
int imported = GeoJsonStreamingImporter.importObjectsStreaming(
        imageData, file.toPath(), chunkSize, clearExisting
)
print "Imported ${imported} objects from ${file.getAbsolutePath()}"