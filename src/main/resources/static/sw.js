/* ============================================================================
   UniSubmit service worker — app shell cache + offline fallback.
   Served from the origin root (/sw.js) so its scope covers the whole app.
   Strategy:
     - navigations  → network-first, offline.html when the network is gone
     - static shell → cache-first with background refresh (stale-while-revalidate)
     - everything dynamic (/api/, /files/, non-GET) is never touched, so auth,
       CSRF-protected POSTs, and uploads behave exactly as before.
   Bump VERSION whenever the shell assets change shape.
   ============================================================================ */
const VERSION = "unisubmit-shell-v1";
const OFFLINE_URL = "/offline.html";

const SHELL = [
  OFFLINE_URL,
  "/css/base.css",
  "/js/app.js",
  "/favicon.svg",
  "/manifest.webmanifest",
  "/icons/icon.svg",
  "/icons/icon-192.png",
  "/icons/icon-512.png",
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches
      .open(VERSION)
      .then((cache) => cache.addAll(SHELL))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== VERSION).map((key) => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

const STATIC_PREFIXES = ["/css/", "/js/", "/icons/", "/images/"];
const STATIC_FILES = ["/favicon.svg", "/favicon.ico", "/manifest.webmanifest", OFFLINE_URL];

function isStaticAsset(pathname) {
  return STATIC_PREFIXES.some((prefix) => pathname.startsWith(prefix)) || STATIC_FILES.includes(pathname);
}

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET") return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  // Never intercept dynamic or private routes — polling, uploads, downloads.
  if (
    url.pathname.startsWith("/api/") ||
    url.pathname.startsWith("/files/") ||
    url.pathname.startsWith("/h2-console")
  ) {
    return;
  }

  // Page navigations: always fresh from the network (auth state, CSRF tokens),
  // with a calm offline page when the connection is gone. Pages themselves are
  // never cached — nothing personal is ever stored.
  if (request.mode === "navigate") {
    event.respondWith(fetch(request).catch(() => caches.match(OFFLINE_URL)));
    return;
  }

  // Static shell assets: instant from cache, quietly refreshed in the background.
  if (isStaticAsset(url.pathname)) {
    event.respondWith(
      caches.open(VERSION).then((cache) =>
        cache.match(request).then((cached) => {
          const refresh = fetch(request)
            .then((response) => {
              if (response && response.ok) cache.put(request, response.clone());
              return response;
            })
            .catch(() => cached);
          return cached || refresh;
        })
      )
    );
  }
});
