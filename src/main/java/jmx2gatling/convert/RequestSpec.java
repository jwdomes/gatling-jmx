package jmx2gatling.convert;

import java.util.ArrayList;
import java.util.List;

/**
 * An HTTP request as it will be written. Values ending in {@code El} are Gatling EL text, not yet
 * quoted. It stays mutable until writing because headers shared by every request are moved to
 * the protocol afterwards.
 */
final class RequestSpec {

    record NameValue(String name, String valueEl) {
    }

    sealed interface Body permits InlineBody, ElFile, RawFile {
    }

    record InlineBody(String textEl) implements Body {
    }

    /** A body resource on the Gatling classpath, relative to the resources folder. */
    record ElFile(String resourcePath) implements Body {
    }

    record RawFile(String path) implements Body {
    }

    record FilePart(String partName, String path, String contentType, String fileName) {
    }

    final String nameEl;
    final String method;
    /** The request URL as a Java expression (quoted literal, possibly prefixed by a base URL constant). */
    final Origins.Url url;
    final List<NameValue> headers = new ArrayList<>();
    final List<NameValue> queryParams = new ArrayList<>();
    final List<NameValue> formParams = new ArrayList<>();
    final List<FilePart> fileParts = new ArrayList<>();
    final List<String> checks = new ArrayList<>();
    Body body;
    boolean multipart;
    boolean disableFollowRedirect;

    RequestSpec(String nameEl, String method, Origins.Url url) {
        this.nameEl = nameEl;
        this.method = method;
        this.url = url;
    }
}
