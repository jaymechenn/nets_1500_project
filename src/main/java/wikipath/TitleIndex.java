package wikipath;

import java.util.HashMap;
import java.util.Map;

/**
 * Bidirectional mapping between Wikipedia article IDs and titles.
 *
 * <p>Lookups are case-insensitive and treat {@code '_'} and {@code ' '}
 * as equivalent so users can type {@code "Kanye West"} or
 * {@code "kanye_west"} interchangeably.</p>
 */
public final class TitleIndex {

    private final String[] idToTitle;
    private final Map<String, Integer> titleToId;

    TitleIndex(String[] idToTitle, Map<String, Integer> titleToId) {
        this.idToTitle = idToTitle;
        this.titleToId = titleToId;
    }

    /** Number of indexed articles. */
    public int size() {
        return idToTitle.length;
    }

    /** Title for an article ID, or {@code null} if the ID is unknown. */
    public String titleOf(int id) {
        if (id < 0 || id >= idToTitle.length) {
            return null;
        }
        return idToTitle[id];
    }

    /**
     * Resolve a user-supplied title to a node ID.
     * Returns {@code -1} if no article matches.
     */
    public int idOf(String userTitle) {
        if (userTitle == null) {
            return -1;
        }
        Integer id = titleToId.get(normalize(userTitle));
        return id == null ? -1 : id;
    }

    static String normalize(String title) {
        // Collapse runs of whitespace/underscores into a single underscore,
        // strip leading/trailing separators, and lowercase.
        StringBuilder sb = new StringBuilder(title.length());
        boolean lastSep = true;
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            if (c == ' ' || c == '_' || c == '\t') {
                if (!lastSep) {
                    sb.append('_');
                    lastSep = true;
                }
            } else {
                sb.append(Character.toLowerCase(c));
                lastSep = false;
            }
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '_') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    static Map<String, Integer> emptyMap(int expectedSize) {
        // 0.75 load factor + slight headroom.
        return new HashMap<>((int) (expectedSize / 0.7f) + 16);
    }
}
