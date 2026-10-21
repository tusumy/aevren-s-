#!/usr/bin/env python3
"""Regression tests for opt-in playback clips. Standard library only."""
import base64
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


    def sample_video(self, offsets=(2100, 4300)):
        jpeg = bytes((255, 216, 255)) + bytes(200) + bytes((255, 217))
        return json.dumps({
            "capture_id": "manual-12s-unit-test",
            "captured_at_ms": 1792600000000,
            "duration_seconds": 12,
            "frames": [{"offset_ms": t, "jpeg_b64": base64.b64encode(jpeg).decode("ascii")} for t in offsets]
        }).encode("utf-8")

    def test_video_roundtrip_and_privacy(self):
        code, body = self.post("/api/video/clip", self.sample_video(), device="A")
        self.assertEqual((code, body["frame_count"]), (200, 2))
        code, raw = self.get("/api/video/latest?device_id=A")
        clip = json.loads(raw)
        self.assertEqual(clip["frame_count"], 2)
        self.assertEqual([x["offset_ms"] for x in clip["frames"]], [2100, 4300])
        self.assertTrue(clip["frames"][0]["jpeg_b64"])
        code, meta = self.get("/api/video/latest.json?device_id=A")
        self.assertEqual(json.loads(meta)["offsets_ms"], [2100, 4300])
        self.assertNotIn("frames", json.loads(meta))
        with self.assertRaises(HTTPError) as error:
            self.get("/api/video/latest?device_id=B")
        self.assertEqual(error.exception.code, 404)
        self.assertFalse((Path(self.temp.name) / "video").exists())

    def test_video_expiry_and_authorization(self):
        with self.assertRaises(HTTPError) as error:
            self.post("/api/video/clip", self.sample_video(), token="wrong")
        self.assertEqual(error.exception.code, 403)
        self.post("/api/video/clip", self.sample_video(), device="A")
        with self.state.video_lock:
            self.state.video_clips["A"]["mtime"] = time.time() - 181
        with self.assertRaises(HTTPError) as error:
            self.get("/api/video/latest?device_id=A")
        self.assertEqual(error.exception.code, 404)

    def test_video_rejects_invalid_order_and_jpeg(self):
        with self.assertRaises(HTTPError) as error:
            self.post("/api/video/clip", self.sample_video(offsets=(4300, 2100)))
        self.assertEqual(error.exception.code, 400)
        bad = json.loads(self.sample_video())
        bad["frames"][0]["jpeg_b64"] = base64.b64encode(b"not-jpeg" * 30).decode("ascii")
        with self.assertRaises(HTTPError) as error:
            self.post("/api/video/clip", json.dumps(bad).encode("utf-8"))
        self.assertEqual(error.exception.code, 400)
        bad["frames"][0]["jpeg_b64"] = "!bad-base64"
        with self.assertRaises(HTTPError) as error:
            self.post("/api/video/clip", json.dumps(bad).encode("utf-8"))
        self.assertEqual(error.exception.code, 400)

if __name__ == "__main__":
    unittest.main()
