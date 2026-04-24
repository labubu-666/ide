package filetype;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javafx.scene.image.Image;
import language.Language;
import language.Languages;

public class FileTypeRegistry {

    private static final String FOLDER_ICON = "editor/icons/folder.png";

    public static final FileType UNKNOWN = new FileType(
        Languages.UNKNOWN.displayName(), Languages.UNKNOWN.extensions(), Languages.UNKNOWN.iconPath()
    );

    private final Map<String, FileType> byExtension = new HashMap<>();
    private final Map<String, Image> iconCache = new HashMap<>();

    public FileTypeRegistry() {
        for (Language lang : Languages.all()) {
            FileType ft = new FileType(lang.displayName(), lang.extensions(), lang.iconPath());
            for (String ext : lang.extensions()) {
                byExtension.put(ext.toLowerCase(Locale.ROOT), ft);
            }
        }
    }

    public FileType forExtension(String ext) {
        if (ext == null || ext.isEmpty()) return UNKNOWN;
        return byExtension.getOrDefault(ext.toLowerCase(Locale.ROOT), UNKNOWN);
    }

    public FileType forPath(Path path) {
        if (path == null) return UNKNOWN;
        String name = path.getFileName() != null ? path.getFileName().toString() : "";
        int dot = name.lastIndexOf('.');
        String ext = (dot > 0 && dot < name.length() - 1) ? name.substring(dot + 1) : "";
        return forExtension(ext);
    }

    public Image folderIcon() {
        Image cached = iconCache.get(FOLDER_ICON);
        if (cached != null) return cached;
        var url = FileTypeRegistry.class.getClassLoader().getResource(FOLDER_ICON);
        if (url == null) return null;
        Image img = new Image(url.toExternalForm(), 16, 16, true, true);
        iconCache.put(FOLDER_ICON, img);
        return img;
    }

    public Image iconFor(FileType type) {
        FileType t = (type != null) ? type : UNKNOWN;
        String path = t.iconPath();
        Image cached = iconCache.get(path);
        if (cached != null) return cached;
        var url = FileTypeRegistry.class.getClassLoader().getResource(path);
        if (url == null) return null;
        Image img = new Image(url.toExternalForm(), 16, 16, true, true);
        iconCache.put(path, img);
        return img;
    }
}
