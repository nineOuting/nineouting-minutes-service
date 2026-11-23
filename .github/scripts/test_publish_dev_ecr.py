"""Exercise publication guards and exact config verification without a registry."""
import hashlib
import importlib.util
import io
from contextlib import redirect_stdout, redirect_stderr
import runpy
from contextlib import contextmanager
import sys


@contextmanager
def stdin(stream):
    previous = sys.stdin
    sys.stdin = stream
    try:
        yield
    finally:
        sys.stdin = previous
import json
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('publish-dev-ecr.sh').resolve()
SPEC = importlib.util.spec_from_file_location('image_config_digest', SCRIPT.with_name('image_config_digest.py'))
HELPER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HELPER)
CONFIG = {'os': 'linux', 'architecture': 'amd64', 'rootfs': {'type': 'layers', 'diff_ids': ['sha256:' + 'a' * 64]}}
RAW = json.dumps(CONFIG, sort_keys=True, separators=(',', ':')).encode()
CONFIG_DIGEST = 'sha256:' + hashlib.sha256(RAW).hexdigest()
MOCK = r'''#!/usr/bin/env python3
import hashlib, io, json, os, sys, tarfile
a = sys.argv[1:]
case = os.environ['CASE']
def digest(c): return 'sha256:' + c * 64
config = {'os': 'linux', 'architecture': 'amd64', 'rootfs': {'type': 'layers', 'diff_ids': [digest('a')]}}
raw = json.dumps(config, sort_keys=True, separators=(',', ':')).encode()
expected = 'sha256:' + hashlib.sha256(raw).hexdigest()
with open(os.environ['MOCK_LOG'], 'a') as f: f.write(json.dumps(a) + '\n')
if a[:2] == ['image', 'save']:
    if case == 'save-fails': sys.exit(1)
    remote = 'amazonaws.com/' in a[2]
    if remote and case == 'wrong-pulled-id': config['changed'] = True
    if case == 'wrong-platform' or (remote and case == 'wrong-pulled-platform'): config['architecture'] = 'arm64'
    raw = json.dumps(config, sort_keys=True, separators=(',', ':')).encode()
    name = 'blobs/sha256/' + hashlib.sha256(raw).hexdigest()
    with tarfile.open(a[a.index('-o') + 1], 'w') as t:
        for path, content in [('manifest.json', json.dumps([{'Config': name, 'RepoTags': ['image:test'], 'Layers': []}]).encode()), (name, raw)]:
            info = tarfile.TarInfo(path); info.size = len(content); t.addfile(info, io.BytesIO(content))
elif a[:2] == ['image', 'inspect']:
    if a[-1] == '{{.Id}}': print(digest('e') if case == 'containerd-id' else expected)
    else: print('linux/arm64' if case == 'wrong-platform' else 'linux/amd64')
elif a[0] == 'tag': pass
elif a[0] == 'push': sys.exit(1 if case == 'push-fails' else 0)
elif a[0] == 'pull': sys.exit(1 if case == 'pull-fails' else 0)
elif a[:3] == ['buildx', 'imagetools', 'inspect']:
    if '--raw' in a:
        if case == 'missing-config': print(json.dumps({'manifests': []}))
        else: print(json.dumps({'config': {'digest': digest('f') if case == 'wrong-config' else expected}, 'layers': []}))
    else: print('Digest: ' + ('invalid' if case == 'bad-digest' else digest('e')))
else: sys.exit(99)
'''


class Publish(unittest.TestCase):
    def exercise(self, case='pass', overrides=None):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td)
            fake = p / 'docker'; fake.write_text(MOCK); fake.chmod(0o755)
            env = dict(os.environ, PATH=str(p) + os.pathsep + os.environ['PATH'], CASE=case,
                       MOCK_LOG=str(p / 'calls'), GITHUB_REPOSITORY='nineOuting/nineouting-minutes-service',
                       GITHUB_REF='refs/heads/dev', GITHUB_EVENT_NAME='push', EXPECTED_ACCOUNT='443845329525',
                       AWS_REGION='ap-northeast-2', LOCAL_IMAGE='nineouting-verified:local',
                       TARGET_IMAGE='443845329525.dkr.ecr.ap-northeast-2.amazonaws.com/nineouting-dev-minutes',
                       GITHUB_SHA='1' * 40, GITHUB_RUN_ID='42', GITHUB_RUN_ATTEMPT='1',
                       GITHUB_OUTPUT=str(p / 'output'), GITHUB_STEP_SUMMARY=str(p / 'summary'))
            env.update(overrides or {})
            result = subprocess.run(['bash', str(SCRIPT)], cwd=p, env=env, capture_output=True, text=True)
            calls = [json.loads(line) for line in (p / 'calls').read_text().splitlines()] if (p / 'calls').exists() else []
            evidence = (p / 'image-result.txt').read_text() if (p / 'image-result.txt').exists() else ''
            output = (p / 'output').read_text() if (p / 'output').exists() else ''
            return result, calls, evidence, output

    def test_exact_config_is_verified_with_both_image_store_id_kinds(self):
        for case in ['pass', 'containerd-id']:
            with self.subTest(case=case):
                r, calls, evidence, output = self.exercise(case)
                self.assertEqual(r.returncode, 0, r.stderr)
                self.assertEqual(len(evidence.splitlines()), 4)
                self.assertIn('image.digest=sha256:' + 'e' * 64, evidence)
                self.assertIn('image_digest=sha256:' + 'e' * 64, output)
                self.assertTrue(any(c[0] == 'push' and 'amazonaws.com/' in c[1] for c in calls))
                self.assertEqual(sum(c[:2] == ['image', 'save'] for c in calls), 2)
                self.assertFalse(any('{{.Id}}' in c for c in calls))
                self.assertFalse(any(c[:3] == ['buildx', 'imagetools', 'create'] for c in calls))

    def test_failures_emit_no_verified_evidence(self):
        for case in ['wrong-config', 'wrong-pulled-id', 'wrong-platform', 'wrong-pulled-platform',
                     'bad-digest', 'missing-config', 'push-fails', 'pull-fails', 'save-fails']:
            with self.subTest(case=case):
                r, _, evidence, output = self.exercise(case)
                self.assertNotEqual(r.returncode, 0)
                self.assertEqual((evidence, output), ('', ''))
                self.assertIn('::error::', r.stderr + r.stdout)

    def test_wrong_context_never_contacts_registry(self):
        for override in [{'GITHUB_REPOSITORY': 'other/repo'}, {'GITHUB_REF': 'refs/heads/main'},
                         {'GITHUB_EVENT_NAME': 'pull_request'}, {'EXPECTED_ACCOUNT': '000000000000'},
                         {'TARGET_IMAGE': 'other/image'}, {'GITHUB_SHA': 'latest'}, {'LOCAL_IMAGE': 'other:local'},
                         {'AWS_REGION': 'us-east-1'}]:
            with self.subTest(override=override):
                r, calls, _, _ = self.exercise(overrides=override)
                self.assertNotEqual(r.returncode, 0)
                self.assertEqual(calls, [])


