# Server Agent Handoff: Mewgenics Radio Android

Date: 2026-06-28

## Goal

Deploy the online radio asset hosting for the Android app.

The Android source code is already on GitHub. The server agent should clone the
source code from GitHub, but the generated audio/resource files must be provided
separately by the user because they are intentionally not committed to Git.

## GitHub Source Repository

```text
https://github.com/1900265660/mewgenics-radio-android.git
```

Clone command:

```bash
git clone https://github.com/1900265660/mewgenics-radio-android.git
cd mewgenics-radio-android
```

## Important Rule

Do not ask the user to upload source code manually.

The user only needs to upload or transfer this generated resource folder from
their Windows machine:

```text
E:\Projects\mewgenics-radio-android\dist\radio-assets\128kbps
```

Expected contents:

```text
manifest.json
radio.gon
audio/music/radio/**/*.opus
```

Approximate size from the previous local build:

```text
about 549 files
about 305 MB
```

These resource files are ignored by Git and are not in the GitHub repository.

## Recommended Server Layout

Use a static file server. No application backend is required for the current
project.

Recommended target directory:

```text
/srv/mewgenics-radio/radio/v1/
```

Expected final layout:

```text
/srv/mewgenics-radio/radio/v1/manifest.json
/srv/mewgenics-radio/radio/v1/radio.gon
/srv/mewgenics-radio/radio/v1/audio/music/radio/**/*.opus
```

Recommended public URL:

```text
https://<domain>/radio/v1/manifest.json
```

Example:

```text
https://radio.example.com/radio/v1/manifest.json
```

## User Upload Task

The user can upload only the generated resource folder.

Example from Windows PowerShell:

```powershell
scp -r E:\Projects\mewgenics-radio-android\dist\radio-assets\128kbps\* `
  <ssh-user>@<server-host>:/srv/mewgenics-radio/radio/v1/
```

If the target directory does not exist, create it on the server first:

```bash
sudo mkdir -p /srv/mewgenics-radio/radio/v1
sudo chown -R "$USER:$USER" /srv/mewgenics-radio
```

## Manifest URL Warning

The local manifest may still point to the old LAN test URL:

```text
http://10.99.239.143:8088/
```

Before production use, the manifest must point to the server HTTPS base URL.

If the public asset base URL is:

```text
https://<domain>/radio/v1/
```

then the user should regenerate resources locally with:

```powershell
cd E:\Projects\mewgenics-radio-android

powershell -ExecutionPolicy Bypass -File .\tools\convert-radio-assets.ps1 `
  -BitrateKbps 128 `
  -BaseUrl "https://<domain>/radio/v1/"
```

Then upload the regenerated folder:

```text
E:\Projects\mewgenics-radio-android\dist\radio-assets\128kbps
```

## Static Server Requirements

The server must support:

```text
HTTPS
static file serving
HTTP Range requests
206 Partial Content responses for ranged .opus requests
valid access to manifest.json
valid access to .opus files
```

Recommended cache behavior:

```text
manifest.json: short cache, for example 60 seconds
.opus files: long cache, for example 1 year immutable
```

Recommended MIME types:

```text
manifest.json -> application/json
.opus -> audio/ogg
```

## Caddy Example

If using Caddy, an example configuration is:

```caddyfile
<domain> {
    root * /srv/mewgenics-radio
    encode zstd gzip

    @manifest path /radio/v1/manifest.json
    header @manifest Cache-Control "public, max-age=60"

    @opus path *.opus
    header @opus Content-Type "audio/ogg"
    header @opus Cache-Control "public, max-age=31536000, immutable"

    header {
        Access-Control-Allow-Origin "*"
        Accept-Ranges "bytes"
    }

    file_server
}
```

Validate and reload:

```bash
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
sudo systemctl status caddy
```

## Nginx Example

If using Nginx, an example server block is:

```nginx
server {
    listen 443 ssl http2;
    server_name <domain>;

    root /srv/mewgenics-radio;

    location / {
        try_files $uri =404;
        add_header Access-Control-Allow-Origin "*" always;
        add_header Accept-Ranges "bytes" always;
    }

    location = /radio/v1/manifest.json {
        types { application/json json; }
        try_files $uri =404;
        add_header Cache-Control "public, max-age=60" always;
        add_header Access-Control-Allow-Origin "*" always;
    }

    location ~* \.opus$ {
        types { audio/ogg opus; }
        try_files $uri =404;
        add_header Cache-Control "public, max-age=31536000, immutable" always;
        add_header Access-Control-Allow-Origin "*" always;
        add_header Accept-Ranges "bytes" always;
    }
}
```

Configure TLS certificate separately if not already handled by the hosting
provider.

## Server Verification

Run these checks from any machine after deployment.

Manifest:

```bash
curl -I "https://<domain>/radio/v1/manifest.json"
curl "https://<domain>/radio/v1/manifest.json" | head
```

One Opus file with Range:

```bash
curl -I -r 0-99 "https://<domain>/radio/v1/audio/music/radio/songs/catsanova.opus"
```

Expected Range result:

```text
HTTP status: 206 Partial Content
Content-Type: audio/ogg
Content-Range: bytes 0-99/...
Accept-Ranges: bytes
```

If `catsanova.opus` does not exist, inspect `manifest.json` and test any track
URL listed there.

## Android Build After Server Is Ready

The Android app should be built with the server manifest URL:

```powershell
cd E:\Projects\mewgenics-radio-android

.\gradlew.bat --no-daemon :app:testDebugUnitTest

.\gradlew.bat --no-daemon :app:assembleRelease `
  -PRADIO_MANIFEST_URL="https://<domain>/radio/v1/manifest.json"
```

For HTTPS release builds, do not pass `-PALLOW_CLEARTEXT=true`.

For temporary HTTP testing only, use `onlineDebug` or explicitly allow cleartext.

## What Not To Commit Or Upload To GitHub

Do not commit these files:

```text
dist/
app/src/debug/assets/radio/
app/src/main/assets/radio/
*.gpak
*.ogg
*.opus
*.swf
*.jks
local.properties
```

## Legal / Distribution Note

The repository is source code and tooling only. The generated `.opus` files come
from the user's local game install and may be copyrighted game assets. Treat the
server deployment as private testing unless the user confirms they have rights
to publicly redistribute those audio files.

## Short Answer For The User

Yes: for the server handoff, the user should let the server agent clone source
code from GitHub and only transfer the generated resource folder:

```text
E:\Projects\mewgenics-radio-android\dist\radio-assets\128kbps
```

However, the resource folder should be regenerated first if `manifest.json` still
contains the old LAN URL instead of the final HTTPS server URL.
