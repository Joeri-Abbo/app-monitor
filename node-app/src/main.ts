import { createApp } from './app.js';

const port = Number(process.env['PORT'] ?? 8080);

createApp().listen(port, () => {
  console.log(`node-app listening on http://0.0.0.0:${port}`);
});
