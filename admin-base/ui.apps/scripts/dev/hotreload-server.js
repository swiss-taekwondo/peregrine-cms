const http = require('http');

const port = parseInt(process.env.HOT_RELOAD_PORT || '35729', 10);
const host = process.env.HOT_RELOAD_HOST || '127.0.0.1';
const clients = new Set();

function writeSseEvent(response, eventName, payload) {
  response.write(`event: ${eventName}\n`);
  response.write(`data: ${JSON.stringify(payload)}\n\n`);
}

function broadcastReload(payload) {
  for (const client of clients) {
    writeSseEvent(client, 'reload', payload);
  }
}

const server = http.createServer((request, response) => {
  if (request.method === 'GET' && request.url === '/events') {
    response.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive',
      'Access-Control-Allow-Origin': '*',
    });
    response.write('\n');
    clients.add(response);
    writeSseEvent(response, 'connected', { connectedAt: Date.now() });
    request.on('close', () => {
      clients.delete(response);
    });
    return;
  }

  if (request.method === 'POST' && request.url === '/reload') {
    let body = '';
    request.on('data', chunk => {
      body += chunk.toString();
    });
    request.on('end', () => {
      let payload = { triggeredAt: Date.now() };
      if (body) {
        try {
          payload = { ...payload, ...JSON.parse(body) };
        } catch (error) {
          payload.rawBody = body;
        }
      }
      broadcastReload(payload);
      response.writeHead(204, { 'Access-Control-Allow-Origin': '*' });
      response.end();
    });
    return;
  }

  if (request.method === 'GET' && request.url === '/health') {
    response.writeHead(200, {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
    });
    response.end(JSON.stringify({ ok: true, clients: clients.size }));
    return;
  }

  response.writeHead(404, { 'Content-Type': 'text/plain' });
  response.end('Not found');
});

server.listen(port, host, () => {
  console.log(`[hotreload] listening on http://${host}:${port}`);
});

function shutdown() {
  for (const client of clients) {
    client.end();
  }
  server.close(() => {
    process.exit(0);
  });
}

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
