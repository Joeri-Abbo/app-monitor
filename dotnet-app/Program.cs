using System.Globalization;

using DotnetApp;

using Prometheus;

var builder = WebApplication.CreateBuilder(args);
var app = builder.Build();

AppMetrics.RegisterCollectCallback();

// Counts every handled request, including the ones that fall through to 404.
app.Use(async (context, next) =>
{
    await next();

    // Skip the scrape itself, to avoid the exporter counting its own traffic on
    // each Prometheus poll.
    var route = Route(context.Request.Path);
    if (route != "/metrics")
    {
        AppMetrics.ObserveRequest(route, context.Response.StatusCode);
    }
});

app.MapGet("/", () => Results.Content(RenderHome(), "text/html"));

app.MapGet("/dotnetinfo", () => Results.Content(DotnetInfo.Render(), "text/html"));

// Serves the default registry in Prometheus text format.
app.MapMetrics("/metrics");

app.Run();

/// <summary>
/// Normalise a path into a low-cardinality label: leading/trailing slashes are
/// collapsed so <c>/dotnetinfo/</c> and <c>/dotnetinfo</c> share a series.
/// </summary>
static string Route(PathString path) => "/" + (path.Value ?? string.Empty).Trim('/');

static string RenderHome()
{
    var memory = SystemReadings.GetSystemMemory();
    var load = SystemReadings.GetSystemLoad();
    var cpus = SystemReadings.SystemCpuCount();

    var systemMemoryRow = memory is null
        ? Html.Row("System memory", "n/a (not Linux)")
        : Html.Row(
            "System memory used",
            string.Create(
                CultureInfo.InvariantCulture,
                $"{Html.Mib(memory.Used)} / {Html.Mib(memory.Total)} ({memory.UsedPercentage:F0}%)"));

    var loadRow = load is null
        ? string.Empty
        : Html.Row(
            "Load average (1/5/15m)",
            string.Create(
                CultureInfo.InvariantCulture,
                $"{load.One:F2} / {load.Five:F2} / {load.Fifteen:F2} across {cpus} CPU{(cpus == 1 ? "" : "s")}"));

    var rows = string.Concat(
        systemMemoryRow,
        loadRow,
        Html.Row("Managed heap", Html.Mib(SystemReadings.ManagedMemoryBytes())),
        Html.Row("Allocated since start", Html.Mib(SystemReadings.AllocatedBytes())),
        Html.Row("Working set", Html.Mib(SystemReadings.WorkingSetBytes())),
        Html.Row(
            "This process CPU",
            string.Create(CultureInfo.InvariantCulture, $"{SystemReadings.ProcessCpuSeconds():F3}s")),
        Html.Row("OS threads", SystemReadings.ThreadCount()),
        Html.Row(
            "Uptime",
            string.Create(CultureInfo.InvariantCulture, $"{SystemReadings.UptimeSeconds():F1}s")));

    return $"""
        <!doctype html>
        <html lang="en">
        <head>
            <meta charset="utf-8">
            <title>dotnet-app</title>
            {Html.Style}
        </head>
        <body>
            <h1>🟪 dotnet-app</h1>
            <p>A small C# website served by <strong>ASP.NET Core</strong>.</p>
            <ul>
                <li><a href="/dotnetinfo"><code>/dotnetinfo</code></a> &mdash; the full .NET runtime report</li>
                <li><a href="/metrics"><code>/metrics</code></a> &mdash; Prometheus metrics exporter</li>
            </ul>
            <h2>Live resource usage</h2>
            <table>
                {rows}
            </table>
            <p><small>Full time series is on <a href="/metrics"><code>/metrics</code></a>.</small></p>
        </body>
        </html>
        """;
}
