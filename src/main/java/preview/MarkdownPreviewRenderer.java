package preview;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

import host.EditorNavigator;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Node;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.data.MutableDataSet;

public class MarkdownPreviewRenderer implements PreviewRenderer {

    private static final MutableDataSet OPTIONS = new MutableDataSet()
        .set(Parser.EXTENSIONS, Arrays.asList(
            TablesExtension.create(),
            AutolinkExtension.create(),
            StrikethroughExtension.create(),
            TaskListExtension.create(),
            YamlFrontMatterExtension.create()
        ));

    private static final Parser PARSER = Parser.builder(OPTIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder(OPTIONS).build();

    private static final String HTML_TEMPLATE =
        "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>" +
        "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;" +
        "font-size:14px;line-height:1.6;color:#24292e;max-width:900px;margin:24px auto;padding:0 16px;}" +
        "h1,h2{border-bottom:1px solid #eaecef;padding-bottom:.3em;}" +
        "code{background:#f6f8fa;border-radius:3px;padding:.2em .4em;font-size:85%;}" +
        "pre{background:#f6f8fa;border-radius:6px;padding:16px;overflow:auto;}" +
        "pre code{background:none;padding:0;font-size:100%;}" +
        "blockquote{border-left:4px solid #dfe2e5;color:#6a737d;margin:0;padding:0 1em;}" +
        "table{border-collapse:collapse;width:100%;}" +
        "th,td{border:1px solid #dfe2e5;padding:6px 13px;}" +
        "tr:nth-child(even){background:#f6f8fa;}" +
        "a{color:#0366d6;text-decoration:none;}" +
        "a:hover{text-decoration:underline;}" +
        "img{max-width:100%;}" +
        "</style></head><body>{{body}}</body></html>";

    @Override
    public String id() {
        return "markdown";
    }

    @Override
    public String displayName() {
        return "Markdown Preview";
    }

    @Override
    public boolean supports(String extension) {
        if (extension == null) return false;
        String lower = extension.toLowerCase(Locale.ROOT);
        return lower.equals("md") || lower.equals("markdown");
    }

    String renderToHtml(String markdown) {
        Document doc = PARSER.parse(markdown == null ? "" : markdown);
        String body = RENDERER.render(doc);
        return HTML_TEMPLATE.replace("{{body}}", body);
    }

    @Override
    public Node render(String text, Path source, EditorNavigator navigator) {
        WebView webView = new WebView();
        webView.setContextMenuEnabled(false);
        String html = renderToHtml(text);

        WebEngine engine = webView.getEngine();

        // Intercept link clicks via JavaScript alert signal
        engine.setOnAlert(event -> {
            String data = event.getData();
            if (!data.startsWith("NAVIGATE:")) return;
            String url = data.substring(9);
            handleUrl(url, source, navigator);
        });

        // Inject click interceptors once the page has finished loading
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                engine.executeScript(
                    "document.querySelectorAll('a[href]').forEach(function(a) {" +
                    "  a.addEventListener('click', function(e) {" +
                    "    e.preventDefault();" +
                    "    window.alert('NAVIGATE:' + a.href);" +
                    "  });" +
                    "});"
                );
            }
        });

        engine.loadContent(html, "text/html");
        return webView;
    }

    private void handleUrl(String url, Path source, EditorNavigator navigator) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (navigator != null) {
            try {
                Path target;
                if (url.startsWith("file:")) {
                    target = Path.of(new URI(url));
                } else {
                    Path dir = source != null ? source.getParent() : Path.of(".");
                    target = (dir != null ? dir : Path.of(".")).resolve(url);
                }
                target = target.normalize();
                if (Files.exists(target)) {
                    Path resolved = target;
                    Platform.runLater(() -> navigator.openFile(resolved));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
