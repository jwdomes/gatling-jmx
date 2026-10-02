package jmx2gatling.convert;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import jmx2gatling.model.JmxElement;
import jmx2gatling.model.Property;
import jmx2gatling.model.Props;

/** Maps an {@code HTTPSamplerProxy} (with the headers and defaults in its scope) to a {@link RequestSpec}. */
final class RequestMapper {

    /** Bodies longer than this are written to a resource file instead of staying inline. */
    static final int INLINE_BODY_LIMIT = 200;

    private static final Set<String> FORM_METHODS = Set.of("POST", "PUT", "PATCH");
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private record Argument(String name, String value) {
    }

    private record FileArg(String path, String paramName, String mimeType) {
    }

    private final Conversion c;
    private final ElTranslator el;
    private final StaticValues statics;

    RequestMapper(Conversion c, ElTranslator el, StaticValues statics) {
        this.c = c;
        this.el = el;
        this.statics = statics;
    }

    RequestSpec map(JmxElement sampler, Scope scope, StepNotes notes) {
        Props p = sampler.props();
        Scope.Defaults defaults = scope.defaults();
        defaults.sources().forEach(c.appliedElements::add);

        String method = p.string("HTTPSampler.method").trim().toUpperCase(Locale.ROOT);
        if (method.isEmpty()) {
            method = "GET";
        }
        String name = sampler.name().isBlank() ? method + " " + p.string("HTTPSampler.path") : sampler.name();
        RequestSpec spec = new RequestSpec(el.toEl(name, sampler, notes), method, url(sampler, defaults, notes));

        for (Scope.Header h : scope.headers()) {
            c.appliedElements.add(h.source());
            spec.headers.add(new RequestSpec.NameValue(h.name(), el.toEl(h.value(), sampler, notes)));
        }

        List<Argument> arguments = arguments(p);
        if (!p.bool("HTTPSampler.postBodyRaw", false)) {
            addDefaultArguments(arguments, defaults);
        }
        List<FileArg> files = files(p);
        boolean rawBody = p.bool("HTTPSampler.postBodyRaw", false)
            || !arguments.isEmpty() && arguments.stream().allMatch(a -> a.name().isEmpty());
        if (rawBody) {
            StringBuilder body = new StringBuilder();
            arguments.forEach(a -> body.append(a.value()));
            if (!BODY_METHODS.contains(method)) {
                notes.comments.addAll(c.todo(sampler, "Request body on a " + method + " request is not sent by Gatling's " + method + " DSL", body.toString()));
            } else if (body.length() > 0) {
                spec.body = body(sampler, body.toString(), notes);
            }
        } else if (FORM_METHODS.contains(method)) {
            for (Argument a : arguments) {
                spec.formParams.add(new RequestSpec.NameValue(el.toEl(a.name(), sampler, notes), el.toEl(a.value(), sampler, notes)));
            }
        } else {
            for (Argument a : arguments) {
                spec.queryParams.add(new RequestSpec.NameValue(el.toEl(a.name(), sampler, notes), el.toEl(a.value(), sampler, notes)));
            }
        }
        mapFiles(sampler, spec, files, arguments, notes);
        if (p.bool("HTTPSampler.DO_MULTIPART_POST", false) && FORM_METHODS.contains(method)) {
            spec.multipart = true;
        }

        if (!p.bool("HTTPSampler.follow_redirects", false) && !p.bool("HTTPSampler.auto_redirects", false)) {
            spec.disableFollowRedirect = true;
        }
        boolean embedded = p.has("HTTPSampler.image_parser") ? p.bool("HTTPSampler.image_parser", false)
            : defaults.sources().stream().anyMatch(d -> d.props().bool("HTTPSampler.image_parser", false));
        if (embedded) {
            notes.comments.addAll(c.todo(sampler, "\"Retrieve all embedded resources\" is on; Gatling does not fetch them unless you add"
                + " http.inferHtmlResources() to the protocol or list them with .resources(...)", ""));
        }
        approximateIgnoredSettings(sampler, p);
        return spec;
    }

