package preview;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class PreviewRegistry {

    private final List<PreviewRenderer> renderers;

    public PreviewRegistry() {
        renderers = List.of(new MarkdownPreviewRenderer());
    }

    public Optional<PreviewRenderer> forExtension(String ext) {
        if (ext == null || ext.isEmpty()) return Optional.empty();
        String lower = ext.toLowerCase(Locale.ROOT);
        return renderers.stream().filter(r -> r.supports(lower)).findFirst();
    }

    public Optional<PreviewRenderer> forPath(Path path) {
        if (path == null) return Optional.empty();
        String name = path.getFileName() != null ? path.getFileName().toString() : "";
        int dot = name.lastIndexOf('.');
        String ext = (dot > 0 && dot < name.length() - 1) ? name.substring(dot + 1) : "";
        return forExtension(ext);
    }

    public boolean hasPreview(String ext) {
        return forExtension(ext).isPresent();
    }
}
