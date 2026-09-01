package com.example.javamonitor;

import org.springframework.web.util.HtmlUtils;

/**
 * Tiny HTML helpers shared by the pages. The app renders its two pages inline
 * rather than through a template engine — there is not enough markup here to
 * justify one.
 */
final class Html {

    /** Kept in sync with the PHP app's pages so the two look the same. */
    static final String STYLE = """
            <style>
                body { font-family: system-ui, sans-serif; max-width: 60rem; margin: 4rem auto; line-height: 1.6; }
                code { background: #f4f4f5; padding: .1rem .3rem; border-radius: .25rem; }
                a { color: #4f46e5; }
                h2 { margin-top: 2.5rem; border-bottom: 2px solid #e4e4e7; padding-bottom: .25rem; }
                table { border-collapse: collapse; margin-top: .5rem; width: 100%; }
                td { border: 1px solid #e4e4e7; padding: .3rem .6rem; vertical-align: top; }
                td:first-child { color: #52525b; width: 18rem; }
                th { border: 1px solid #e4e4e7; padding: .3rem .6rem; background: #f4f4f5; text-align: left; }
            </style>
            """;

    private Html() {
    }

    /** A two-cell table row with both values escaped. */
    static String row(String label, String value) {
        return "<tr><td>%s</td><td>%s</td></tr>".formatted(escape(label), escape(value));
    }

    static String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    static String mib(long bytes) {
        return "%.1f MiB".formatted(bytes / 1048576.0);
    }
}