    private Origins.Url url(JmxElement sampler, Scope.Defaults defaults, StepNotes notes) {
        Props p = sampler.props();
        String protocol = firstNonBlank(p.string("HTTPSampler.protocol"), defaults.protocol());
        String domain = firstNonBlank(p.string("HTTPSampler.domain"), defaults.domain());
        String port = firstNonBlank(p.string("HTTPSampler.port"), defaults.port());
        String path = firstNonBlank(p.string("HTTPSampler.path"), defaults.path());

        String lowerPath = path.toLowerCase(Locale.ROOT);
        if (lowerPath.startsWith("http://") || lowerPath.startsWith("https://")) {
            int schemeEnd = path.indexOf("://");
            int pathStart = path.indexOf('/', schemeEnd + 3);
            String authority = pathStart < 0 ? path.substring(schemeEnd + 3) : path.substring(schemeEnd + 3, pathStart);
            protocol = path.substring(0, schemeEnd);
            int colon = authority.lastIndexOf(':');
            domain = colon < 0 ? authority : authority.substring(0, colon);
            port = colon < 0 ? "" : authority.substring(colon + 1);
            path = pathStart < 0 ? "/" : path.substring(pathStart);
        }
        if (protocol.isEmpty()) {
            protocol = port.equals("443") ? "https" : "http";
        }
        protocol = protocol.toLowerCase(Locale.ROOT);
        if (port.equals("80") && protocol.equals("http") || port.equals("443") && protocol.equals("https")) {
            port = "";
        }
        if (path.isEmpty()) {
            path = "/";
        } else if (!path.startsWith("/") && !path.startsWith("${")) {
            path = "/" + path;
        }
        String pathEl = el.toEl(path, sampler, notes);

        if (domain.isEmpty()) {
            notes.comments.addAll(c.todo(sampler, "No server name on the sampler or in HTTP Request Defaults; the request uses BASE_URL (set -DbaseUrl=...)", ""));
            domain = "localhost";
        }
        String origin = protocol + "://" + domain + (port.isEmpty() ? "" : ":" + port);
        Optional<String> originExpr = statics.string(origin, sampler);
        if (originExpr.isPresent()) {
            return c.origins.url(origin, originExpr.get(), pathEl);
        }
        c.approximate(sampler, "Host depends on runtime variables, so the request uses a full URL", "This request does not use the protocol's base URL.");
        return new Origins.Url(null, el.toEl(origin, sampler, notes) + pathEl);
    }

    private RequestSpec.Body body(JmxElement sampler, String body, StepNotes notes) {
        String bodyEl = el.toEl(body, sampler, notes);
        if (!c.allBodiesToFiles && body.length() <= INLINE_BODY_LIMIT) {
            return new RequestSpec.InlineBody(bodyEl);
        }
        String trimmed = body.trim();
        String extension = trimmed.startsWith("{") || trimmed.startsWith("[") ? ".json" : trimmed.startsWith("<") ? ".xml" : ".txt";
        String path = c.bodiesResourceDir + c.bodyFileNames.fileStem(sampler.name(), "body") + extension;
        c.bodyFiles.put(path, bodyEl);
        return new RequestSpec.ElFile(path);
    }

    private void mapFiles(JmxElement sampler, RequestSpec spec, List<FileArg> files, List<Argument> arguments, StepNotes notes) {
        if (files.isEmpty()) {
            return;
        }
        if (!BODY_METHODS.contains(spec.method)) {
            notes.comments.addAll(c.todo(sampler, "File upload on a " + spec.method + " request", ""));
            return;
        }
        boolean singleBodyFile = files.size() == 1 && files.get(0).paramName().isEmpty();
        if (singleBodyFile && arguments.isEmpty() && spec.body == null) {
            FileArg f = files.get(0);
            spec.body = new RequestSpec.RawFile(el.toEl(f.path(), sampler, notes));
            boolean hasContentType = spec.headers.stream().anyMatch(h -> h.name().equalsIgnoreCase("Content-Type"));
            if (!f.mimeType().isEmpty() && !hasContentType) {
                spec.headers.add(new RequestSpec.NameValue("Content-Type", f.mimeType()));
            }
            approximateFilePath(sampler, f);
            return;
        }
        if (files.stream().anyMatch(f -> f.paramName().isEmpty()) || spec.body != null) {
            notes.comments.addAll(c.todo(sampler, "File upload mixing a raw file body with parameters", ""));
            return;
        }
        for (FileArg f : files) {
            String fileName = f.path().substring(Math.max(f.path().lastIndexOf('/'), f.path().lastIndexOf('\\')) + 1);
            spec.fileParts.add(new RequestSpec.FilePart(el.toEl(f.paramName(), sampler, notes), el.toEl(f.path(), sampler, notes), f.mimeType(), fileName));
            approximateFilePath(sampler, f);
        }
        spec.multipart = true;
    }

