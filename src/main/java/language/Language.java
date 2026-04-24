package language;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.fxmisc.richtext.model.StyleSpans;

public record Language(
    String displayName,
    List<String> extensions,
    String iconPath,
    String languageId,
    Supplier<List<String>> lspCommand,
    Function<String, StyleSpans<Collection<String>>> styler,
    String stylesheetResource
) {
    public boolean supportsLsp() {
        return lspCommand != null;
    }

    public List<String> buildLspCommand() {
        return lspCommand != null ? lspCommand.get() : List.of();
    }
}
