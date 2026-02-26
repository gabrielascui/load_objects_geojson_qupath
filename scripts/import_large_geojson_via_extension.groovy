import javafx.stage.FileChooser
import qupath.lib.gui.QuPathGUI
import qupath.lib.images.ImageData
import javafx.application.Platform

import java.lang.reflect.Method
import java.nio.file.Path
import java.util.concurrent.FutureTask

/*
 * Example usage script for LoadBigGeoJSON extension.
 *
 * Requirements:
 * 1) Extension JAR installed in QuPath.
 * 2) An image open in QuPath.
 *
 * Behavior:
 * - Prompts for a GeoJSON/GeoJSON.gz file.
 * - Calls LoadBigGeoJSON.importObjectsStreaming(...) via reflection.
 */

// Validate QuPath context.
def imageData = getCurrentImageData()
if (imageData == null) {
    print 'No image open. Open an image in QuPath first.'
    return
}

// User options:
// - chunkSize: how many objects to add per hierarchy update.
// - clearExisting: remove existing objects before import.
int chunkSize = 1000
boolean clearExisting = true

// Build file chooser (must be shown on JavaFX thread).
def gui = QuPathGUI.getInstance()
def chooser = new FileChooser()
chooser.setTitle('Import GeoJSON (streaming)')
chooser.getExtensionFilters().addAll(
        new FileChooser.ExtensionFilter('GeoJSON', '*.geojson', '*.json', '*.geojson.gz', '*.json.gz'),
        new FileChooser.ExtensionFilter('All files', '*.*')
)

def fileTask = new FutureTask({ chooser.showOpenDialog(gui.getStage()) } as java.util.concurrent.Callable)
if (Platform.isFxApplicationThread()) {
    fileTask.run()
} else {
    Platform.runLater(fileTask)
}
def file = fileTask.get()
if (file == null) {
    print 'Import canceled.'
    return
}

// Access the extension importer method.
Class<?> extClass = Class.forName('qupath.ext.load_obj_chunk.LoadBigGeoJSON')
Method importMethod = extClass.getDeclaredMethod(
        'importObjectsStreaming',
        ImageData,
        Path,
        Integer.TYPE,
        Boolean.TYPE
)
importMethod.setAccessible(true)

// Execute import and report summary.
int imported = (Integer) importMethod.invoke(null, imageData, file.toPath(), chunkSize, clearExisting)
print "Imported ${imported} objects from ${file.getAbsolutePath()}"
