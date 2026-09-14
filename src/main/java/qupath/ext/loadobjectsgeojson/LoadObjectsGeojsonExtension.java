package qupath.ext.loadobjectsgeojson;

import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.Node;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;

public class LoadObjectsGeojsonExtension implements QuPathExtension {

    private static final Logger logger = LoggerFactory.getLogger(LoadObjectsGeojsonExtension.class);

    private static final String IMPORT_MENU_TEXT = "Import large GeoJSON (streaming)...";
    private static final String CLEAR_MENU_TEXT = "Clear existing objects before import";
    private static final String IMPORT_TOOLTIP = "Import .geojson/.json/.gz by streaming features in chunks (default 1000).";
    private static final String CLEAR_TOOLTIP = "If enabled, remove all current objects before importing new ones.";

    @Override
    public String getName() {
        return "Load Objects GeoJSON Extension";
    }

    @Override
    public String getDescription() {
        return "Import very large GeoJSON files using a streaming parser";
    }

    @Override
    public void installExtension(QuPathGUI qupath) {
        Menu objectsMenu = qupath.getMenu("Objects", true);
        MenuItem importItem = new MenuItem(IMPORT_MENU_TEXT);
        importItem.setGraphic(createTooltipGraphic(IMPORT_TOOLTIP));
        CheckMenuItem clearExisting = new CheckMenuItem(CLEAR_MENU_TEXT);
        clearExisting.setGraphic(createTooltipGraphic(CLEAR_TOOLTIP));

        importItem.setOnAction(event -> {
            if (qupath.getImageData() == null) {
                logger.warn("No image is open; import canceled");
                return;
            }

            FileChooser chooser = new FileChooser();
            chooser.setTitle("Import GeoJSON (streaming)");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("GeoJSON", "*.geojson", "*.json", "*.geojson.gz", "*.json.gz"),
                    new FileChooser.ExtensionFilter("All files", "*.*")
            );

            var file = chooser.showOpenDialog(qupath.getStage());
            if (file == null) {
                return;
            }

            try {
                logger.info(
                        "Starting GeoJSON import from {} (chunkSize={}, clearExisting={})",
                        file.getAbsolutePath(),
                        GeoJsonStreamingImporter.DEFAULT_CHUNK_SIZE,
                        clearExisting.isSelected()
                );
                int imported = GeoJsonStreamingImporter.importObjectsStreaming(
                        qupath.getImageData(),
                        file.toPath(),
                        GeoJsonStreamingImporter.DEFAULT_CHUNK_SIZE,
                        clearExisting.isSelected()
                );
                logger.info("Imported {} objects from {}", imported, file.getAbsolutePath());
            } catch (Exception e) {
                logger.error("Failed to import GeoJSON: {}", e.getMessage(), e);
            }
        });

        objectsMenu.getItems().add(importItem);
        objectsMenu.getItems().add(clearExisting);
    }

    private static Node createTooltipGraphic(String text) {
        Label infoLabel = new Label("i");
        infoLabel.setStyle("-fx-font-size: 10px; -fx-opacity: 0.8;");
        Tooltip.install(infoLabel, new Tooltip(text));
        return infoLabel;
    }
}
