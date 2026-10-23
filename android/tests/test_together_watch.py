#!/usr/bin/env python3
"""Round-trip regression for opt-in Together Watch frames and app audio."""
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import threading
import time
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from http.server import ThreadingHTTPServer


SOURCE = Path(__file__).resolve().parents[2] / "server" / "linjian_server.py"
spec = importlib.util.spec_from_file_location("linjian_server_under_test", SOURCE)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class TogetherWatchTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        os.environ["LINJIAN_TOKEN"] = "unit-test-watch-token"
        os.environ["LINJIAN_DATA_DIR"] = self.temp.name
        self.state = module.State()
        module.Handler.state = self.state
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), module.Handler)
        self.host = "http://127.0.0.1:" + str(self.server.server_address[1])
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)
        self.temp.cleanup()

    def request(self, path, method="GET", data=None, content_type=None, authorized=True):
        headers = {}
        if authorized: headers["X-Auth-Token"] = "unit-test-watch-token"
        if content_type: headers["Content-Type"] = content_type
        req = Request(self.host + path, method=method, data=data, headers=headers)
        with urlopen(req, timeout=3) as resp:
            return resp.status, resp.read(), resp.headers

    def test_six_frames_audio_expiry_and_auth(self):
        sid = "faafe8b9-cd1b-421a-92cd-9bc2aa927073"
        image = b"\xff\xd8" + b"J" * 150
        audio = b"RIFF" + b"0" * 100
        for i in range(6):
            status, data, _ = self.request(
                "/api/watch/frame?session_id=" + sid + "&index=" + str(i),
                method="POST", data=image, content_type="image/jpeg")
            self.assertEqual(status, 200)
            self.assertEqual(json.loads(data)["count"], i + 1)
        status, data, _ = self.request("/api/watch/audio?session_id=" + sid,
            method="POST", data=audio, content_type="audio/wav")
        self.assertTrue(json.loads(data)["audio_uploaded"])
        status, meta_bytes, _ = self.request("/api/watch/latest")
        meta = json.loads(meta_bytes)
        self.assertEqual(meta["frames"], list(range(6)))
        self.assertTrue(meta["audio_uploaded"])
        self.assertEqual(self.request(
            "/api/watch/frame?session_id=" + sid + "&index=3")[1], image)
        self.assertEqual(self.request(
            "/api/watch/audio?session_id=" + sid)[1], audio)
        with self.assertRaises(HTTPError) as err:
            self.request("/api/watch/latest", authorized=False)
        self.assertEqual(err.exception.code, 403)
        with self.assertRaises(HTTPError) as err:
            self.request("/api/watch/frame?session_id=../bad&index=1")
        self.assertEqual(err.exception.code, 404)
        meta["updated_epoch"] = time.time() - 1900
        (self.state.watch_dir / "latest.json").write_text(json.dumps(meta), encoding="utf-8")
        with self.assertRaises(HTTPError) as err:
            self.request("/api/watch/latest")
        self.assertEqual(err.exception.code, 404)
        self.assertFalse((self.state.watch_dir / sid).exists())

    def test_uploads_validate_media_type_and_size(self):
        sid = "faafe8b9-cd1b-421a-92cd-9bc2aa927073"
        for idx, body, ctype in [
            ("6", b"\xff\xd8" + b"a" * 150, "image/jpeg"),
            ("0", b"not-jpeg" * 20, "image/jpeg"),
            ("0", b"\xff\xd8" + b"a" * 150, "text/plain"),
        ]:
            with self.assertRaises(HTTPError):
                self.request("/api/watch/frame?session_id=" + sid + "&index=" + idx,
                    method="POST", data=body, content_type=ctype)


if __name__ == "__main__":
    unittest.main()
