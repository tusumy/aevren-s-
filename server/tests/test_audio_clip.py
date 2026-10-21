#!/usr/bin/env python3
"""Regression tests for opt-in playback clips. Standard library only."""
import json
import os
import sys
import tempfile
import threading
import time
import unittest
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from http.server import ThreadingHTTPServer

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from linjian_server import Handler, State


class PlaybackClipContractTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.previous = {k: os.environ.get(k) for k in ("LINJIAN_DATA_DIR", "LINJIAN_TOKEN")}
        os.environ["LINJIAN_DATA_DIR"] = self.temp.name
        os.environ["LINJIAN_TOKEN"] = "test-token-long-enough"
        self.state = State()
        Handler.state = self.state
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.url = "http://127.0.0.1:%d" % self.server.server_port

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=3)
        self.temp.cleanup()
        for k, value in self.previous.items():
            if value is None: os.environ.pop(k, None)
            else: os.environ[k] = value

    def get(self, path, token="test-token-long-enough"):
        req = Request(self.url + path, headers={"X-Auth-Token": token})
        with urlopen(req, timeout=5) as response:
            return response.status, response.read()

    def post(self, path, data, token="test-token-long-enough", device="android-phone"):
        req = Request(self.url + path, data=data, method="POST",
                      headers={"X-Auth-Token": token, "X-Device-Id": device,
                               "Content-Type": "audio/wav"})
        with urlopen(req, timeout=5) as response:
            return response.status, json.loads(response.read())

    def test_requires_authorization(self):
        with self.assertRaises(HTTPError) as error:
            self.post("/api/audio/clip", b"RIFF" + bytes(4) + b"WAVE" + bytes(40), token="wrong")
        self.assertEqual(error.exception.code, 403)

    def test_clip_roundtrip_and_ram_only(self):
        clip = b"RIFF" + bytes(4) + b"WAVE" + bytes(128)
        code, body = self.post("/api/audio/clip", clip)
        self.assertEqual(code, 200)
        self.assertTrue(body["ok"])
        code, latest = self.get("/api/audio/latest?device_id=android-phone")
        self.assertEqual((code, latest), (200, clip))
        code, meta = self.get("/api/audio/latest.json?device_id=android-phone")
        self.assertEqual(json.loads(meta)["size"], len(clip))
        self.assertFalse((Path(self.temp.name) / "audio").exists())

    def test_stale_clip_is_rejected(self):
        clip = b"RIFF" + bytes(4) + b"WAVE" + bytes(128)
        self.post("/api/audio/clip", clip, device="A")
        with self.state.audio_lock:
            self.state.audio_clips["A"]["mtime"] = time.time() - 181
        with self.assertRaises(HTTPError) as error:
            self.get("/api/audio/latest?device_id=A")
        self.assertEqual(error.exception.code, 404)

    def test_invalid_audio_and_cross_device(self):
        with self.assertRaises(HTTPError) as error:
            self.post("/api/audio/clip", b"not-a-wave" * 12)
        self.assertEqual(error.exception.code, 400)
        with self.assertRaises(HTTPError) as error:
            self.get("/api/audio/latest.json?device_id=someone-else")
        self.assertEqual(error.exception.code, 404)


if __name__ == "__main__":
    unittest.main()