    private void approximateFilePath(JmxElement sampler, FileArg f) {
        c.approximate(sampler, "Upload file `" + f.path() + "` referenced by path",
            "Gatling resolves the path on its classpath (the resources folder) first, then on the file system. Copy the file next to the simulation's resources or keep it at this path.");
    }

    private void approximateIgnoredSettings(JmxElement sampler, Props p) {
        for (String timeout : List.of("HTTPSampler.connect_timeout", "HTTPSampler.response_timeout")) {
            if (!p.string(timeout).isBlank()) {
                c.approximate(sampler, timeout + " = " + p.string(timeout) + " not converted",
                    "Gatling has no per-request timeout; set gatling.http.requestTimeout in gatling.conf if it matters.");
            }
        }
        if (p.has("HTTPSampler.use_keepalive") && !p.bool("HTTPSampler.use_keepalive", true)) {
            c.approximate(sampler, "Keep-alive was off", "Gatling keeps connections alive.");
        }
        String encoding = p.string("HTTPSampler.contentEncoding").trim();
        if (!encoding.isEmpty() && !encoding.equalsIgnoreCase("UTF-8")) {
            c.approximate(sampler, "Content encoding " + encoding + " not converted", "Gatling encodes request bodies and parameters as UTF-8 by default.");
        }
        for (String setting : List.of("HTTPSampler.ipSource", "HTTPSampler.proxyHost")) {
            if (!p.string(setting).isBlank()) {
                c.approximate(sampler, setting + " not converted", "Configure the equivalent on the Gatling protocol (localAddress / proxy) if needed.");
            }
        }
    }

    private static List<Argument> arguments(Props p) {
        List<Argument> result = new ArrayList<>();
        Optional<Property.Element> args = p.element("HTTPsampler.Arguments");
        if (args.isEmpty()) {
            return result;
        }
        for (Property item : args.get().props().collection("Arguments.arguments")) {
            if (item instanceof Property.Element arg) {
                String name = arg.props().has("Argument.name") ? arg.props().string("Argument.name") : arg.name();
                String value = arg.props().string("Argument.value");
                if (!name.isEmpty() || !value.isEmpty()) {
                    result.add(new Argument(name, value));
                }
            }
        }
        return result;
    }

    /** JMeter adds the defaults' parameters that the sampler does not already have. */
    private static void addDefaultArguments(List<Argument> arguments, Scope.Defaults defaults) {
        for (JmxElement d : defaults.sources()) {
            for (Argument a : arguments(d.props())) {
                if (!a.name().isEmpty() && arguments.stream().noneMatch(existing -> existing.name().equals(a.name()))) {
                    arguments.add(a);
                }
            }
        }
    }

    private static List<FileArg> files(Props p) {
        List<FileArg> result = new ArrayList<>();
        Optional<Property.Element> files = p.element("HTTPsampler.Files");
        if (files.isEmpty()) {
            return result;
        }
        for (Property item : files.get().props().collection("HTTPFileArgs.files")) {
            if (item instanceof Property.Element f) {
                String path = f.props().string("File.path");
                if (!path.isBlank()) {
                    result.add(new FileArg(path, f.props().string("File.paramname").trim(), f.props().string("File.mimetype").trim()));
                }
            }
        }
        return result;
    }

    private static String firstNonBlank(String a, String b) {
        return !a.isBlank() ? a.trim() : b.trim();
    }
}
