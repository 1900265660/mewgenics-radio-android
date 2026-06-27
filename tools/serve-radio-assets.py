#!/usr/bin/env python3
"""Serve generated radio assets for local Android streaming tests.

Python's built-in SimpleHTTPRequestHandler is convenient, but Android's
Media3/ExoPlayer expects byte range requests for robust seeking/buffering.
This tiny server keeps the local test path close to a real CDN by supporting
Range, HEAD, cache headers, and predictable MIME types.
"""

from __future__ import annotations

import argparse
import mimetypes
import os
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import BinaryIO
from urllib.parse import unquote, urlsplit


DEFAULT_CHUNK_SIZE = 1024 * 1024


class RangeRequestHandler(BaseHTTPRequestHandler):
    server_version = "MewgenicsRadioAssetServer/1.0"

    def do_GET(self) -> None:
        self._serve(send_body=True)

    def do_HEAD(self) -> None:
        self._serve(send_body=False)

    def end_headers(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Accept-Ranges", "bytes")
        super().end_headers()

    def log_message(self, fmt: str, *args: object) -> None:
        if not getattr(self.server, "quiet", False):
            super().log_message(fmt, *args)

    def _serve(self, send_body: bool) -> None:
        path = self._resolve_path()
        if path is None:
            self.send_error(HTTPStatus.FORBIDDEN, "Path is outside the asset root")
            return

        if not path.exists() or not path.is_file():
            self.send_error(HTTPStatus.NOT_FOUND, "File not found")
            return

        file_size = path.stat().st_size
        range_header = self.headers.get("Range")
        byte_range = self._parse_range(range_header, file_size) if range_header else None

        if range_header and byte_range is None:
            self.send_response(HTTPStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
            self.send_header("Content-Range", f"bytes */{file_size}")
            self.send_header("Content-Length", "0")
            self.end_headers()
            return

        start, end = byte_range if byte_range else (0, file_size - 1)
        content_length = max(0, end - start + 1)
        status = HTTPStatus.PARTIAL_CONTENT if byte_range else HTTPStatus.OK

        self.send_response(status)
        self.send_header("Content-Type", self._content_type(path))
        self.send_header("Content-Length", str(content_length))
        self.send_header("Cache-Control", "public, max-age=3600")
        if byte_range:
            self.send_header("Content-Range", f"bytes {start}-{end}/{file_size}")
        self.end_headers()

        if send_body and content_length > 0:
            with path.open("rb") as input_file:
                input_file.seek(start)
                self._copy(input_file, content_length)

    def _resolve_path(self) -> Path | None:
        root: Path = self.server.root.resolve()
        request_path = unquote(urlsplit(self.path).path)
        relative = request_path.lstrip("/").replace("/", os.sep)
        candidate = (root / relative).resolve()

        try:
            candidate.relative_to(root)
        except ValueError:
            return None
        return candidate

    @staticmethod
    def _parse_range(range_header: str, file_size: int) -> tuple[int, int] | None:
        if not range_header.startswith("bytes=") or "," in range_header:
            return None

        start_raw, _, end_raw = range_header.removeprefix("bytes=").partition("-")
        try:
            if start_raw == "":
                suffix_length = int(end_raw)
                if suffix_length <= 0:
                    return None
                start = max(file_size - suffix_length, 0)
                end = file_size - 1
            else:
                start = int(start_raw)
                end = int(end_raw) if end_raw else file_size - 1
        except ValueError:
            return None

        if start < 0 or end < start or start >= file_size:
            return None
        return start, min(end, file_size - 1)

    @staticmethod
    def _content_type(path: Path) -> str:
        suffix = path.suffix.lower()
        if suffix == ".json":
            return "application/json; charset=utf-8"
        if suffix == ".gon":
            return "text/plain; charset=utf-8"
        if suffix == ".opus":
            return "audio/ogg"
        guessed, _ = mimetypes.guess_type(str(path))
        return guessed or "application/octet-stream"

    def _copy(self, input_file: BinaryIO, remaining: int) -> None:
        while remaining > 0:
            chunk = input_file.read(min(DEFAULT_CHUNK_SIZE, remaining))
            if not chunk:
                break
            self.wfile.write(chunk)
            remaining -= len(chunk)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Serve dist/radio-assets for local Android streaming tests.",
    )
    parser.add_argument(
        "--root",
        default="dist/radio-assets/128kbps",
        help="Directory containing manifest.json, radio.gon, and audio/.",
    )
    parser.add_argument("--host", default="0.0.0.0", help="Bind host.")
    parser.add_argument("--port", default=8088, type=int, help="Bind port.")
    parser.add_argument("--quiet", action="store_true", help="Disable request logs.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    root = Path(args.root).resolve()
    if not root.exists() or not root.is_dir():
        raise SystemExit(f"Asset root does not exist: {root}")

    server = ThreadingHTTPServer((args.host, args.port), RangeRequestHandler)
    server.root = root
    server.quiet = args.quiet

    print(f"Serving {root}")
    print(f"Listening on http://{args.host}:{args.port}/")
    print("Press Ctrl+C to stop.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping server.")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