class Archive(unittest.TestCase):
    def read_config(self, path):
        with path.open('rb') as stream:
            return HELPER.config_digest(stream)
    def archive(self, p, config=RAW, name=None, manifests=None):
        name = name or ('blobs/sha256/' + hashlib.sha256(config).hexdigest())
        with tarfile.open(p, 'w') as t:
            for member, data in [('manifest.json', json.dumps(manifests if manifests is not None else [{'Config': name}]).encode()), (name, config)]:
                info = tarfile.TarInfo(member); info.size = len(data); t.addfile(info, io.BytesIO(data))

    def test_config_digest_is_identical_for_classic_and_containerd_archive_paths(self):
        with tempfile.TemporaryDirectory() as td:
            for name in [CONFIG_DIGEST[7:] + '.json', 'blobs/sha256/' + CONFIG_DIGEST[7:]]:
                p = Path(td) / 'image.tar'; self.archive(p, name=name)
                self.assertEqual(self.read_config(p), CONFIG_DIGEST)

    def test_corrupt_config_is_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / 'image.tar'; self.archive(p, name='blobs/sha256/' + 'f' * 64)
            with self.assertRaises(ValueError): self.read_config(p)

    def test_wrong_platform_is_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / 'image.tar'; self.archive(p, config=json.dumps(dict(CONFIG, architecture='arm64')).encode())
            with self.assertRaises(ValueError): self.read_config(p)

    def test_multiple_images_are_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / 'image.tar'; self.archive(p, manifests=[{'Config': 'x'}, {'Config': 'y'}])
            with self.assertRaises(ValueError): self.read_config(p)

    def test_unsafe_member_path_is_rejected_without_extraction(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / 'image.tar'; self.archive(p, name='../' + CONFIG_DIGEST[7:] + '.json')
            with self.assertRaises(ValueError): self.read_config(p)

    def test_missing_config_is_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / 'image.tar'; self.archive(p, manifests=[{'Config': 'missing.json'}])
            with self.assertRaises(KeyError): self.read_config(p)

    def test_invalid_member_size_and_type_are_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            for oversized in [False, True]:
                p = Path(td) / 'image.tar'
                with tarfile.open(p, 'w') as t:
                    info = tarfile.TarInfo('manifest.json')
                    if oversized:
                        data = b' ' * (1024 * 1024 + 1)
                        info.size = len(data); t.addfile(info, io.BytesIO(data))
                    else:
                        info.type = tarfile.SYMTYPE; info.linkname = 'other'; t.addfile(info)
                with self.assertRaises(ValueError): self.read_config(p)

    def test_non_digest_config_name_is_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / 'image.tar'; self.archive(p, name='config.json')
            with self.assertRaises(ValueError): self.read_config(p)

    def test_cli_reads_stdin_without_accepting_a_filesystem_path(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / 'image.tar'; self.archive(p)
            with p.open('rb') as stream, io.TextIOWrapper(stream) as input_stream, stdin(input_stream), redirect_stdout(io.StringIO()) as output:
                runpy.run_path(str(SCRIPT.with_name('image_config_digest.py')), run_name='__main__')
            self.assertEqual(output.getvalue().strip(), CONFIG_DIGEST)

    def test_cli_failure_prints_no_config_data(self):
        stream = io.TextIOWrapper(io.BytesIO(b'not-an-image'))
        with stdin(stream), redirect_stdout(io.StringIO()) as output, redirect_stderr(io.StringIO()) as errors:
            with self.assertRaises(SystemExit) as failure:
                runpy.run_path(str(SCRIPT.with_name('image_config_digest.py')), run_name='__main__')
        self.assertEqual(failure.exception.code, 1)
        self.assertEqual(output.getvalue(), '')
        self.assertIn('::error::', errors.getvalue())


if __name__ == '__main__':
    unittest.main()



