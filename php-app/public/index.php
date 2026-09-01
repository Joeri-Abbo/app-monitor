<?php

declare(strict_types=1);

use App\Metrics;
use Illuminate\Container\Container;
use Illuminate\Events\Dispatcher;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Routing\CallableDispatcher;
use Illuminate\Routing\Contracts\CallableDispatcher as CallableDispatcherContract;
use Illuminate\Routing\Router;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

require __DIR__ . '/../vendor/autoload.php';

$container = new Container();
Container::setInstance($container);

// Standalone illuminate/routing needs the closure dispatcher bound explicitly;
// in a full Laravel app the RoutingServiceProvider does this for us.
$container->singleton(
    CallableDispatcherContract::class,
    static fn (Container $app): CallableDispatcher => new CallableDispatcher($app),
);

$router = new Router(new Dispatcher($container), $container);
$metrics = new Metrics();

/*
|--------------------------------------------------------------------------
| Routes
|--------------------------------------------------------------------------
*/

$router->get('/', function () use ($metrics): Response {
    $mib = static fn (?int $bytes): string => $bytes === null ? 'n/a' : number_format($bytes / 1048576, 1) . ' MiB';

    $sysMem = $metrics->systemMemory();
    $load = $metrics->systemLoad();
    $cpus = $metrics->systemCpuCount();
    $cpu = $metrics->processCpu();

    $systemMemRow = $sysMem === null
        ? '<tr><td>System memory</td><td>n/a (not Linux)</td></tr>'
        : sprintf(
            '<tr><td>System memory used</td><td>%s / %s (%.0f%%)</td></tr>',
            $mib($sysMem['used']),
            $mib($sysMem['total']),
            $sysMem['used'] / $sysMem['total'] * 100,
        );

    $loadRow = $load === null
        ? ''
        : sprintf(
            '<tr><td>Load average (1/5/15m)</td><td>%.2f / %.2f / %.2f%s</td></tr>',
            $load[0],
            $load[1],
            $load[2],
            $cpus === null ? '' : sprintf(' across %d CPU%s', $cpus, $cpus === 1 ? '' : 's'),
        );

    $procMemRow = sprintf('<tr><td>This process memory</td><td>%s (peak %s)</td></tr>', $mib($metrics->processMemoryBytes()), $mib($metrics->processMemoryPeakBytes()));
    $procCpuRow = sprintf('<tr><td>This process CPU</td><td>%.3fs user / %.3fs system</td></tr>', $cpu['user'], $cpu['system']);

    $body = <<<HTML
        <!doctype html>
        <html lang="en">
        <head>
            <meta charset="utf-8">
            <title>php-app</title>
            <style>
                body { font-family: system-ui, sans-serif; max-width: 40rem; margin: 4rem auto; line-height: 1.6; }
                code { background: #f4f4f5; padding: .1rem .3rem; border-radius: .25rem; }
                a { color: #4f46e5; }
                table { border-collapse: collapse; margin-top: .5rem; }
                td { border: 1px solid #e4e4e7; padding: .3rem .6rem; }
                td:first-child { color: #52525b; }
            </style>
        </head>
        <body>
            <h1>🐘 php-app</h1>
            <p>A small PHP website served through an <strong>Illuminate</strong> router.</p>
            <ul>
                <li><a href="/phpinfo"><code>/phpinfo</code></a> &mdash; the full <code>phpinfo()</code> report</li>
                <li><a href="/metrics"><code>/metrics</code></a> &mdash; Prometheus metrics exporter</li>
            </ul>
            <h2>Live resource usage</h2>
            <table>
                {$systemMemRow}
                {$loadRow}
                {$procMemRow}
                {$procCpuRow}
            </table>
            <p><small>Full time series is on <a href="/metrics"><code>/metrics</code></a>.</small></p>
        </body>
        </html>
        HTML;

    return new Response($body);
});

$router->get('/phpinfo', function (): Response {
    ob_start();
    phpinfo();
    $html = (string) ob_get_clean();

    return new Response($html);
});

$router->get('/metrics', function () use ($metrics): Response {
    return new Response(
        $metrics->render(),
        Response::HTTP_OK,
        ['Content-Type' => $metrics->contentType()],
    );
});

/*
|--------------------------------------------------------------------------
| Dispatch
|--------------------------------------------------------------------------
*/

$request = Request::capture();

try {
    $response = $router->dispatch($request);
} catch (NotFoundHttpException) {
    $response = new Response('404 Not Found', Response::HTTP_NOT_FOUND);
}

// Record every handled request except the scrape itself, to avoid the exporter
// counting its own traffic on each Prometheus poll.
$route = '/' . trim($request->path(), '/');
if ($route !== '/metrics') {
    $metrics->observeRequest($route, $response->getStatusCode());
}

$response->send();
