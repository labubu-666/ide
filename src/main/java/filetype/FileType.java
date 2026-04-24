package filetype;

import java.util.List;

public record FileType(String name, List<String> extensions, String iconPath) {
}
