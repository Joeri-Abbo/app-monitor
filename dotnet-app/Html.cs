using System.Globalization;
using System.Net;

namespace DotnetApp;

/// <summary>
/// Tiny HTML helpers shared by the pages. The app renders its two pages inline
/// rather than through Razor — there is not enough markup here to justify it.
/// </summary>
internal static class Html
{
    /// <summary>Kept in sync with the other apps' pages so they all look the same.</summary>
    public const string Style = """
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

    public static string Escape(object? value) =>
        WebUtility.HtmlEncode(value?.ToString() ?? string.Empty);

    /// <summary>A two-cell table row with both values escaped.</summary>
    public static string Row(string label, object? value) =>
        $"<tr><td>{Escape(label)}</td><td>{Escape(value)}</td></tr>";

    public static string Table(params string[] rows) =>
        $"<table>{string.Concat(rows)}</table>";

    public static string Mib(double bytes) =>
        string.Create(CultureInfo.InvariantCulture, $"{bytes / 1048576.0:F1} MiB");
}
